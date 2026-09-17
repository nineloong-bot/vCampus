package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferScoreTemplateDocument;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.util.Objects;

/**
 * Handles downloading pre-filled CSV score entry templates for college administrators.
 */
final class MajorTransferScoreExport {
    private MajorTransferScoreExport() { }

    /**
     * Fetches the pre-populated score import template for an option and prompts the user to save it.
     *
     * @param parent   parent UI component
     * @param students student client service
     * @param optionId major transfer option ID
     */
    static void download(Component parent, StudentClientService students, String optionId) {
        Objects.requireNonNull(students, "students");
        Objects.requireNonNull(optionId, "optionId");
        students.exportTransferScoreTemplate(optionId).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success() || response.data() == null) {
                        String message = response == null || response.message() == null
                                ? "下载模板失败，请重试" : response.message();
                        JOptionPane.showMessageDialog(parent, message, "下载失败", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    save(parent, response.data());
                }));
    }

    private static void save(Component parent, MajorTransferScoreTemplateDocument document) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("保存成绩导入模板");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV 文件 (*.csv)", "csv"));
        chooser.setSelectedFile(new File(document.fileName()));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase().endsWith(".csv")) {
            target = new File(target.getParentFile(), target.getName() + ".csv");
        }
        try {
            Files.write(target.toPath(), document.content());
            JOptionPane.showMessageDialog(parent, "模板已成功保存至：\n" + target.getAbsolutePath(),
                    "下载成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception error) {
            JOptionPane.showMessageDialog(parent, "文件保存失败：" + error.getMessage(),
                    "保存失败", JOptionPane.ERROR_MESSAGE);
        }
    }
}
