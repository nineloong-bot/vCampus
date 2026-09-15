package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Page skeleton and field definitions for the student detail panel segments. */
abstract class StudentDetailPanelSections extends StudentDetailPanelTable {

    StudentDetailPanelSections(StudentClientService students,
            edu.seu.vcampus.client.core.network.ClientConnection connection,
            String studentId, boolean canEdit) {
        super(students, connection, studentId, canEdit);
    }

    @Override
    void initializePanel() {
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
        if (canEdit) {
            content.add(Box.createVerticalStrut(UiSpacing.SPACE_6));
            content.add(sectionHeader("变更记录", false));
            content.add(changesTable());
        }

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
}
