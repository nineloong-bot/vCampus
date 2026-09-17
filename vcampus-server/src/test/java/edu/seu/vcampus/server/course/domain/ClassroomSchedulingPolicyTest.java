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

class ClassroomSchedulingPolicyTest {
    @Test void rejectsCapacityAboveRoomLimit() {
        ClassroomSchedulingPolicy policy = new ClassroomSchedulingPolicy(repository(50, List.of()));

        assertThatThrownBy(() -> policy.validate(null, "term", null, 51, List.of(schedule(1, 2))))
                .isInstanceOf(ClassroomRuleException.class)
                .extracting("code").isEqualTo("COURSE_CLASSROOM_CAPACITY_EXCEEDED");
    }

    @Test void rejectsOverlappingRoomOccupation() {
        ClassroomSchedulingPolicy policy = new ClassroomSchedulingPolicy(
                repository(100, List.of(schedule(2, 3))));

        assertThatThrownBy(() -> policy.validate(null, "term", null, 40, List.of(schedule(1, 2))))
                .isInstanceOf(ClassroomRuleException.class)
                .extracting("code").isEqualTo("COURSE_CLASSROOM_CONFLICT");
    }

    @Test void acceptsSameRoomAtNonOverlappingPeriods() {
        ClassroomSchedulingPolicy policy = new ClassroomSchedulingPolicy(
                repository(50, List.of(schedule(3, 4))));

        assertThatCode(() -> policy.validate(null, "term", null, 40, List.of(schedule(1, 2))))
                .doesNotThrowAnyException();
    }

    private static CourseRepository repository(int capacity, List<Schedule> occupied) {
        return (CourseRepository) Proxy.newProxyInstance(CourseRepository.class.getClassLoader(),
                new Class<?>[]{CourseRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findClassroom" -> Optional.of(new Classroom("教一-101", capacity, true));
                    case "findClassroomSchedules" -> occupied;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Schedule schedule(int start, int end) {
        return new Schedule("schedule", "offering", DayOfWeek.MONDAY,
                start, end, 1, 16, "教一-101");
    }
}
