package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.buyer.CheckoutPanel;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.user.UserView;

import java.util.Objects;

/** Buyer page factory wiring the concrete page set for the shop page coordinator segments. */
abstract class ShopPageCoordinatorBuyerFactory extends ShopPageCoordinatorBuyerPageSet {

    /** Builds the buyer page set and forwards the shared cart count model to it. */
    static final class BuyerPageFactory implements PageFactory {
        private final ShopClientPort client;
        private final CheckoutPageFactory checkoutFactory;
        private final CallbackObserver callbackObserver;
        private CartCountModel cartCount = new CartCountModel();

        BuyerPageFactory(ShopClientPort client) {
            this(client, (checkoutClient, navigator, uiKit, sessionExpired) -> new CheckoutPanel(
                    checkoutClient, navigator, uiKit, dialogs(), sessionExpired), (page, callback) -> { });
        }

        BuyerPageFactory(ShopClientPort client, CheckoutPageFactory checkoutFactory,
                CallbackObserver callbackObserver) {
            this.client = Objects.requireNonNull(client, "client");
            this.checkoutFactory = Objects.requireNonNull(checkoutFactory, "checkoutFactory");
            this.callbackObserver = Objects.requireNonNull(callbackObserver, "callbackObserver");
        }

        @Override
        public PageSet create(UserView user, ShopNavigator navigator, ShopUiKit uiKit,
                Runnable homeSessionExpired,
                Runnable searchSessionExpired, Runnable productSessionExpired,
                Runnable storefrontSessionExpired, Runnable cartSessionExpired,
                Runnable checkoutSessionExpired, Runnable mySessionExpired) {
            return new BuyerPageSet(user, client, navigator, cartCount, uiKit,
                    homeSessionExpired, searchSessionExpired,
                    productSessionExpired, storefrontSessionExpired, cartSessionExpired,
                    checkoutSessionExpired, mySessionExpired, checkoutFactory, callbackObserver);
        }

        @Override
        public void setCartCountModel(CartCountModel cartCount) {
            this.cartCount = Objects.requireNonNull(cartCount, "cartCount");
        }
    }
}
