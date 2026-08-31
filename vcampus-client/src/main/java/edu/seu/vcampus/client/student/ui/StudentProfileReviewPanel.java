package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Administrator queue for comparing, approving and rejecting student profile drafts. */
public final class StudentProfileReviewPanel extends JPanel {
    private final StudentClientService students;
    private final DefaultTableModel queueModel = readonly("学生ID", "提交时间", "就读方式");
    private final DefaultTableModel diffModel = readonly("字段", "正式信息", "申请修改为");
    private final JTable queue = new JTable(queueModel), differences = new JTable(diffModel);
    private final JLabel status = new JLabel("请选择一条待审核申请");
    private final JButton approve = new JButton("审核通过"), reject = new JButton("驳回申请");
    private final List<StudentProfileApplicationView> applications = new ArrayList<>();
    private StudentProfileWorkspace selected;
    private volatile boolean active;

    public StudentProfileReviewPanel(StudentClientService students, ClientConnection connection) {
        super(new BorderLayout(UiSpacing.SPACE_4, UiSpacing.SPACE_4));
        this.students = Objects.requireNonNull(students); Objects.requireNonNull(connection);
        setName("student.profile.review"); setBackground(UiColors.BACKGROUND_PAGE); setBorder(UiBorders.pageInset());
        build();
    }

    private void build() {
        JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false);
        JPanel copy = new JPanel(); copy.setOpaque(false); copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel title = label("学生资料审核", UiTypography.PAGE_TITLE, UiColors.TEXT_PRIMARY);
        copy.add(title); copy.add(label("修改仅在审核通过后写入正式学籍档案", UiTypography.CAPTION, UiColors.TEXT_SECONDARY));
        heading.add(copy); JButton refresh = new JButton("刷新待审核"); refresh.setName("student.profile.review.refresh");
        refresh.addActionListener(e -> refresh()); heading.add(refresh, BorderLayout.EAST); add(heading, BorderLayout.NORTH);

