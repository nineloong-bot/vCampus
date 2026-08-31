package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Saves the only student-editable academic field into the draft. */
public record SaveStudentAttendanceDraftCommand(AttendanceMode attendanceMode,
                                                long expectedApplicationVersion)
        implements Serializable { }
