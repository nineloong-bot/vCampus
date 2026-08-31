package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.StudentChangeView;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class StudentDetailPanel extends JPanel {
    private final StudentClientService students;
    private final ClientConnection connection;
    private final String studentId;
    private final boolean canEdit;
    private final AtomicLong requestGeneration = new AtomicLong();
    private volatile boolean active;
    private StudentView profile;
    private JLabel statusLabel, errorLabel;
    private JButton editContactButton, changeStatusButton, transferButton;
    private JLabel nameValue, genderValue, studentTypeValue;
    private JLabel campusCardValue, campusCardBreakdown, studentNumberValue, studentNumberBreakdown;
    private JLabel classIdValue, enrollmentDateValue, statusValue, rowVersionValue;
    private JLabel emailValue, phoneValue;
    private final ChangesTableModel changesModel = new ChangesTableModel();
    private JTable changesTable;

    public StudentDetailPanel(StudentClientService students, ClientConnection connection,
                              String studentId, boolean canEdit) {
        super(new BorderLayout(0, UiSpacing.SPACE_6));
        this.students = Objects.requireNonNull(students, "students");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.canEdit = canEdit;
        setName("student.detail");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        buildPage();
        connection.addStateListener(this::connectionChanged);
    }

    private void buildPage() {
        JPanel heading = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        heading.setOpaque(false);
        JLabel breadcrumb = label("学籍管理 > 学生详情", UiTypography.CAPTION);
        breadcrumb.setName("student.detail.breadcrumb");
        breadcrumb.setForeground(UiColors.TEXT_SECONDARY);
        heading.add(breadcrumb, BorderLayout.NORTH);
        JLabel title = label("学生详情", UiTypography.PAGE_TITLE);
        title.setName("student.detail.title");
        heading.add(title, BorderLayout.CENTER);
        statusLabel = label("正在加载...", UiTypography.CAPTION);
        statusLabel.setName("student.detail.status");
        heading.add(statusLabel, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        JPanel fields = new FieldsPanel();
        fields.setName("student.detail.fields");

        JPanel identity = group("身份信息");
        addField(identity, "姓名", "student.detail.name");
        nameValue = lastValue(identity);
        addField(identity, "性别", "student.detail.gender");
        genderValue = lastValue(identity);
        addField(identity, "学生类型", "student.detail.studentType");
        studentTypeValue = lastValue(identity);

        JPanel numbering = group("编号信息");
        addField(numbering, "校园卡号", "student.detail.card");
        campusCardValue = lastValue(numbering);
        addField(numbering, "卡号释义", "student.detail.card.breakdown");
        campusCardBreakdown = lastValue(numbering);
        campusCardBreakdown.setFont(UiTypography.CAPTION);
        campusCardBreakdown.setForeground(UiColors.TEXT_SECONDARY);
        addField(numbering, "学号", "student.detail.studentNumber");
        studentNumberValue = lastValue(numbering);
        addField(numbering, "学号释义", "student.detail.studentNumber.breakdown");
        studentNumberBreakdown = lastValue(numbering);
        studentNumberBreakdown.setFont(UiTypography.CAPTION);
        studentNumberBreakdown.setForeground(UiColors.TEXT_SECONDARY);

        JPanel academic = group("学籍信息");
        addField(academic, "班级", "student.detail.classId");
        classIdValue = lastValue(academic);
        addField(academic, "入学日期", "student.detail.enrollmentDate");
        enrollmentDateValue = lastValue(academic);
        addField(academic, "状态", "student.detail.status.field");
        statusValue = lastValue(academic);
        addField(academic, "数据版本", "student.detail.rowVersion");
        rowVersionValue = lastValue(academic);

        JPanel contact = group("联系方式");
        addField(contact, "邮箱", "student.detail.email");
        emailValue = lastValue(contact);
        addField(contact, "电话", "student.detail.phone");
        phoneValue = lastValue(contact);

        fields.add(identity);
        fields.add(Box.createVerticalStrut(UiSpacing.SPACE_4));
        fields.add(numbering);
        fields.add(Box.createVerticalStrut(UiSpacing.SPACE_4));
        fields.add(academic);
        fields.add(Box.createVerticalStrut(UiSpacing.SPACE_4));
        fields.add(contact);
        fields.add(Box.createVerticalStrut(UiSpacing.SPACE_6));

        JLabel changesTitle = label("变更记录", UiTypography.SECTION_TITLE);
        changesTitle.setName("student.detail.changes.title");
        changesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        fields.add(changesTitle);
        fields.add(Box.createVerticalStrut(UiSpacing.SPACE_2));

        changesTable = new JTable(changesModel);
        changesTable.setName("student.detail.changes");
        changesTable.setFont(UiTypography.BODY);
        changesTable.setRowHeight(UiSpacing.SPACE_6);
        changesTable.getTableHeader().setFont(UiTypography.CAPTION);
        changesTable.getTableHeader().setReorderingAllowed(false);
        changesTable.getAccessibleContext().setAccessibleName("变更记录");
        JScrollPane tableScroll = new JScrollPane(changesTable);
        tableScroll.setName("student.detail.changes.scroll");
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableScroll.setPreferredSize(new Dimension(0, 200));
        tableScroll.setBorder(UiBorders.LINE);
        fields.add(tableScroll);

        JScrollPane fieldScroll = new JScrollPane(fields,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        fieldScroll.setName("student.detail.fields.scroll");
        fieldScroll.getAccessibleContext().setAccessibleName("学生详情字段");
        fieldScroll.setOpaque(false);
        fieldScroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        fieldScroll.getViewport().setOpaque(false);
        fieldScroll.getVerticalScrollBar().setName("student.detail.fields.vertical-scroll");
        fieldScroll.getVerticalScrollBar().getAccessibleContext().setAccessibleName("学生详情字段滚动条");
        fieldScroll.getVerticalScrollBar().setFocusable(true);
        fieldScroll.getVerticalScrollBar().setUnitIncrement(UiSpacing.SPACE_4);
        add(fieldScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0));
        bottom.setOpaque(false);
        errorLabel = label("", UiTypography.CAPTION);
        errorLabel.setName("student.detail.error");
        errorLabel.setForeground(UiColors.ERROR_FG);
        bottom.add(errorLabel, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        actions.setOpaque(false);
        editContactButton = new JButton("编辑联系方式");
        editContactButton.setName("student.detail.edit-contact");
        editContactButton.getAccessibleContext().setAccessibleName("编辑联系方式");
        editContactButton.setEnabled(false);
        editContactButton.addActionListener(e -> editContact());
        changeStatusButton = new JButton("变更状态");
        changeStatusButton.setName("student.detail.change-status");
        changeStatusButton.getAccessibleContext().setAccessibleName("变更状态");
        changeStatusButton.setEnabled(false);
        transferButton = new JButton("转班");
        transferButton.setName("student.detail.transfer");
        transferButton.getAccessibleContext().setAccessibleName("转班");
        transferButton.setEnabled(false);
        actions.add(editContactButton);
        actions.add(changeStatusButton);
        actions.add(transferButton);
        bottom.add(actions, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        editContactButton.setVisible(canEdit);
        changeStatusButton.setVisible(canEdit);
        transferButton.setVisible(canEdit);
    }

    private JPanel group(String title) {
        JPanel p = new JPanel(new GridLayout(0, 2, UiSpacing.SPACE_4, UiSpacing.SPACE_2));
        p.setOpaque(false);
        JLabel h = label(title, UiTypography.SECTION_TITLE);
        h.setName("student.detail.group." + title);
        p.add(h);
        p.add(new JLabel());
        return p;
    }

    private void addField(JPanel p, String name, String componentName) {
        p.add(label(name, UiTypography.CAPTION));
        JLabel value = label("未填写", UiTypography.BODY);
        value.setName(componentName);
        p.add(value);
    }

    private static JLabel lastValue(JPanel group) {
        Component[] components = group.getComponents();
        return (JLabel) components[components.length - 1];
    }

    private static JLabel label(String text, Font font) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(UiColors.TEXT_PRIMARY);
        return l;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        active = true;
        connectionChanged(connection.state());
        loadStudent();
    }

    @Override
    public void removeNotify() {
        active = false;
        requestGeneration.incrementAndGet();
        super.removeNotify();
    }

    private void loadStudent() {
        long generation = requestGeneration.incrementAndGet();
        onEdt(() -> {
            if (active && generation == requestGeneration.get()) renderLoading();
        });
        students.get(studentId).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure != null) renderError("学生信息加载失败，请稍后重试");
            else if (body == null || !body.success() || body.data() == null) renderError(safeMessage(body));
            else {
                profile = body.data();
                renderProfile(profile);
                loadChanges(generation);
            }
        }));
    }

    private void loadChanges(long generation) {
        students.listChanges(studentId).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure != null) {
                errorLabel.setText("变更记录加载失败");
                return;
            }
            if (body != null && body.success() && body.data() != null) {
                changesModel.setData(body.data());
            }
        }));
    }

    private void renderLoading() {
        statusLabel.setText("正在加载...");
        errorLabel.setText("");
        editContactButton.setEnabled(false);
        changeStatusButton.setEnabled(false);
        transferButton.setEnabled(false);
    }

    private void renderError(String message) {
        statusLabel.setText("加载失败");
        errorLabel.setText(message);
        editContactButton.setEnabled(false);
        changeStatusButton.setEnabled(false);
        transferButton.setEnabled(false);
    }

    private void renderProfile(StudentView p) {
        statusLabel.setText(connection.state() == ConnectionState.CONNECTED ? "已加载" : "已断开连接");
        errorLabel.setText("");
        nameValue.setText(filled(p.studentName()));
        genderValue.setText(genderLabel(p.gender()));
        studentTypeValue.setText(studentType(p.studentType()));
        campusCardValue.setText(filled(p.campusCardNumber()));
        campusCardBreakdown.setText(campusCardBreakdown(p.campusCardNumber()));
        studentNumberValue.setText(filled(p.studentNumber()));
        studentNumberBreakdown.setText(studentNumberBreakdown(p.studentNumber()));
        classIdValue.setText(filled(p.classId()));
        enrollmentDateValue.setText(p.enrollmentDate() == null ? "未填写" : p.enrollmentDate().toString());
        statusValue.setText(status(p.status()));
        rowVersionValue.setText(Long.toString(p.rowVersion()));
        emailValue.setText(filled(p.email()));
        phoneValue.setText(filled(p.phone()));
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean connected = connection.state() == ConnectionState.CONNECTED;
        editContactButton.setEnabled(canEdit && connected && profile != null);
        changeStatusButton.setEnabled(canEdit && connected && profile != null);
        transferButton.setEnabled(canEdit && connected && profile != null);
    }

    private void editContact() {
        if (profile == null || connection.state() != ConnectionState.CONNECTED) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        new UpdateContactDialog(owner, students, profile, this::contactSaved).setVisible(true);
    }

    private void contactSaved(StudentView saved) {
        profile = saved;
        renderProfile(saved);
    }

    private void connectionChanged(ConnectionState state) {
        onEdt(() -> {
            if (!active) return;
            if (profile != null) {
                statusLabel.setText(state == ConnectionState.CONNECTED ? "已加载" : "已断开连接");
                updateButtonStates();
            }
        });
    }

    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    private static String filled(String value) {
        return value == null || value.isBlank() ? "未填写" : value;
    }

    private static String safeMessage(ResponseBody<?> body) {
        return body != null && body.message() != null && !body.message().isBlank()
                ? body.message() : "学生信息加载失败，请稍后重试";
    }

    private static String studentType(StudentType type) {
        return type == null ? "未填写" : switch (type) {
            case UNDERGRADUATE -> "本科生";
            case MASTER -> "硕士生";
            case DOCTORATE -> "博士生";
        };
    }

    private static String status(StudentStatus value) {
        return value == null ? "未填写" : switch (value) {
            case ACTIVE -> "正常";
            case SUSPENDED -> "休学";
            case GRADUATED -> "已毕业";
            case WITHDRAWN -> "已退学";
        };
    }

    private static String genderLabel(String gender) {
        if (gender == null || gender.isBlank()) return "未填写";
        return switch (gender) {
            case "MALE" -> "男";
            case "FEMALE" -> "女";
            default -> gender;
        };
    }

    private static String changeTypeLabel(String type) {
        if (type == null) return "";
        return switch (type) {
            case "ADMISSION" -> "录取";
            case "CLASS_CHANGE" -> "转班";
            case "STATUS_CHANGE" -> "状态变更";
            default -> type;
        };
    }

    private static String campusCardBreakdown(String card) {
        if (card == null || card.length() < 9) return "";
        String typeChar = String.valueOf(card.charAt(1));
        String typeLabel = switch (typeChar) {
            case "1" -> "本科";
            case "2" -> "硕士";
            case "3" -> "博士";
            default -> "未知";
        };
        String year = card.substring(3, 5);
        String seq = card.substring(5, 9);
        return "首位:" + card.charAt(0) + " 类型:" + typeLabel + " 校验:" + card.charAt(2)
                + " 入学年:20" + year + " 序号:" + seq;
    }

    private static String studentNumberBreakdown(String number) {
        if (number == null || number.length() < 8) return "";
        String major = number.substring(0, 3);
        String year = number.substring(3, 5);
        String classNum = String.valueOf(number.charAt(5));
        String seq = number.substring(6, 8);
        return "专业:" + major + " 入学年:20" + year + " 班级:" + classNum + " 班内序号:" + seq;
    }

    private static final class ChangesTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"变更类型", "变更前", "变更后", "原因", "生效日期", "创建时间"};
        private final java.util.List<StudentChangeView> data = new ArrayList<>();

        void setData(java.util.List<StudentChangeView> rows) {
            data.clear();
            data.addAll(rows);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Object getValueAt(int row, int column) {
            StudentChangeView item = data.get(row);
            return switch (column) {
                case 0 -> changeTypeLabel(item.changeType());
                case 1 -> filled(item.oldValue());
                case 2 -> filled(item.newValue());
                case 3 -> filled(item.reason());
                case 4 -> item.effectiveDate() == null ? "" : item.effectiveDate().toString();
                case 5 -> item.createdAt() == null ? "" : item.createdAt().toString();
                default -> "";
            };
        }
    }

    private static final class FieldsPanel extends JPanel implements Scrollable {
        FieldsPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return UiSpacing.SPACE_4; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(UiSpacing.SPACE_4, visible.height - UiSpacing.SPACE_4); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
