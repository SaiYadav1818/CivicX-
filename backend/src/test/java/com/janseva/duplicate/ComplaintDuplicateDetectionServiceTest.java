package com.janseva.duplicate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.janseva.complaint.Complaint;
import com.janseva.complaint.ComplaintCategory;
import com.janseva.complaint.ComplaintLocation;
import com.janseva.complaint.ComplaintLocationRepository;
import com.janseva.complaint.ComplaintMediaRepository;
import com.janseva.complaint.ComplaintRepository;
import com.janseva.complaint.ComplaintStatus;
import com.janseva.complaint.LocationSource;
import com.janseva.config.CivicxProperties;
import com.janseva.embedding.ImageEmbeddingRepository;
import com.janseva.geo.GeoService;
import com.janseva.hashing.ImageHashRepository;
import com.janseva.text.TextSimilarityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintDuplicateDetectionServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private ComplaintLocationRepository complaintLocationRepository;
    @Mock
    private ComplaintMediaRepository complaintMediaRepository;
    @Mock
    private DuplicateDetectionResultRepository duplicateResultRepository;
    @Mock
    private DuplicateComplaintLinkRepository duplicateLinkRepository;
    @Mock
    private ImageHashRepository imageHashRepository;
    @Mock
    private ImageEmbeddingRepository imageEmbeddingRepository;

    private ComplaintDuplicateDetectionService service;

    private final CivicxProperties properties = new CivicxProperties(
            new CivicxProperties.Ai(true, "rule-based"),
            new CivicxProperties.Duplicate(true, 100, 0.25, 10, 0.90, 0.55, 0.45,
                    new CivicxProperties.Duplicate.Weights(0.40, 0.30, 0.20, 0.10)),
            new CivicxProperties.Outbox(5000, 10)
    );

    @BeforeEach
    void setUp() {
        service = new ComplaintDuplicateDetectionService(
                complaintRepository, complaintLocationRepository, complaintMediaRepository,
                duplicateResultRepository, duplicateLinkRepository, imageHashRepository,
                imageEmbeddingRepository,
                new GeoService(), new TextSimilarityService(), properties, new ObjectMapper());
        lenient().when(complaintMediaRepository.findByComplaintIdOrderByUploadedAtAsc(anyLong()))
                .thenReturn(List.of());
        lenient().when(imageHashRepository.findByMediaIdIn(any())).thenReturn(List.of());
        lenient().when(imageEmbeddingRepository.findByMediaIdIn(any())).thenReturn(List.of());
    }

    private Complaint complaint(Long id, Long categoryId, String title, String description,
                                ComplaintStatus status, Long locationId) {
        ComplaintCategory category = mock(ComplaintCategory.class);
        lenient().when(category.getId()).thenReturn(categoryId);
        Complaint c = new Complaint(mock(com.janseva.user.User.class), category, status, title, description);
        ReflectionTestUtils.setField(c, "id", id);
        c.setPrimaryLocationId(locationId);
        return c;
    }

    private ComplaintLocation location(Long id, Complaint complaint, double lat, double lng) {
        ComplaintLocation location = new ComplaintLocation(complaint,
                BigDecimal.valueOf(lat), BigDecimal.valueOf(lng), null,
                null, null, null, null, null, LocationSource.DEVICE_GPS, Instant.now());
        ReflectionTestUtils.setField(location, "id", id);
        return location;
    }

    @Test
    void nearbySimilarComplaintIsDetectedAsPossibleDuplicate() {
        Complaint mine = complaint(2L, 1L, "Garbage near bus stop", "Garbage dumped near the bus stop", ComplaintStatus.SUBMITTED, 10L);
        Complaint other = complaint(1L, 1L, "Garbage pile", "Garbage pile near the bus stop", ComplaintStatus.SUBMITTED, 9L);
        ComplaintLocation myLocation = location(10L, mine, 17.4840, 78.3910);
        ComplaintLocation otherLocation = location(9L, other, 17.4842, 78.3912);

        when(complaintRepository.findById(2L)).thenReturn(Optional.of(mine));
        when(complaintLocationRepository.findById(10L)).thenReturn(Optional.of(myLocation));
        when(complaintLocationRepository.findCurrentInBoundingBox(eq(2L), any(), any(), any(), any()))
                .thenReturn(List.of(otherLocation));
        when(duplicateLinkRepository.findByDuplicateComplaintId(1L)).thenReturn(List.of());
        when(duplicateResultRepository.save(any(DuplicateDetectionResult.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DuplicateRunSummary summary = service.runDetection(2L);

        assertThat(summary.state()).isEqualTo(DuplicateEngineState.POSSIBLE_DUPLICATE);
        assertThat(summary.candidateCount()).isEqualTo(1);
        assertThat(summary.duplicateReviewRequired()).isTrue();
        verify(duplicateResultRepository).save(org.mockito.ArgumentMatchers.argThat(
                r -> r.isPossibleDuplicate() && r.getMatchingComplaintId().equals(1L)));
    }

    @Test
    void distantComplaintIsNotMatched() {
        Complaint mine = complaint(2L, 1L, "Garbage", "Garbage dump", ComplaintStatus.SUBMITTED, 10L);
        ComplaintLocation myLocation = location(10L, mine, 17.4840, 78.3910);

        when(complaintRepository.findById(2L)).thenReturn(Optional.of(mine));
        when(complaintLocationRepository.findById(10L)).thenReturn(Optional.of(myLocation));
        when(complaintLocationRepository.findCurrentInBoundingBox(eq(2L), any(), any(), any(), any()))
                .thenReturn(List.of());

        DuplicateRunSummary summary = service.runDetection(2L);

        assertThat(summary.state()).isEqualTo(DuplicateEngineState.NO_MATCH_FOUND);
        assertThat(summary.candidateCount()).isZero();
        verify(duplicateResultRepository, never()).save(any());
    }

    @Test
    void noLocationAndNoMediaIsInsufficientData() {
        Complaint mine = complaint(2L, 1L, "T", "D", ComplaintStatus.SUBMITTED, null);

        when(complaintRepository.findById(2L)).thenReturn(Optional.of(mine));
        when(complaintRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        DuplicateRunSummary summary = service.runDetection(2L);

        assertThat(summary.state()).isEqualTo(DuplicateEngineState.INSUFFICIENT_DATA);
        verify(duplicateResultRepository, never()).save(any());
    }

    @Test
    void categoryOnlyCandidateDoesNotScore() {
        Complaint mine = complaint(2L, 1L, "Hello world", "Nothing to see here", ComplaintStatus.SUBMITTED, null);
        Complaint other = complaint(1L, 1L, "Unrelated", "Completely different story", ComplaintStatus.SUBMITTED, 9L);

        when(complaintRepository.findById(2L)).thenReturn(Optional.of(mine));
        when(complaintRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(other)));
        when(duplicateLinkRepository.findByDuplicateComplaintId(1L)).thenReturn(List.of());

        DuplicateRunSummary summary = service.runDetection(2L);

        assertThat(summary.state()).isEqualTo(DuplicateEngineState.INSUFFICIENT_DATA);
        verify(duplicateResultRepository, never()).save(any());
    }

    @Test
    void rejectedAndClosedComplaintsAreExcluded() {
        Complaint mine = complaint(2L, 1L, "Garbage near bus stop", "Garbage dumped near the bus stop", ComplaintStatus.SUBMITTED, 10L);
        Complaint rejected = complaint(1L, 1L, "Garbage pile", "Garbage pile near the bus stop", ComplaintStatus.REJECTED, 9L);
        ComplaintLocation myLocation = location(10L, mine, 17.4840, 78.3910);
        ComplaintLocation rejectedLocation = location(9L, rejected, 17.4842, 78.3912);

        when(complaintRepository.findById(2L)).thenReturn(Optional.of(mine));
        when(complaintLocationRepository.findById(10L)).thenReturn(Optional.of(myLocation));
        when(complaintLocationRepository.findCurrentInBoundingBox(eq(2L), any(), any(), any(), any()))
                .thenReturn(List.of(rejectedLocation));

        DuplicateRunSummary summary = service.runDetection(2L);

        assertThat(summary.state()).isEqualTo(DuplicateEngineState.NO_MATCH_FOUND);
        verify(duplicateResultRepository, never()).save(any());
    }
}
