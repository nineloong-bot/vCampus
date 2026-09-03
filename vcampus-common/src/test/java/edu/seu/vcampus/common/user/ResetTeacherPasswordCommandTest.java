package edu.seu.vcampus.common.user;

import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResetTeacherPasswordCommandTest {
    @Test
    void carriesOnlyTeacherIdentityAndOptimisticLockVersion() {
        ResetTeacherPasswordCommand command =
                new ResetTeacherPasswordCommand("teacher-id", 7);

        assertThat(command).isInstanceOf(Serializable.class);
        assertThat(command.targetUserId()).isEqualTo("teacher-id");
        assertThat(command.expectedRowVersion()).isEqualTo(7);
        assertThat(command.toString())
                .doesNotContain("12345678", "password", "hash", "salt", "token");
    }

    @Test
    void rejectsMissingIdentityAndNegativeVersion() {
        assertThatThrownBy(() -> new ResetTeacherPasswordCommand(" ", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_VALIDATION_FAILED");
        assertThatThrownBy(() -> new ResetTeacherPasswordCommand("teacher-id", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_VALIDATION_FAILED");
    }
}
