package edu.seu.vcampus.server.course.domain;

import edu.seu.vcampus.server.course.repository.Classroom;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Schedule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferingSchedulingPolicyTest {
    @Test void rejectsRoomCapacityAndOrdinaryRoomOverlap() {
        var policy = new OfferingSchedulingPolicy(repository(
                new Classroom("教一-101", 30, true, false), List.of(schedule("old", 2, 3)), List.of()));
        assertThatThrownBy(() -> policy.validate(null, "term", null, "teacher", false,
                31, List.of(schedule("new", 4, 5))))
                .isInstanceOf(SchedulingRuleException.class)
                .extracting("code").isEqualTo("COURSE_CLASSROOM_CAPACITY_EXCEEDED");
        assertThatThrownBy(() -> policy.validate(null, "term", null, "teacher", false,
                30, List.of(schedule("new", 1, 2))))
                .isInstanceOf(SchedulingRuleException.class)
                .extracting("code").isEqualTo("COURSE_CLASSROOM_CONFLICT");
    }

    @Test void rejectsTeacherOverlapEvenWhenRoomsDiffer() {
        var policy = new OfferingSchedulingPolicy(repository(
                new Classroom("教一-101", 100, true, false), List.of(), List.of(schedule("old", 2, 3))));
        assertThatThrownBy(() -> policy.validate(null, "term", null, "teacher", false,
                40, List.of(schedule("new", 1, 2))))
                .isInstanceOf(SchedulingRuleException.class)
                .extracting("code").isEqualTo("COURSE_TEACHER_CONFLICT");
    }

    @Test void allowsOverlappingPhysicalEducationAtSharedSportsVenueOnly() {
        var policy = new OfferingSchedulingPolicy(repository(
                new Classroom("桃园操场", Integer.MAX_VALUE, true, true),
                List.of(schedule("old", 1, 2)), List.of()));
        assertThatCode(() -> policy.validate(null, "term", null, "teacher", true,
                200, List.of(schedule("new", 1, 2)))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate(null, "term", null, "teacher", false,
                40, List.of(schedule("new", 3, 4))))
                .isInstanceOf(SchedulingRuleException.class)
                .extracting("code").isEqualTo("COURSE_CLASSROOM_SPORTS_ONLY");
    }

    private static CourseRepository repository(Classroom room, List<Schedule> roomSchedules,
                                               List<Schedule> teacherSchedules) {
        return (CourseRepository) Proxy.newProxyInstance(CourseRepository.class.getClassLoader(),
                new Class<?>[]{CourseRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findClassroom" -> Optional.of(room);
                    case "findClassroomSchedules" -> roomSchedules;
                    case "findTeacherSchedules" -> teacherSchedules;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Schedule schedule(String offering, int start, int end) {
        return new Schedule("schedule-" + offering, offering, DayOfWeek.MONDAY,
                start, end, 1, 16, "教一-101");
    }
}
