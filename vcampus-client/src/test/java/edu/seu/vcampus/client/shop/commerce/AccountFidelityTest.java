package edu.seu.vcampus.client.shop.commerce;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import edu.seu.vcampus.common.wallet.WalletOperationResult;
import edu.seu.vcampus.common.wallet.WalletBalance;
import edu.seu.vcampus.common.wallet.WalletEntryView;
import edu.seu.vcampus.common.paging.PageResult;
import java.time.Instant;
import java.util.List;
import edu.seu.vcampus.common.shop.governance.GovernanceDtos;
import static org.assertj.core.api.Assertions.assertThat;

class AccountFidelityTest {
    @Test void accountHasFourDescriptiveStackedDestinationsWhileLoading() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CommercePanel ui = new CommercePanel((c, b, k) -> new CompletableFuture<>(), false);
            new AccountPages(ui).open();
            Component entries = named(ui, "account.entries");
            assertThat(entries).isInstanceOf(JPanel.class);
            assertThat(((JPanel) entries).getComponentCount()).isEqualTo(4);
            assertThat(named(ui, "account.balance")).isInstanceOf(JLabel.class);
            assertThat(text(entries)).contains("申请开店", "提交主体资质", "查看订单", "我的举报", "我的钱包");
        });
    }

    @Test void walletHasSeparateBalanceAndSettlementAndLedger() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CommercePanel ui = new CommercePanel((c, b, k) -> new CompletableFuture<>(), false);
            new WalletPage(ui).open();
            assertThat(named(ui, "wallet.balance")).isInstanceOf(JLabel.class);
            assertThat(named(ui, "wallet.pending")).isInstanceOf(JLabel.class);
            assertThat(named(ui, "wallet.ledger")).isInstanceOf(JPanel.class);
            assertThat(text(ui)).contains("可用余额", "待结算金额", "买家确认收货后到账", "资金明细");
        });
    }

    @Test void invalidRechargeStaysInFormAndSuccessUsesServerBalance() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        CommercePanel[] holder = new CommercePanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CommercePanel((command, body, key) -> {
                if ("WALLET_RECHARGE".equals(command)) {
                    writes.incrementAndGet();
                    return CompletableFuture.completedFuture(new WalletOperationResult("receipt", 12345));
                }
                return new CompletableFuture<>();
            }, false);
            new WalletPage(holder[0]).recharge();
            JTextField amount = field(holder[0]);
            amount.setText("1000.01");
            button(holder[0], "确认充值").doClick();
            assertThat(writes.get()).isZero();
            assertThat(text(holder[0])).contains("请输入大于 0");
            amount.setText("100");
            button(holder[0], "确认充值").doClick();
        });
        SwingUtilities.invokeAndWait(() -> {
            assertThat(writes.get()).isEqualTo(1);
            assertThat(text(holder[0])).contains("充值成功", "已到账 ¥100.00", "可用余额 ¥123.45");
        });
    }

    @Test void singleLedgerEntryFitsWithoutClippingAtDesktopHeight() throws Exception {
        CommercePanel[] holder = new CommercePanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CommercePanel((command, body, key) -> {
                if ("WALLET_GET_BALANCE".equals(command))
                    return CompletableFuture.completedFuture(new WalletBalance(10000, 0, 1));
                if ("WALLET_GET_HISTORY".equals(command))
                    return CompletableFuture.completedFuture(new PageResult<>(List.of(new WalletEntryView(
                            "one", "RECHARGE", 10000, 10000, null, Instant.EPOCH)), 1, 15, 1));
                return new CompletableFuture<>();
            }, false);
            holder[0].setSize(1240, 820);
            new WalletPage(holder[0]).open();
        });
        SwingUtilities.invokeAndWait(() -> {
            layout(holder[0]);
            layout(holder[0]);
            Component entry = named(holder[0], "wallet.entry.one");
            JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, entry);
            Rectangle bounds = SwingUtilities.convertRectangle(entry.getParent(), entry.getBounds(), viewport.getView());
            assertThat(bounds.y + bounds.height).isLessThanOrEqualTo(viewport.getExtentSize().height);
        });
    }

    private static void layout(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested);
    }

    @Test void applicationReceiptKeepsTheSubmittedFormSnapshot() throws Exception {
        CommercePanel[] holder = new CommercePanel[1];
        CompletableFuture<java.io.Serializable> receipt = new CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CommercePanel((command, body, key) -> {
                if ("SHOP2_GOV_SELF".equals(command))
                    return CompletableFuture.completedFuture(new GovernanceDtos.Views(List.of()));
                if ("SHOP2_GOV_APPLY".equals(command)) return receipt;
                return new CompletableFuture<>();
            }, false);
            new AccountPages(holder[0]).open();
        });
        SwingUtilities.invokeAndWait(() -> {
            ((JButton) ((JPanel) named(holder[0], "account.entries")).getComponent(0)).doClick();
            var fields = new java.util.ArrayList<JTextField>();
            fields(holder[0], fields);
            fields.get(0).setText("青禾小店");
            fields.get(1).setText("校园主体");
            fields.get(2).setText("LICENSE-001");
            ((JCheckBox) named(holder[0], "application.agreement")).setSelected(true);
            button(holder[0], "提交申请").doClick();
            fields.get(0).setText("点击提交后修改的文字");
            receipt.complete(new GovernanceDtos.View("APP-001", "RECEIPT", "APP-001",
                    "COMPLETED", "", "", "", "", "", null));
        });
        SwingUtilities.invokeAndWait(() -> assertThat(text(holder[0]))
                .contains("开店申请已提交", "APP-001", "青禾小店", "校园主体", "LICENSE-001")
                .doesNotContain("点击提交后修改的文字"));
    }

    private static void fields(Component c, List<JTextField> result) {
        if (c instanceof JTextField field) result.add(field);
        if (c instanceof Container box) for (Component child : box.getComponents()) fields(child, result);
    }

    private static JTextField field(Component c) {
        if (c instanceof JTextField field) return field;
        if (c instanceof Container box) for (Component child : box.getComponents()) {
            JTextField found = field(child);
            if (found != null) return found;
        }
        return null;
    }

    private static JButton button(Component c, String text) {
        if (c instanceof JButton button && text.equals(button.getText())) return button;
        if (c instanceof Container box) for (Component child : box.getComponents()) {
            JButton found = button(child, text);
            if (found != null) return found;
        }
        return null;
    }

    private static Component named(Component c, String name) {
        if (name.equals(c.getName())) return c;
        if (c instanceof Container box) for (Component child : box.getComponents()) {
            Component found = named(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static String text(Component c) {
        StringBuilder result = new StringBuilder();
        if (c instanceof JLabel l) result.append(l.getText());
        if (c instanceof AbstractButton b) result.append(b.getText());
        if (c instanceof Container box) for (Component child : box.getComponents()) result.append(text(child));
        return result.toString();
    }
}
