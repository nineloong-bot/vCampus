package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.server.student.domain.StudentClass;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MajorTransferBatchPlannerTest {
    private final MajorTransferBatchPlanner planner = new MajorTransferBatchPlanner();

    @Test
    void balancesUnevenClassesAndBreaksTiesDeterministically() {
        var assignments = planner.plan(List.of(
                        candidate("app-3", "student-3", "major-cs", "213", 2026),
                        candidate("app-1", "student-1", "major-cs", "213", 2026),
                        candidate("app-2", "student-2", "major-cs", "213", 2026),
                        candidate("app-4", "student-4", "major-cs", "213", 2026)),
                List.of(slot("class-3", "major-cs", 2026, 3, 30),
                        slot("class-2", "major-cs", 2026, 2, 30),
                        slot("class-1", "major-cs", 2026, 1, 28)));

        assertThat(assignments).extracting(MajorTransferBatchPlanner.Assignment::applicationId)
                .containsExactly("app-1", "app-2", "app-3", "app-4");
        assertThat(assignments).extracting(a -> a.targetClass().classId())
                .containsExactly("class-1", "class-1", "class-1", "class-2");
        assertThat(assignments).extracting(MajorTransferBatchPlanner.Assignment::sequenceKey)
                .containsExactly("STUDENT_NUMBER:213:26:1", "STUDENT_NUMBER:213:26:1",
                        "STUDENT_NUMBER:213:26:1",
                        "STUDENT_NUMBER:213:26:2");
    }

    @Test
    void balancesEachMajorAndCohortIndependently() {
        var assignments = planner.plan(List.of(
                        candidate("a-26", "s-26", "major-cs", "213", 2026),
                        candidate("a-25", "s-25", "major-cs", "213", 2025),
                        candidate("a-ee", "s-ee", "major-ee", "214", 2026)),
                List.of(slot("cs-26", "major-cs", 2026, 1, 20),
                        slot("cs-25", "major-cs", 2025, 1, 20),
                        slot("ee-26", "major-ee", 2026, 1, 20)));

        assertThat(assignments).extracting(a -> a.targetClass().classId())
                .containsExactly("cs-25", "cs-26", "ee-26");
    }

    @Test
    void rejectsMissingMatchingYearClass() {
        assertThatThrownBy(() -> planner.plan(
                List.of(candidate("app", "student", "major-cs", "213", 2026)),
                List.of(slot("class", "major-cs", 2025, 1, 20))))
                .isInstanceOf(MajorTransferException.class)
                .hasMessageContaining("缺少").hasMessageContaining("2026");
    }

    @Test
    void rejectsCandidatesFromMixedTargetDepartments() {
        var candidates = List.of(
                new MajorTransferBatchPlanner.Candidate("a", "s1", "o1", "dept-cs",
                        "major-cs", "213", 2026),
                new MajorTransferBatchPlanner.Candidate("b", "s2", "o2", "dept-ee",
                        "major-ee", "214", 2026));

        assertThatThrownBy(() -> planner.plan(candidates, List.of()))
                .isInstanceOf(MajorTransferException.class)
                .hasMessageContaining("目标学院");
    }

    private MajorTransferBatchPlanner.Candidate candidate(String applicationId, String studentId,
            String majorId, String majorCode, int year) {
        return new MajorTransferBatchPlanner.Candidate(applicationId, studentId,
                "option-" + applicationId, "dept-cs", majorId, majorCode, year);
    }

    private MajorTransferBatchPlanner.ClassSlot slot(String classId, String majorId,
            int year, int classNumber, int count) {
        return new MajorTransferBatchPlanner.ClassSlot(new StudentClass(classId, majorId,
                "code-" + classId, "name-" + classId, year, classNumber, true, 0), count);
    }
}
