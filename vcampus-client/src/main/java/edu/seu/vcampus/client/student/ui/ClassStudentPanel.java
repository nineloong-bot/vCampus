package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.StudentSearchQuery;
import edu.seu.vcampus.common.student.StudentSummary;
import edu.seu.vcampus.common.student.StudentView;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Shows the compact student roster for the class selected in the organization tree. */
final class ClassStudentPanel extends JPanel {
    private final JLabel statistics = new JLabel("请选择班级查看学生");
    private final DefaultTableModel rows = new DefaultTableModel(
            new String[]{"姓名", "性别", "一卡通号", "学号"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final AtomicLong requestGeneration = new AtomicLong();

    ClassStudentPanel() {
        super(new BorderLayout(0, UiSpacing.SPACE_2));
        setName("student.org.class-students");
        setBorder(BorderFactory.createCompoundBorder(UiBorders.LINE,
                BorderFactory.createEmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_2,
                        UiSpacing.SPACE_2, UiSpacing.SPACE_2)));
        setBackground(UiColors.BACKGROUND_SUBTLE);
        setPreferredSize(new Dimension(0, 230));

        JLabel title = new JLabel("班级学生");
        title.setFont(UiTypography.SECTION_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        add(title, BorderLayout.NORTH);

        JTable table = new JTable(rows);
        table.setName("student.org.class-students.table");
        table.setFont(UiTypography.CAPTION);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.setBackground(UiColors.BACKGROUND_PAGE);
        add(new JScrollPane(table), BorderLayout.CENTER);

        statistics.setName("student.org.class-students.statistics");
        statistics.setFont(UiTypography.CAPTION);
        statistics.setForeground(UiColors.TEXT_SECONDARY);
        add(statistics, BorderLayout.SOUTH);
    }

    void load(StudentClientService students, String classId) {
        long generation = requestGeneration.incrementAndGet();
        statistics.setText("正在加载班级学生");
        rows.setRowCount(0);
        students.search(new StudentSearchQuery(null, null, null, classId, null, 1, 100))
                .whenComplete((body, failure) -> SwingUtilities.invokeLater(() -> {
                    if (generation != requestGeneration.get()) return;
                    if (failure != null || body == null || !body.success() || body.data() == null) {
                        statistics.setText("班级学生加载失败");
                        return;
                    }
                    loadDetails(students, body.data().items(), generation);
                }));
    }

    void clear() {
        requestGeneration.incrementAndGet();
        rows.setRowCount(0);
        statistics.setText("请选择班级查看学生");
    }

    private void loadDetails(StudentClientService students, List<StudentSummary> summaries,
            long generation) {
        List<CompletableFuture<StudentView>> details = summaries.stream().map(summary -> students
                .get(summary.studentId()).thenApply(body -> body != null && body.success() ? body.data() : null))
                .toList();
        CompletableFuture.allOf(details.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
                    if (generation != requestGeneration.get()) return;
                    if (failure != null) {
                        statistics.setText("班级学生加载失败");
                        return;
                    }
                    List<StudentView> studentsInClass = details.stream()
                            .map(CompletableFuture::join).toList();
                    if (studentsInClass.stream().anyMatch(value -> value == null)) {
                        statistics.setText("班级学生加载失败");
                        return;
                    }
                    int male = (int) studentsInClass.stream().filter(value -> "男".equals(value.gender())).count();
                    int female = (int) studentsInClass.stream().filter(value -> "女".equals(value.gender())).count();
                    for (StudentView student : studentsInClass) {
                        rows.addRow(new Object[]{student.studentName(), student.gender(),
                                student.campusCardNumber(), student.studentNumber()});
                    }
                    statistics.setText("共 " + studentsInClass.size() + " 人，男 " + male + " 人，女 "
                            + female + " 人");
                }));
    }
}