        queue.setName("student.profile.review.queue"); queue.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        queue.setRowHeight(30); queue.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) loadSelection(); });
        differences.setName("student.profile.review.diff"); differences.setRowHeight(30); differences.setFillsViewportHeight(true);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableBox("待审核申请", queue),
                tableBox("正式信息与申请差异", differences)); split.setResizeWeight(.36); split.setDividerLocation(360); add(split);

        JPanel bottom = new JPanel(new BorderLayout()); bottom.setOpaque(false); status.setName("student.profile.review.status");
        status.setFont(UiTypography.CAPTION); status.setForeground(UiColors.TEXT_SECONDARY); bottom.add(status);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0)); actions.setOpaque(false);
        approve.setName("student.profile.review.approve"); reject.setName("student.profile.review.reject");
        approve.addActionListener(e -> approve()); reject.addActionListener(e -> reject());
        actions.add(reject); actions.add(approve); bottom.add(actions, BorderLayout.EAST); add(bottom, BorderLayout.SOUTH);
        controls(false);
    }

    @Override public void addNotify() { super.addNotify(); active = true; refresh(); }
    @Override public void removeNotify() { active = false; super.removeNotify(); }

    public void refresh() {
        status.setText("正在加载待审核申请…"); controls(false);
        students.listProfileReviews(new StudentProfileReviewQuery(1, 100)).whenComplete((body, failure) -> onEdt(() -> {
            if (!active) return;
            queueModel.setRowCount(0); diffModel.setRowCount(0); applications.clear(); selected = null;
            if (failure != null || body == null || !body.success() || body.data() == null) {
                status.setText(message(body, "待审核申请加载失败")); return;
            }
            applications.addAll(body.data().items());
            for (StudentProfileApplicationView app : applications)
                queueModel.addRow(new Object[] {app.studentId(), show(app.submittedAt()),
                        app.attendanceMode() == null ? "未填写" : app.attendanceMode().displayName()});
            status.setText(applications.isEmpty() ? "当前没有待审核申请" : "共 " + body.data().total() + " 条待审核申请");
            if (!applications.isEmpty()) queue.setRowSelectionInterval(0, 0);
        }));
    }

    private void loadSelection() {
        int row = queue.getSelectedRow(); if (row < 0 || row >= applications.size()) { controls(false); return; }
        StudentProfileApplicationView app = applications.get(row); status.setText("正在读取申请详情…"); controls(false);
        students.getProfileReview(app.applicationId()).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || queue.getSelectedRow() != row) return;
            if (failure != null || body == null || !body.success() || body.data() == null) {
                status.setText(message(body, "申请详情加载失败")); return;
            }
            selected = body.data(); renderDifferences(selected); controls(true);
            StudentView core = selected.formalProfile().core();
            status.setText("正在审核：" + core.studentName() + "（" + core.studentNumber() + "）");
        }));
    }

    private void renderDifferences(StudentProfileWorkspace workspace) {
        diffModel.setRowCount(0); StudentPersonalProfile f = workspace.formalProfile().personal();
        StudentPersonalProfile d = workspace.application().personal();
        addDiff("姓名拼音", f.namePinyin(), d.namePinyin()); addDiff("曾用名", f.formerName(), d.formerName());
        addDiff("政治面貌", f.politicalStatus(), d.politicalStatus()); addDiff("民族", f.ethnicity(), d.ethnicity());
        addDiff("婚姻状态", f.maritalStatus(), d.maritalStatus()); addDiff("证件类型", f.idDocumentType(), d.idDocumentType());
        addDiff("身份证件号", f.idDocumentNumber(), d.idDocumentNumber()); addDiff("证件签发日期", f.idIssuedDate(), d.idIssuedDate());
        addDiff("出生日期", f.birthDate(), d.birthDate()); addDiff("籍贯", f.nativePlace(), d.nativePlace());
        addDiff("国家地区", f.countryRegion(), d.countryRegion()); addDiff("出生地", f.birthplace(), d.birthplace());
        addDiff("生源地", f.studentOriginPlace(), d.studentOriginPlace()); addDiff("户口性质", f.householdRegistrationType(), d.householdRegistrationType());
        addDiff("入学前户口", f.householdBeforeEnrollment(), d.householdBeforeEnrollment());
        addDiff("入学后户口", f.householdAfterEnrollment(), d.householdAfterEnrollment());
        addDiff("港澳台侨外", f.overseasChineseStatus(), d.overseasChineseStatus()); addDiff("信仰宗教", f.religion(), d.religion());
        addDiff("是否团员", yesNo(f.leagueMember()), yesNo(d.leagueMember())); addDiff("入团时间", f.leagueJoinDate(), d.leagueJoinDate());
        addDiff("是否党员", yesNo(f.partyMember()), yesNo(d.partyMember())); addDiff("入党时间", f.partyJoinDate(), d.partyJoinDate());
        addDiff("健康状况", f.healthStatus(), d.healthStatus()); addDiff("血型", f.bloodType(), d.bloodType());
        addDiff("体重(KG)", f.weightKg(), d.weightKg()); addDiff("身高(CM)", f.heightCm(), d.heightCm());
        addDiff("特长", f.specialties(), d.specialties()); addDiff("爱好", f.hobbies(), d.hobbies());
        addDiff("是否独生子女", yesNo(f.onlyChild()), yesNo(d.onlyChild())); addDiff("邮箱", f.email(), d.email());
        addDiff("联系电话", f.phone(), d.phone());
        AttendanceMode formalMode = workspace.formalProfile().academic().attendanceMode();
        addDiff("就读方式", formalMode == null ? null : formalMode.displayName(),
                workspace.application().attendanceMode() == null ? null : workspace.application().attendanceMode().displayName());
        if (diffModel.getRowCount() == 0) diffModel.addRow(new Object[] {"无差异", "—", "—"});
    }

    private void addDiff(String field, Object formal, Object draft) {
        if (!Objects.equals(formal, draft)) diffModel.addRow(new Object[] {field, filled(formal), filled(draft)});
    }
    private void approve() {
        if (selected == null) return;
        int decision = JOptionPane.showConfirmDialog(this, "确认将这些修改写入正式学籍档案？", "审核通过",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (decision != JOptionPane.OK_OPTION) return;
        review(true, null);
    }
    private void reject() {
        if (selected == null) return;
        String reason = JOptionPane.showInputDialog(this, "请输入驳回原因（学生端将显示）：", "驳回申请",
                JOptionPane.WARNING_MESSAGE);
        if (reason == null) return;
        if (reason.isBlank()) { JOptionPane.showMessageDialog(this, "驳回原因不能为空", "无法驳回", JOptionPane.ERROR_MESSAGE); return; }
        review(false, reason.trim());
    }
    private void review(boolean approved, String comment) {
        controls(false); status.setText(approved ? "正在通过申请…" : "正在驳回申请…");
        ReviewStudentProfileCommand command = new ReviewStudentProfileCommand(selected.application().applicationId(), comment);
        var future = approved ? students.approveProfile(command) : students.rejectProfile(command);
        future.whenComplete((body, failure) -> onEdt(() -> {
            if (failure != null || body == null || !body.success()) {
                status.setText(message(body, approved ? "审核通过失败" : "驳回失败")); controls(true); return;
            }
            refresh();
        }));
    }
    private void controls(boolean enabled) { approve.setEnabled(enabled); reject.setEnabled(enabled); }
    private static JPanel tableBox(String title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2)); panel.setBackground(UiColors.BACKGROUND_PAGE);
        panel.add(label(title, UiTypography.SECTION_TITLE, UiColors.TEXT_PRIMARY), BorderLayout.NORTH);
        panel.add(new JScrollPane(table)); return panel;
    }
    private static DefaultTableModel readonly(String... columns) {
        return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int row, int column) { return false; } };
    }
    private static JLabel label(String value, Font font, Color color) { JLabel result = new JLabel(value); result.setFont(font); result.setForeground(color); return result; }
    private static String show(Object value) { return value == null ? "未填写" : value.toString(); }
    private static String filled(Object value) { return value == null || value.toString().isBlank() ? "未填写" : value.toString(); }
    private static String yesNo(boolean value) { return value ? "是" : "否"; }
    private static String message(ResponseBody<?> body, String fallback) { return body != null && body.message() != null && !body.message().isBlank() ? body.message() : fallback; }
    private static void onEdt(Runnable task) { if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task); }
}
