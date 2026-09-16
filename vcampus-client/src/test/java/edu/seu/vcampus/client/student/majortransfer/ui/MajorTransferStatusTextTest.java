package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.common.student.majortransfer.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MajorTransferStatusTextTest {
    @Test
    void mapsEveryTransferEnumToChineseText() {
        for (var status : MajorTransferStatus.values()) {
            assertThat(MajorTransferStatusText.status(status)).doesNotContain(status.name());
        }
        assertThat(MajorTransferStatusText.status(MajorTransferStatus.SUBMITTED)).isEqualTo("待审核");
        assertThat(MajorTransferStatusText.status(MajorTransferStatus.PENDING_EFFECTIVE)).isEqualTo("待生效");
        assertThat(MajorTransferStatusText.decision(MajorTransferDecision.APPROVE)).isEqualTo("已通过");
        assertThat(MajorTransferStatusText.decision(MajorTransferDecision.REJECT)).isEqualTo("已驳回");
        assertThat(MajorTransferStatusText.reviewStage(MajorTransferReviewStage.SOURCE_REVIEW))
                .isEqualTo("转出学院审核");
    }

    @Test
    void mapsBatchStatusAndNullToSafeChineseLabels() {
        assertThat(MajorTransferStatusText.batchStatus(MajorTransferBatchStatus.OPEN)).isEqualTo("开放报名");
        assertThat(MajorTransferStatusText.batchStatus(MajorTransferBatchStatus.EFFECTIVE))
                .isEqualTo("已终审生效");
        assertThat(MajorTransferStatusText.status(null)).isEqualTo("未知状态");
    }
}
