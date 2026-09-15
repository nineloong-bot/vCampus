package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.wallet.WalletBalance;
import edu.seu.vcampus.common.wallet.WalletEntryView;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class WalletFineDisplayTest {
    @Test void libraryPaymentsHaveTheirOwnLabelAndLoanReference() throws Exception {
        var holder = new CommercePanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CommercePanel((command, body, key) -> {
                if ("WALLET_GET_BALANCE".equals(command))
                    return CompletableFuture.completedFuture(new WalletBalance(8950, 0, 1));
                return CompletableFuture.completedFuture(new PageResult<>(List.of(
                        new WalletEntryView("receipt", "LIBRARY_FINE", -1050, 8950, "loan-1", Instant.now())), 1, 20, 1));
            }, false);
            new WalletPage(holder[0]).open();
        });
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(text(holder[0])).contains("图书罚款", "图书借阅 loan-1").doesNotContain("订单 loan-1");
    }

    private static String text(Container root) {
        StringBuilder value = new StringBuilder();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) value.append(label.getText());
            if (child instanceof Container c) value.append(text(c));
        }
        return value.toString();
    }
}
