package edu.seu.vcampus.client.shop.commerce;

import org.junit.jupiter.api.Test;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import static org.assertj.core.api.Assertions.assertThat;

class ManagementFidelityTest {
    @Test void editorOffersEveryPresetAsAVisibleChoice() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CommercePanel ui = new CommercePanel((command, body, id) -> new CompletableFuture<>(), false);
            new ProductEditorPage(ui).open(null);
            List<JToggleButton> presets = descendants(ui).stream().filter(JToggleButton.class::isInstance)
                    .map(JToggleButton.class::cast).filter(b -> b.getName() != null && b.getName().startsWith("preset.")).toList();
            assertThat(presets).hasSize(PresetImages.IDS.length - 1);
            presets.getLast().doClick();
            assertThat(presets.stream().filter(AbstractButton::isSelected)).hasSize(1);
            assertThat(presets.getLast().isSelected()).isTrue();
        });
    }

    @Test void orderFiltersAreVisibleButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CommercePanel ui = new CommercePanel((command, body, id) -> new CompletableFuture<>(), false);
            new OrderPages(ui).list(false);
            assertThat(descendants(ui).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .map(AbstractButton::getText)).contains("全部", "待付款", "待发货", "待收货", "已完成", "退款 / 已关闭");
        });
    }

    @Test void cardsOnlyOfferBatchPaymentForPendingOrders() throws Exception {
        var pending = new CompletableFuture<java.io.Serializable>();
        var request = new java.util.concurrent.atomic.AtomicReference<java.io.Serializable>();
        CommercePanel[] panel = new CommercePanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new CommercePanel((command, body, id) -> {
                if (command.equals("SHOP2_ORDER_BUYER_LIST")) return pending;
                if (command.equals("SHOP2_ORDER_VALIDATE")) request.set(body);
                return new CompletableFuture<>();
            }, false);
            new OrderPages(panel[0]).list(false);
        });
        pending.complete(new edu.seu.vcampus.common.shop.order.OrderResult(List.of(
                order("pending", "PENDING_PAYMENT"), order("paid", "PAID")), ""));
        SwingUtilities.invokeAndWait(() -> {
            List<JCheckBox> choices = descendants(panel[0]).stream().filter(JCheckBox.class::isInstance)
                    .map(JCheckBox.class::cast).toList();
            assertThat(choices).hasSize(1);
            choices.getFirst().doClick();
            descendants(panel[0]).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> button.getText().equals("支付所选待付款订单")).findFirst().orElseThrow().doClick();
            assertThat(((edu.seu.vcampus.common.shop.order.OrderAction) request.get()).orderIds()).containsExactly("pending");
        });
    }

    private static edu.seu.vcampus.common.shop.order.OrderView order(String id, String state) {
        return new edu.seu.vcampus.common.shop.order.OrderView(id, "group", "shop", "校园书店", "同学", state,
                java.math.BigDecimal.TEN, java.time.Instant.now(), null, null, List.of());
    }

    private static List<Component> descendants(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            result.add(child);
            if (child instanceof Container nested) result.addAll(descendants(nested));
        }
        return result;
    }
}
