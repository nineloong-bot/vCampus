package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.Instant;

/** A personal-profile snapshot with submission and review metadata. */
/**
 * Carries immutable student profile application view data.
 * @param applicationId the application identifier
 * @param studentId the student identifier
 * @param status the status
 * @param personal the personal
 * @param attendanceMode the attendance mode
 * @param baseStudentVersion the base student version
 * @param applicationVersion the application version
 * @param submittedAt the submitted at
 * @param reviewerUserId the reviewer user identifier
 * @param reviewedAt the reviewed at
 * @param reviewComment the review comment
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
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
