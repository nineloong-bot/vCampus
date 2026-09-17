package edu.seu.vcampus.common.student;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests deterministic, major-isolated freshman class assignment. */
class FreshmanClassAssignerTest {
    @Test
    void splitsThirtySixStudentsIntoTwoBalancedClasses() {
        var assignments = FreshmanClassAssigner.assign(rows("软件工程", 18, 18), 2026);

        assertThat(assignments).hasSize(36);
        assertThat(assignments).extracting(FreshmanClassAssignment::classNumber)
                .containsOnly(1, 2);
        assertThat(assignments.stream().filter(value -> value.classNumber() == 1)).hasSize(18);
        assertThat(assignments.stream().filter(value -> value.classNumber() == 2)).hasSize(18);
        assertThat(assignments.stream().filter(value -> value.classNumber() == 1
                && value.row().gender().equals("男"))).hasSize(9);
        assertThat(assignments.stream().filter(value -> value.classNumber() == 2
                && value.row().gender().equals("女"))).hasSize(9);
        assertThat(assignments).allSatisfy(value -> assertThat(value.className())
                .isIn("软件工程2601班", "软件工程2602班"));
    }

    @Test
    void keepsDifferentMajorsInSeparateNumberingSequences() {
        var rows = new ArrayList<>(rows("软件工程", 20, 0));
        rows.addAll(rows("人工智能", 20, 0));

        var assignments = FreshmanClassAssigner.assign(rows, 2026);

        assertThat(assignments).allSatisfy(value -> assertThat(value.classNumber()).isEqualTo(1));
        assertThat(assignments).extracting(FreshmanClassAssignment::className)
                .contains("软件工程2601班", "人工智能2601班");
    }

    @Test
    void producesSameAssignmentWhenInputOrderChanges() {
        var original = rows("软件工程", 18, 18);
        var shuffled = new ArrayList<>(original);
        Collections.reverse(shuffled);

        assertThat(FreshmanClassAssigner.assign(shuffled, 2026))
                .extracting(value -> value.row().idDocumentNumber() + ":" + value.classNumber())
                .containsExactlyInAnyOrderElementsOf(FreshmanClassAssigner.assign(original, 2026).stream()
                        .map(value -> value.row().idDocumentNumber() + ":" + value.classNumber()).toList());
    }

    private static List<FreshmanAdmissionRow> rows(String major, int males, int females) {
        var values = new ArrayList<FreshmanAdmissionRow>();
        for (int index = 0; index < males + females; index++) {
            values.add(new FreshmanAdmissionRow(index + 2, "学生" + index,
                    index < males ? "男" : "女", String.format("ID%04d", index), "计算机学院", major));
        }
        return values;
    }
}
