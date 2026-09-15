package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.PdfDocument;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.IOException;
import java.nio.file.Files;

/** PDF export flow for the profile panel. */
abstract class MyStudentProfilePanelExporting extends MyStudentProfilePanelSubmitting {

    /** Creates the exporting segment of the profile panel. */
    protected MyStudentProfilePanelExporting(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    void exportPdf() {
        exportButton.setEnabled(false); errorLabel.setText("正在生成正式信息 PDF…");
        students.exportProfilePdf().whenComplete((body, failure) -> {
            if (failure != null || body == null || !body.success() || body.data() == null) {
                onEdt(() -> { errorLabel.setText(message(body, "PDF 生成失败，请稍后重试")); exportButton.setEnabled(true); });
                return;
            }
            PdfDocument document = body.data();
            onEdt(() -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("PDF 文件 (*.pdf)", "pdf"));
                chooser.setSelectedFile(new java.io.File(ensurePdfExtension(document.filename())));
                if (chooser.showSaveDialog(SwingUtilities.getWindowAncestor(this)) == JFileChooser.APPROVE_OPTION) {
                    java.io.File file = ensurePdfExtension(chooser.getSelectedFile());
                    try { Files.write(file.toPath(), document.content()); errorLabel.setText("已导出到: " + file.getAbsolutePath()); }
                    catch (IOException error) { errorLabel.setText("文件保存失败，请检查目录权限"); }
                } else { errorLabel.setText(" "); }
                exportButton.setEnabled(connection.state() == ConnectionState.CONNECTED);
            });
        });
    }
}
