package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.student.StudentFieldError;
import edu.seu.vcampus.common.student.StudentFieldValidator;
import edu.seu.vcampus.common.student.StudentPersonalProfile;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Scrollable editor for the student-owned personal section. */
public final class PersonalProfileEditPanel extends JPanel {
    private static final Map<String, String> HINTS = Map.ofEntries(
            Map.entry("namePinyin", "使用大写拼音，姓与名之间留空格，例：ZHANG SAN"),
            Map.entry("formerName", "填写曾用名；如无可留空"),
            Map.entry("politicalStatus", "从列表选择，例：共青团员"),
            Map.entry("ethnicity", "从列表选择，例：汉族"),
            Map.entry("maritalStatus", "从列表选择，例：未婚"),
            Map.entry("idDocumentType", "选择证件类型，例：居民身份证"),
            Map.entry("idDocumentNumber", "居民身份证填 18 位号码，例：11010519491231002X"),
            Map.entry("idIssuedDate", "日期格式 yyyy-MM-dd，例：2020-08-18，不能晚于今天"),
            Map.entry("birthDate", "日期格式 yyyy-MM-dd，例：2005-12-03；入学时须已年满 18 周岁"),
            Map.entry("nativePlace", "填写省市，例：江苏省南京市"),
            Map.entry("countryRegion", "填写国家或地区，例：中国"),
            Map.entry("birthplace", "填写省市县，例：江苏省南京市"),
            Map.entry("studentOriginPlace", "填写高考生源地，例：江苏省南京市"),
            Map.entry("householdRegistrationType", "从列表选择户口性质"),
            Map.entry("householdBeforeEnrollment", "填写入学前户口所在地的完整地址"),
            Map.entry("householdAfterEnrollment", "填写入学后户口所在地的完整地址"),
            Map.entry("overseasChineseStatus", "从列表选择，例：无"),
            Map.entry("religion", "填写宗教信仰；无信仰可填“无宗教信仰”"),
            Map.entry("leagueJoinDate", "团员必填，格式 yyyy-MM-dd，例：2020-12-12"),
            Map.entry("partyJoinDate", "党员必填，格式 yyyy-MM-dd，例：2025-07-01"),
            Map.entry("healthStatus", "从列表选择，例：健康或良好"),
            Map.entry("bloodType", "从列表选择，例：A"),
            Map.entry("weightKg", "填写 20–300 之间的整数，例：58"),
            Map.entry("heightCm", "填写 100–280 之间的整数，例：172"),
            Map.entry("specialties", "简要填写特长，例：书法"),
            Map.entry("hobbies", "简要填写爱好，例：乒乓球"),
            Map.entry("email", "填写完整邮箱，例：student@seu.edu.cn"),
            Map.entry("phone", "填写 11 位大陆手机号或固话，例：13800000000"));
    private static final Map<String, String[]> OPTIONS = Map.of(
            "politicalStatus", new String[]{"", "中共党员", "中共预备党员", "共青团员", "群众", "其他"},
            "maritalStatus", new String[]{"", "未婚", "已婚", "离异", "丧偶"},
            "idDocumentType", new String[]{"", "居民身份证", "护照", "港澳台居民居住证", "其他"},
            "householdRegistrationType", new String[]{"", "农业家庭户口", "非农业家庭户口", "集体户口", "其他"},
            "overseasChineseStatus", new String[]{"", "无", "港澳台学生", "华侨", "外国留学生"},
            "healthStatus", new String[]{"", "健康或良好", "一般或较弱", "有慢性病", "残疾"},
            "bloodType", new String[]{"", "A", "B", "AB", "O", "不详"});
    private static final String[] ETHNICITIES = {"", "汉族", "壮族", "回族", "满族", "维吾尔族", "苗族", "彝族", "土家族", "藏族", "蒙古族", "侗族", "布依族", "瑶族", "白族", "朝鲜族", "哈尼族", "黎族", "哈萨克族", "傣族", "畲族", "拉祜族", "水族", "东乡族", "纳西族", "景颇族", "柯尔克孜族", "土族", "达斡尔族", "仫佬族", "羌族", "布朗族", "撒拉族", "毛南族", "仡佬族", "锡伯族", "阿昌族", "普米族", "塔吉克族", "怒族", "乌孜别克族", "俄罗斯族", "鄂温克族", "德昂族", "保安族", "裕固族", "京族", "塔塔尔族", "独龙族", "鄂伦春族", "赫哲族", "门巴族", "珞巴族", "基诺族", "高山族", "其他"};

    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    private final LocalDate enrollmentDate;
    private final JCheckBox league = check("是否团员"), party = check("是否党员"), onlyChild = check("是否独生子女");

