package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.admin.ShopAdminPanel;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.buyer.BuyerShopPanel;
import edu.seu.vcampus.client.shop.ui.buyer.CartPanel;
import edu.seu.vcampus.client.shop.ui.buyer.CheckoutPanel;
import edu.seu.vcampus.client.shop.ui.buyer.MyShopPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ProductDetailPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ProductSearchPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ShopHomePanel;
import edu.seu.vcampus.client.shop.ui.navigation.HomeViewState;
import edu.seu.vcampus.client.shop.ui.navigation.SearchViewState;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.navigation.StorefrontViewState;
import edu.seu.vcampus.client.shop.ui.seller.SellerApplicationPanel;
import edu.seu.vcampus.client.shop.ui.seller.SellerWorkspacePanel;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.concurrent.CompletableFuture;

/** The concrete buyer page set backing every fixed shop route. */
abstract class ShopPageCoordinatorBuyerPageSet extends ShopPageCoordinatorSupport {

    /** The concrete buyer page set backing every fixed shop route. */
    static final class BuyerPageSet implements PageSet {
        private final ShopHomePanel home;
        private final ProductSearchPanel search;
        private final ProductDetailPanel product;
        private final BuyerShopPanel storefront;
        private final CartPanel cart;
        private final CheckoutPanel checkout;
        private final PaymentResultHost paymentResult;
        private final MyShopPanel my;
        private final SellerApplicationPanel sellerApplication;
        private final SellerWorkspacePanel sellerWorkspace;
        private final ShopAdminPanel adminWorkspace;
        private final CartCountModel cartCount;
        private final ShopClientPort client;
        private final Runnable cartSessionExpired;
        private final LatestRequest cartSync = new LatestRequest();
        private boolean cartSyncInFlight;
        private boolean cartCountSynchronized;

        BuyerPageSet(UserView user, ShopClientPort client, ShopNavigator navigator,
                CartCountModel cartCount,
                ShopUiKit uiKit,
                Runnable homeSessionExpired, Runnable searchSessionExpired,
                Runnable productSessionExpired, Runnable storefrontSessionExpired,
                Runnable cartSessionExpired, Runnable checkoutSessionExpired,
                Runnable mySessionExpired,
                CheckoutPageFactory checkoutFactory, CallbackObserver callbackObserver) {
            this.cartCount = cartCount;
            this.client = client;
            this.cartSessionExpired = cartSessionExpired;
            callbackObserver.passedTo("home", homeSessionExpired);
            home = new ShopHomePanel(client, navigator, uiKit, homeSessionExpired);
            callbackObserver.passedTo("search", searchSessionExpired);
            search = new ProductSearchPanel(client, navigator, uiKit, searchSessionExpired);
            callbackObserver.passedTo("product", productSessionExpired);
            product = new ProductDetailPanel(client, navigator, uiKit, cartCount, productSessionExpired);
            callbackObserver.passedTo("storefront", storefrontSessionExpired);
            storefront = new BuyerShopPanel(client, navigator, uiKit, storefrontSessionExpired);
            callbackObserver.passedTo("cart", cartSessionExpired);
            cart = new CartPanel(client, navigator, uiKit, cartCount, cartSessionExpired);
            callbackObserver.passedTo("checkout", checkoutSessionExpired);
            checkout = checkoutFactory.create(client, navigator, uiKit, checkoutSessionExpired);
            checkout.setCartCountModel(cartCount);
            paymentResult = new PaymentResultHost(navigator, uiKit);
            callbackObserver.passedTo("my", mySessionExpired);
            SellerShopClientPort sellerPort = client instanceof SellerShopClientPort value
                    ? value : null;
            AdminShopClientPort adminPort = client instanceof AdminShopClientPort value
                    ? value : null;
            my = new MyShopPanel(user, client, sellerPort, navigator, uiKit, mySessionExpired);
            sellerApplication = sellerPort == null ? null
                    : SellerApplicationPanel.withApprovalNavigation(sellerPort, uiKit, mySessionExpired,
                            ignored -> navigator.open(new ShopRoute.SellerWorkspace()));
            sellerWorkspace = sellerPort == null ? null
                    : new SellerWorkspacePanel(sellerPort, uiKit, mySessionExpired);
            adminWorkspace = adminPort == null ? null
                    : new ShopAdminPanel(adminPort, uiKit, mySessionExpired);
        }

