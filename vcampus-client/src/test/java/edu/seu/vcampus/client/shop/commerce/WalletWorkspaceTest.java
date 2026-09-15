package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.wallet.WalletBalance;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class WalletWorkspaceTest {
    @Test
    void walletHistoryIsAQueryPageAndRechargeIsAnOnDemandEditor() throws Exception {
        CommercePanel[] holder = new CommercePanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CommercePanel((command, body, key) -> switch (command) {
                case "WALLET_GET_BALANCE" -> CompletableFuture.completedFuture(new WalletBalance(1000, 0, 1));
                case "WALLET_GET_HISTORY" -> CompletableFuture.completedFuture(new PageResult<>(List.of(), 1, 15, 0));
                default -> new CompletableFuture<>();
            }, false);
            new WalletPage(holder[0]).open();
        });
        SwingUtilities.invokeAndWait(() -> {
            assertThat(find(holder[0], JSplitPane.class)).isNull();
            button(holder[0], "充值").doClick();
            assertThat(find(holder[0], JSplitPane.class)).isNotNull();
        });
    }

    private static JButton button(Component root, String text) {
        if (root instanceof JButton button && text.equals(button.getText())) return button;
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            JButton found = button(child, text); if (found != null) return found;
        }
        return null;
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            T found = find(child, type); if (found != null) return found;
        }
        return null;
    }
}