    public PersonalProfileEditPanel(StudentPersonalProfile value) {
        this(value, null);
    }

    public PersonalProfileEditPanel(StudentPersonalProfile value, LocalDate enrollmentDate) {
        super(new GridBagLayout());
        this.enrollmentDate = enrollmentDate;
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(BorderFactory.createEmptyBorder(UiSpacing.SPACE_4, UiSpacing.SPACE_4,
                UiSpacing.SPACE_4, UiSpacing.SPACE_4));
        String[][] definitions = {
                {"namePinyin", "姓名拼音"}, {"formerName", "曾用名"}, {"politicalStatus", "政治面貌"},
                {"ethnicity", "民族"}, {"maritalStatus", "婚姻状态"}, {"idDocumentType", "证件类型"},
                {"idDocumentNumber", "身份证件号"}, {"idIssuedDate", "证件签发日期(yyyy-MM-dd)"},
                {"birthDate", "出生日期(yyyy-MM-dd)"}, {"nativePlace", "籍贯"}, {"countryRegion", "国家地区"},
                {"birthplace", "出生地"}, {"studentOriginPlace", "生源地"}, {"householdRegistrationType", "户口性质"},
                {"householdBeforeEnrollment", "入学前户口所在地"}, {"householdAfterEnrollment", "入学后户口所在地"},
                {"overseasChineseStatus", "港澳台侨外"}, {"religion", "信仰宗教"},
                {"leagueJoinDate", "入团时间(yyyy-MM-dd)"}, {"partyJoinDate", "入党时间(yyyy-MM-dd)"},
                {"healthStatus", "健康状况"}, {"bloodType", "血型"}, {"weightKg", "体重(KG)"},
                {"heightCm", "身高(CM)"}, {"specialties", "特长"}, {"hobbies", "爱好"},
                {"email", "邮箱"}, {"phone", "联系电话"}
        };
        for (int i = 0; i < definitions.length; i++) addField(i, definitions[i][0], definitions[i][1]);
        int row = definitions.length;
        addCheck(row++, league); addCheck(row++, party); addCheck(row, onlyChild);
        populate(value);
    }

    private void addField(int row, String key, String title) {
        GridBagConstraints label = constraint(0, row); label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(UiSpacing.SPACE_1, 0, UiSpacing.SPACE_1, UiSpacing.SPACE_3);
        JLabel text = new JLabel(title); text.setFont(UiTypography.BODY); text.setForeground(UiColors.TEXT_PRIMARY); add(text, label);
        JComponent field;
        String[] options = "ethnicity".equals(key) ? ETHNICITIES : OPTIONS.get(key);
        if (options == null) field = new JTextField(28);
        else field = new JComboBox<>(options);
        field.setName("student.profile.personal." + key);
        field.setToolTipText(HINTS.get(key));
        field.getAccessibleContext().setAccessibleName(title); fields.put(key, field);
        GridBagConstraints input = constraint(1, row); input.weightx = 1; input.fill = GridBagConstraints.HORIZONTAL;
        input.insets = new Insets(UiSpacing.SPACE_1, 0, UiSpacing.SPACE_1, 0); add(field, input);
    }
    private void addCheck(int row, JCheckBox value) {
        GridBagConstraints c = constraint(0, row); c.gridwidth = 2; c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(UiSpacing.SPACE_1, 0, UiSpacing.SPACE_1, 0); add(value, c);
    }
    private static GridBagConstraints constraint(int x, int y) { GridBagConstraints c = new GridBagConstraints(); c.gridx = x; c.gridy = y; return c; }
    private static JCheckBox check(String title) { JCheckBox value = new JCheckBox(title); value.setOpaque(false); value.setFont(UiTypography.BODY); return value; }

