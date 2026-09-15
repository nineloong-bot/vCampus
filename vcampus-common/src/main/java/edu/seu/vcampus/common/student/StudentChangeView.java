package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/** Immutable audit entry shown on the student detail timeline. */
/**
 * Carries immutable student change view data.
 * @param changeId the change identifier
 * @param studentId the student identifier
 * @param changeType the change type
 * @param oldValue the old value
 * @param newValue the new value
 * @param reason the reason
 * @param operatorUserId the operator user identifier
 * @param effectiveDate the effective date
 * @param createdAt the created at
 */
public record StudentChangeView(String changeId, String studentId, String changeType,
        String oldValue, String newValue, String reason, String operatorUserId,
        LocalDate effectiveDate, Instant createdAt) implements Serializable { }
