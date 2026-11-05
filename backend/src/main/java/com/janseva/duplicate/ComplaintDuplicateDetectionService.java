package com.janseva.duplicate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.janseva.complaint.Complaint;
import com.janseva.complaint.ComplaintLocation;
import com.janseva.complaint.ComplaintLocationRepository;
import com.janseva.complaint.ComplaintMedia;
import com.janseva.complaint.ComplaintMediaRepository;
import com.janseva.complaint.ComplaintRepository;
import com.janseva.complaint.ComplaintStatus;
import com.janseva.config.CivicxProperties;
import com.janseva.embedding.ImageEmbedding;
import com.janseva.embedding.ImageEmbeddingRepository;
import com.janseva.embedding.ImageEmbeddingService;
import com.janseva.geo.GeoService;
import com.janseva.hashing.ImageHash;
import com.janseva.hashing.ImageHashRepository;
import com.janseva.hashing.ImageHashService;
import com.janseva.text.TextSimilarityService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ComplaintDuplicateDetectionService {

    private static final Set<ComplaintStatus> EXCLUDED_STATUSES =
            Set.of(ComplaintStatus.REJECTED, ComplaintStatus.CLOSED);

    private final ComplaintRepository complaintRepository;
    private final ComplaintLocationRepository complaintLocationRepository;
    private final ComplaintMediaRepository complaintMediaRepository;
    private final DuplicateDetectionResultRepository duplicateResultRepository;
    private final DuplicateComplaintLinkRepository duplicateLinkRepository;
    private final ImageHashRepository imageHashRepository;
    private final ImageEmbeddingRepository imageEmbeddingRepository;
    private final GeoService geoService;
    private final TextSimilarityService textSimilarityService;
    private final CivicxProperties properties;
    private final ObjectMapper objectMapper;

    public ComplaintDuplicateDetectionService(ComplaintRepository complaintRepository,
                                              ComplaintLocationRepository complaintLocationRepository,
                                              ComplaintMediaRepository complaintMediaRepository,
                                              DuplicateDetectionResultRepository duplicateResultRepository,
                                              DuplicateComplaintLinkRepository duplicateLinkRepository,
                                              ImageHashRepository imageHashRepository,
                                              ImageEmbeddingRepository imageEmbeddingRepository,
                                              GeoService geoService,
                                              TextSimilarityService textSimilarityService,
                                              CivicxProperties properties,
                                              ObjectMapper objectMapper) {
        this.complaintRepository = complaintRepository;
        this.complaintLocationRepository = complaintLocationRepository;
        this.complaintMediaRepository = complaintMediaRepository;
        this.duplicateResultRepository = duplicateResultRepository;
        this.duplicateLinkRepository = duplicateLinkRepository;
        this.imageHashRepository = imageHashRepository;
        this.imageEmbeddingRepository = imageEmbeddingRepository;
        this.geoService = geoService;
        this.textSimilarityService = textSimilarityService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DuplicateRunSummary runDetection(Long complaintId) {
        if (!properties.duplicate().enabled()) {
            return new DuplicateRunSummary(DuplicateEngineState.NO_MATCH_FOUND, 0, false);
        }
        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) {
            return new DuplicateRunSummary(DuplicateEngineState.NO_MATCH_FOUND, 0, false);
        }

        ComplaintLocation myLocation = complaint.getPrimaryLocationId() != null
                ? complaintLocationRepository.findById(complaint.getPrimaryLocationId()).orElse(null)
                : null;
        List<Long> myMediaIds = complaintMediaRepository.findByComplaintIdOrderByUploadedAtAsc(complaintId).stream()
                .map(ComplaintMedia::getId)
                .toList();
        String myText = complaint.getTitle() + " " + complaint.getDescription();

        boolean anyCandidateSignal = false;
        int persisted = 0;

        duplicateResultRepository.deleteByComplaintIdAndStatus(complaintId, DuplicateReviewStatus.PENDING_ADMIN);

        List<CandidateComplaint> candidates = selectCandidates(complaint, myLocation);
        boolean anyPossible = false;
        for (CandidateComplaint candidate : candidates) {
            SignalSet signals = computeSignals(complaint, myLocation, myMediaIds, myText, candidate);
            double score = combine(signals);
            if (score < properties.duplicate().reviewThreshold()) {
                continue;
            }
            boolean possible = score >= properties.duplicate().duplicateThreshold();
            anyCandidateSignal = true;
            anyPossible = anyPossible || possible;
            duplicateResultRepository.save(new DuplicateDetectionResult(
                    complaintId,
                    BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP),
                    possible,
                    possible ? "ADMIN_REVIEW" : "REVIEW_REQUIRED",
                    toJson(signals.toMap()),
                    candidate.complaint.getId(),
                    candidate.distanceMeters != null
                            ? BigDecimal.valueOf(candidate.distanceMeters).setScale(2, RoundingMode.HALF_UP) : null,
                    signals.imageSimilarity() != null
                            ? BigDecimal.valueOf(signals.imageSimilarity()).setScale(4, RoundingMode.HALF_UP) : null,
                    signals.categoryMatch()
            ));
            persisted++;
        }

        if (!anyCandidateSignal && myLocation == null && myMediaIds.isEmpty()) {
            return new DuplicateRunSummary(DuplicateEngineState.INSUFFICIENT_DATA, 0, false);
        }
        if (persisted == 0) {
            return new DuplicateRunSummary(DuplicateEngineState.NO_MATCH_FOUND, 0, false);
        }
        return anyPossible
                ? new DuplicateRunSummary(DuplicateEngineState.POSSIBLE_DUPLICATE, persisted, true)
                : new DuplicateRunSummary(DuplicateEngineState.REVIEW_REQUIRED, persisted, true);
    }

    private List<CandidateComplaint> selectCandidates(Complaint complaint, ComplaintLocation myLocation) {
        Set<Long> seen = new HashSet<>();
        List<CandidateComplaint> candidates = new ArrayList<>();

        if (myLocation != null) {
            GeoService.BoundingBox box = geoService.boundingBox(
                    myLocation.getLatitude().doubleValue(),
                    myLocation.getLongitude().doubleValue(),
                    properties.duplicate().locationRadiusMeters());
            List<ComplaintLocation> nearbyLocations = complaintLocationRepository.findCurrentInBoundingBox(
                    complaint.getId(),
                    BigDecimal.valueOf(box.minLat()), BigDecimal.valueOf(box.maxLat()),
                    BigDecimal.valueOf(box.minLng()), BigDecimal.valueOf(box.maxLng()));
            for (ComplaintLocation otherLocation : nearbyLocations) {
                Complaint other = otherLocation.getComplaint();
                if (!seen.add(other.getId()) || excluded(other)) {
                    continue;
                }
                double distance = geoService.haversineMeters(
                        myLocation.getLatitude(), myLocation.getLongitude(),
                        otherLocation.getLatitude(), otherLocation.getLongitude());
                if (distance > properties.duplicate().locationRadiusMeters()) {
                    continue;
                }
                candidates.add(new CandidateComplaint(other, distance, otherLocation));
            }
        } else {
            Instant since = Instant.now().minus(90, ChronoUnit.DAYS);
            Specification<Complaint> spec = (r, q, cb) -> cb.and(
                    cb.equal(r.get("category").get("id"), complaint.getCategory().getId()),
                    cb.greaterThanOrEqualTo(r.get("createdAt"), since),
                    cb.notEqual(r.get("id"), complaint.getId()));
            List<Complaint> recent = complaintRepository.findAll(spec,
                    PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
            for (Complaint other : recent) {
                if (!seen.add(other.getId()) || excluded(other)) {
                    continue;
                }
                ComplaintLocation otherLocation = other.getPrimaryLocationId() != null
                        ? complaintLocationRepository.findById(other.getPrimaryLocationId()).orElse(null)
                        : null;
                candidates.add(new CandidateComplaint(other, null, otherLocation));
            }
        }
        return candidates;
    }

    private boolean excluded(Complaint other) {
        if (EXCLUDED_STATUSES.contains(other.getStatus())) {
            return true;
        }
        return !duplicateLinkRepository.findByDuplicateComplaintId(other.getId()).isEmpty();
    }

    private SignalSet computeSignals(Complaint complaint, ComplaintLocation myLocation,
                                     List<Long> myMediaIds, String myText, CandidateComplaint candidate) {
        Complaint other = candidate.complaint;

        Double locationScore = null;
        if (myLocation != null && candidate.location != null && candidate.distanceMeters != null) {
            locationScore = Math.max(0.0,
                    1.0 - candidate.distanceMeters / properties.duplicate().locationRadiusMeters());
        }

        Double textSimilarity = textSimilarityService.jaccard(myText, other.getTitle() + " " + other.getDescription());

        Double imageSimilarity = null;
        if (!myMediaIds.isEmpty()) {
            List<Long> otherMediaIds = complaintMediaRepository.findByComplaintIdOrderByUploadedAtAsc(other.getId())
                    .stream().map(ComplaintMedia::getId).toList();
            if (!otherMediaIds.isEmpty()) {
                imageSimilarity = maxImageSimilarity(myMediaIds, otherMediaIds);
            }
        }

        boolean categoryMatch = complaint.getCategory().getId().equals(other.getCategory().getId());

        return new SignalSet(locationScore, textSimilarity, imageSimilarity, categoryMatch);
    }

    private Double maxImageSimilarity(List<Long> myMediaIds, List<Long> otherMediaIds) {
        List<ImageHash> myHashes = imageHashRepository.findByMediaIdIn(myMediaIds);
        List<ImageHash> otherHashes = imageHashRepository.findByMediaIdIn(otherMediaIds);
        List<ImageEmbedding> myEmbeddings = imageEmbeddingRepository.findByMediaIdIn(myMediaIds);
        List<ImageEmbedding> otherEmbeddings = imageEmbeddingRepository.findByMediaIdIn(otherMediaIds);

        boolean hasHashSignal = !myHashes.isEmpty() && !otherHashes.isEmpty();
        boolean hasEmbeddingSignal = !myEmbeddings.isEmpty() && !otherEmbeddings.isEmpty();
        if (!hasHashSignal && !hasEmbeddingSignal) {
            return null;
        }

        double best = 0.0;
        if (hasHashSignal) {
            for (ImageHash mine : myHashes) {
                for (ImageHash other : otherHashes) {
                    best = Math.max(best, hashSimilarity(mine, other));
                }
            }
        }
        if (hasEmbeddingSignal) {
            for (ImageEmbedding mine : myEmbeddings) {
                double[] mineVector = parseVector(mine.getVector());
                if (mineVector == null) {
                    continue;
                }
                for (ImageEmbedding other : otherEmbeddings) {
                    double[] otherVector = parseVector(other.getVector());
                    if (otherVector == null) {
                        continue;
                    }
                    double cosine = ImageEmbeddingService.cosineSimilarity(mineVector, otherVector);
                    best = Math.max(best, cosine);
                }
            }
        }
        return best;
    }

    private double[] parseVector(String vectorJson) {
        try {
            return objectMapper.readValue(vectorJson, double[].class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private double hashSimilarity(ImageHash a, ImageHash b) {
        if (a.getSha256() != null && a.getSha256().equals(b.getSha256())) {
            return 1.0;
        }
        if (a.getPhash() != null && b.getPhash() != null) {
            int hamming = ImageHashService.hammingDistance(a.getPhash(), b.getPhash());
            return Math.max(0.0, 1.0 - hamming / 64.0);
        }
        if (a.getDhash() != null && b.getDhash() != null) {
            int hamming = ImageHashService.hammingDistance(a.getDhash(), b.getDhash());
            return Math.max(0.0, 1.0 - hamming / 64.0);
        }
        return 0.0;
    }

    private double combine(SignalSet signals) {
        boolean hasContentSignal = signals.locationScore() != null
                || signals.textSimilarity() != null
                || signals.imageSimilarity() != null;
        if (!hasContentSignal) {
            return 0.0;
        }
        CivicxProperties.Duplicate.Weights w = properties.duplicate().weights();
        double weightedSum = 0.0;
        double weightSum = 0.0;

        if (signals.locationScore != null) {
            weightedSum += w.location() * signals.locationScore;
            weightSum += w.location();
        }
        if (signals.textSimilarity != null) {
            double capped = Math.min(1.0, signals.textSimilarity / properties.duplicate().textThreshold());
            weightedSum += w.text() * capped;
            weightSum += w.text();
        }
        if (signals.imageSimilarity != null) {
            weightedSum += w.image() * signals.imageSimilarity;
            weightSum += w.image();
        }
        if (signals.categoryMatch != null) {
            weightedSum += w.category() * (signals.categoryMatch ? 1.0 : 0.0);
            weightSum += w.category();
        }
        if (weightSum == 0.0) {
            return 0.0;
        }
        return weightedSum / weightSum;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize signals", e);
        }
    }

    private record CandidateComplaint(Complaint complaint, Double distanceMeters, ComplaintLocation location) {
    }

    private record SignalSet(Double locationScore, Double textSimilarity, Double imageSimilarity, Boolean categoryMatch) {

        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("locationScore", locationScore);
            map.put("textSimilarity", textSimilarity);
            map.put("imageSimilarity", imageSimilarity);
            map.put("categoryMatch", categoryMatch);
            return map;
        }
    }
}
