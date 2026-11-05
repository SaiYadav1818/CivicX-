package com.janseva.complaint;

import com.janseva.audit.AuditService;
import com.janseva.common.ApiException;
import com.janseva.complaint.dto.ComplaintCreateRequest;
import com.janseva.complaint.dto.RejectRequest;
import com.janseva.complaint.dto.StatusUpdateRequest;
import com.janseva.outbox.OutboxEventPublisher;
import com.janseva.user.User;
import com.janseva.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private ComplaintLocationRepository complaintLocationRepository;
    @Mock
    private ComplaintMediaRepository complaintMediaRepository;
    @Mock
    private ComplaintHistoryRepository complaintHistoryRepository;
    @Mock
    private ComplaintCategoryRepository categoryRepository;
    @Mock
    private WardRepository wardRepository;
    @Mock
    private ZoneRepository zoneRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private ComplaintService complaintService;
    private ComplaintCategory category;
    private User citizen;

    @BeforeEach
    void setUp() {
        complaintService = new ComplaintService(complaintRepository, complaintLocationRepository,
                complaintMediaRepository, complaintHistoryRepository, categoryRepository,
                wardRepository, zoneRepository, userRepository, auditService, outboxEventPublisher);
        citizen = mock(User.class);
        lenient().when(citizen.getId()).thenReturn(1L);
        category = mock(ComplaintCategory.class);
        lenient().when(category.getId()).thenReturn(1L);
        lenient().when(category.getCode()).thenReturn("GARBAGE");
        lenient().when(category.getName()).thenReturn("Garbage accumulation");
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(citizen));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private ComplaintCreateRequest validRequest() {
        return new ComplaintCreateRequest(
                1L, "Garbage dumped beside main road", "Garbage has accumulated near the bus stop.",
                new BigDecimal("17.4840000"), new BigDecimal("78.3910000"),
                new BigDecimal("12.50"), "Kukatpally, Hyderabad", "Near bus stop",
                "Hyderabad", "Telangana", "500072", null, null, LocationSource.DEVICE_GPS);
    }

    @Test
    void createsComplaintWithSafeReferenceNumber() {
        when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(category));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
            Complaint c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 42L);
            return c;
        });
        when(complaintLocationRepository.save(any(ComplaintLocation.class))).thenAnswer(inv -> {
            ComplaintLocation l = inv.getArgument(0);
            ReflectionTestUtils.setField(l, "id", 7L);
            return l;
        });
        when(complaintHistoryRepository.save(any(ComplaintHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(complaintLocationRepository.findById(7L)).thenReturn(Optional.empty());
        when(complaintMediaRepository.findByComplaintIdOrderByUploadedAtAsc(42L)).thenReturn(List.of());

        var response = complaintService.create(1L, validRequest());

        assertThat(response.complaintNumber()).isEqualTo("CX-000042");
        assertThat(response.status()).isEqualTo("SUBMITTED");
        verify(complaintHistoryRepository).save(any(ComplaintHistory.class));
        verify(auditService).log(anyLong(), any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidCategory() {
        when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> complaintService.create(1L, validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsWardFromDifferentZone() {
        when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(category));
        Zone zone = mock(Zone.class);
        when(zone.getId()).thenReturn(1L);
        Zone otherZone = mock(Zone.class);
        when(otherZone.getId()).thenReturn(2L);
        Ward ward = mock(Ward.class);
        when(ward.getZone()).thenReturn(otherZone);
        when(zoneRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(zone));
        when(wardRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(ward));

        ComplaintCreateRequest request = new ComplaintCreateRequest(
                1L, "Title", "Description",
                new BigDecimal("17.4840000"), new BigDecimal("78.3910000"),
                null, null, null, null, null, null, 5L, 1L, LocationSource.MANUAL_PIN);

        assertThatThrownBy(() -> complaintService.create(1L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsInvalidStatusTransition() {
        Complaint complaint = new Complaint(citizen, category, ComplaintStatus.SUBMITTED, "T", "D");
        when(complaintRepository.findById(9L)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> complaintService.updateStatus(9L, 2L,
                new StatusUpdateRequest(ComplaintStatus.CLOSED, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectionRequiresReason() {
        Complaint complaint = new Complaint(citizen, category, ComplaintStatus.SUBMITTED, "T", "D");
        when(complaintRepository.findById(9L)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> complaintService.updateStatus(9L, 2L,
                new StatusUpdateRequest(ComplaintStatus.REJECTED, "  ")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void citizenCannotViewAnotherCitizensComplaint() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(9L, null,
                        List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))));
        User otherCitizen = mock(User.class);
        when(otherCitizen.getId()).thenReturn(5L);
        Complaint complaint = new Complaint(otherCitizen, category, ComplaintStatus.SUBMITTED, "T", "D");
        when(complaintRepository.findById(3L)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> complaintService.getForOwner(9L, 3L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void staffCanViewAnyComplaint() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(9L, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        User otherCitizen = mock(User.class);
        when(otherCitizen.getId()).thenReturn(5L);
        Complaint complaint = new Complaint(otherCitizen, category, ComplaintStatus.SUBMITTED, "T", "D");
        ReflectionTestUtils.setField(complaint, "id", 3L);
        when(complaintRepository.findById(3L)).thenReturn(Optional.of(complaint));
        when(complaintMediaRepository.findByComplaintIdOrderByUploadedAtAsc(3L)).thenReturn(List.of());

        var response = complaintService.getForOwner(9L, 3L);

        assertThat(response.title()).isEqualTo("T");
    }

    @Test
    void rejectCreatesHistoryAndAudit() {
        Complaint complaint = new Complaint(citizen, category, ComplaintStatus.SUBMITTED, "T", "D");
        ReflectionTestUtils.setField(complaint, "id", 9L);
        when(complaintRepository.findById(9L)).thenReturn(Optional.of(complaint));
        when(complaintHistoryRepository.save(any(ComplaintHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(complaintMediaRepository.findByComplaintIdOrderByUploadedAtAsc(9L)).thenReturn(List.of());

        var response = complaintService.reject(9L, 2L, new RejectRequest("Photo does not show any civic issue"));

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(complaintHistoryRepository).save(any(ComplaintHistory.class));
        verify(auditService).log(anyLong(), any(), any(), any(), any());
    }
}
