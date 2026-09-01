package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.Instant;

/** A personal-profile snapshot with submission and review metadata. */
public record StudentProfileApplicationView(
        String applicationId,
        String studentId,
        StudentProfileApplicationStatus status,
        StudentPersonalProfile personal,
        AttendanceMode attendanceMode,
        long baseStudentVersion,
        long applicationVersion,
        Instant submittedAt,
        String reviewerUserId,
        Instant reviewedAt,
        String reviewComment,
        Instant createdAt,
        Instant updatedAt) implements Serializable { }
