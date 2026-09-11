package edu.seu.vcampus.server.course.domain;

import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.CurriculumCourse;
import edu.seu.vcampus.server.course.repository.CurriculumRepository;
import edu.seu.vcampus.server.course.repository.Term;
import edu.seu.vcampus.server.course.service.StudentEnrollmentEligibility;

import java.sql.Connection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Resolves the single server-side candidate set used by listing and enrollment mutations. */
public final class CurriculumSelectionPolicy {
    private final CurriculumRepository curricula;
    private final CourseRepository courses;

    public CurriculumSelectionPolicy(CurriculumRepository curricula, CourseRepository courses) {
        this.curricula = curricula;
        this.courses = courses;
    }

    public CandidateSet resolve(Connection connection, StudentEnrollmentEligibility student, Term term) {
        if (student == null || !student.hasCurriculumContext()) return CandidateSet.legacyMode();
        var plan = curricula.findPublishedPlan(connection, student.majorCode(), student.cohortYear())
                .orElseThrow(CurriculumNotConfiguredException::new);
        int academicYearNo = term.academicYearStart() - student.cohortYear() + 1;
        Map<String, CurriculumCourse> allowed = new HashMap<>();
        if (academicYearNo >= 1) {
            for (CurriculumCourse course : curricula.findScheduledCourses(
                    connection, plan.planId(), academicYearNo, term.season())) {
                if (courses.existsPassedAttempt(connection, student.studentId(), course.courseId())) continue;
                boolean prerequisitesPassed = curricula.findPrerequisiteCourseIds(
                                connection, plan.planId(), course.courseId()).stream()
                        .allMatch(required -> courses.existsPassedAttempt(
                                connection, student.studentId(), required));
                if (prerequisitesPassed) allowed.put(course.courseId(), course);
            }
        }
        Set<String> retakes = new HashSet<>();
        for (CurriculumCourse course : curricula.findEarlierCourses(
                connection, plan.planId(), Math.max(1, academicYearNo), term.season())) {
            if (courses.existsFailedAttempt(connection, student.studentId(), course.courseId())
                    && !courses.existsPassedAttempt(connection, student.studentId(), course.courseId())) {
                allowed.put(course.courseId(), course);
                retakes.add(course.courseId());
            }
        }
        return new CandidateSet(false, Map.copyOf(allowed), Set.copyOf(retakes));
    }

    public record CandidateSet(boolean legacy, Map<String, CurriculumCourse> courses,
                               Set<String> retakeCourseIds) {
        private static CandidateSet legacyMode() { return new CandidateSet(true, Map.of(), Set.of()); }
        public boolean allows(String courseId) { return legacy || courses.containsKey(courseId); }
        public boolean isRetake(String courseId) { return retakeCourseIds.contains(courseId); }
        public CurriculumCourse metadata(String courseId) { return courses.get(courseId); }
        public void requireAllowed(String courseId) {
            if (!allows(courseId)) throw new CurriculumCourseUnavailableException();
        }
    }
}