    private void populate(StudentPersonalProfile v) {
        if (v == null) return;
        set("namePinyin", v.namePinyin()); set("formerName", v.formerName()); set("politicalStatus", v.politicalStatus());
        set("ethnicity", v.ethnicity()); set("maritalStatus", v.maritalStatus()); set("idDocumentType", v.idDocumentType());
        set("idDocumentNumber", v.idDocumentNumber()); set("idIssuedDate", show(v.idIssuedDate())); set("birthDate", show(v.birthDate()));
        set("nativePlace", v.nativePlace()); set("countryRegion", v.countryRegion()); set("birthplace", v.birthplace());
        set("studentOriginPlace", v.studentOriginPlace()); set("householdRegistrationType", v.householdRegistrationType());
        set("householdBeforeEnrollment", v.householdBeforeEnrollment()); set("householdAfterEnrollment", v.householdAfterEnrollment());
        set("overseasChineseStatus", v.overseasChineseStatus()); set("religion", v.religion());
        set("leagueJoinDate", show(v.leagueJoinDate())); set("partyJoinDate", show(v.partyJoinDate()));
        set("healthStatus", v.healthStatus()); set("bloodType", v.bloodType()); set("weightKg", show(v.weightKg()));
        set("heightCm", show(v.heightCm())); set("specialties", v.specialties()); set("hobbies", v.hobbies());
        set("email", v.email()); set("phone", v.phone()); league.setSelected(v.leagueMember());
        party.setSelected(v.partyMember()); onlyChild.setSelected(v.onlyChild());
        membershipBehavior(league, "leagueJoinDate"); membershipBehavior(party, "partyJoinDate");
    }

    public StudentPersonalProfile value() {
        try {
            StudentPersonalProfile value = new StudentPersonalProfile(text("namePinyin"), text("formerName"), text("politicalStatus"),
                    text("ethnicity"), text("maritalStatus"), text("idDocumentType"), text("idDocumentNumber"),
                    date("idIssuedDate"), date("birthDate"), text("nativePlace"), text("countryRegion"),
                    text("birthplace"), text("studentOriginPlace"), text("householdRegistrationType"),
                    text("householdBeforeEnrollment"), text("householdAfterEnrollment"), text("overseasChineseStatus"),
                    text("religion"), league.isSelected(), league.isSelected() ? date("leagueJoinDate") : null,
                    party.isSelected(), party.isSelected() ? date("partyJoinDate") : null,
                    text("healthStatus"), text("bloodType"), integer("weightKg"), integer("heightCm"),
                    text("specialties"), text("hobbies"), onlyChild.isSelected(), text("email"), text("phone"));
            List<StudentFieldError> errors = StudentFieldValidator.validatePersonal(
                    value, LocalDate.now(), enrollmentDate);
            if (!errors.isEmpty()) throw new IllegalArgumentException(errors.getFirst().message());
            return value;
        } catch (DateTimeParseException | NumberFormatException error) {
            throw new IllegalArgumentException("日期须为 yyyy-MM-dd，身高和体重须为整数");
        }
    }
    private void membershipBehavior(JCheckBox member, String dateKey) {
        JComponent date = fields.get(dateKey);
        date.setEnabled(member.isSelected());
        member.addActionListener(event -> {
            date.setEnabled(member.isSelected());
            if (!member.isSelected() && date instanceof JTextField text) text.setText("");
        });
    }
    private void set(String key, String value) {
        JComponent field = fields.get(key);
        String shown = value == null ? "" : value;
        if (field instanceof JTextField text) text.setText(shown);
        else if (field instanceof JComboBox<?> raw) {
            @SuppressWarnings("unchecked") JComboBox<String> combo = (JComboBox<String>) raw;
            ComboBoxModel<String> model = combo.getModel();
            boolean present = false;
            for (int i = 0; i < model.getSize(); i++) if (shown.equals(model.getElementAt(i))) present = true;
            if (!present && !shown.isEmpty()) combo.addItem(shown);
            combo.setSelectedItem(shown);
        }
    }
    private String text(String key) {
        JComponent field = fields.get(key);
        Object raw = field instanceof JTextField text ? text.getText() : ((JComboBox<?>) field).getSelectedItem();
        String value = raw == null ? "" : raw.toString().trim();
        return value.isEmpty() ? null : value;
    }
    private LocalDate date(String key) { String value = text(key); return value == null ? null : LocalDate.parse(value); }
    private Integer integer(String key) { String value = text(key); return value == null ? null : Integer.valueOf(value); }
    private static String show(Object value) { return value == null ? null : value.toString(); }
}
