package com.janseva.complaint;

import com.janseva.common.PagedResponse;
import com.janseva.complaint.dto.AiSummaryResponse;
import com.janseva.complaint.dto.ComplaintCreateRequest;
import com.janseva.complaint.dto.ComplaintHistoryResponse;
import com.janseva.complaint.dto.ComplaintMediaResponse;
import com.janseva.complaint.dto.ComplaintResponse;
import com.janseva.complaint.dto.ComplaintSummaryResponse;
import com.janseva.processing.ProcessingQueryService;
import com.janseva.security.CurrentUser;
import com.janseva.storage.ComplaintFileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/complaints")
@Tag(name = "Complaints (Citizen)", description = "Citizen complaint submission and management")
public class ComplaintController {

    private final ComplaintService complaintService;
    private final ComplaintMediaService complaintMediaService;
    private final ComplaintFileStorageService storageService;
    private final ProcessingQueryService processingQueryService;

    public ComplaintController(ComplaintService complaintService,
                               ComplaintMediaService complaintMediaService,
                               ComplaintFileStorageService storageService,
                               ProcessingQueryService processingQueryService) {
        this.complaintService = complaintService;
        this.complaintMediaService = complaintMediaService;
        this.storageService = storageService;
        this.processingQueryService = processingQueryService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Submit a new civic complaint",
            description = "Creates a complaint with a unique CX reference number. GPS coordinates are validated to "
                    + "ranges (lat -90..90, lng -180..180). The citizen identity is taken from the JWT token.")
    public ResponseEntity<ComplaintResponse> create(@Valid @RequestBody ComplaintCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complaintService.create(CurrentUser.id(), request));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "List my complaints",
            description = "Returns the authenticated citizen's complaints, newest first.")
    public ResponseEntity<PagedResponse<ComplaintSummaryResponse>> mine(
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(PagedResponse.of(complaintService.getMine(CurrentUser.id(), page, size)));
    }

    @GetMapping("/{complaintId}")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Get my complaint details",
            description = "Returns full details including location and media metadata. "
                    + "Only the owner (or staff via the admin API) can access it.")
    public ResponseEntity<ComplaintResponse> get(@PathVariable Long complaintId) {
        return ResponseEntity.ok(complaintService.getForOwner(CurrentUser.id(), complaintId));
    }

    @GetMapping("/{complaintId}/history")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Get my complaint status history")
    public ResponseEntity<List<ComplaintHistoryResponse>> history(@PathVariable Long complaintId) {
        return ResponseEntity.ok(complaintService.getHistoryForOwner(CurrentUser.id(), complaintId));
    }

    @GetMapping("/{complaintId}/ai-summary")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Get the automated analysis summary for my complaint",
            description = "Returns the processing status, the classifier's category suggestion and a flag whether "
                    + "possible duplicates were found. Only the complaint owner can view this; matching complaint "
                    + "details are never exposed to citizens.")
    public ResponseEntity<AiSummaryResponse> aiSummary(@PathVariable Long complaintId) {
        return ResponseEntity.ok(processingQueryService.getAiSummary(complaintId, CurrentUser.id()));
    }

    @PostMapping(value = "/{complaintId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CITIZEN','ADMIN','SUPERVISOR')")
    @Operation(summary = "Upload a complaint photograph",
            description = "Accepts JPEG, PNG and WebP. File content is validated by magic bytes, not filename. "
                    + "Max size and max images per complaint are configurable. Path traversal is prevented by "
                    + "generating random storage keys.")
    public ResponseEntity<ComplaintMediaResponse> uploadImage(
            @PathVariable Long complaintId,
            @Parameter(description = "Image file (JPEG, PNG or WebP)")
            @RequestParam("file") MultipartFile file) {
        ComplaintMedia media = complaintMediaService.upload(complaintId, CurrentUser.id(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ComplaintMediaResponse(
                media.getId(),
                media.getOriginalFilename(),
                media.getContentType(),
                media.getFileSizeBytes(),
                media.getWidth(),
                media.getHeight(),
                media.getProcessingStatus().name(),
                media.getUploadedAt()));
    }

    @GetMapping("/{complaintId}/images/{mediaId}")
    @PreAuthorize("hasAnyRole('CITIZEN','ADMIN','SUPERVISOR')")
    @Operation(summary = "Download a complaint photograph",
            description = "Returns the stored image. Only the complaint owner or staff can download it. "
                    + "The storage key is never exposed.")
    public ResponseEntity<Resource> downloadImage(@PathVariable Long complaintId, @PathVariable Long mediaId) {
        ComplaintMedia media = complaintMediaService.getMediaForDownload(mediaId, CurrentUser.id());
        if (!media.getComplaint().getId().equals(complaintId)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = storageService.load(media.getObjectKey());
        String filename = media.getOriginalFilename() != null ? media.getOriginalFilename() : "image";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString())
                .body(resource);
    }
}
