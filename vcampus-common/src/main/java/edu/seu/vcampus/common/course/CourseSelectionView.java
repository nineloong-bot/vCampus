package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One student-facing course row with expandable teaching-class options. */
public record CourseSelectionView(String courseId, String courseCode, String courseName,
                                  BigDecimal credit, String courseNature, String courseCategory,
                                  String offeringUnit, boolean retakeCourse,
                                  String courseAction, String courseReason,
                                  String activeEnrollmentId, Long activeEnrollmentVersion,
                                  String activeOfferingId,
                                  List<TeachingClassOptionView> teachingClasses) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public CourseSelectionView {
        CourseValidation.text("courseId", Objects.requireNonNull(courseId, "courseId"), 36);
        CourseValidation.text("courseCode", Objects.requireNonNull(courseCode, "courseCode"), 24);
        CourseValidation.text("courseName", Objects.requireNonNull(courseName, "courseName"), 128);
        Objects.requireNonNull(credit, "credit");
        if (credit.signum() <= 0 || !Set.of("REQUIRED", "RESTRICTED", "ELECTIVE").contains(courseNature)) {
            throw new IllegalArgumentException("invalid curriculum course");
        }
        CourseValidation.text("courseCategory", Objects.requireNonNull(courseCategory), 64);
        CourseValidation.text("offeringUnit", Objects.requireNonNull(offeringUnit), 64);
        if (!Set.of("SELECT_COURSE", "CANCEL_SELECTION", "DISABLED").contains(courseAction)) {
            throw new IllegalArgumentException("invalid course action");
        }
        CourseValidation.optionalText("courseReason", courseReason, 128);
        boolean noEnrollment = activeEnrollmentId == null && activeEnrollmentVersion == null
                && activeOfferingId == null;
        boolean completeEnrollment = activeEnrollmentId != null && activeEnrollmentVersion != null
                && activeOfferingId != null;
        if (!noEnrollment && !completeEnrollment) throw new IllegalArgumentException("incomplete active enrollment");
        if (completeEnrollment) {
            CourseValidation.text("activeEnrollmentId", activeEnrollmentId, 36);
            CourseValidation.text("activeOfferingId", activeOfferingId, 36);
            if (activeEnrollmentVersion < 0) throw new IllegalArgumentException("invalid enrollment version");
        }
        teachingClasses = List.copyOf(Objects.requireNonNull(teachingClasses, "teachingClasses"));
        if (teachingClasses.isEmpty()) throw new IllegalArgumentException("course requires teaching classes");
    }

    public CourseSelectionView(String courseId, String courseCode, String courseName,
                               String courseAction, String courseReason,
                               String activeEnrollmentId, Long activeEnrollmentVersion,
                               String activeOfferingId, List<TeachingClassOptionView> teachingClasses) {
        this(courseId, courseCode, courseName, BigDecimal.ONE, "ELECTIVE", "其他课程",
                "开课单位", false, courseAction, courseReason, activeEnrollmentId,
                activeEnrollmentVersion, activeOfferingId, teachingClasses);
    }
}
