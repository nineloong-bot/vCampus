package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/** Immutable audit entry shown on the student detail timeline. */
public record StudentChangeView(String changeId, String studentId, String changeType,
        String oldValue, String newValue, String reason, String operatorUserId,
        LocalDate effectiveDate, Instant createdAt) implements Serializable { }
