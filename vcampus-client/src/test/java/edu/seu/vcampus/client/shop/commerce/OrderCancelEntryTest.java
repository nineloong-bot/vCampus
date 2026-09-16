package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.order.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class OrderCancelEntryTest {
    @Test
    void pendingBuyerOrdersHaveDirectCancellationButPaidOrdersAndSellerOrdersDoNot() throws Exception {
        check("PENDING_PAYMENT", false, true);
        check("PAID", false, false);
        check("PENDING_PAYMENT", true, false);
    }

    private void check(String state, boolean seller, boolean expected) throws Exception {
        CommercePanel[] ui = new CommercePanel[1];
        SwingUtilities.invokeAndWait(() -> {
            var order = new OrderView("order", "group", "shop", "校园店铺", "买家", state,
                    BigDecimal.TEN, Instant.now(), Instant.now().plusSeconds(1800), "", List.of());
            ui[0] = new CommercePanel((command, body, key) ->
                    CompletableFuture.completedFuture(new OrderResult(List.of(order), "")), seller);
            new OrderPages(ui[0]).list(seller);
        });
        SwingUtilities.invokeAndWait(() -> assertThat(hasCancel(ui[0])).isEqualTo(expected));
    }

    private boolean hasCancel(Component root) {
        if (root instanceof JButton b && "取消订单".equals(b.getText())) return true;
        if (root instanceof Container parent) for (Component child : parent.getComponents()) {
            if (hasCancel(child)) return true;
        }
        return false;
    }
}
