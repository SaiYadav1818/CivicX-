package com.janseva.complaint;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComplaintStatusWorkflowTest {

    @Test
    void submittedCanMoveToReviewVerifiedOrRejected() {
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.SUBMITTED, ComplaintStatus.UNDER_REVIEW)).isTrue();
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.SUBMITTED, ComplaintStatus.VERIFIED)).isTrue();
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.SUBMITTED, ComplaintStatus.REJECTED)).isTrue();
    }

    @Test
    void submittedCannotSkipToResolvedOrClosed() {
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.SUBMITTED, ComplaintStatus.RESOLVED)).isFalse();
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.SUBMITTED, ComplaintStatus.CLOSED)).isFalse();
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.SUBMITTED, ComplaintStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void verifiedCannotBeRejectedDirectly() {
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.VERIFIED, ComplaintStatus.REJECTED)).isFalse();
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.VERIFIED, ComplaintStatus.ASSIGNED)).isTrue();
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.VERIFIED, ComplaintStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    void resolvedCanCloseOrReopen() {
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED)).isTrue();
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.RESOLVED, ComplaintStatus.REOPENED)).isTrue();
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.CLOSED, ComplaintStatus.REOPENED)).isTrue();
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.REJECTED, ComplaintStatus.REOPENED)).isTrue();
    }

    @Test
    void terminalStatesDoNotAllowRandomTransitions() {
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.REJECTED, ComplaintStatus.VERIFIED)).isFalse();
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.CLOSED, ComplaintStatus.REJECTED)).isFalse();
        assertThat(ComplaintStatusWorkflow.canTransition(ComplaintStatus.IN_PROGRESS, ComplaintStatus.VERIFIED)).isFalse();
    }
}
