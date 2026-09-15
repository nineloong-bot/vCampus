package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** State, lifecycle hooks and shared helpers for the student detail panel segments. */
abstract class StudentDetailPanelBase extends JPanel {
    static final Color TABLE_BORDER = new Color(178, 218, 211);
    static final Color TABLE_LABEL = new Color(239, 247, 245);
    static final Color SECTION_ACCENT = new Color(52, 151, 136);
    static final Color EDIT_LINK = new Color(43, 174, 205);

    final StudentClientService students;
    final ClientConnection connection;
    String studentId;
    final boolean canEdit;
    final AtomicLong requestGeneration = new AtomicLong();
    final Map<String, JLabel> values = new LinkedHashMap<>();
    final ChangesTableModel changesModel = new ChangesTableModel();
    volatile boolean active;
    boolean loaded;
    StudentProfileData profile;
    JLabel statusLabel;
    JLabel errorLabel;
    JButton academicEditButton;
    JTable changesTable;

    StudentDetailPanelBase(StudentClientService students, ClientConnection connection,
                              String studentId, boolean canEdit) {
        super(new BorderLayout(0, UiSpacing.SPACE_4));
        this.students = Objects.requireNonNull(students, "students");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.studentId = studentId;
        this.canEdit = canEdit;
        setName("student.detail");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        initializePanel();
        connection.addStateListener(this::connectionChanged);
    }

    /** Builds the page skeleton once the section helpers are available. */
    abstract void initializePanel();

    /** Opens the academic edit dialog; implemented where the profile is loaded. */
    abstract void editAcademic();

    void connectionChanged(ConnectionState state) {
        onEdt(() -> {
            if (!active) return;
            if (loaded) statusLabel.setText(state == ConnectionState.CONNECTED ? "已加载" : "已断开连接");
            updateEditingState();
        });
    }

    void updateEditingState() {
        setEditingEnabled(canEdit && profile != null && connection.state() == ConnectionState.CONNECTED);
    }

    void setEditingEnabled(boolean enabled) {
        if (academicEditButton != null) academicEditButton.setEnabled(enabled);
    }

    void put(String key, Object value) {
        JLabel target = values.get(key);
        if (target != null) {
            String text = filled(value);
            target.setText(text);
            // Set tooltip so hovering shows full text when truncated
            target.setToolTipText(text);
        }
    }

    static String filled(Object value) { return value == null || value.toString().isBlank() ? "未填写" : value.toString(); }
    static String message(ResponseBody<?> body, String fallback) {
        return body != null && body.message() != null && !body.message().isBlank() ? body.message() : fallback;
    }
    static void onEdt(Runnable task) { if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task); }
    static JLabel text(String value, Font font, Color color) {
        JLabel label = new JLabel(value); label.setFont(font); label.setForeground(color); return label;
    }

    static String changeTypeLabel(String type) {
        if (type == null) return "";
        return switch (type) {
            case "ADMISSION" -> "录取"; case "CLASS_CHANGE" -> "转班"; case "STATUS_CHANGE" -> "状态变更";
            case "ENROLLMENT_CHANGE" -> "学籍变更"; case "ACADEMIC_CHANGE" -> "学籍修改";
            case "PROFILE_CHANGE" -> "信息修改"; default -> type;
        };
    }

    /** Table model backing the change history grid. */
    static final class ChangesTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"变更类型", "变更前", "变更后", "原因", "生效日期", "创建时间"};
        private final java.util.List<StudentChangeView> data = new ArrayList<>();
        void setData(java.util.List<StudentChangeView> rows) { data.clear(); data.addAll(rows); fireTableDataChanged(); }
        StudentChangeView getChangeAt(int row) { return row >= 0 && row < data.size() ? data.get(row) : null; }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }
        @Override public Object getValueAt(int row, int column) {
            StudentChangeView item = data.get(row);
            return switch (column) {
                case 0 -> changeTypeLabel(item.changeType()); case 1 -> filled(item.oldValue()); case 2 -> filled(item.newValue());
                case 3 -> filled(item.reason()); case 4 -> item.effectiveDate() == null ? "" : item.effectiveDate().toString();
                case 5 -> item.createdAt() == null ? "" : item.createdAt().toString(); default -> "";
            };
        }
    }

    /** Scroll content list that only tracks the viewport width. */
    static final class ScrollContent extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(18, visible.height - 18); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
