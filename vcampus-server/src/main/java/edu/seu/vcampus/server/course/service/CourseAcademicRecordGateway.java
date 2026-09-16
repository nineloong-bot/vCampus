package edu.seu.vcampus.server.course.service;

/** Read-only course boundary backed by the student module's existing grade service. */
@FunctionalInterface
public interface CourseAcademicRecordGateway {
    /** Loads one stable grade snapshot for the requested student. */
    CourseAcademicRecord findByStudent(String studentId);

    /** Empty gateway for isolated previews and compatibility tests without student grades. */
    static CourseAcademicRecordGateway empty() {
        return ignored -> new CourseAcademicRecord(java.util.List.of());
    }
}
