package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.SubmitStudentProfileCommand;
import edu.seu.vcampus.common.student.WithdrawStudentProfileCommand;

import javax.swing.JOptionPane;

/** Submit and withdraw flows for the profile panel. */
abstract class MyStudentProfilePanelSubmitting extends MyStudentProfilePanelEditing {

    /** Creates the submitting segment of the profile panel. */
    protected MyStudentProfilePanelSubmitting(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    void submitOrWithdraw() {
        if (pendingApplication()) withdrawPending();
        else submitDraft();
    }

    void submitDraft() {
        if (workspace == null || workspace.application() == null || !submitButton.isEnabled()) return;
        int decision = JOptionPane.showConfirmDialog(this, "提交后在管理员审核完成前不能继续编辑，确定提交吗？",
                "提交资料审核", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (decision != JOptionPane.OK_OPTION) return;
        setControls(false); errorLabel.setText("正在提交审核…");
        students.submitProfile(new SubmitStudentProfileCommand(workspace.application().applicationVersion()))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (failure != null || body == null || !body.success() || body.data() == null) {
                        errorLabel.setText(message(body, "提交失败，请稍后重试")); render(workspace); return;
                    }
                    render(body.data());
                }));
    }

    void withdrawPending() {
        if (!pendingApplication() || !submitButton.isEnabled()) return;
        int decision = JOptionPane.showConfirmDialog(this,
                "撤回后可在当前修改内容基础上继续编辑，确定撤回申请吗？",
                "撤回资料申请", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (decision != JOptionPane.YES_OPTION) return;
        long expectedVersion = workspace.application().applicationVersion();
        setControls(false);
        errorLabel.setText("正在撤回申请…");
        students.withdrawProfile(new WithdrawStudentProfileCommand(expectedVersion))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (failure != null || body == null || !body.success() || body.data() == null) {
                        errorLabel.setText(message(body, "撤回失败，申请可能已被管理员处理"));
                        refreshProfile();
                        return;
                    }
                    render(body.data());
                    errorLabel.setText("申请已撤回，可继续编辑上一版修改");
                }));
    }
}
