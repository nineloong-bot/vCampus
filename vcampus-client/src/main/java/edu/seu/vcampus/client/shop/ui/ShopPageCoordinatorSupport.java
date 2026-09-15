package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.shop.ui.buyer.PaymentResultPanel;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.PaymentView;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Dialog helper and mutable payment receipt host for the shop page coordinator segments. */
abstract class ShopPageCoordinatorSupport extends ShopPageCoordinatorContracts {

    static ShopDialogs dialogs() {
        return new ShopDialogs() {
            @Override
            public void showError(String code) {
                JOptionPane.showMessageDialog(null, ShopUiErrors.message(code),
                        "校园商城", JOptionPane.ERROR_MESSAGE);
            }

            @Override
            public void confirm(String code, Runnable accepted) {
                if (JOptionPane.showConfirmDialog(null, ShopUiErrors.message(code), "校园商城",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                    accepted.run();
                }
            }
        };
    }

    /** Fixed card that accepts a new immutable receipt for each payment-result route. */
    static final class PaymentResultHost extends JPanel {
        private final ShopNavigator navigator;
        private final ShopUiKit uiKit;

        PaymentResultHost(ShopNavigator navigator, ShopUiKit uiKit) {
            super(new BorderLayout());
            this.navigator = navigator;
            this.uiKit = uiKit;
        }

        void load(PaymentView payment) {
            removeAll();
            add(new PaymentResultPanel(navigator, uiKit, payment), BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }
}
