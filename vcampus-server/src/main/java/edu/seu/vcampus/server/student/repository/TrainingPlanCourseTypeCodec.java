package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.common.student.CourseType;

/** Keeps cross-disciplinary courses compatible with legacy 16-character Access columns. */
final class TrainingPlanCourseTypeCodec {
    private static final String CROSS_DISCIPLINARY_VALUE = "CROSS";

    private TrainingPlanCourseTypeCodec() {
    }

    static String store(CourseType value) {
        return value == CourseType.CROSS_DISCIPLINARY
                ? CROSS_DISCIPLINARY_VALUE : value.name();
    }

    static CourseType read(String value) {
        return CROSS_DISCIPLINARY_VALUE.equals(value)
                ? CourseType.CROSS_DISCIPLINARY : CourseType.valueOf(value);
    }
}
