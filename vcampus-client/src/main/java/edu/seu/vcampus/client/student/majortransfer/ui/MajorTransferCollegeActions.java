package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;
import java.util.function.Consumer;

final class MajorTransferCollegeActions {
    private final JComponent parent;
    private final StudentClientService students;
    private final JPanel actions;
    private final JLabel status;
    private final Runnable reloadDetail;
    private final Runnable reloadBatch;
    private final Consumer<EmbeddedEditor> openEditor;
    private final Runnable closeEditor;

    MajorTransferCollegeActions(JComponent parent, StudentClientService students,
            JPanel actions, JLabel status, Runnable reloadDetail, Runnable reloadBatch,
            Consumer<EmbeddedEditor> openEditor, Runnable closeEditor) {
        this.parent = parent;
        this.students = students;
        this.actions = actions;
        this.status = status;
        this.reloadDetail = reloadDetail;
        this.reloadBatch = reloadBatch;
        this.openEditor = openEditor;
        this.closeEditor = closeEditor;
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
            add("录入成绩", "recordScoreButton", () -> openEditor.accept(
                    new MajorTransferScoreEditorPanel(students, app, reloadDetail, closeEditor)));
            add("批量导入成绩", "importScoreButton", () -> MajorTransferScoreImport.choose(
                    parent, students, app.optionId(), reloadBatch));
        } else if (targetOwned && app.status() == MajorTransferStatus.ASSESSED) {
            add("终审通过", "finalizeButton", () -> finalizeApplication(app));
        } else if (targetOwned && (app.status() == MajorTransferStatus.PENDING_EFFECTIVE
                || app.status() == MajorTransferStatus.EXECUTION_FAILED)) {
            add("执行转专业", "executeButton", () -> openEditor.accept(
                    new MajorTransferExecutionPanel(students, app, reloadDetail, closeEditor)));
        }
        if (targetOwned && MajorTransferStateMachine.adminMayCancel(app.status())) {
            add("取消申请", "cancelButton", () -> cancel(app));
        }
        actions.revalidate();
        actions.repaint();
    }

    private void source(MajorTransferApplicationView app, boolean approve) {
        if (!approve) {
            openEditor.accept(new MajorTransferReasonEditorPanel("驳回原因",
                    reason -> sourceWithReason(app, false, reason), closeEditor));
            return;
        }
        sourceWithReason(app, true, "原学院核实通过");
    }

    private void sourceWithReason(MajorTransferApplicationView app, boolean approve, String reason) {
        students.reviewTransferSource(new ReviewMajorTransferSourceCommand(app.applicationId(),
                approve ? MajorTransferDecision.APPROVE : MajorTransferDecision.REJECT,
                approve, approve, approve, reason, app.applicationVersion()))
                .whenComplete((response, failure) -> complete(response));
    }

    private void target(MajorTransferApplicationView app, boolean approve) {
        if (!approve) {
            openEditor.accept(new MajorTransferReasonEditorPanel("驳回原因",
                    reason -> targetWithReason(app, false, reason), closeEditor));
            return;
        }
        targetWithReason(app, true, "转入学院审核通过");
    }

    private void targetWithReason(MajorTransferApplicationView app, boolean approve, String reason) {
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
        openEditor.accept(new MajorTransferReasonEditorPanel("取消原因",
                reason -> cancelWithReason(app, reason), closeEditor));
    }

    private void cancelWithReason(MajorTransferApplicationView app, String reason) {
        students.cancelTransfer(new CancelMajorTransferCommand(
                app.applicationId(), reason, app.applicationVersion()))
                .whenComplete((response, failure) -> complete(response));
    }

    private void complete(ResponseBody<?> response) {
        SwingUtilities.invokeLater(() -> {
            if (response != null && response.success()) {
                closeEditor.run();
                reloadDetail.run();
            }
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
}
