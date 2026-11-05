package com.janseva.duplicate;

import com.janseva.audit.AuditService;
import com.janseva.common.ApiException;
import com.janseva.complaint.Complaint;
import com.janseva.complaint.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateReviewServiceTest {

    @Mock
    private DuplicateDetectionResultRepository duplicateResultRepository;
    @Mock
    private DuplicateComplaintLinkRepository duplicateLinkRepository;
    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private AuditService auditService;

    private DuplicateReviewService service;

    @BeforeEach
    void setUp() {
        service = new DuplicateReviewService(duplicateResultRepository, duplicateLinkRepository,
                complaintRepository, auditService);
        Complaint c1 = mock(Complaint.class);
        Complaint c2 = mock(Complaint.class);
        lenient().when(c1.getComplaintNumber()).thenReturn("CX-000001");
        lenient().when(c2.getComplaintNumber()).thenReturn("CX-000002");
        lenient().when(complaintRepository.findById(1L)).thenReturn(Optional.of(c1));
        lenient().when(complaintRepository.findById(2L)).thenReturn(Optional.of(c2));
    }

    private DuplicateDetectionResult pendingResult() {
        DuplicateDetectionResult result = mock(DuplicateDetectionResult.class);
        lenient().when(result.getStatus()).thenReturn(DuplicateReviewStatus.PENDING_ADMIN);
        lenient().when(result.getId()).thenReturn(7L);
        return result;
    }

    @Test
    void confirmCreatesLinkAndMarksConfirmed() {
        DuplicateDetectionResult result = pendingResult();
        when(duplicateResultRepository.findByComplaintIdAndMatchingComplaintId(2L, 1L))
                .thenReturn(Optional.of(result));
        when(duplicateLinkRepository.findByDuplicateComplaintId(1L)).thenReturn(List.of());
        when(duplicateLinkRepository.existsByPrimaryComplaintIdAndDuplicateComplaintId(1L, 2L)).thenReturn(false);

        service.confirm(2L, 1L, 5L, "Same location");

        verify(duplicateLinkRepository).save(any(DuplicateComplaintLink.class));
        verify(result).setStatus(DuplicateReviewStatus.CONFIRMED);
        verify(result).setReviewedBy(5L);
        verify(auditService).log(anyLong(), any(), any(), any(), any());
    }

    @Test
    void confirmRejectsAlreadyReviewedCandidate() {
        DuplicateDetectionResult result = mock(DuplicateDetectionResult.class);
        when(result.getStatus()).thenReturn(DuplicateReviewStatus.CONFIRMED);
        when(duplicateResultRepository.findByComplaintIdAndMatchingComplaintId(2L, 1L))
                .thenReturn(Optional.of(result));

        assertThatThrownBy(() -> service.confirm(2L, 1L, 5L, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(duplicateLinkRepository, never()).save(any());
    }

    @Test
    void confirmRejectsCycleFormingLink() {
        DuplicateDetectionResult result = pendingResult();
        when(duplicateResultRepository.findByComplaintIdAndMatchingComplaintId(2L, 1L))
                .thenReturn(Optional.of(result));
        DuplicateComplaintLink existing = new DuplicateComplaintLink(2L, 1L, 5L, null);
        when(duplicateLinkRepository.findByDuplicateComplaintId(1L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.confirm(2L, 1L, 5L, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(duplicateLinkRepository, never()).save(any());
    }

    @Test
    void confirmRejectsSelfDuplicate() {
        assertThatThrownBy(() -> service.confirm(1L, 1L, 5L, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void dismissMarksReviewedWithoutLinking() {
        DuplicateDetectionResult result = pendingResult();
        when(duplicateResultRepository.findByComplaintIdAndMatchingComplaintId(2L, 1L))
                .thenReturn(Optional.of(result));

        service.dismiss(2L, 1L, 5L, "Not the same issue");

        verify(result).setStatus(DuplicateReviewStatus.DISMISSED);
        verify(duplicateLinkRepository, never()).save(any());
        verify(auditService).log(anyLong(), any(), any(), any(), any());
    }

    @Test
    void confirmMissingCandidateReturnsNotFound() {
        when(duplicateResultRepository.findByComplaintIdAndMatchingComplaintId(2L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(2L, 1L, 5L, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
