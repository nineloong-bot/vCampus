package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Saves the only student-editable academic field into the draft. */
/**
 * Carries immutable save student attendance draft command data.
 * @param attendanceMode the attendance mode
 * @param expectedApplicationVersion the expected application version
 */
public record SaveStudentAttendanceDraftCommand(AttendanceMode attendanceMode,
                                                long expectedApplicationVersion)
        implements Serializable { }
