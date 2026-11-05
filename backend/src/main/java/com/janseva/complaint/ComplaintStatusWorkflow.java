package com.janseva.complaint;

import java.util.Map;
import java.util.Set;

public final class ComplaintStatusWorkflow {

    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ALLOWED_TRANSITIONS = Map.ofEntries(
            Map.entry(ComplaintStatus.SUBMITTED, Set.of(ComplaintStatus.UNDER_REVIEW, ComplaintStatus.VERIFIED, ComplaintStatus.REJECTED)),
            Map.entry(ComplaintStatus.UNDER_REVIEW, Set.of(ComplaintStatus.VERIFIED, ComplaintStatus.REJECTED)),
            Map.entry(ComplaintStatus.VERIFIED, Set.of(ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS)),
            Map.entry(ComplaintStatus.ASSIGNED, Set.of(ComplaintStatus.IN_PROGRESS)),
            Map.entry(ComplaintStatus.IN_PROGRESS, Set.of(ComplaintStatus.RESOLVED)),
            Map.entry(ComplaintStatus.RESOLVED, Set.of(ComplaintStatus.CLOSED, ComplaintStatus.REOPENED)),
            Map.entry(ComplaintStatus.CLOSED, Set.of(ComplaintStatus.REOPENED)),
            Map.entry(ComplaintStatus.REOPENED, Set.of(ComplaintStatus.VERIFIED, ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS)),
            Map.entry(ComplaintStatus.REJECTED, Set.of(ComplaintStatus.REOPENED))
    );

    private ComplaintStatusWorkflow() {
    }

    public static boolean canTransition(ComplaintStatus from, ComplaintStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
