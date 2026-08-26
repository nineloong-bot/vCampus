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
        if (!isActive(term) || !inWindow(now, term.enrollmentStartAt(), term.enrollmentEndAt())) {
            throw new EnrollmentClosedException();
        }
    }

    /**
     * Requires drop, change, or late-add adjustment to be open at {@code now}.
     * The lower bound is inclusive and the upper bound is exclusive.
     */
    public void requireAdjustmentOpen(Term term, Instant now) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(now, "now");
        if (!isActive(term) || !inWindow(now, term.adjustmentStartAt(), term.adjustmentEndAt())) {
            throw new AdjustmentClosedException();
        }
    }

    /** Retake enrollment follows the normal enrollment window and status rule. */
    public void requireRetakeOpen(Term term, Instant now) {
        requireEnrollmentOpen(term, now);
    }

    private static boolean isActive(Term term) {
        return "ACTIVE".equalsIgnoreCase(term.termStatus());
    }

    private static boolean inWindow(Instant now, Instant start, Instant end) {
        return start != null && end != null
                && !now.isBefore(start)
                && now.isBefore(end);
    }
}
