package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Read-only student self-service profile page. */
public final class MyStudentProfilePanel extends JPanel {
    private final StudentClientService students;
    private final ClientConnection connection;
    private final AtomicLong requestGeneration = new AtomicLong();
    private volatile boolean active;
    private StudentView profile;
    private JLabel statusLabel, errorLabel;
    private JButton refreshButton, editButton;
    private final java.util.List<JLabel> valueLabels = new java.util.ArrayList<>();

    public MyStudentProfilePanel(StudentClientService students, ClientConnection connection) {
        super(new BorderLayout(0, UiSpacing.SPACE_6));
        this.students = Objects.requireNonNull(students, "students");
        this.connection = Objects.requireNonNull(connection, "connection");
        setName("student.profile");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        buildPage();
        connection.addStateListener(this::connectionChanged);
    }

    private void buildPage() {
        JPanel heading = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2)); heading.setOpaque(false);
        JLabel title = label("我的学籍档案", UiTypography.PAGE_TITLE); title.setName("student.profile.title"); heading.add(title, BorderLayout.NORTH);
        JLabel summary = label("查看你的学籍身份与联系方式", UiTypography.CAPTION); summary.setName("student.profile.summary"); heading.add(summary, BorderLayout.CENTER);
        statusLabel = label("正在加载", UiTypography.CAPTION); statusLabel.setName("student.profile.status"); heading.add(statusLabel, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        JPanel fields = new JPanel(); fields.setOpaque(false); fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        JPanel identity = group("身份信息"); addField(identity, "姓名", "student.profile.name"); addField(identity, "学号", "student.profile.studentNumber"); addField(identity, "校园卡号", "student.profile.card"); addField(identity, "学生类型", "student.profile.type"); addField(identity, "状态", "student.profile.lifecycle");
        JPanel academic = group("学籍信息"); addField(academic, "专业", "student.profile.major"); addField(academic, "班级", "student.profile.class"); addField(academic, "入学日期", "student.profile.enrollment");
        JPanel contact = group("联系方式"); addField(contact, "邮箱", "student.profile.email"); addField(contact, "电话", "student.profile.phone"); addField(contact, "数据版本", "student.profile.version");
        fields.add(identity); fields.add(Box.createVerticalStrut(UiSpacing.SPACE_4)); fields.add(academic); fields.add(Box.createVerticalStrut(UiSpacing.SPACE_4)); fields.add(contact); add(fields, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0)); bottom.setOpaque(false);
        errorLabel = label("", UiTypography.CAPTION); errorLabel.setName("student.profile.error"); bottom.add(errorLabel, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0)); actions.setOpaque(false);
        refreshButton = new JButton("重试"); refreshButton.setName("student.profile.refresh"); refreshButton.getAccessibleContext().setAccessibleName("刷新学籍档案"); refreshButton.addActionListener(e -> refreshProfile());
        editButton = new JButton("编辑"); editButton.setName("student.profile.edit"); editButton.getAccessibleContext().setAccessibleName("编辑联系方式"); editButton.setEnabled(false); editButton.addActionListener(e -> editContact());
        actions.add(refreshButton); actions.add(editButton); bottom.add(actions, BorderLayout.EAST); add(bottom, BorderLayout.SOUTH);
    }

    private JPanel group(String title) { JPanel p = new JPanel(new GridLayout(0, 2, UiSpacing.SPACE_4, UiSpacing.SPACE_2)); p.setOpaque(false); JLabel h = label(title, UiTypography.SECTION_TITLE); h.setName("student.profile.group." + title); p.add(h); p.add(new JLabel()); return p; }
    private void addField(JPanel p, String name, String componentName) { p.add(label(name, UiTypography.CAPTION)); JLabel value = label("未填写", UiTypography.BODY); value.setName(componentName); valueLabels.add(value); p.add(value); }
    private static JLabel label(String text, Font font) { JLabel l = new JLabel(text); l.setFont(font); l.setForeground(UiColors.TEXT_PRIMARY); return l; }

    @Override public void addNotify() { super.addNotify(); active = true; connectionChanged(connection.state()); refreshProfile(); }
    @Override public void removeNotify() { active = false; requestGeneration.incrementAndGet(); super.removeNotify(); }

    public void refreshProfile() {
        long generation = requestGeneration.incrementAndGet();
        onEdt(() -> { if (active && generation == requestGeneration.get()) renderLoading(); });
        students.getCurrent().whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure != null) renderError("档案加载失败，请稍后重试");
            else if (body == null || !body.success() || body.data() == null) renderError(safeMessage(body));
            else { profile = body.data(); renderProfile(profile); }
        }));
    }

    private void renderLoading() { statusLabel.setText("正在加载"); errorLabel.setText(""); refreshButton.setEnabled(false); editButton.setEnabled(false); }
    private void renderError(String message) { statusLabel.setText("加载失败"); errorLabel.setText(message); refreshButton.setText("重试"); refreshButton.setEnabled(true); editButton.setEnabled(false); }
    private void renderProfile(StudentView p) {
        statusLabel.setText(connection.state() == ConnectionState.CONNECTED ? "已加载" : "已断开连接"); errorLabel.setText(""); refreshButton.setText("刷新"); refreshButton.setEnabled(true);
        String[] values = {p.studentName(), p.studentNumber(), p.campusCardNumber(), studentType(p.studentType()), status(p.status()), p.majorId(), p.classId(), p.enrollmentDate() == null ? null : p.enrollmentDate().toString(), p.email(), p.phone(), Long.toString(p.rowVersion())};
        for (int i = 0; i < values.length; i++) valueLabels.get(i).setText(filled(values[i]));
        editButton.setEnabled(connection.state() == ConnectionState.CONNECTED);
    }
    private void editContact() {
        if (profile == null || connection.state() != ConnectionState.CONNECTED) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        new UpdateContactDialog(owner, students, profile, this::contactSaved).setVisible(true);
    }
    private void contactSaved(StudentView saved) { profile = saved; renderProfile(saved); }
    private void connectionChanged(ConnectionState state) { onEdt(() -> { if (!active) return; if (profile != null) { statusLabel.setText(state == ConnectionState.CONNECTED ? "已加载" : "连接已断开"); editButton.setEnabled(state == ConnectionState.CONNECTED); } }); }
    private static void onEdt(Runnable task) { if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task); }
    private static String filled(String value) { return value == null || value.isBlank() ? "未填写" : value; }
    private static String safeMessage(ResponseBody<?> body) { return body != null && body.message() != null && !body.message().isBlank() ? body.message() : "档案加载失败，请稍后重试"; }
    private static String studentType(StudentType type) { return type == null ? null : switch (type) { case UNDERGRADUATE -> "本科生"; case MASTER -> "硕士生"; case DOCTORATE -> "博士生"; }; }
    private static String status(StudentStatus value) { return value == null ? null : switch (value) { case ACTIVE -> "正常"; case SUSPENDED -> "休学"; case GRADUATED -> "已毕业"; case WITHDRAWN -> "已退学"; }; }
}
