package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationView;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

/** Stable list/detail workspace used while no transfer editor is open. */
public final class MajorTransferCollegeWorkspaceView extends JPanel {
    private final JTextArea detail;

    /** Creates two bounded scrollable cards with an internal text area. */
    public MajorTransferCollegeWorkspaceView(JList<?> applications, JPanel attachments) {
        this(applications, new JTextArea(), attachments);
    }

    /** Creates two bounded scrollable cards with a visible empty-detail prompt. */
    public MajorTransferCollegeWorkspaceView(JList<?> applications, JTextArea detail,
                                              JPanel attachments) {
        super(new BorderLayout());
        this.detail = detail;
        setOpaque(false);
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        JScrollPane list = card(new JScrollPane(applications), "major-transfer-application-list");
        JPanel detailBody = new JPanel(new BorderLayout());
        detailBody.setOpaque(false);
        clearDetail();
        detailBody.add(new JScrollPane(detail), BorderLayout.CENTER);
        detailBody.add(attachments, BorderLayout.SOUTH);
        JScrollPane detailCard = card(new JScrollPane(detailBody), "major-transfer-application-detail");
        list.setMinimumSize(new Dimension(320, 260));
        detailCard.setMinimumSize(new Dimension(480, 260));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, list, detailCard);
        split.setName("major-transfer-workspace-split");
        split.setResizeWeight(0.38);
        split.setDividerLocation(0.38);
        add(split, BorderLayout.CENTER);
    }

    /** Clears the detail view back to empty selection prompt. */
    public void clearDetail() {
        detail.setText("请选择一条转专业申请查看详情");
    }

    /** Formats and displays the application detail. */
    public void renderApplicationDetail(MajorTransferApplicationView app) {
        StringBuilder sb = new StringBuilder();
        sb.append("学生：").append(app.studentName()).append("（学号：").append(app.fromStudentNumber()).append("）\n");
        if (app.campusCardNumber() != null && !app.campusCardNumber().isBlank()) {
            sb.append("一卡通号：").append(app.campusCardNumber()).append("\n");
        }
        sb.append("原学院/专业：").append(app.fromDepartmentName()).append(" / ").append(app.fromMajorName()).append("\n")
                .append("目标学院/专业：").append(app.targetDepartmentName()).append(" / ").append(app.targetMajorName()).append("\n")
                .append("状态：").append(MajorTransferStatusText.status(app.status())).append("\n")
                .append("申请理由：").append(app.reason() != null ? app.reason() : "无");
        detail.setText(sb.toString());
    }

    private static JScrollPane card(JScrollPane scroll, String name) {
        scroll.setName(name);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return scroll;
    }
}
