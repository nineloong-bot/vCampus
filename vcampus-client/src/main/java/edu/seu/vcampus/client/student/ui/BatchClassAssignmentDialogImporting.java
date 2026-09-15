package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.BatchImportCommand;
import edu.seu.vcampus.common.student.BatchImportResult;
import edu.seu.vcampus.common.student.BatchStudentEntry;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Batch import submission and result pane for the batch assignment segments. */
abstract class BatchClassAssignmentDialogImporting extends BatchClassAssignmentDialogAssigning {

    BatchClassAssignmentDialogImporting(Window owner, StudentClientService students,
            MajorView major, List<ClassView> classes) {
        super(owner, students, major, classes);
    }

    void doImport() {
        if (rows.isEmpty()) return;
        List<String> classIds = availableClasses.stream().map(ClassView::classId).toList();
        List<BatchStudentEntry> entries = new ArrayList<>();
        for (StudentRow r : rows) {
            entries.add(new BatchStudentEntry(r.campusCard, r.name, r.gender, r.score, r.classIndex));
        }
        BatchImportCommand command = new BatchImportCommand(major.majorId(), classIds, entries);
        long current = generation.incrementAndGet();
        importButton.setEnabled(false);
        assignButton.setEnabled(false);
        errorLabel.setText("正在导入...");
        CompletableFuture<ResponseBody<BatchImportResult>> response;
        try { response = students.batchImport(command); }
        catch (RuntimeException f) { response = CompletableFuture.failedFuture(f); }
        response.whenComplete((body, failure) -> SwingUtilities.invokeLater(() -> {
            if (disposed || current != generation.get()) return;
            if (failure != null || body == null || !body.success() || body.data() == null) {
                importButton.setEnabled(true);
                assignButton.setEnabled(true);
                errorLabel.setText(body != null && body.message() != null ? body.message() : "导入失败，请稍后重试");
                return;
            }
            showResult(body.data());
        }));
    }

    void showResult(BatchImportResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><h3>导入完成</h3>");
        sb.append("成功: ").append(result.totalCreated()).append(" 条<br>");
        sb.append("失败: ").append(result.totalFailed()).append(" 条<br>");
        if (!result.errors().isEmpty()) {
            sb.append("<br><b>错误详情:</b><br>");
            for (String err : result.errors()) {
                sb.append(err).append("<br>");
            }
        }
        sb.append("</html>");
        JLabel msg = new JLabel(sb.toString());
        msg.setFont(UiTypography.BODY);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UiColors.BACKGROUND_PAGE);
        panel.setBorder(UiBorders.pageInset());
        panel.add(msg, BorderLayout.CENTER);
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dispose());
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bp.setOpaque(false);
        bp.add(close);
        panel.add(bp, BorderLayout.SOUTH);
        setContentPane(panel);
        revalidate();
        repaint();
    }
}
