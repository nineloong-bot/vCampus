package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationView;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferAttachmentDocument;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

final class MajorTransferAttachmentDownloader {
    private MajorTransferAttachmentDownloader() { }

    static void render(JComponent parent, StudentClientService students, JPanel target,
            JLabel status, List<MajorTransferApplicationView.AttachmentInfo> attachments) {
        target.removeAll();
        if (!attachments.isEmpty()) target.add(new JLabel("申请材料"));
        for (var attachment : attachments) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row.add(new JLabel(attachment.fileName() + "（" + attachment.fileSize() + " 字节）"));
            JButton download = new JButton("下载");
            download.setName("downloadAttachmentButton");
            download.addActionListener(event -> download(
                    parent, students, status, attachment.attachmentId()));
            row.add(download);
            target.add(row);
        }
        target.revalidate();
        target.repaint();
    }

    private static void download(JComponent parent, StudentClientService students,
            JLabel status, String attachmentId) {
        students.getTransferAttachment(attachmentId).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success() || response.data() == null) {
                        status.setText(response == null || response.message() == null
                                ? "附件下载失败" : response.message());
                        return;
                    }
                    save(parent, status, response.data());
                }));
    }

    private static void save(JComponent parent, JLabel status,
            MajorTransferAttachmentDocument document) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(document.fileName()));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.write(chooser.getSelectedFile().toPath(), document.content());
            status.setText("附件已保存");
        } catch (IOException error) {
            status.setText("附件保存失败，请检查目录权限");
        }
    }
}
