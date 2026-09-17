package edu.seu.vcampus.common.student.majortransfer;

import edu.seu.vcampus.common.student.StudentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MajorTransferEligibilityPolicyTest {
    private final MajorTransferEligibilityPolicy policy = new MajorTransferEligibilityPolicy();

    @Test
    void acceptsSecondYearStudentWhenTargetCollegeDiffers() {
        var result = policy.check(input(2025, LocalDate.of(2006, 5, 1), "dept-cs", "dept-math"));

        assertThat(result.eligible()).isTrue();
    }

    @Test
    void rejectsThirdYearStudentEvenWhenTargetCollegeDiffers() {
        var result = policy.check(input(2024, LocalDate.of(2005, 5, 1), "dept-cs", "dept-math"));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("TRANSFER_INELIGIBLE");
        assertThat(result.message()).isEqualTo("仅允许大一、大二的学生申请");
    }

    @Test
    void acceptsGradeOneAndTwoRegardlessOfAgeAndRejectsSameDepartment() {
        // Younger student in grade 1
        assertThat(policy.check(input(2026, LocalDate.of(2011, 1, 1), "dept-cs", "dept-math")).eligible())
                .isTrue();
        // Older student in grade 2
        assertThat(policy.check(input(2025, LocalDate.of(1998, 1, 1), "dept-cs", "dept-math")).eligible())
                .isTrue();

        var sameCollege = policy.check(input(2026, LocalDate.of(2008, 9, 1), "dept-cs", "dept-cs"));
        assertThat(sameCollege.eligible()).isFalse();
        assertThat(sameCollege.reasonCode()).isEqualTo("TRANSFER_INVALID_TARGET");
    }

    private static MajorTransferEligibilityInput input(int enrollmentYear, LocalDate birthDate,
                                                         String currentDepartmentId,
                                                         String targetDepartmentId) {
        return new MajorTransferEligibilityInput(
                StudentType.UNDERGRADUATE, true, true, true, birthDate,
                LocalDate.of(2026, 9, 15), enrollmentYear,
                currentDepartmentId, targetDepartmentId, "major-current", "major-target");
    }
}
