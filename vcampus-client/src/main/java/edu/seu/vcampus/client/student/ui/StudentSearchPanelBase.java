package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentSummary;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Shared state, filter widgets and small helpers for the student search segments. */
abstract class StudentSearchPanelBase extends JPanel {
    static final int PAGE_SIZE = 20;

    protected final StudentClientService students;
    protected final ClientConnection connection;
    protected final boolean canEdit;
    protected final AtomicLong requestGeneration = new AtomicLong();
    protected final java.util.List<StudentSummary> currentResults = new ArrayList<>();
    protected volatile boolean active;
    protected boolean suppressComboEvents;
    protected int currentPage = 1;

    protected JTextField keywordField;
    protected JComboBox<Object> departmentCombo;
    protected JComboBox<Object> majorCombo;
    protected JComboBox<Object> classCombo;
    protected JComboBox<Object> statusCombo;
    protected JButton searchButton;
    protected JTable resultsTable;
    protected JScrollPane resultsScrollPane;
    protected DefaultTableModel tableModel;
    protected JLabel emptyLabel;
    protected JLabel pageInfoLabel;
    protected JButton prevButton;
    protected JButton nextButton;
    protected JLabel statusLabel;
    protected JLabel errorLabel;
    protected JSplitPane splitPane;
    protected StudentDetailPanel detailPanel;

    /** Stores the service, connection and edit permission shared by the segments. */
    protected StudentSearchPanelBase(StudentClientService students, ClientConnection connection,
            boolean canEdit) {
        this.students = Objects.requireNonNull(students, "students");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.canEdit = canEdit;
    }

    String getSelectedId(JComboBox<Object> combo, Class<?> type) {
        Object item = combo.getSelectedItem();
        if (item == null || item.equals("全部") || item.equals("正在加载...")) return null;
        if (type == DepartmentView.class && item instanceof DepartmentView d) return d.departmentId();
        if (type == MajorView.class && item instanceof MajorView m) return m.majorId();
        if (type == ClassView.class && item instanceof ClassView c) return c.classId();
        return null;
    }

    StudentStatus getSelectedStatus() {
        Object item = statusCombo.getSelectedItem();
        if (item == null || item.equals("全部")) return null;
        return switch ((String) item) {
            case "正常" -> StudentStatus.ACTIVE;
            case "休学" -> StudentStatus.SUSPENDED;
            case "已毕业" -> StudentStatus.GRADUATED;
            case "已退学" -> StudentStatus.WITHDRAWN;
            default -> null;
        };
    }

    static GridBagConstraints constraints(int column, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        return constraints;
    }

    static void addFilterLabel(JPanel bar, String title, int column, int row) {
        JLabel label = new JLabel(title, SwingConstants.RIGHT);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        GridBagConstraints constraints = constraints(column, row);
        constraints.anchor = GridBagConstraints.EAST;
        constraints.insets = new Insets(UiSpacing.SPACE_1,
                column == 0 ? 0 : UiSpacing.SPACE_3, UiSpacing.SPACE_1, UiSpacing.SPACE_2);
        bar.add(label, constraints);
    }

    static void addFilterControl(JPanel bar, JComponent component, int column,
            int row, int width, double weight) {
        GridBagConstraints constraints = constraints(column, row);
        constraints.gridwidth = width;
        constraints.weightx = weight;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(UiSpacing.SPACE_1, 0, UiSpacing.SPACE_1, 0);
        bar.add(component, constraints);
    }

    static String statusText(StudentStatus status) {
        if (status == null) return "";
        return switch (status) {
            case ACTIVE -> "正常";
            case SUSPENDED -> "休学";
            case GRADUATED -> "已毕业";
            case WITHDRAWN -> "已退学";
        };
    }

    static String classDisplayName(String studentNumber) {
        if (studentNumber == null || studentNumber.length() < 6) return "";
        return studentNumber.substring(0, 6) + "班";
    }

    static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    static String safeMessage(ResponseBody<?> body) {
        return body != null && body.message() != null && !body.message().isBlank() ? body.message() : "搜索失败，请稍后重试";
    }
}
