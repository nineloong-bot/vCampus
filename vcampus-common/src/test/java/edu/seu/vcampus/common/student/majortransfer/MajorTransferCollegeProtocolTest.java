package edu.seu.vcampus.common.student.majortransfer;

import org.junit.jupiter.api.Test;

import static edu.seu.vcampus.common.student.majortransfer.MajorTransferCollegeStatus.REVIEWED;
import static org.assertj.core.api.Assertions.assertThat;

class MajorTransferCollegeProtocolTest {
    @Test
    void readinessRepresentsReviewedZeroAdmissionCollege() {
        var view = new MajorTransferCollegeReadinessView("batch", "dept", REVIEWED,
                0, 0, 4, 1, 0, false, true, true, null, 3);

        assertThat(view.pendingEffective()).isZero();
        assertThat(view.canReview()).isFalse();
        assertThat(view.canRollback()).isTrue();
        assertThat(view.canEffect()).isTrue();
        assertThat(view.collegeVersion()).isEqualTo(3);
    }

    @Test
    void reviewAndEffectResultsHaveDistinctCounts() {
        var review = new MajorTransferBatchReviewResult("batch", "dept", 2, REVIEWED, 1);
        var effect = new MajorTransferBatchEffectResult("batch", "dept", 2, 3,
                MajorTransferCollegeStatus.EFFECTIVE, 2);

        assertThat(review.preparedApplications()).isEqualTo(2);
        assertThat(effect.effectiveStudents()).isEqualTo(2);
        assertThat(effect.droppedEnrollments()).isEqualTo(3);
    }
}
