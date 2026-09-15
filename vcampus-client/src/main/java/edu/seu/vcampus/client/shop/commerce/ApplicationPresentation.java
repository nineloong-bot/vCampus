package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.View;
import edu.seu.vcampus.common.shop.governance.GovernanceDtos.Apply;
import javax.swing.*;
import java.awt.*;

/** The submitted, pending and approved full-page application layouts. */
final class ApplicationPresentation {
    private final CommercePanel ui;

    ApplicationPresentation(CommercePanel ui) { this.ui = ui; }

    void show(View application, String stage, Runnable account, Runnable next, Runnable edit) {
        show(new Details(application.id(), application.title(), application.subjectName(),
                application.licenseNumber(), application.reason()), stage, account, next, edit);
    }

    void submitted(View receipt, Apply request, Runnable account, Runnable next, Runnable edit) {
        show(new Details(receipt.id(), request.shopName(), request.subjectName(), request.licenseNumber(), ""),
                "submitted", account, next, edit);
    }

    private record Details(String id, String title, String subjectName, String licenseNumber, String reason) { }

    private void show(Details application, String stage, Runnable account, Runnable next, Runnable edit) {
        boolean pending = "pending".equals(stage);
        boolean approved = "approved".equals(stage);
        boolean rejected = "rejected".equals(stage);
        String title = approved ? "审核通过" : rejected ? "申请未通过" : pending ? "等待审核" : "提交成功";
        JPanel body = CommerceTheme.form();
        body.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        JPanel steps = CommerceTheme.row(new JLabel("✓  提交申请"),
                new JLabel(approved ? "✓  平台审核" : "②  平台审核"), new JLabel(approved ? "✓  开店完成" : "③  开店完成"));
        ((FlowLayout) steps.getLayout()).setAlignment(FlowLayout.CENTER);
        body.add(steps);
        body.add(CommerceTheme.gap(28));
        JPanel hero = CommerceTheme.card(pending ? new Color(0xeaf0e4) : CommerceTheme.BACKGROUND, 26);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.add(CommerceTheme.heading(pending ? "◷" : rejected ? "!" : "✓", 32));
        hero.add(CommerceTheme.gap(14));
        hero.add(CommerceTheme.muted(title));
        hero.add(CommerceTheme.gap(9));
        hero.add(CommerceTheme.heading(approved ? "开店成功，欢迎成为店主" : rejected ? "请完善资料后重新提交"
                : pending ? "你的开店申请正在等待审核" : "开店申请已提交", 29));
        hero.add(CommerceTheme.gap(12));
        hero.add(CommerceTheme.muted(approved ? "你的店铺已准备就绪，可以开始创建第一件商品。"
                : rejected ? safe(application.reason()) : pending ? "资料已提交，审核结果将显示在本页面。"
                : "你的资料已成功提交，接下来将由平台进行审核。"));
        if (!pending) for (Component component : hero.getComponents())
            if (component instanceof JComponent child) child.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.add(hero);
        body.add(CommerceTheme.gap(22));
        JPanel facts = CommerceTheme.card(Color.WHITE, 28);
        facts.setLayout(new BoxLayout(facts, BoxLayout.Y_AXIS));
        facts.add(CommerceTheme.heading(approved ? "店铺资料" : "申请资料", 17));
        facts.add(CommerceTheme.gap(20));
        fact(facts, "店铺名称", application.title());
        if (!approved) fact(facts, "申请编号", application.id());
        fact(facts, "主体名称", application.subjectName());
        fact(facts, "营业执照编号", application.licenseNumber());
        fact(facts, "经营规则", "已阅读并同意");
        if (pending) {
            JPanel columns = new JPanel(new GridLayout(1, 2, 22, 0));
            columns.setOpaque(false);
            columns.add(facts);
            JPanel progress = CommerceTheme.card(Color.WHITE, 28);
            progress.setLayout(new BoxLayout(progress, BoxLayout.Y_AXIS));
            progress.add(CommerceTheme.heading("申请进度", 17));
            progress.add(CommerceTheme.gap(24));
            progress.add(CommerceTheme.heading("✓  申请已提交", 14));
            progress.add(CommerceTheme.gap(26));
            progress.add(CommerceTheme.heading("●  等待平台审核", 14));
            progress.add(CommerceTheme.gap(10));
            progress.add(paragraph("管理员根据提交资料进行审核。"));
            progress.add(CommerceTheme.gap(26));
            progress.add(CommerceTheme.muted("○  审核结果"));
            progress.add(CommerceTheme.gap(10));
            progress.add(paragraph("审核完成后可在这里查看结果。"));
            progress.add(CommerceTheme.gap(22));
            progress.add(paragraph("你可以继续逛店，稍后从“我的 → 申请开店”查看进度。"));
            columns.add(progress);
            body.add(columns);
        } else body.add(new ApplicationContainer(facts, 650));
        if (approved) {
            body.add(CommerceTheme.gap(16));
            body.add(paragraph("普通商品可直接经营。发布许可管控类目商品前，请先在“经营资质”中申请专项资质。"));
        }
        JPanel actions = CommerceTheme.row(CommerceTheme.button("返回我的", account),
                CommerceTheme.button("继续逛店", ui::home));
        actions.add(CommerceTheme.primary(CommerceTheme.button(approved ? "进入店铺管理 →"
                : rejected ? "修改后重新申请" : pending ? "刷新申请进度" : "查看申请进度 →", rejected ? edit : next)));
        ((FlowLayout) actions.getLayout()).setAlignment(pending ? FlowLayout.RIGHT : FlowLayout.CENTER);
        body.add(CommerceTheme.gap(25));
        body.add(actions);
        ui.display(title, CommerceTheme.scroll(new ApplicationContainer(body, 1020)), null, account);
    }

    static void field(JPanel panel, String title, JComponent value) {
        JPanel field = CommerceTheme.form();
        field.add(new JLabel(title));
        field.add(CommerceTheme.gap(8));
        value.setPreferredSize(new Dimension(400, value instanceof JScrollPane ? 140 : 44));
        value.setMaximumSize(new Dimension(Integer.MAX_VALUE, value instanceof JScrollPane ? 140 : 44));
        field.add(value);
        field.add(CommerceTheme.gap(18));
        panel.add(field);
    }

    static JComponent paragraph(String text) {
        JTextArea paragraph = new JTextArea(text);
        paragraph.setLineWrap(true);
        paragraph.setWrapStyleWord(true);
        paragraph.setEditable(false);
        paragraph.setOpaque(false);
        paragraph.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        paragraph.setForeground(new Color(0x7b8c78));
        paragraph.setColumns(35);
        paragraph.putClientProperty("commerce.styled", true);
        return paragraph;
    }

    private static void fact(JPanel facts, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(20, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xeef1eb)),
                BorderFactory.createEmptyBorder(13, 0, 13, 0)));
        JLabel title = CommerceTheme.muted(label);
        title.setPreferredSize(new Dimension(110, 20));
        row.add(title, BorderLayout.WEST);
        row.add(new JLabel(safe(value)), BorderLayout.CENTER);
        facts.add(row);
    }

    private static String safe(String value) { return value == null ? "—" : value; }
}
