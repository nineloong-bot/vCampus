package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.wallet.*;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.paging.PageResult;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Virtual balance, recharge and immutable account history. */
final class WalletPage {
    private final CommercePanel ui;
    private int page = 1;
    private Long balanceCents;

    WalletPage(CommercePanel ui) { this.ui = ui; }

    void open() {
        JPanel body = new WalletColumn();
        body.add(CommerceTheme.muted("平台虚拟货币"));
        body.add(CommerceTheme.gap(18));
        JPanel hero = CommerceTheme.card(new Color(0xeaf2e7), 24);
        hero.setLayout(new GridLayout(1, 2, 20, 0));
        JLabel balance = CommerceTheme.heading("加载中…", 36);
        balance.setName("wallet.balance");
        JLabel pending = CommerceTheme.heading("加载中…", 24);
        pending.setName("wallet.pending");
        hero.add(amountBlock("可用余额", balance, null));
        hero.add(amountBlock("待结算金额", pending, "买家确认收货后到账，暂不可消费"));
        body.add(hero);
        body.add(CommerceTheme.gap(18));
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(CommerceTheme.heading("资金明细", 18), BorderLayout.WEST);
        actions.add(CommerceTheme.primary(CommerceTheme.button("充值", this::recharge)), BorderLayout.EAST);
        body.add(actions);
        body.add(CommerceTheme.gap(12));
        JPanel ledger = new WalletColumn();
        ledger.setName("wallet.ledger");
        ledger.add(CommerceTheme.muted("资金明细加载中…"));
        body.add(ledger);
        JLabel counter = CommerceTheme.muted("第 " + page + " 页");
        JButton previous = CommerceTheme.button("上一页", () -> { page--; open(); });
        previous.setEnabled(page > 1);
        JButton next = CommerceTheme.button("下一页", () -> { page++; open(); });
        next.setEnabled(false);
        JPanel footer = new WalletColumn();
        footer.add(CommerceTheme.row(previous, counter, next));
        footer.add(CommerceTheme.row(CommerceTheme.button("返回我的", () -> new AccountPages(ui).open()),
                CommerceTheme.muted("仅用于校园虚拟交易")));
        ui.modal("我的钱包", body, footer, () -> new AccountPages(ui).open(), 760);
        ui.fetch("WALLET_GET_BALANCE", EmptyRequest.INSTANCE, data -> {
            WalletBalance value = (WalletBalance) data;
            balanceCents = value.balanceCents();
            balance.setText(CommerceTheme.money(value.balanceCents()));
            pending.setText(CommerceTheme.money(value.pendingCents()));
        }, () -> { balance.setText("加载失败"); pending.setText("暂不可用"); });
        ui.fetch("WALLET_GET_HISTORY", new WalletHistoryQuery(page, 15), data -> {
            PageResult<?> result = (PageResult<?>) data;
            ledger.removeAll();
            if (result.items().isEmpty()) {
                JPanel empty = CommerceTheme.card(Color.WHITE, 34);
                empty.add(CommerceTheme.muted("暂无资金记录，充值后开始体验。"));
                ledger.add(empty);
            } else for (Object item : result.items()) ledger.add(entry((WalletEntryView) item));
            counter.setText("第 " + page + " 页 · 共 " + result.total() + " 笔");
            next.setEnabled((long) page * 15 < result.total());
            ledger.revalidate();
            ledger.repaint();
        }, () -> {
            ledger.removeAll();
            ledger.add(CommerceTheme.button("资金明细加载失败，点击重试", this::open));
            ledger.revalidate();
            ledger.repaint();
        });
    }

    private JPanel amountBlock(String title, JLabel amount, String detail) {
        JPanel block = new WalletColumn();
        block.add(new JLabel(title));
        block.add(CommerceTheme.gap(14));
        block.add(amount);
        if (detail != null) {
            block.add(CommerceTheme.gap(10));
            block.add(CommerceTheme.muted(detail));
        }
        return block;
    }

