package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.buyer.CheckoutPanel;
import edu.seu.vcampus.client.shop.ui.navigation.HomeViewState;
import edu.seu.vcampus.client.shop.ui.navigation.SearchViewState;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRouteHost;
import edu.seu.vcampus.client.shop.ui.navigation.StorefrontViewState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Navigator and page-factory contracts shared by the shop page coordinator segments. */
abstract class ShopPageCoordinatorContracts implements ShopRouteHost, ShopUiInstaller.InstalledCoordinator {

    /** Registers and shows the fixed cards owned by the shop module panel. */
    interface CardNavigator {
        void register(String pageId, JPanel page);
        void show(String pageId);
        default void installToolbar(ShopToolbar toolbar) { }
    }

    /** Creates the page set backing every fixed shop route. */
    @FunctionalInterface
    interface PageFactory {
        PageSet create(UserView user, ShopNavigator navigator, ShopUiKit uiKit,
                Runnable homeSessionExpired,
                Runnable searchSessionExpired, Runnable productSessionExpired,
                Runnable storefrontSessionExpired, Runnable cartSessionExpired,
                Runnable checkoutSessionExpired, Runnable mySessionExpired);
        default void setCartCountModel(CartCountModel cartCount) { }
    }

    /** Exposes one panel, loader and route capture per fixed shop page. */
    interface PageSet {
        JPanel home();
        JPanel search();
        JPanel product();
        JPanel storefront();
        JPanel cart();
        JPanel checkout();
        JPanel paymentResult();
        JPanel my();
        default JPanel sellerApplication() { return new JPanel(); }
        default JPanel sellerWorkspace() { return new JPanel(); }
        default JPanel adminWorkspace() { return new JPanel(); }
        void loadHome(HomeViewState state);
        void search(SearchViewState state);
        void loadProduct(String productId);
        void loadStorefront(StorefrontViewState state);
        void loadCart();
        void loadCheckout();
        void loadPaymentResult(PaymentView payment);
        void loadMy();
        default void loadSellerApplication() { }
        default void requestSellerApplicationLeave(Runnable proceed) { proceed.run(); }
        default void loadSellerWorkspace() { }
        default void loadAdminWorkspace() { }
        default void syncCartCount() { }
        HomeViewState captureHome(HomeViewState state);
        SearchViewState captureSearch(SearchViewState state);
        StorefrontViewState captureStorefront(StorefrontViewState state);
        void dispose();
    }

    /** Creates the checkout panel used by the buyer page set. */
    @FunctionalInterface
    interface CheckoutPageFactory {
        CheckoutPanel create(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
                Runnable sessionExpired);
    }

    /** Observes which session-expiry callback each page receives. */
    @FunctionalInterface
    interface CallbackObserver {
        void passedTo(String page, Runnable callback);
    }

    static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Shop UI must be constructed and mutated on the EDT");
        }
    }
}
