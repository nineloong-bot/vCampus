package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.student.StudentPersonalProfile;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Scrollable editor for the student-owned personal section. */
public final class PersonalProfileEditPanel extends JPanel {
    private final Map<String, JTextField> fields = new LinkedHashMap<>();
    private final JCheckBox league = check("是否团员"), party = check("是否党员"), onlyChild = check("是否独生子女");

    public PersonalProfileEditPanel(StudentPersonalProfile value) {
        super(new GridBagLayout());
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
        JTextField field = new JTextField(28); field.setName("student.profile.personal." + key);
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
    }

    public StudentPersonalProfile value() {
        try {
            return new StudentPersonalProfile(text("namePinyin"), text("formerName"), text("politicalStatus"),
                    text("ethnicity"), text("maritalStatus"), text("idDocumentType"), text("idDocumentNumber"),
                    date("idIssuedDate"), date("birthDate"), text("nativePlace"), text("countryRegion"),
                    text("birthplace"), text("studentOriginPlace"), text("householdRegistrationType"),
                    text("householdBeforeEnrollment"), text("householdAfterEnrollment"), text("overseasChineseStatus"),
                    text("religion"), league.isSelected(), date("leagueJoinDate"), party.isSelected(), date("partyJoinDate"),
                    text("healthStatus"), text("bloodType"), integer("weightKg"), integer("heightCm"),
                    text("specialties"), text("hobbies"), onlyChild.isSelected(), text("email"), text("phone"));
        } catch (DateTimeParseException | NumberFormatException error) {
            throw new IllegalArgumentException("日期须为 yyyy-MM-dd，身高和体重须为整数");
        }
    }
    private void set(String key, String value) { fields.get(key).setText(value == null ? "" : value); }
    private String text(String key) { String value = fields.get(key).getText().trim(); return value.isEmpty() ? null : value; }
    private LocalDate date(String key) { String value = text(key); return value == null ? null : LocalDate.parse(value); }
    private Integer integer(String key) { String value = text(key); return value == null ? null : Integer.valueOf(value); }
    private static String show(Object value) { return value == null ? null : value.toString(); }
}
