package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Assembles the profile page: header, scrollable tables and footer actions. */
abstract class MyStudentProfilePanelLayout extends MyStudentProfilePanelTables {

    /** Creates the layout segment of the profile panel. */
    protected MyStudentProfilePanelLayout(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    void build() {
        JPanel top = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0)); top.setOpaque(false);
        JPanel titleBox = new JPanel(); titleBox.setOpaque(false); titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel title = text("我的学籍档案", UiTypography.PAGE_TITLE, UiColors.TEXT_PRIMARY);
        titleBox.add(title); titleBox.add(statuses.loadingGap()); titleBox.add(statuses.loadingLabel()); top.add(titleBox);
        refreshButton = new JButton("刷新"); refreshButton.setName("student.profile.refresh");
        refreshButton.getAccessibleContext().setAccessibleName("刷新学籍档案"); refreshButton.addActionListener(e -> refreshProfile());
        top.add(refreshButton, BorderLayout.EAST); add(top, BorderLayout.NORTH);

        JPanel content = new ScrollContent(); content.setName("student.profile.fields");
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(sectionHeader("个人基本信息", true));

        personalCardLayout = new CardLayout();
        personalCardContainer = new JPanel(personalCardLayout);
        personalCardContainer.setOpaque(false);
        personalCardContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        personalCardContainer.add(profileTable(personalDefinitions()), "VIEW");
        personalCardContainer.add(buildPersonalEditTable(personalDefinitions()), "EDIT");
        content.add(personalCardContainer);

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
        errorLabel = text(" ", UiTypography.CAPTION, UiColors.ERROR_FG); errorLabel.setName("student.profile.error");
        messages.add(statuses.applicationLabel()); messages.add(errorLabel); footer.add(messages, BorderLayout.NORTH);
        JPanel actions = new JPanel(new BorderLayout()); actions.setOpaque(false);
        exportButton = action("导出基本信息 PDF", "student.profile.export"); exportButton.addActionListener(e -> exportPdf());
        submitButton = action("提交审核", "student.profile.submit"); submitButton.addActionListener(e -> submitOrWithdraw());
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
        edit.addActionListener(e -> { if (personal) togglePersonalEdit(); else editAttendance(); });
        if (personal) {
            personalEdit = edit;
            header.add(edit);

            personalSave = new JButton("暂存");
            personalSave.setName("student.profile.personal.save");
            personalSave.setFont(UiTypography.BODY.deriveFont(Font.BOLD));
            personalSave.setForeground(Color.WHITE);
            personalSave.setBackground(ACTION_GREEN);
            personalSave.setOpaque(true);
            personalSave.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            personalSave.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            personalSave.setVisible(false);
            personalSave.addActionListener(e -> savePersonalDraft());
            header.add(personalSave);
        } else {
            academicEdit = edit;
            header.add(edit);
        }
        return header;
    }

    /** Scrollable content wrapper reporting the profile page scroll increment. */
    private static final class ScrollContent extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(18, visible.height - 18); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
