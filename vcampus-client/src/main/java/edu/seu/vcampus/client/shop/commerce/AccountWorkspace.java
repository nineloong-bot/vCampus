package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.shop.governance.GovernanceDtos.View;
import edu.seu.vcampus.common.wallet.WalletBalance;
import javax.swing.*;
import java.awt.*;

/** Seller overview composed from the same cards and hierarchy as the approved workspace. */
final class AccountWorkspace {
    private final CommercePanel ui;

    AccountWorkspace(CommercePanel ui) { this.ui = ui; }

    void open(View shop, Runnable settings) {
        JPanel body = CommerceTheme.form();
        JPanel wallet = CommerceTheme.card(Color.WHITE, 24);
        wallet.setLayout(new BorderLayout(20, 0));
        JLabel funds = new JLabel("店铺资金加载中…");
        wallet.add(funds, BorderLayout.CENTER);
        wallet.add(CommerceTheme.button("查看资金明细", () -> new WalletPage(ui).open()), BorderLayout.EAST);
        body.add(wallet);
        body.add(CommerceTheme.gap(20));
        JPanel welcome = CommerceTheme.card(Color.WHITE, 28);
        welcome.setLayout(new BorderLayout(20, 0));
        JPanel introduction = CommerceTheme.form();
        introduction.add(CommerceTheme.muted("开始经营"));
        introduction.add(CommerceTheme.gap(12));
        introduction.add(CommerceTheme.heading("让第一件好物，遇见它的新主人", 24));
        introduction.add(CommerceTheme.gap(12));
        introduction.add(CommerceTheme.muted("单个创建商品，或通过 Excel 导入后完善草稿。"));
        welcome.add(introduction, BorderLayout.CENTER);
        welcome.add(CommerceTheme.primary(CommerceTheme.button("管理商品 →", () -> new SellerCatalogPage(ui).open())), BorderLayout.EAST);
        body.add(welcome);
        body.add(CommerceTheme.gap(20));
        JPanel columns = new JPanel(new GridLayout(1, 2, 22, 0));
        columns.setOpaque(false);
        JPanel tasks = CommerceTheme.card(Color.WHITE, 28);
        tasks.setLayout(new BoxLayout(tasks, BoxLayout.Y_AXIS));
        tasks.add(CommerceTheme.heading("待办事项", 20));
        tasks.add(CommerceTheme.gap(20));
        tasks.add(CommerceTheme.actionCard("管理店铺订单", "查看订单进度，安排履约", () -> new OrderPages(ui).list(true)));
        tasks.add(CommerceTheme.gap(12));
        tasks.add(CommerceTheme.actionCard("查看经营资质", "主体资质与许可类目申请", () -> new GovernancePages(ui).qualifications(false)));
        JPanel notices = CommerceTheme.card(Color.WHITE, 28);
        notices.setLayout(new BoxLayout(notices, BoxLayout.Y_AXIS));
        notices.add(CommerceTheme.heading("店铺通知", 20));
        notices.add(CommerceTheme.gap(20));
        notices.add(CommerceTheme.heading(shop.title(), 17));
        notices.add(CommerceTheme.gap(12));
        notices.add(CommerceTheme.muted("ACTIVE".equals(shop.state()) ? "主体资质已通过 · 正常营业" : "暂停营业"));
        if (shop.reason() != null && !shop.reason().isBlank()) {
            notices.add(CommerceTheme.gap(12));
            notices.add(ApplicationPresentation.paragraph(shop.reason()));
        }
        notices.add(CommerceTheme.gap(18));
        notices.add(CommerceTheme.button("查看警告与整改", () -> new GovernancePages(ui).notices()));
        notices.add(CommerceTheme.gap(12));
        notices.add(CommerceTheme.button("维护店铺资料", settings));
        if ("SUSPENDED".equals(shop.state())) notices.add(CommerceTheme.button("申请重新营业",
                () -> new GovernancePages(ui).requestReopen(shop.id())));
        columns.add(tasks);
        columns.add(notices);
        body.add(columns);
        ui.display("店铺概览", ManagementLayout.seller(ui, body, "店铺概览"), null, () -> new AccountPages(ui).open());
        ui.fetch("WALLET_GET_BALANCE", EmptyRequest.INSTANCE, data -> {
            WalletBalance value = (WalletBalance) data;
            funds.setText("可用余额 " + CommerceTheme.money(value.balanceCents())
                    + "  ·  待结算 " + CommerceTheme.money(value.pendingCents()));
        }, () -> funds.setText("资金信息暂不可用，请重试"));
    }
}
