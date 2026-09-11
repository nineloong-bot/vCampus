package edu.seu.vcampus.common.student.majortransfer;

import org.junit.jupiter.api.Test;

import static edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus.*;
import static org.assertj.core.api.Assertions.*;

class MajorTransferStateMachineTest {

    @Test
    void draftCanOnlyTransitionToSubmitted() {
        assertThatCode(() -> MajorTransferStateMachine.requireTransition(DRAFT, SUBMITTED))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> MajorTransferStateMachine.requireTransition(DRAFT, SOURCE_APPROVED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void submittedAllowsWithdrawToDraft() {
        assertThatCode(() -> MajorTransferStateMachine.requireTransition(SUBMITTED, DRAFT))
                .doesNotThrowAnyException();
    }

    @Test
    void submittedAllowsSourceApproval() {
        assertThatCode(() -> MajorTransferStateMachine.requireTransition(SUBMITTED, SOURCE_APPROVED))
                .doesNotThrowAnyException();
    }

    @Test
    void finalApprovalDoesNotBecomeEffectiveImmediately() {
        assertThatCode(() -> MajorTransferStateMachine.requireTransition(
                PROPOSED, PENDING_EFFECTIVE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> MajorTransferStateMachine.requireTransition(
                PROPOSED, EFFECTIVE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pendingEffectiveAllowsExecution() {
        assertThatCode(() -> MajorTransferStateMachine.requireTransition(
                PENDING_EFFECTIVE, EFFECTIVE)).doesNotThrowAnyException();
    }

    @Test
    void pendingEffectiveAllowsExecutionFailure() {
        assertThatCode(() -> MajorTransferStateMachine.requireTransition(
                PENDING_EFFECTIVE, EXECUTION_FAILED)).doesNotThrowAnyException();
    }

    @Test
    void executionFailedCanRetryOrBeCancelled() {
        assertThatCode(() -> MajorTransferStateMachine.requireTransition(
                EXECUTION_FAILED, EFFECTIVE)).doesNotThrowAnyException();
        assertThatCode(() -> MajorTransferStateMachine.requireTransition(
                EXECUTION_FAILED, CANCELLED)).doesNotThrowAnyException();
    }

    @Test
    void terminalStatesAllowNoTransitions() {
        assertThatThrownBy(() -> MajorTransferStateMachine.requireTransition(EFFECTIVE, DRAFT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MajorTransferStateMachine.requireTransition(REJECTED, DRAFT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MajorTransferStateMachine.requireTransition(CANCELLED, DRAFT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void studentCanWithdrawOnlyBeforeSourceReview() {
        assertThat(MajorTransferStateMachine.studentMayWithdraw(SUBMITTED)).isTrue();
        assertThat(MajorTransferStateMachine.studentMayWithdraw(SOURCE_APPROVED)).isFalse();
        assertThat(MajorTransferStateMachine.studentMayWithdraw(DRAFT)).isFalse();
    }

    @Test
    void studentCanEditOnlyInDraft() {
        assertThat(MajorTransferStateMachine.studentMayEdit(DRAFT)).isTrue();
        assertThat(MajorTransferStateMachine.studentMayEdit(SUBMITTED)).isFalse();
    }

    @Test
    void studentCanSubmitOnlyFromDraft() {
        assertThat(MajorTransferStateMachine.studentMaySubmit(DRAFT)).isTrue();
        assertThat(MajorTransferStateMachine.studentMaySubmit(SUBMITTED)).isFalse();
    }

    @Test
    void adminMayCancelAfterSourceApprovalAndBeforeEffective() {
        assertThat(MajorTransferStateMachine.adminMayCancel(SOURCE_APPROVED)).isTrue();
        assertThat(MajorTransferStateMachine.adminMayCancel(QUALIFIED)).isTrue();
        assertThat(MajorTransferStateMachine.adminMayCancel(ASSESSED)).isTrue();
        assertThat(MajorTransferStateMachine.adminMayCancel(PROPOSED)).isTrue();
        assertThat(MajorTransferStateMachine.adminMayCancel(PENDING_EFFECTIVE)).isTrue();
        assertThat(MajorTransferStateMachine.adminMayCancel(DRAFT)).isFalse();
        assertThat(MajorTransferStateMachine.adminMayCancel(SUBMITTED)).isFalse();
        assertThat(MajorTransferStateMachine.adminMayCancel(EFFECTIVE)).isFalse();
        assertThat(MajorTransferStateMachine.adminMayCancel(REJECTED)).isFalse();
    }

    @Test
    void completeWorkflowPathIsValid() {
        assertThatCode(() -> {
            MajorTransferStateMachine.requireTransition(DRAFT, SUBMITTED);
            MajorTransferStateMachine.requireTransition(SUBMITTED, SOURCE_APPROVED);
            MajorTransferStateMachine.requireTransition(SOURCE_APPROVED, QUALIFIED);
            MajorTransferStateMachine.requireTransition(QUALIFIED, ASSESSED);
            MajorTransferStateMachine.requireTransition(ASSESSED, PROPOSED);
            MajorTransferStateMachine.requireTransition(PROPOSED, PENDING_EFFECTIVE);
            MajorTransferStateMachine.requireTransition(PENDING_EFFECTIVE, EFFECTIVE);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectionPathIsValidFromAnyReviewStage() {
        for (MajorTransferStatus status : new MajorTransferStatus[]{
                SUBMITTED, SOURCE_APPROVED, QUALIFIED, ASSESSED, PROPOSED}) {
            assertThatCode(() -> MajorTransferStateMachine.requireTransition(status, REJECTED))
                    .doesNotThrowAnyException();
        }
    }
}
