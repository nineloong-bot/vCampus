package edu.seu.vcampus.common.student.majortransfer;

import java.util.Map;
import java.util.Set;

import static edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus.*;

/** Validates allowed state transitions for major-transfer applications. */
public final class MajorTransferStateMachine {

    private static final Map<MajorTransferStatus, Set<MajorTransferStatus>> ALLOWED = Map.ofEntries(
            Map.entry(DRAFT, Set.of(SUBMITTED)),
            Map.entry(SUBMITTED, Set.of(DRAFT, SOURCE_APPROVED, REJECTED)),
            Map.entry(SOURCE_APPROVED, Set.of(QUALIFIED, REJECTED, CANCELLED)),
            Map.entry(QUALIFIED, Set.of(ASSESSED, REJECTED, CANCELLED)),
            Map.entry(ASSESSED, Set.of(PROPOSED, REJECTED, CANCELLED)),
            Map.entry(PROPOSED, Set.of(PENDING_EFFECTIVE, REJECTED, CANCELLED)),
            Map.entry(PENDING_EFFECTIVE, Set.of(EFFECTIVE, EXECUTION_FAILED, CANCELLED)),
            Map.entry(EXECUTION_FAILED, Set.of(EFFECTIVE, CANCELLED))
    );

    private MajorTransferStateMachine() {
    }

    /** Throws {@link IllegalStateException} if the transition is not allowed. */
    public static void requireTransition(MajorTransferStatus from, MajorTransferStatus to) {
        Set<MajorTransferStatus> targets = ALLOWED.get(from);
        if (targets == null || !targets.contains(to)) {
            throw new IllegalStateException(
                    "Invalid major transfer transition: " + from + " -> " + to);
        }
    }

    /** Returns true when the student may edit draft fields (application is in DRAFT). */
    public static boolean studentMayEdit(MajorTransferStatus status) {
        return status == DRAFT;
    }

    /** Returns true when the student may submit the application. */
    public static boolean studentMaySubmit(MajorTransferStatus status) {
        return status == DRAFT;
    }

    /** Returns true when the student may withdraw (only SUBMITTED before source review). */
    public static boolean studentMayWithdraw(MajorTransferStatus status) {
        return status == SUBMITTED;
    }

    /** Returns true when an administrator may cancel the application. */
    public static boolean adminMayCancel(MajorTransferStatus status) {
        return switch (status) {
            case SOURCE_APPROVED, QUALIFIED, ASSESSED, PROPOSED, PENDING_EFFECTIVE -> true;
            default -> false;
        };
    }
}
