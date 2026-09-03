package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Administrator-facing complete student profile with academic-only editing. */
public final class StudentDetailPanel extends JPanel {
    private static final Color TABLE_BORDER = new Color(178, 218, 211);
    private static final Color TABLE_LABEL = new Color(239, 247, 245);
    private static final Color SECTION_ACCENT = new Color(52, 151, 136);
    private static final Color EDIT_LINK = new Color(43, 174, 205);

    private final StudentClientService students;
    private final ClientConnection connection;
    private final String studentId;
    private final boolean canEdit;
    private final AtomicLong requestGeneration = new AtomicLong();
    private final Map<String, JLabel> values = new LinkedHashMap<>();
    private final ChangesTableModel changesModel = new ChangesTableModel();
    private volatile boolean active;
    private boolean loaded;
    private StudentProfileData profile;
    private JLabel statusLabel;
    private JLabel errorLabel;
    private JButton academicEditButton;
    private JTable changesTable;

    public StudentDetailPanel(StudentClientService students, ClientConnection connection,
                              String studentId, boolean canEdit) {
        super(new BorderLayout(0, UiSpacing.SPACE_4));
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
        JPanel heading = new JPanel(new BorderLayout(0, UiSpacing.SPACE_1));
        heading.setOpaque(false);
        JLabel breadcrumb = text("学籍管理 > 学生详情", UiTypography.CAPTION, UiColors.TEXT_SECONDARY);
        breadcrumb.setName("student.detail.breadcrumb");
        heading.add(breadcrumb, BorderLayout.NORTH);
        JLabel title = text("学生学籍档案", UiTypography.PAGE_TITLE, UiColors.TEXT_PRIMARY);
        title.setName("student.detail.title");
        heading.add(title, BorderLayout.CENTER);
        statusLabel = text("正在加载...", UiTypography.CAPTION, UiColors.TEXT_SECONDARY);
        statusLabel.setName("student.detail.status");
        heading.add(statusLabel, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        JPanel content = new ScrollContent();
        content.setName("student.detail.fields");
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(sectionHeader("个人基本信息", false));
        content.add(profileTable(canEdit ? personalDefinitions() : teacherPersonalDefinitions()));
        content.add(Box.createVerticalStrut(UiSpacing.SPACE_6));
        content.add(sectionHeader("学籍信息", true));
        content.add(profileTable(academicDefinitions()));
        content.add(Box.createVerticalStrut(UiSpacing.SPACE_6));
        content.add(sectionHeader("变更记录", false));
        content.add(changesTable());

        JScrollPane scroll = new JScrollPane(content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("student.detail.fields.scroll");
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getAccessibleContext().setAccessibleName("学生学籍档案字段");
        add(scroll, BorderLayout.CENTER);

        errorLabel = text(" ", UiTypography.CAPTION, UiColors.ERROR_FG);
        errorLabel.setName("student.detail.error");
        add(errorLabel, BorderLayout.SOUTH);
        setEditingEnabled(false);
    }

    private JPanel sectionHeader(String title, boolean editableAcademic) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel accent = new JLabel(" ");
        accent.setOpaque(true);
        accent.setBackground(SECTION_ACCENT);
        accent.setPreferredSize(new Dimension(6, 30));
        header.add(accent);
        JLabel titleLabel = text(title, UiTypography.SECTION_TITLE.deriveFont(Font.BOLD, 20f),
                UiColors.TEXT_PRIMARY);
        if ("个人基本信息".equals(title)) titleLabel.setName("student.detail.personal.title");
        else if ("学籍信息".equals(title)) titleLabel.setName("student.detail.academic.title");
        else titleLabel.setName("student.detail.changes.title");
        header.add(titleLabel);
        if (editableAcademic && canEdit) {
            academicEditButton = new JButton("编辑");
            academicEditButton.setName("student.detail.academic.edit");
            academicEditButton.setBorderPainted(false);
            academicEditButton.setContentAreaFilled(false);
            academicEditButton.setForeground(EDIT_LINK);
            academicEditButton.setFont(UiTypography.BODY.deriveFont(Font.BOLD));
            academicEditButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            academicEditButton.getAccessibleContext().setAccessibleName("编辑学籍信息");
            academicEditButton.addActionListener(event -> editAcademic());
            header.add(academicEditButton);
        }
        return header;
    }

    private JPanel profileTable(String[][] definitions) {
        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(false);
        table.setAlignmentX(Component.LEFT_ALIGNMENT);
        int rows = (definitions.length + 2) / 3;
        for (int index = 0; index < rows * 3; index++) {
            int row = index / 3;
            int pair = index % 3;
            String key = index < definitions.length ? definitions[index][0] : null;
            String title = index < definitions.length ? definitions[index][1] : "";
            JLabel label = key == null ? emptyCell() : cell(title, true);
            JLabel value = key == null ? emptyCell() : cell("未填写", false);
            if (key != null) {
                value.setName("student.detail.profile." + key);
                values.put(key, value);
            }
            addCell(table, label, pair * 2, row, .12);
            addCell(table, value, pair * 2 + 1, row, .21);
        }
        return table;
    }

    private static JLabel emptyCell() {
        JLabel empty = new JLabel();
        empty.setOpaque(false);
        return empty;
    }

    private JScrollPane changesTable() {
        changesTable = new JTable(changesModel);
        changesTable.setName("student.detail.changes");
        changesTable.setFont(UiTypography.BODY);
        changesTable.setRowHeight(UiSpacing.SPACE_6);
        changesTable.getTableHeader().setFont(UiTypography.CAPTION);
        changesTable.getTableHeader().setReorderingAllowed(false);
        changesTable.getAccessibleContext().setAccessibleName("变更记录");
        changesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        changesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) openChangeDetail();
            }
        });
        JScrollPane scroll = new JScrollPane(changesTable);
        scroll.setName("student.detail.changes.scroll");
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(0, 200));
        scroll.setBorder(UiBorders.LINE);
        return scroll;
    }

    private static void addCell(JPanel table, JComponent cell, int x, int y, double weight) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.weightx = weight;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        table.add(cell, constraints);
    }

    private static JLabel cell(String value, boolean label) {
        JLabel result = text(value, label ? UiTypography.BODY.deriveFont(Font.BOLD) : UiTypography.BODY,
                UiColors.TEXT_PRIMARY);
        result.setOpaque(true);
        result.setBackground(label ? TABLE_LABEL : Color.WHITE);
        result.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(TABLE_BORDER),
                BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        result.setMinimumSize(new Dimension(label ? 105 : 140, 38));
        return result;
    }

    @Override public void addNotify() {
        super.addNotify();
        active = true;
        connectionChanged(connection.state());
        loadProfile();
    }

    @Override public void removeNotify() {
        active = false;
        requestGeneration.incrementAndGet();
        super.removeNotify();
    }

    private void loadProfile() {
        long generation = requestGeneration.incrementAndGet();
        onEdt(() -> {
            if (active && generation == requestGeneration.get()) renderLoading();
        });
        if (!canEdit) {
            students.get(studentId).whenComplete((body, failure) -> onEdt(() -> {
                if (!active || generation != requestGeneration.get()) return;
                if (failure != null || body == null || !body.success() || body.data() == null) {
                    renderError(message(body, "学生信息加载失败，请稍后重试"));
                    return;
                }
                renderLimitedProfile(body.data());
                loadChanges(generation);
            }));
            return;
        }
        students.getProfile(studentId).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure != null || body == null || !body.success() || body.data() == null) {
                renderError(message(body, "学生档案加载失败，请稍后重试"));
                return;
            }
            profile = body.data();
            renderProfile(profile);
            loadChanges(generation);
        }));
    }

    private void loadChanges(long generation) {
        students.listChanges(studentId).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure != null) errorLabel.setText("变更记录加载失败");
            else if (body != null && body.success() && body.data() != null) changesModel.setData(body.data());
        }));
    }

    private void renderLoading() {
        statusLabel.setText("正在加载...");
        errorLabel.setText(" ");
        setEditingEnabled(false);
    }

    private void renderError(String value) {
        statusLabel.setText("加载失败");
        errorLabel.setText(value);
        setEditingEnabled(false);
    }

    private void renderProfile(StudentProfileData data) {
        StudentView core = data.core();
        StudentPersonalProfile personal = data.personal();
        StudentAcademicProfile academic = data.academic();
        put("card", core.campusCardNumber()); put("studentNumber", core.studentNumber()); put("name", core.studentName());
        put("namePinyin", personal.namePinyin()); put("formerName", personal.formerName()); put("gender", gender(core.gender()));
        put("politicalStatus", personal.politicalStatus()); put("ethnicity", personal.ethnicity()); put("maritalStatus", personal.maritalStatus());
        put("idDocumentType", personal.idDocumentType()); put("idDocumentNumber", personal.idDocumentNumber()); put("idIssuedDate", personal.idIssuedDate());
        put("birthDate", personal.birthDate()); put("nativePlace", personal.nativePlace()); put("countryRegion", personal.countryRegion());
        put("birthplace", personal.birthplace()); put("studentOriginPlace", personal.studentOriginPlace());
        put("householdRegistrationType", personal.householdRegistrationType()); put("householdBeforeEnrollment", personal.householdBeforeEnrollment());
        put("householdAfterEnrollment", personal.householdAfterEnrollment()); put("overseasChineseStatus", personal.overseasChineseStatus());
        put("religion", personal.religion()); put("leagueMember", yesNo(personal.leagueMember())); put("leagueJoinDate", personal.leagueJoinDate());
        put("partyMember", yesNo(personal.partyMember())); put("partyJoinDate", personal.partyJoinDate()); put("healthStatus", personal.healthStatus());
        put("bloodType", personal.bloodType()); put("weightKg", personal.weightKg()); put("heightCm", personal.heightCm());
        put("specialties", personal.specialties()); put("hobbies", personal.hobbies()); put("onlyChild", yesNo(personal.onlyChild()));
        put("email", personal.email()); put("phone", personal.phone());

        put("studentCategory", academic.studentCategory()); put("enrolled", yesNo(academic.enrolled())); put("onCampus", yesNo(academic.onCampus()));
        put("academicStatus", academic.academicStatus()); put("campus", academic.campus()); put("currentGrade", academic.currentGrade());
        put("department", academic.departmentName()); put("major", academic.majorName()); put("class", academic.className());
        put("educationLevel", academic.educationLevel()); put("trainingMode", academic.trainingMode()); put("programLength", academic.programLengthYears());
        put("attendanceMode", academic.attendanceMode() == null ? null : academic.attendanceMode().displayName());
        put("degreeName", academic.degreeName()); put("educationName", academic.educationName());
        put("expectedGraduationDate", academic.expectedGraduationDate()); put("graduationDate", academic.graduationDate());
        put("studentSource", academic.studentSource()); put("graduateStudyMode", academic.graduateStudyMode());
        put("counselorName", academic.counselorName()); put("counselorContact", academic.counselorContact());
        loaded = true;
        statusLabel.setText(connection.state() == ConnectionState.CONNECTED ? "已加载" : "已断开连接");
        errorLabel.setText(" ");
        updateEditingState();
    }

    private void renderLimitedProfile(StudentView core) {
        put("card", core.campusCardNumber());
        put("studentNumber", core.studentNumber());
        put("name", core.studentName());
        put("gender", gender(core.gender()));
        put("email", core.email());
        put("phone", core.phone());
        put("studentCategory", studentType(core.studentType()));
        put("academicStatus", status(core.status()));
        put("department", core.departmentName());
        put("major", core.majorName());
        put("class", core.className());
        loaded = true;
        statusLabel.setText(connection.state() == ConnectionState.CONNECTED ? "已加载" : "已断开连接");
        errorLabel.setText(" ");
    }

    private void editAcademic() {
        if (profile == null || connection.state() != ConnectionState.CONNECTED) return;
        new AdminStudentInfoEditDialog(SwingUtilities.getWindowAncestor(this), students,
                profile.core(), profile.academic(), saved -> loadProfile()).setVisible(true);
    }

    private void openChangeDetail() {
        StudentChangeView change = changesModel.getChangeAt(changesTable.getSelectedRow());
        if (change != null) new ChangeDetailDialog(SwingUtilities.getWindowAncestor(this), change).setVisible(true);
    }

    private void connectionChanged(ConnectionState state) {
        onEdt(() -> {
            if (!active) return;
            if (loaded) statusLabel.setText(state == ConnectionState.CONNECTED ? "已加载" : "已断开连接");
            updateEditingState();
        });
    }

    private void updateEditingState() {
        setEditingEnabled(canEdit && profile != null && connection.state() == ConnectionState.CONNECTED);
    }

    private void setEditingEnabled(boolean enabled) {
        if (academicEditButton != null) academicEditButton.setEnabled(enabled);
    }

    private void put(String key, Object value) {
        JLabel target = values.get(key);
        if (target != null) target.setText(filled(value));
    }

    private static String filled(Object value) { return value == null || value.toString().isBlank() ? "未填写" : value.toString(); }
    private static String yesNo(boolean value) { return value ? "是" : "否"; }
    private static String gender(String value) {
        if (value == null || value.isBlank()) return "未填写";
        return switch (value) { case "MALE" -> "男"; case "FEMALE" -> "女"; default -> value; };
    }
    private static String studentType(StudentType value) {
        if (value == null) return "未填写";
        return switch (value) { case UNDERGRADUATE -> "本科生"; case MASTER -> "硕士生"; case DOCTORATE -> "博士生"; };
    }
    private static String status(StudentStatus value) {
        if (value == null) return "未填写";
        return switch (value) { case ACTIVE -> "正常"; case SUSPENDED -> "休学"; case GRADUATED -> "已毕业"; case WITHDRAWN -> "已退学"; };
    }
    private static String message(ResponseBody<?> body, String fallback) {
        return body != null && body.message() != null && !body.message().isBlank() ? body.message() : fallback;
    }
    private static void onEdt(Runnable task) { if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task); }
    private static JLabel text(String value, Font font, Color color) {
        JLabel label = new JLabel(value); label.setFont(font); label.setForeground(color); return label;
    }

    private static String[][] teacherPersonalDefinitions() { return new String[][] {
            {"card", "一卡通号"}, {"studentNumber", "学号"}, {"name", "姓名"},
            {"gender", "性别"}, {"email", "邮箱"}, {"phone", "联系电话"}
    }; }

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

    private static String changeTypeLabel(String type) {
        if (type == null) return "";
        return switch (type) {
            case "ADMISSION" -> "录取"; case "CLASS_CHANGE" -> "转班"; case "STATUS_CHANGE" -> "状态变更";
            case "ENROLLMENT_CHANGE" -> "学籍变更"; case "ACADEMIC_CHANGE" -> "学籍修改";
            case "PROFILE_CHANGE" -> "信息修改"; default -> type;
        };
    }

    private static final class ChangesTableModel extends AbstractTableModel {
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

    private static final class ScrollContent extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(18, visible.height - 18); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
