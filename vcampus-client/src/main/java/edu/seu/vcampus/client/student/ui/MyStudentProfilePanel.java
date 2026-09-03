package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Student profile workspace with draft, approval and formal-PDF workflow. */
public final class MyStudentProfilePanel extends JPanel {
    private static final Color TABLE_BORDER = new Color(178, 218, 211);
    private static final Color TABLE_LABEL = new Color(239, 247, 245);
    private static final Color ACTION_GREEN = new Color(139, 195, 74);
    private final StudentClientService students;
    private final ClientConnection connection;
    private final AtomicLong generation = new AtomicLong();
    private final Map<String, JLabel> values = new LinkedHashMap<>();
    private volatile boolean active;
    private StudentProfileWorkspace workspace;
    private JLabel statusLabel, errorLabel, applicationStatus;
    private JButton refreshButton, personalEdit, academicEdit, exportButton, submitButton;

    public MyStudentProfilePanel(StudentClientService students, ClientConnection connection) {
        super(new BorderLayout(0, UiSpacing.SPACE_4));
        this.students = Objects.requireNonNull(students); this.connection = Objects.requireNonNull(connection);
        setName("student.profile"); setBackground(UiColors.BACKGROUND_PAGE); setBorder(UiBorders.pageInset());
        build(); connection.addStateListener(this::connectionChanged);
    }

