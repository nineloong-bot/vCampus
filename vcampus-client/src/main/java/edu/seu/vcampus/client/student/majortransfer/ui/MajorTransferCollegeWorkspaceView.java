package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
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
    /** Creates two bounded scrollable cards with a visible empty-detail prompt. */
    public MajorTransferCollegeWorkspaceView(JList<?> applications, JTextArea detail,
                                              JPanel attachments) {
        super(new BorderLayout());
        setOpaque(false);
        JScrollPane list = card(new JScrollPane(applications), "major-transfer-application-list");
        JPanel detailBody = new JPanel(new BorderLayout());
        detailBody.setOpaque(false);
        detail.setText("请选择一条转专业申请查看详情");
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

    private static JScrollPane card(JScrollPane scroll, String name) {
        scroll.setName(name);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return scroll;
    }
}
