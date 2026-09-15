package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.StudentProfileWorkspace;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Shared state, definitions and small helpers for the profile panel segments. */
abstract class MyStudentProfilePanelBase extends JPanel {
    static final Color TABLE_BORDER = new Color(178, 218, 211);
    static final Color TABLE_LABEL = new Color(239, 247, 245);
    static final Color ACTION_GREEN = new Color(139, 195, 74);
    static final Set<String> REQUIRED_EDIT_KEYS = Set.of(
            "namePinyin", "idIssuedDate", "nativePlace", "birthplace",
            "householdBeforeEnrollment", "householdAfterEnrollment",
            "leagueMember", "leagueJoinDate", "partyMember", "healthStatus",
            "weightKg", "heightCm", "specialties", "hobbies", "onlyChild");
    static final Set<String> CORE_READONLY_KEYS = Set.of(
            "card", "studentNumber", "name", "gender", "birthDate",
            "idDocumentType", "idDocumentNumber");

    protected final StudentClientService students;
    protected final ClientConnection connection;
    protected final AtomicLong generation = new AtomicLong();
    protected final Map<String, JLabel> values = new LinkedHashMap<>();
    protected final Map<String, JComponent> editComponents = new LinkedHashMap<>();
    protected final Map<String, JLabel> editReadOnlyLabels = new LinkedHashMap<>();
    protected volatile boolean active;
    protected StudentProfileWorkspace workspace;
    protected final StudentProfileStatusView statuses = new StudentProfileStatusView();
    protected JLabel errorLabel;
    protected JButton refreshButton, personalEdit, personalSave, academicEdit, exportButton, submitButton;
    protected CardLayout personalCardLayout;
    protected JPanel personalCardContainer;
    protected boolean isPersonalEditing;

    /** Stores the service and connection shared by the panel segments. */
    protected MyStudentProfilePanelBase(StudentClientService students, ClientConnection connection) {
        this.students = Objects.requireNonNull(students);
        this.connection = Objects.requireNonNull(connection);
    }

    abstract void connectionChanged(ConnectionState state);

    void setControls(boolean enabled) { personalEdit.setEnabled(enabled); academicEdit.setEnabled(enabled); exportButton.setEnabled(enabled); submitButton.setEnabled(enabled); }

    void put(String key, Object value) {
        JLabel label = values.get(key);
        if (label != null) {
            String text = filled(value);
            label.setText(text);
            label.setToolTipText(text);
        }
    }

    static String filled(Object value) { return value == null || value.toString().isBlank() ? "未填写" : value.toString(); }
    static String yesNo(boolean value) { return value ? "是" : "否"; }
    static String message(ResponseBody<?> body, String fallback) { return body != null && body.message() != null && !body.message().isBlank() ? body.message() : fallback; }
    static void onEdt(Runnable task) { if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task); }

    static JButton action(String title, String name) {
        JButton button = new JButton(title); button.setName(name); button.setFont(UiTypography.SECTION_TITLE);
        button.setForeground(Color.WHITE); button.setBackground(ACTION_GREEN); button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18)); button.getAccessibleContext().setAccessibleName(title); return button;
    }

    static JLabel text(String value, Font font, Color color) { JLabel label = new JLabel(value); label.setFont(font); label.setForeground(color); return label; }

    static JLabel cell(String value, boolean label) {
        JLabel result = text(value, label ? UiTypography.BODY.deriveFont(Font.BOLD) : UiTypography.BODY,
                UiColors.TEXT_PRIMARY);
        result.setOpaque(true); result.setBackground(label ? TABLE_LABEL : Color.WHITE);
        result.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(TABLE_BORDER),
                BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        result.setMinimumSize(new Dimension(label ? 105 : 140, 38));
        if (value != null && !value.isBlank()) {
            result.setToolTipText(value);
        }
        return result;
    }

    static void addCell(JPanel table, JComponent cell, int x, int y, double weight) {
        GridBagConstraints c = new GridBagConstraints(); c.gridx = x; c.gridy = y; c.weightx = weight;
        c.fill = GridBagConstraints.BOTH; c.anchor = GridBagConstraints.WEST; table.add(cell, c);
    }

    static String ensurePdfExtension(String name) { return name.endsWith(".pdf") ? name : name + ".pdf"; }
    static java.io.File ensurePdfExtension(java.io.File file) {
        return file.getName().endsWith(".pdf") ? file : new java.io.File(file.getParentFile(), file.getName() + ".pdf");
    }

    static boolean isComboField(String key) {
        return switch (key) {
            case "politicalStatus", "ethnicity", "maritalStatus",
                 "householdRegistrationType", "overseasChineseStatus",
                 "leagueMember", "partyMember", "healthStatus", "bloodType", "onlyChild" -> true;
            default -> false;
        };
    }

    static String[] getComboOptions(String key) {
        return switch (key) {
            case "politicalStatus" -> PersonalProfileEditPanel.OPTIONS.get("politicalStatus");
            case "ethnicity" -> PersonalProfileEditPanel.ETHNICITIES;
            case "maritalStatus" -> PersonalProfileEditPanel.OPTIONS.get("maritalStatus");
            case "householdRegistrationType" -> PersonalProfileEditPanel.OPTIONS.get("householdRegistrationType");
            case "overseasChineseStatus" -> PersonalProfileEditPanel.OPTIONS.get("overseasChineseStatus");
            case "leagueMember" -> new String[]{"是", "否"};
            case "partyMember" -> new String[]{"是", "否"};
            case "healthStatus" -> PersonalProfileEditPanel.OPTIONS.get("healthStatus");
            case "bloodType" -> PersonalProfileEditPanel.OPTIONS.get("bloodType");
            case "onlyChild" -> new String[]{"请选择...", "是", "否"};
            default -> new String[]{""};
        };
    }

    static String[][] personalDefinitions() { return new String[][] {
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

    static String[][] academicDefinitions() { return new String[][] {
            {"studentCategory", "学生类别"}, {"enrolled", "是否在籍"}, {"onCampus", "是否在校"},
            {"academicStatus", "学籍状态（本）"}, {"campus", "校区"}, {"currentGrade", "现在年级"},
            {"department", "院系"}, {"major", "专业"}, {"class", "班级"}, {"educationLevel", "培养层次"},
            {"trainingMode", "培养方式"}, {"programLength", "学制"}, {"attendanceMode", "就读方式"},
            {"degreeName", "就读学位"}, {"educationName", "就读学历"}, {"expectedGraduationDate", "预计毕业日期"},
            {"graduationDate", "毕业日期"}, {"studentSource", "学生来源"}, {"graduateStudyMode", "学习形式（研）"},
            {"counselorName", "辅导员姓名"}, {"counselorContact", "辅导员联系方式"}
    }; }
}
