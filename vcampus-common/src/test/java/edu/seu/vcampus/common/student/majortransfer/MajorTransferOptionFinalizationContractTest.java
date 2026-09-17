package edu.seu.vcampus.common.student.majortransfer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MajorTransferOptionFinalizationContractTest {
    @Test
    void commandsRequireOptionIdentityAndNonNegativeVersion() {
        assertThatThrownBy(() -> new FinalizeMajorTransferOptionCommand("", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EffectiveMajorTransferOptionCommand("option-ai", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new RollbackMajorTransferOptionCommand("option-ai", 3).optionId())
                .isEqualTo("option-ai");
    }

    @Test
    void readinessCarriesExplicitOptionScope() {
        var view = new MajorTransferOptionReadinessView(
                "option-ai", "batch-1", "dept-cse", "major-ai", "人工智能",
                MajorTransferOptionFinalizationStatus.PROCESSING,
                1, 0, 0, 0, 0, true, false, false, null, 2);

        assertThat(view.optionId()).isEqualTo("option-ai");
        assertThat(view.targetMajorName()).isEqualTo("人工智能");
        assertThat(view.optionVersion()).isEqualTo(2);
    }
}
