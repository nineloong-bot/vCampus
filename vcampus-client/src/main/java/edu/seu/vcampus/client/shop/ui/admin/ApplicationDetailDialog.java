package edu.seu.vcampus.client.shop.ui.admin;

import edu.seu.vcampus.common.shop.SellerApplicationView;
import edu.seu.vcampus.common.shop.SellerReviewDecision;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

/** Read-only seller-application detail with optional review actions. */
final class ApplicationDetailDialog {
    private ApplicationDetailDialog() { }

    static Optional<ApplicationReviewPanel.DetailReview> show(Component parent,
            SellerApplicationView application, boolean reviewable) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "开店申请详情", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 6));
        fields.add(new JLabel("申请人")); fields.add(value(application.applicantUserId()));
        fields.add(new JLabel("店铺名称")); fields.add(value(application.shopName()));
        fields.add(new JLabel("店铺类别")); fields.add(value(application.category()));
        fields.add(new JLabel("联系方式")); fields.add(value(application.contact()));
        fields.add(new JLabel("状态")); fields.add(value(application.status().name()));
        JTextArea statement = new JTextArea(application.applicationStatement(), 12, 48);
        statement.setName("admin.applications.detail.statement");
        statement.setEditable(false); statement.setLineWrap(true); statement.setWrapStyleWord(true);
        statement.setCaretPosition(0);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(fields, BorderLayout.NORTH);
        JPanel plan = new JPanel(new BorderLayout(4, 4));
        plan.add(new JLabel("经营计划"), BorderLayout.NORTH);
        plan.add(new JScrollPane(statement), BorderLayout.CENTER);
        content.add(plan, BorderLayout.CENTER);
        ApplicationReviewPanel.DetailReview[] result = new ApplicationReviewPanel.DetailReview[1];
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton approve = new JButton("通过");
        JButton reject = new JButton("驳回");
        JButton close = new JButton("关闭");
        approve.setEnabled(reviewable); reject.setEnabled(reviewable);
        approve.addActionListener(event -> {
            result[0] = new ApplicationReviewPanel.DetailReview(SellerReviewDecision.APPROVE, null);
            dialog.dispose();
        });
        reject.addActionListener(event -> {
            String reason = JOptionPane.showInputDialog(dialog, "请输入驳回原因");
            if (reason != null && !reason.isBlank()) {
                result[0] = new ApplicationReviewPanel.DetailReview(
                        SellerReviewDecision.REJECT, reason.strip());
                dialog.dispose();
            }
        });
        close.addActionListener(event -> dialog.dispose());
        actions.add(approve); actions.add(reject); actions.add(close);
        content.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setSize(700, 520);
        dialog.setMinimumSize(new Dimension(560, 420));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return Optional.ofNullable(result[0]);
    }

    private static JTextField value(String text) {
        JTextField field = new JTextField(text == null ? "" : text);
        field.setEditable(false);
        return field;
    }
}
