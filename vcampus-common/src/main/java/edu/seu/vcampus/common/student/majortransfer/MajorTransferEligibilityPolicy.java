package edu.seu.vcampus.common.student.majortransfer;

import java.util.Objects;

import static edu.seu.vcampus.common.student.StudentType.UNDERGRADUATE;

/** Applies the shared eligibility rules for an undergraduate major transfer. */
public final class MajorTransferEligibilityPolicy {
    /** Evaluates the supplied trusted student and organization facts. */
    public Result check(MajorTransferEligibilityInput input) {
        Objects.requireNonNull(input, "input");
        if (input.studentType() != UNDERGRADUATE || !input.active()
                || !input.enrolled() || !input.onCampus()) {
            return ineligible("仅允许正常在籍且在校的本科生申请");
        }
        if (input.applicationStart() == null) {
            return ineligible("申请时间无效");
        }
        int grade = input.applicationStart().getYear()
                - (input.applicationStart().getMonthValue() < 9 ? 1 : 0)
                - input.enrollmentYear() + 1;
        if (grade != 1 && grade != 2) {
            return ineligible("仅允许大一、大二的学生申请");
        }
        if (blankOrSame(input.currentDepartmentId(), input.targetDepartmentId())) {
            return invalidTarget("转专业必须跨学院办理");
        }
        if (blankOrSame(input.currentMajorId(), input.targetMajorId())) {
            return invalidTarget("目标专业不能与当前专业相同");
        }
        return new Result(true, null, "符合申请条件");
    }

    private static Result ineligible(String message) {
        return new Result(false, "TRANSFER_INELIGIBLE", message);
    }

    private static Result invalidTarget(String message) {
        return new Result(false, "TRANSFER_INVALID_TARGET", message);
    }

    private static boolean blankOrSame(String left, String right) {
        return left == null || left.isBlank() || right == null || right.isBlank()
                || left.equals(right);
    }

    /** Immutable result of an eligibility evaluation. */
    public record Result(boolean eligible, String reasonCode, String message)
            implements java.io.Serializable { }
}