        @Override public JPanel home() { return home; }
        @Override public JPanel search() { return search; }
        @Override public JPanel product() { return product; }
        @Override public JPanel storefront() { return storefront; }
        @Override public JPanel cart() { return cart; }
        @Override public JPanel checkout() { return checkout; }
        @Override public JPanel paymentResult() { return paymentResult; }
        @Override public JPanel my() { return my; }
        @Override public JPanel sellerApplication() {
            return sellerApplication == null ? unavailable("当前客户端不支持开店申请") : sellerApplication;
        }
        @Override public JPanel sellerWorkspace() {
            return sellerWorkspace == null ? unavailable("当前账号无卖家工作区") : sellerWorkspace;
        }
        @Override public JPanel adminWorkspace() {
            return adminWorkspace == null ? unavailable("当前账号无商城管理权限") : adminWorkspace;
        }
        @Override public void loadHome(HomeViewState state) { home.load(state); }
        @Override public void search(SearchViewState state) { search.search(state); }
        @Override public void loadProduct(String productId) { product.load(productId); }
        @Override public void loadStorefront(StorefrontViewState state) { storefront.load(state); }
        @Override public void loadCart() { cart.load(); }
        @Override public void loadCheckout() { checkout.load(); }
        @Override public void loadPaymentResult(PaymentView payment) {
            paymentResult.load(payment);
        }
        @Override public void loadMy() { my.load(); }
        @Override public void loadSellerApplication() {
            if (sellerApplication != null) sellerApplication.load();
        }
        @Override public void requestSellerApplicationLeave(Runnable proceed) {
            if (sellerApplication == null) proceed.run();
            else sellerApplication.requestLeave(proceed);
        }
        @Override public void loadSellerWorkspace() {
            if (sellerWorkspace != null) sellerWorkspace.load();
        }
        @Override public void loadAdminWorkspace() {
            if (adminWorkspace != null) adminWorkspace.load();
        }
        @Override public void syncCartCount() {
            if (cartCountSynchronized || cartSyncInFlight) return;
            cartSyncInFlight = true;
            long request = cartSync.begin();
            long updateRevision = cartCount.beginUpdate();
            CompletableFuture<CartView> response = client.getCart();
            if (response != null) {
                response.whenComplete((result, failure) -> finishCartSync(
                        request, updateRevision, result, failure));
            } else {
                cartSyncInFlight = false;
                cartCount.cancel(updateRevision);
            }
        }
        @Override public HomeViewState captureHome(HomeViewState state) { return home.capture(state); }
        @Override public SearchViewState captureSearch(SearchViewState state) { return search.capture(state); }
        @Override public StorefrontViewState captureStorefront(StorefrontViewState state) {
            return storefront.capture(state);
        }
        @Override public void dispose() {
            cartSync.dispose();
            cartSyncInFlight = false;
            home.dispose();
            search.dispose();
            product.dispose();
            storefront.dispose();
            cart.disposePage();
            checkout.disposePage();
            my.disposePage();
            if (sellerApplication != null) sellerApplication.disposePage();
            if (sellerWorkspace != null) sellerWorkspace.disposePage();
            if (adminWorkspace != null) adminWorkspace.disposePage();
        }

        private static JPanel unavailable(String message) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel(message), BorderLayout.CENTER);
            return panel;
        }

        private void finishCartSync(long request, long updateRevision, CartView result,
                Throwable failure) {
            SwingUtilities.invokeLater(() -> {
                if (!cartSync.accepts(request)) return;
                cartSyncInFlight = false;
                if (failure == null) {
                    cartCountSynchronized = true;
                    cartCount.update(updateRevision, result);
                } else if (ShopUiErrors.sessionExpired(ShopUiErrors.code(failure))) {
                    cartSessionExpired.run();
                }
            });
        }
    }
}
