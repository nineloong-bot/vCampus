package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;

final class MajorTransferCollegeActions {
    private final JComponent parent;
    private final StudentClientService students;
    private final JPanel actions;
    private final JLabel status;
    private final Runnable reloadDetail;
    private final Runnable reloadBatch;

    MajorTransferCollegeActions(JComponent parent, StudentClientService students,
            JPanel actions, JLabel status, Runnable reloadDetail, Runnable reloadBatch) {
        this.parent = parent;
        this.students = students;
        this.actions = actions;
        this.status = status;
        this.reloadDetail = reloadDetail;
        this.reloadBatch = reloadBatch;
    }

    void render(MajorTransferApplicationView app, boolean targetOwned) {
        actions.removeAll();
        if (app.status() == MajorTransferStatus.SUBMITTED && app.sourceApprovalAllowed()) {
            add("转出审批通过", "sourceReviewButton", () -> source(app, true));
            add("转出审批驳回", null, () -> source(app, false));
        } else if (targetOwned && app.status() == MajorTransferStatus.SOURCE_APPROVED) {
            add("转入审批通过", "targetReviewButton", () -> target(app, true));
            add("转入审批驳回", null, () -> target(app, false));
        } else if (targetOwned && app.status() == MajorTransferStatus.QUALIFIED) {
            add("录入成绩", "recordScoreButton", () -> MajorTransferCollegeDialogs.score(
                    parent, students, app, reloadDetail));
            add("批量导入成绩", "importScoreButton", () -> MajorTransferScoreImport.choose(
                    parent, students, app.optionId(), reloadBatch));
        } else if (targetOwned && app.status() == MajorTransferStatus.ASSESSED) {
            add("终审通过", "finalizeButton", () -> finalizeApplication(app));
        } else if (targetOwned && (app.status() == MajorTransferStatus.PENDING_EFFECTIVE
                || app.status() == MajorTransferStatus.EXECUTION_FAILED)) {
            add("执行转专业", "executeButton", () -> MajorTransferCollegeDialogs.execute(
                    parent, students, app, reloadDetail));
        }
        if (targetOwned && MajorTransferStateMachine.adminMayCancel(app.status())) {
            add("取消申请", "cancelButton", () -> cancel(app));
        }
        actions.revalidate();
        actions.repaint();
    }

    private void source(MajorTransferApplicationView app, boolean approve) {
        String reason = approve ? "原学院核实通过" : prompt("驳回原因");
        if (!approve && reason == null) return;
        students.reviewTransferSource(new ReviewMajorTransferSourceCommand(app.applicationId(),
                approve ? MajorTransferDecision.APPROVE : MajorTransferDecision.REJECT,
                approve, approve, approve, reason, app.applicationVersion()))
                .whenComplete((response, failure) -> complete(response));
    }

    private void target(MajorTransferApplicationView app, boolean approve) {
        String reason = approve ? "转入学院审核通过" : prompt("驳回原因");
        if (!approve && reason == null) return;
        students.reviewTransferQualification(new ReviewMajorTransferQualificationCommand(
                app.applicationId(), approve ? MajorTransferDecision.APPROVE
                        : MajorTransferDecision.REJECT, reason, app.applicationVersion()))
                .whenComplete((response, failure) -> complete(response));
    }

    private void finalizeApplication(MajorTransferApplicationView app) {
        students.finalizeTransfer(new FinalizeMajorTransferCommand(
                app.applicationId(), app.applicationVersion()))
                .whenComplete((response, failure) -> complete(response));
    }

    private void cancel(MajorTransferApplicationView app) {
        String reason = prompt("取消原因");
        if (reason == null) return;
        students.cancelTransfer(new CancelMajorTransferCommand(
                app.applicationId(), reason, app.applicationVersion()))
                .whenComplete((response, failure) -> complete(response));
    }

    private void complete(ResponseBody<?> response) {
        SwingUtilities.invokeLater(() -> {
            if (response != null && response.success()) reloadDetail.run();
            else status.setText(response == null || response.message() == null
                    ? "操作失败" : response.message());
        });
    }

    private void add(String text, String name, Runnable action) {
        JButton button = new JButton(text);
        button.setName(name);
        button.addActionListener(event -> action.run());
        actions.add(button);
    }

    private String prompt(String label) {
        String value = JOptionPane.showInputDialog(parent, label);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