    private void build() {
        JPanel top = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0)); top.setOpaque(false);
        JPanel titleBox = new JPanel(); titleBox.setOpaque(false); titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel title = text("我的学籍档案", UiTypography.PAGE_TITLE, UiColors.TEXT_PRIMARY);
        statusLabel = text("正在加载", UiTypography.CAPTION, UiColors.TEXT_SECONDARY); statusLabel.setName("student.profile.status");
        titleBox.add(title); titleBox.add(Box.createVerticalStrut(UiSpacing.SPACE_1)); titleBox.add(statusLabel); top.add(titleBox);
        refreshButton = new JButton("刷新"); refreshButton.setName("student.profile.refresh");
        refreshButton.getAccessibleContext().setAccessibleName("刷新学籍档案"); refreshButton.addActionListener(e -> refreshProfile());
        top.add(refreshButton, BorderLayout.EAST); add(top, BorderLayout.NORTH);

        JPanel content = new ScrollContent(); content.setName("student.profile.fields");
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(sectionHeader("个人基本信息", true));
        content.add(profileTable(personalDefinitions()));
        content.add(Box.createVerticalStrut(UiSpacing.SPACE_6));
        content.add(sectionHeader("学籍信息", false));
        content.add(profileTable(academicDefinitions()));
        JScrollPane scroll = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("student.profile.fields.scroll"); scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.getViewport().setBackground(UiColors.BACKGROUND_PAGE); scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getAccessibleContext().setAccessibleName("学籍档案字段"); add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(UiSpacing.SPACE_3, UiSpacing.SPACE_2)); footer.setOpaque(false);
        JPanel messages = new JPanel(); messages.setOpaque(false); messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
        applicationStatus = text("尚无修改申请", UiTypography.CAPTION, UiColors.TEXT_SECONDARY);
        applicationStatus.setName("student.profile.application.status");
        errorLabel = text(" ", UiTypography.CAPTION, UiColors.ERROR_FG); errorLabel.setName("student.profile.error");
        messages.add(applicationStatus); messages.add(errorLabel); footer.add(messages, BorderLayout.NORTH);
        JPanel actions = new JPanel(new BorderLayout()); actions.setOpaque(false);
        exportButton = action("导出基本信息 PDF", "student.profile.export"); exportButton.addActionListener(e -> exportPdf());
        submitButton = action("提交审核", "student.profile.submit"); submitButton.addActionListener(e -> submitDraft());
        actions.add(exportButton, BorderLayout.WEST); actions.add(submitButton, BorderLayout.EAST); footer.add(actions);
        add(footer, BorderLayout.SOUTH); setControls(false);
    }

    private JPanel sectionHeader(String title, boolean personal) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        header.setOpaque(false); header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel accent = new JLabel(" "); accent.setOpaque(true); accent.setBackground(new Color(52, 151, 136));
        accent.setPreferredSize(new Dimension(6, 30)); header.add(accent);
        header.add(text(title, UiTypography.SECTION_TITLE.deriveFont(Font.BOLD, 20f), UiColors.TEXT_PRIMARY));
        JButton edit = new JButton("编辑"); edit.setBorderPainted(false); edit.setContentAreaFilled(false);
        edit.setForeground(new Color(43, 174, 205)); edit.setFont(UiTypography.BODY.deriveFont(Font.BOLD));
        edit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        edit.setName(personal ? "student.profile.personal.edit" : "student.profile.academic.edit");
        edit.getAccessibleContext().setAccessibleName("编辑" + title);
        edit.addActionListener(e -> { if (personal) editPersonal(); else editAttendance(); });
        if (personal) personalEdit = edit; else academicEdit = edit; header.add(edit); return header;
    }

    private JPanel profileTable(String[][] definitions) {
        JPanel table = new JPanel(new GridBagLayout()); table.setOpaque(false); table.setAlignmentX(Component.LEFT_ALIGNMENT);
        int rows = (definitions.length + 2) / 3;
        for (int index = 0; index < rows * 3; index++) {
            int row = index / 3, pair = index % 3; String key = index < definitions.length ? definitions[index][0] : null;
            String title = index < definitions.length ? definitions[index][1] : "";
            JLabel label = cell(title, true); JLabel value = cell("未填写", false);
            if (key != null) { value.setName("student.profile." + key); values.put(key, value); }
            addCell(table, label, pair * 2, row, .12); addCell(table, value, pair * 2 + 1, row, .21);
        }
        return table;
    }

    private static void addCell(JPanel table, JComponent cell, int x, int y, double weight) {
        GridBagConstraints c = new GridBagConstraints(); c.gridx = x; c.gridy = y; c.weightx = weight;
        c.fill = GridBagConstraints.BOTH; c.anchor = GridBagConstraints.WEST; table.add(cell, c);
    }
    private static JLabel cell(String value, boolean label) {
        JLabel result = text(value, label ? UiTypography.BODY.deriveFont(Font.BOLD) : UiTypography.BODY,
                UiColors.TEXT_PRIMARY);
        result.setOpaque(true); result.setBackground(label ? TABLE_LABEL : Color.WHITE);
        result.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(TABLE_BORDER),
                BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        result.setMinimumSize(new Dimension(label ? 105 : 140, 38)); return result;
    }

    @Override public void addNotify() { super.addNotify(); active = true; refreshProfile(); }
    @Override public void removeNotify() { active = false; generation.incrementAndGet(); super.removeNotify(); }

    public void refreshProfile() {
        long current = generation.incrementAndGet(); onEdt(() -> { if (active) loading(); });
        students.getProfileWorkspace().whenComplete((body, failure) -> onEdt(() -> {
            if (!active || current != generation.get()) return;
            if (failure != null || body == null || !body.success() || body.data() == null)
                failure(message(body, "档案加载失败，请稍后重试"));
            else render(body.data());
        }));
    }

    private void loading() { statusLabel.setText("正在加载"); errorLabel.setText(" "); refreshButton.setEnabled(false); setControls(false); }
    private void failure(String message) { statusLabel.setText("加载失败"); errorLabel.setText(message); refreshButton.setEnabled(true); setControls(false); }
    private void render(StudentProfileWorkspace value) {
        workspace = value; StudentProfileData formal = value.formalProfile(); StudentProfileApplicationView app = value.application();
        boolean draftVisible = app != null && (app.status() == StudentProfileApplicationStatus.DRAFT
                || app.status() == StudentProfileApplicationStatus.PENDING);
        StudentPersonalProfile personal = draftVisible ? app.personal() : formal.personal();
        AttendanceMode attendance = draftVisible ? app.attendanceMode() : formal.academic().attendanceMode();
        renderCore(formal, personal); renderAcademic(formal.academic(), attendance); renderApplication(app);
        statusLabel.setText("已加载"); errorLabel.setText(" "); refreshButton.setEnabled(true);
        boolean connected = connection.state() == ConnectionState.CONNECTED;
        boolean pending = app != null && app.status() == StudentProfileApplicationStatus.PENDING;
        personalEdit.setEnabled(connected && !pending); academicEdit.setEnabled(connected && !pending);
        exportButton.setEnabled(connected); submitButton.setEnabled(connected && app != null
                && app.status() == StudentProfileApplicationStatus.DRAFT);
    }

    private void renderCore(StudentProfileData data, StudentPersonalProfile p) {
        StudentView c = data.core(); put("card", c.campusCardNumber()); put("studentNumber", c.studentNumber()); put("name", c.studentName());
        put("namePinyin", p.namePinyin()); put("formerName", p.formerName()); put("gender", c.gender());
        put("politicalStatus", p.politicalStatus()); put("ethnicity", p.ethnicity()); put("maritalStatus", p.maritalStatus());
        put("idDocumentType", p.idDocumentType()); put("idDocumentNumber", p.idDocumentNumber()); put("idIssuedDate", p.idIssuedDate());
        put("birthDate", p.birthDate()); put("nativePlace", p.nativePlace()); put("countryRegion", p.countryRegion());
        put("birthplace", p.birthplace()); put("studentOriginPlace", p.studentOriginPlace());
        put("householdRegistrationType", p.householdRegistrationType()); put("householdBeforeEnrollment", p.householdBeforeEnrollment());
        put("householdAfterEnrollment", p.householdAfterEnrollment()); put("overseasChineseStatus", p.overseasChineseStatus());
        put("religion", p.religion()); put("leagueMember", yesNo(p.leagueMember())); put("leagueJoinDate", p.leagueJoinDate());
        put("partyMember", yesNo(p.partyMember())); put("partyJoinDate", p.partyJoinDate()); put("healthStatus", p.healthStatus());
        put("bloodType", p.bloodType()); put("weightKg", p.weightKg()); put("heightCm", p.heightCm()); put("specialties", p.specialties());
        put("hobbies", p.hobbies()); put("onlyChild", yesNo(p.onlyChild())); put("email", p.email()); put("phone", p.phone());
    }
    private void renderAcademic(StudentAcademicProfile a, AttendanceMode attendance) {
        put("studentCategory", a.studentCategory()); put("enrolled", yesNo(a.enrolled())); put("onCampus", yesNo(a.onCampus()));
        put("academicStatus", a.academicStatus()); put("campus", a.campus()); put("currentGrade", a.currentGrade());
        put("department", a.departmentName()); put("major", a.majorName()); put("class", a.className());
        put("educationLevel", a.educationLevel()); put("trainingMode", a.trainingMode()); put("programLength", a.programLengthYears());
        put("attendanceMode", attendance == null ? null : attendance.displayName()); put("degreeName", a.degreeName());
        put("educationName", a.educationName()); put("expectedGraduationDate", a.expectedGraduationDate());
        put("graduationDate", a.graduationDate()); put("studentSource", a.studentSource());
        put("graduateStudyMode", a.graduateStudyMode()); put("counselorName", a.counselorName()); put("counselorContact", a.counselorContact());
    }

    private void renderApplication(StudentProfileApplicationView app) {
        if (app == null) { applicationStatus.setText("尚无修改申请"); return; }
        String value = switch (app.status()) {
            case DRAFT -> "已暂存，尚未提交审核";
            case PENDING -> "审核中：资料已锁定，管理员处理后方可再次编辑";
            case APPROVED -> "最近申请已通过";
            case REJECTED -> "已驳回：" + filled(app.reviewComment());
        };
        applicationStatus.setText(value);
    }

    private void editPersonal() {
        if (workspace == null || !personalEdit.isEnabled()) return;
        StudentProfileApplicationView app = workspace.application();
        boolean draft = app != null && app.status() == StudentProfileApplicationStatus.DRAFT;
        StudentPersonalProfile initial = draft ? app.personal() : workspace.formalProfile().personal();
        long expected = draft ? app.applicationVersion() : 0;
        new PersonalProfileEditDialog(SwingUtilities.getWindowAncestor(this), students, initial, expected, this::render).setVisible(true);
    }
    private void editAttendance() {
        if (workspace == null || !academicEdit.isEnabled()) return;
        StudentProfileApplicationView app = workspace.application(); boolean draft = app != null && app.status() == StudentProfileApplicationStatus.DRAFT;
        AttendanceMode initial = draft ? app.attendanceMode() : workspace.formalProfile().academic().attendanceMode();
        long expected = draft ? app.applicationVersion() : 0;
        new AttendanceModeEditDialog(SwingUtilities.getWindowAncestor(this), students, initial, expected, this::render).setVisible(true);
    }

    private void submitDraft() {
        if (workspace == null || workspace.application() == null || !submitButton.isEnabled()) return;
        int decision = JOptionPane.showConfirmDialog(this, "提交后在管理员审核完成前不能继续编辑，确定提交吗？",
                "提交资料审核", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (decision != JOptionPane.OK_OPTION) return;
        setControls(false); errorLabel.setText("正在提交审核…");
        students.submitProfile(new SubmitStudentProfileCommand(workspace.application().applicationVersion()))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (failure != null || body == null || !body.success() || body.data() == null) {
                        errorLabel.setText(message(body, "提交失败，请稍后重试")); render(workspace); return;
                    }
                    render(body.data());
                }));
    }

    private void exportPdf() {
        exportButton.setEnabled(false); errorLabel.setText("正在生成正式信息 PDF…");
        students.exportProfilePdf().whenComplete((body, failure) -> {
            if (failure != null || body == null || !body.success() || body.data() == null) {
                onEdt(() -> { errorLabel.setText(message(body, "PDF 生成失败，请稍后重试")); exportButton.setEnabled(true); });
                return;
            }
            PdfDocument document = body.data();
            onEdt(() -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF 文件 (*.pdf)", "pdf"));
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

    private static String ensurePdfExtension(String name) { return name.endsWith(".pdf") ? name : name + ".pdf"; }
    private static java.io.File ensurePdfExtension(java.io.File file) {
        return file.getName().endsWith(".pdf") ? file : new java.io.File(file.getParentFile(), file.getName() + ".pdf");
    }

    private void connectionChanged(ConnectionState state) { onEdt(() -> { if (active && workspace != null) render(workspace); }); }
    private void setControls(boolean enabled) { personalEdit.setEnabled(enabled); academicEdit.setEnabled(enabled); exportButton.setEnabled(enabled); submitButton.setEnabled(enabled); }
    private void put(String key, Object value) { JLabel label = values.get(key); if (label != null) label.setText(filled(value)); }
    private static String filled(Object value) { return value == null || value.toString().isBlank() ? "未填写" : value.toString(); }
    private static String yesNo(boolean value) { return value ? "是" : "否"; }
    private static String message(ResponseBody<?> body, String fallback) { return body != null && body.message() != null && !body.message().isBlank() ? body.message() : fallback; }
    private static void onEdt(Runnable task) { if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task); }
    private static JButton action(String title, String name) {
        JButton button = new JButton(title); button.setName(name); button.setFont(UiTypography.SECTION_TITLE);
        button.setForeground(Color.WHITE); button.setBackground(ACTION_GREEN); button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18)); button.getAccessibleContext().setAccessibleName(title); return button;
    }
    private static JLabel text(String value, Font font, Color color) { JLabel label = new JLabel(value); label.setFont(font); label.setForeground(color); return label; }

    private static String[][] personalDefinitions() { return new String[][] {
            {"card", "一卡通号"}, {"studentNumber", "学号"}, {"name", "姓名"}, {"namePinyin", "姓名拼音"},
            {"formerName", "曾用名"}, {"gender", "性别"}, {"politicalStatus", "政治面貌"}, {"ethnicity", "民族"},
            {"maritalStatus", "婚姻状态"}, {"idDocumentType", "身份证件类型"}, {"idDocumentNumber", "身份证件号"},
            {"idIssuedDate", "身份证签发日期"}, {"birthDate", "出生日期"}, {"nativePlace", "籍贯"},
            {"countryRegion", "国家地区"}, {"birthplace", "出生地"}, {"studentOriginPlace", "生源地"},
            {"householdRegistrationType", "原户口性质"}, {"householdBeforeEnrollment", "入学前户口所在地"},
            {"householdAfterEnrollment", "入学后户口所在地"}, {"overseasChineseStatus", "港澳台侨外"},
            {"religion", "信仰宗教"}, {"leagueMember", "是否团员"}, {"leagueJoinDate", "入团时间"},
            {"partyMember", "是否党员"}, {"partyJoinDate", "入党时间"}, {"healthStatus", "健康状况"},
            {"bloodType", "血型"}, {"weightKg", "体重(KG)"}, {"heightCm", "身高(CM)"}, {"specialties", "特长"},
            {"hobbies", "爱好"}, {"onlyChild", "是否独生子女"}, {"email", "邮箱"}, {"phone", "联系电话"}
    }; }
    private static String[][] academicDefinitions() { return new String[][] {
            {"studentCategory", "学生类别"}, {"enrolled", "是否在籍"}, {"onCampus", "是否在校"},
            {"academicStatus", "学籍状态（本）"}, {"campus", "校区"}, {"currentGrade", "现在年级"},
            {"department", "院系"}, {"major", "专业"}, {"class", "班级"}, {"educationLevel", "培养层次"},
            {"trainingMode", "培养方式"}, {"programLength", "学制"}, {"attendanceMode", "就读方式"},
            {"degreeName", "就读学位"}, {"educationName", "就读学历"}, {"expectedGraduationDate", "预计毕业日期"},
            {"graduationDate", "毕业日期"}, {"studentSource", "学生来源"}, {"graduateStudyMode", "学习形式（研）"},
            {"counselorName", "辅导员姓名"}, {"counselorContact", "辅导员联系方式"}
    }; }

    private static final class ScrollContent extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(18, visible.height - 18); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
