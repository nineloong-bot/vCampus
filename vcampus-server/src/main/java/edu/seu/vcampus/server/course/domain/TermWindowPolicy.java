package edu.seu.vcampus.server.course.domain;

import edu.seu.vcampus.server.course.repository.Term;

import java.time.Instant;
import java.util.Objects;

/** Enforces server-supplied instants against the configured term mutation windows. */
public final class TermWindowPolicy {
    /**
     * Requires normal enrollment (and retake enrollment) to be open at {@code now}.
     * The lower bound is inclusive and the upper bound is exclusive.
     */
    public void requireEnrollmentOpen(Term term, Instant now) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(now, "now");
        String status = requireStatus(term);
        if ("CLOSED".equals(status)
                || !inWindow(now, term.enrollmentStartAt(), term.enrollmentEndAt())) {
            throw new EnrollmentClosedException();
        }
    }

    /**
     * Requires change or late-add adjustment to be open at {@code now}.
     * The lower bound is inclusive and the upper bound is exclusive.
     */
    public void requireAdjustmentOpen(Term term, Instant now) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(now, "now");
        String status = requireStatus(term);
        if ("CLOSED".equals(status)
                || !inWindow(now, term.adjustmentStartAt(), term.adjustmentEndAt())) {
            throw new AdjustmentClosedException();
        }
    }

    /**
     * Requires either normal enrollment or adjustment to be open for a drop at {@code now}.
     * Both windows include their lower bound and exclude their upper bound.
     */
    public void requireDropOpen(Term term, Instant now) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(now, "now");
        String status = requireStatus(term);
        boolean enrollment = inWindow(now, term.enrollmentStartAt(), term.enrollmentEndAt());
        boolean adjustment = inWindow(now, term.adjustmentStartAt(), term.adjustmentEndAt());
        if ("CLOSED".equals(status) || (!enrollment && !adjustment)) {
            throw new DropClosedException();
        }
    }

    /** Retake enrollment follows the normal enrollment window and status rule. */
    public void requireRetakeOpen(Term term, Instant now) {
        requireEnrollmentOpen(term, now);
    }

    private static String requireStatus(Term term) {
        String status = term.termStatus();
        if (!"ACTIVE".equals(status) && !"PLANNED".equals(status) && !"CLOSED".equals(status)) {
            throw new IllegalArgumentException("Unknown term status: " + status);
        }
        return status;
    }

    private static boolean inWindow(Instant now, Instant start, Instant end) {
        return start != null && end != null
                && !now.isBefore(start)
                && now.isBefore(end);
    }
}
