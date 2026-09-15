package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.DeleteMajorTransferAttachmentCommand;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationType;
import edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferDraftCommand;
import edu.seu.vcampus.common.student.majortransfer.SubmitMajorTransferCommand;
import edu.seu.vcampus.common.student.majortransfer.UploadMajorTransferAttachmentCommand;
import edu.seu.vcampus.common.student.majortransfer.WithdrawMajorTransferCommand;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Draft, submit, withdraw and attachment actions for the major-transfer panel. */
abstract class MyMajorTransferPanelActions extends MyMajorTransferPanelRefreshing {

    /** Creates the actions segment of the major-transfer panel. */
    protected MyMajorTransferPanelActions(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    void saveDraft() {
        MajorTransferOptionItem selected = (MajorTransferOptionItem) targetMajorCombo.getSelectedItem();
        if (selected == null) {
            errorLabel.setText("请选择目标专业");
            return;
        }
        MajorTransferApplicationType type = applicationTypeCombo.getSelectedIndex() == 0
                ? MajorTransferApplicationType.ORDINARY : MajorTransferApplicationType.DIFFICULTY;
        long version = workspace != null && workspace.application() != null
                ? workspace.application().applicationVersion() : 0;
        var cmd = new SaveMajorTransferDraftCommand(currentApplicationId,
                selected.option().batchId(), selected.option().optionId(),
                type, reasonArea.getText(), version);
        setFormEnabled(false);
        errorLabel.setText(" ");
        students.saveTransferDraft(cmd).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    setFormEnabled(true);
                    if (response != null && response.success()) {
                        errorLabel.setText(" ");
                        refresh();
                    }
                    else errorLabel.setText(response != null ? response.message() : "网络错误");
                }));
    }

    void submit() {
        if (workspace == null || workspace.application() == null) return;
        String reason = reasonArea.getText();
        if (reason == null || reason.isBlank()) {
            errorLabel.setText("请填写申请理由后再提交");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "确认提交转专业申请？提交后将等待管理员审核。", "确认",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        long version = workspace.application().applicationVersion();
        setFormEnabled(false);
        errorLabel.setText("正在提交...");
        students.submitTransfer(new SubmitMajorTransferCommand(
                workspace.application().applicationId(), version))
                .whenComplete((response, error) ->
                        SwingUtilities.invokeLater(() -> {
                            setFormEnabled(true);
                            if (response != null && response.success()) {
                                errorLabel.setText(" ");
                                refresh();
                            }
                            else errorLabel.setText(response != null ? response.message() : "提交失败");
                        }));
    }

    void withdraw() {
        if (workspace == null || workspace.application() == null) return;
        int confirm = JOptionPane.showConfirmDialog(this, "确认撤回转专业申请？", "确认",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        long version = workspace.application().applicationVersion();
        setFormEnabled(false);
        students.withdrawTransfer(new WithdrawMajorTransferCommand(
                workspace.application().applicationId(), version))
                .whenComplete((response, error) ->
                        SwingUtilities.invokeLater(() -> {
                            setFormEnabled(true);
                            if (response != null && response.success()) refresh();
                            else errorLabel.setText(response != null ? response.message() : "撤回失败");
                        }));
    }

    void uploadAttachment() {
        if (workspace == null || workspace.application() == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF/JPEG/PNG文件", "pdf", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path file = chooser.getSelectedFile().toPath();
        try {
            byte[] content = Files.readAllBytes(file);
            if (content.length > 5 * 1024 * 1024) {
                errorLabel.setText("附件大小不能超过5MB");
                return;
            }
            String name = file.getFileName().toString();
            String ct = name.endsWith(".pdf") ? "application/pdf" :
                    name.endsWith(".png") ? "image/png" : "image/jpeg";
            long version = workspace.application().applicationVersion();
            students.uploadTransferAttachment(new UploadMajorTransferAttachmentCommand(
                    workspace.application().applicationId(), name, ct, content, version))
                    .whenComplete((response, error) ->
                            SwingUtilities.invokeLater(() -> {
                                if (response != null && response.success()) refresh();
                                else errorLabel.setText(response != null ? response.message() : "上传失败");
                            }));
        } catch (IOException e) {
            errorLabel.setText("读取文件失败: " + e.getMessage());
        }
    }

    @Override void deleteAttachment(String attachmentId) {
        if (workspace == null || workspace.application() == null) return;
        long version = workspace.application().applicationVersion();
        students.deleteTransferAttachment(new DeleteMajorTransferAttachmentCommand(
                attachmentId, workspace.application().applicationId(), version))
                .whenComplete((response, error) ->
                        SwingUtilities.invokeLater(() -> {
                            if (response != null && response.success()) refresh();
                            else errorLabel.setText(response != null ? response.message() : "删除失败");
                        }));
    }
}
