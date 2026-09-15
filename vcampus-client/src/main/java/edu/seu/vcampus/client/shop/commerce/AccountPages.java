package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.wallet.WalletBalance;
import javax.swing.*;
import java.awt.*;

/** Account shortcuts and the application lifecycle backed by authenticated services. */
final class AccountPages {
    private final CommercePanel ui;

    AccountPages(CommercePanel ui) { this.ui = ui; }

    void open() {
        JPanel body = CommerceTheme.form();
        JPanel hero = CommerceTheme.card(new Color(0xedf3eb), 18);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.add(new JLabel("账户余额"));
        hero.add(CommerceTheme.gap(10));
        JLabel balance = CommerceTheme.heading("加载中…", 28);
        balance.setName("account.balance");
        hero.add(balance);
        hero.add(CommerceTheme.gap(8));
        hero.add(CommerceTheme.muted("账户余额服务"));
        body.add(hero);
        body.add(CommerceTheme.gap(22));
        JPanel entries = new JPanel(new GridLayout(0, 1, 0, 12));
        entries.setOpaque(false);
        entries.setName("account.entries");
        entries.add(CommerceTheme.actionCard("申请开店", "提交主体资质，开启你的校园店铺", () -> apply(null)));
        entries.add(CommerceTheme.actionCard("查看订单", "查看购买记录与订单状态", () -> new OrderPages(ui).list(false)));
        entries.add(CommerceTheme.actionCard("我的举报", "查看处理进度与结果", () -> new GovernancePages(ui).cases(false)));
        entries.add(CommerceTheme.actionCard("我的钱包", "虚拟充值 · 资金明细 · 待结算金额", () -> new WalletPage(ui).open()));
        entries.getComponent(0).setEnabled(false);
        entries.getComponent(0).setBackground(new Color(0xe9efe9));
        body.add(entries);
        ui.modal("我的", body, null, ui::closeModal, 620);
        ui.fetch("WALLET_GET_BALANCE", EmptyRequest.INSTANCE,
                data -> balance.setText(CommerceTheme.money(((WalletBalance) data).balanceCents())),
                () -> balance.setText("余额加载失败，请重试"));
        ui.fetch("SHOP2_GOV_SELF", EmptyRequest.INSTANCE, data -> {
            Views own = (Views) data;
            View shop = find(own, "SHOP");
            View application = find(own, "APPLICATION");
            entries.remove(0);
            entries.add(shop != null
                    ? CommerceTheme.actionCard("我的店铺", "进入店铺管理 · " + shop.title(), () -> workspace(shop))
                    : CommerceTheme.actionCard("申请开店", application == null
                    ? "提交主体资质，开启你的校园店铺" : "查看申请进度", () -> {
                        if (application == null) apply(null); else application(application);
                    }), 0);
            entries.getComponent(0).setBackground(new Color(0xe9efe9));
            entries.revalidate();
            entries.repaint();
        }, () -> {
            entries.remove(0);
            entries.add(CommerceTheme.actionCard("开店资料加载失败", "点击重新加载账户资料", this::open), 0);
            entries.revalidate();
            entries.repaint();
        });
    }

    private void apply(View previous) {
        JPanel form = CommerceTheme.form();
        form.add(CommerceTheme.muted("填写主体资料，提交后由平台进行审核。"));
        form.add(CommerceTheme.gap(20));
        JTextField name = new JTextField(previous == null ? "" : previous.title());
        JTextField subject = new JTextField(previous == null ? "" : previous.subjectName());
        JTextField license = new JTextField(previous == null ? "" : previous.licenseNumber());
        ApplicationPresentation.field(form, "店铺名称", name);
        ApplicationPresentation.field(form, "主体名称", subject);
        ApplicationPresentation.field(form, "营业执照编号", license);
        JCheckBox agree = new JCheckBox("我已阅读并同意平台经营规则");
        agree.setName("application.agreement");
        agree.setOpaque(false);
        form.add(agree);
        form.add(CommerceTheme.gap(12));
        form.add(ApplicationPresentation.paragraph("经营规则摘要：店家对商品负责；普通类目完成主体核验后可经营，"
                + "许可类目须通过专项资质审核，禁售商品禁止发布。"));
        JButton submit = CommerceTheme.primary(new JButton("提交申请"));
        submit.addActionListener(e -> {
            Apply request = new Apply(name.getText(), subject.getText(), license.getText(), agree.isSelected());
            ui.write("SHOP2_GOV_APPLY", request, submit,
                    data -> new ApplicationPresentation(ui).submitted((View) data, request, this::open,
                            this::loadApplication, () -> apply(previous)));
        });
        ui.modal("申请开店", form, CommerceTheme.row(CommerceTheme.button("返回我的", this::open), submit), this::open, 620);
    }

    private void loadApplication() {
        ui.fetch("SHOP2_GOV_SELF", EmptyRequest.INSTANCE, data -> {
            Views own = (Views) data;
            View shop = find(own, "SHOP");
            if (shop != null) new ApplicationPresentation(ui).show(shop, "approved", this::open,
                    () -> workspace(shop), () -> apply(null));
            else {
                View application = find(own, "APPLICATION");
                if (application != null) application(application);
            }
        });
    }

    private void application(View v) {
        new ApplicationPresentation(ui).show(v, "REJECTED".equals(v.state()) ? "rejected" : "pending",
                this::open, this::loadApplication, () -> apply(v));
    }

    void loadWorkspace() { loadShop(false); }
    void loadSettings() { loadShop(true); }

    private void loadShop(boolean settings) {
        ui.fetch("SHOP2_GOV_SELF", EmptyRequest.INSTANCE, data -> {
            View shop = find((Views) data, "SHOP");
            if (shop == null) open(); else if (settings) settings(shop); else workspace(shop);
        });
    }

    private void workspace(View shop) { new AccountWorkspace(ui).open(shop, () -> settings(shop)); }

    private void settings(View shop) {
        JPanel form = CommerceTheme.card(Color.WHITE, 28);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(CommerceTheme.heading("店铺资料", 20));
        form.add(CommerceTheme.gap(18));
        form.add(new JLabel("店铺名称  ·  " + shop.title()));
        form.add(CommerceTheme.gap(18));
        JTextArea description = new JTextArea(shop.description(), 6, 40);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        ApplicationPresentation.field(form, "店铺介绍", new JScrollPane(description));
        JButton save = CommerceTheme.primary(new JButton("保存店铺资料"));
        save.addActionListener(e -> ui.write("SHOP2_GOV_SETTINGS", new Settings(description.getText()), save,
                data -> { loadSettings(); ui.notice("店铺介绍已更新"); }));
        form.add(CommerceTheme.row(save));
        ui.display("店铺设置", ManagementLayout.seller(ui, form, "店铺设置"), null, () -> workspace(shop));
    }

    private static View find(Views views, String kind) {
        return views.items().stream().filter(v -> kind.equals(v.kind())).findFirst().orElse(null);
    }
}