    private JPanel entry(WalletEntryView value) {
        JPanel line = new JPanel(new BorderLayout(15, 0));
        line.setName("wallet.entry." + value.operationId());
        line.setOpaque(false);
        line.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xe1e7df)),
                BorderFactory.createEmptyBorder(16, 0, 16, 0)));
        JPanel description = new WalletColumn();
        description.add(CommerceTheme.heading(label(value.type()), 15));
        description.add(CommerceTheme.gap(8));
        description.add(CommerceTheme.muted(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault()).format(value.createdAt())));
        description.add(CommerceTheme.gap(6));
        description.add(CommerceTheme.muted("RECHARGE".equals(value.type()) ? "虚拟充值" : "订单 " + value.orderKey()));
        JPanel amounts = new WalletColumn();
        JLabel delta = CommerceTheme.heading((value.deltaCents() > 0 ? "+" : "−")
                + CommerceTheme.money(Math.abs(value.deltaCents())), 18);
        if (value.deltaCents() > 0) delta.setForeground(new Color(0x2f7250));
        amounts.add(delta);
        amounts.add(CommerceTheme.gap(12));
        amounts.add(CommerceTheme.muted("余额 " + CommerceTheme.money(value.balanceCents())));
        line.add(description, BorderLayout.CENTER);
        line.add(amounts, BorderLayout.EAST);
        return line;
    }

    private String label(String type) {
        return switch (type) {
            case "RECHARGE" -> "充值";
            case "PAYMENT" -> "购物扣款";
            case "REFUND" -> "退款到账";
            case "INCOME" -> "经营收入";
            default -> type;
        };
    }

    void recharge() {
        JPanel form = new WalletColumn();
        form.add(CommerceTheme.muted("平台虚拟货币 · 不涉及真实支付"));
        form.add(CommerceTheme.gap(18));
        JLabel balance = new JLabel("当前可用余额 " + (balanceCents == null ? "加载中…" : CommerceTheme.money(balanceCents)));
        form.add(balance);
        form.add(CommerceTheme.gap(20));
        JTextField amount = new JTextField("", 14);
        amount.setToolTipText("大于 0，单次最多 1,000");
        ApplicationPresentation.field(form, "充值金额", amount);
        JPanel presets = new JPanel(new GridLayout(1, 3, 12, 0));
        presets.setOpaque(false);
        for (int value : new int[]{100, 200, 500}) presets.add(CommerceTheme.button(String.valueOf(value),
                () -> amount.setText(String.valueOf(value))));
        form.add(presets);
        form.add(CommerceTheme.gap(18));
        form.add(CommerceTheme.muted("支持自定义金额，最多两位小数，单次最多 1,000。"));
        JLabel error = new JLabel(" ");
        error.setForeground(new Color(0xa13d2f));
        form.add(error);
        JButton submit = CommerceTheme.primary(new JButton("确认充值"));
        submit.addActionListener(e -> {
            try {
                BigDecimal value = new BigDecimal(amount.getText().strip());
                if (value.signum() <= 0 || value.compareTo(new BigDecimal("1000")) > 0 || value.scale() > 2)
                    throw new NumberFormatException();
                error.setText(" ");
                ui.write("WALLET_RECHARGE", new RechargeCommand(value), submit, data -> {
                    balanceCents = ((WalletOperationResult) data).balanceCents();
                    JPanel success = new WalletColumn();
                    success.add(CommerceTheme.muted("虚拟充值"));
                    success.add(CommerceTheme.gap(20));
                    success.add(CommerceTheme.heading("已到账 " + CommerceTheme.money(value.movePointRight(2).longValueExact()), 28));
                    success.add(CommerceTheme.gap(16));
                    success.add(new JLabel("可用余额 " + CommerceTheme.money(balanceCents)));
                    ui.modal("充值成功", success, CommerceTheme.row(CommerceTheme.primary(
                            CommerceTheme.button("返回继续操作", this::open))), this::open, 620);
                });
            } catch (NumberFormatException ex) { error.setText("请输入大于 0 且不超过 1,000 的金额，最多两位小数"); }
        });
        ui.modal("充值", form, CommerceTheme.row(CommerceTheme.button("返回", this::open), submit), this::open, 620);
        if (balanceCents == null) ui.fetch("WALLET_GET_BALANCE", EmptyRequest.INSTANCE, data -> {
            balanceCents = ((WalletBalance) data).balanceCents();
            balance.setText("当前可用余额 " + CommerceTheme.money(balanceCents));
        });
    }
}
