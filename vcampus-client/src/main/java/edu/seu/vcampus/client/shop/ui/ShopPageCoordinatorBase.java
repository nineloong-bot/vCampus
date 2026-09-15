package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.JPanel;
import java.util.Objects;

/** Route rendering, page registration and lifecycle for the shop page coordinator segments. */
abstract class ShopPageCoordinatorBase extends ShopPageCoordinatorBuyerFactory {
    static final String HOME = "shop.home";
    static final String SEARCH = "shop.search";
    static final String PRODUCT = "shop.product";
    static final String STOREFRONT = "shop.storefront";
    static final String CART = "shop.cart";
    static final String CHECKOUT = "shop.checkout";
    static final String PAYMENT_RESULT = "shop.payment-result";
    static final String MY = "shop.my";
    static final String SELLER_APPLICATION = "shop.seller-application";
    static final String SELLER_WORKSPACE = "shop.seller-workspace";
    static final String ADMIN_WORKSPACE = "shop.admin-workspace";

    final CardNavigator cards;
    final PageSet pages;
    final ShopNavigator navigator;
    final CartCountModel cartCount = new CartCountModel();
    boolean disposed;

    ShopPageCoordinatorBase(CardNavigator cards, UserView user, PageFactory factory, ShopUiKit uiKit,
            Runnable sessionExpired) {
        requireEdt();
        this.cards = Objects.requireNonNull(cards, "cards");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(uiKit, "uiKit");
        Objects.requireNonNull(sessionExpired, "sessionExpired");
        navigator = new ShopNavigator(this);
        cards.installToolbar(new ShopToolbar(navigator, cartCount, uiKit, this::goHome));
        factory.setCartCountModel(cartCount);
        this.pages = factory.create(user, navigator, uiKit, sessionExpired, sessionExpired,
                sessionExpired, sessionExpired, sessionExpired, sessionExpired, sessionExpired);
        register(HOME, this.pages.home());
        register(SEARCH, this.pages.search());
        register(PRODUCT, this.pages.product());
        register(STOREFRONT, this.pages.storefront());
        register(CART, this.pages.cart());
        register(CHECKOUT, this.pages.checkout());
        register(PAYMENT_RESULT, this.pages.paymentResult());
        register(MY, this.pages.my());
        register(SELLER_APPLICATION, this.pages.sellerApplication());
        register(SELLER_WORKSPACE, this.pages.sellerWorkspace());
        register(ADMIN_WORKSPACE, this.pages.adminWorkspace());
    }

    /** Returns the sole Shop history owner used by page actions and the sidebar entry. */
    public ShopNavigator navigator() {
        return navigator;
    }

    /** Enters the module without changing route history after its initial home route. */
    public void enter() {
        requireEdt();
        if (disposed) {
            return;
        }
        pages.syncCartCount();
        ShopRoute current = navigator.current().orElse(null);
        if (current == null) {
            navigator.open(new ShopRoute.Home(defaultHome()));
            return;
        }
        cards.show(pageId(current));
    }

    /** Returns to the canonical first Shop home through the active leave guard. */
    @Override
    public void goHome() {
        requireEdt();
        if (disposed) {
            return;
        }
        pages.syncCartCount();
        navigator.resetToDefaultHome();
    }

    /** Loads the target fixed page before displaying its card. This must run on the EDT. */
    @Override
    public void render(ShopRoute route) {
        requireEdt();
        if (disposed) {
            return;
        }
        ShopRoute requested = Objects.requireNonNull(route, "route");
        navigator.setLeaveGuard(requested instanceof ShopRoute.SellerApplication
                ? pages::requestSellerApplicationLeave
                : edu.seu.vcampus.client.shop.ui.navigation.ShopLeaveGuard.immediate());
        switch (requested) {
            case ShopRoute.Home(var state) -> {
                pages.loadHome(state);
            }
            case ShopRoute.Search(var state) -> {
                pages.search(state);
            }
            case ShopRoute.Product(var productId) -> {
                pages.loadProduct(productId);
            }
            case ShopRoute.Storefront(var state) -> {
                pages.loadStorefront(state);
            }
            case ShopRoute.Cart ignored -> {
                pages.loadCart();
            }
            case ShopRoute.Checkout ignored -> {
                pages.loadCheckout();
            }
            case ShopRoute.PaymentResult(var payment) -> {
                pages.loadPaymentResult(payment);
            }
            case ShopRoute.My ignored -> {
                pages.loadMy();
            }
            case ShopRoute.SellerApplication ignored -> pages.loadSellerApplication();
            case ShopRoute.SellerWorkspace ignored -> pages.loadSellerWorkspace();
            case ShopRoute.AdminWorkspace ignored -> pages.loadAdminWorkspace();
        }
        cards.show(pageId(requested));
    }

    @Override
    public ShopRoute capture(ShopRoute route) {
        requireEdt();
        return switch (Objects.requireNonNull(route, "route")) {
            case ShopRoute.Home(var state) -> new ShopRoute.Home(pages.captureHome(state));
            case ShopRoute.Search(var state) -> new ShopRoute.Search(pages.captureSearch(state));
            case ShopRoute.Storefront(var state) -> new ShopRoute.Storefront(
                    pages.captureStorefront(state));
            default -> route;
        };
    }

    /** Invalidates every page lifecycle and any active cashier. This operation is idempotent. */
    public void dispose() {
        requireEdt();
        if (disposed) {
            return;
        }
        disposed = true;
        pages.dispose();
    }

    private void register(String pageId, JPanel page) {
        page.setName(pageId);
        cards.register(pageId, page);
    }

    private static HomeProductQuery defaultHome() {
        return new HomeProductQuery(null, null, ProductSortMode.SALES_DESC, 0, 20);
    }

    private static String pageId(ShopRoute route) {
        return switch (route) {
            case ShopRoute.Home ignored -> HOME;
            case ShopRoute.Search ignored -> SEARCH;
            case ShopRoute.Product ignored -> PRODUCT;
            case ShopRoute.Storefront ignored -> STOREFRONT;
            case ShopRoute.Cart ignored -> CART;
            case ShopRoute.Checkout ignored -> CHECKOUT;
            case ShopRoute.PaymentResult ignored -> PAYMENT_RESULT;
            case ShopRoute.My ignored -> MY;
            case ShopRoute.SellerApplication ignored -> SELLER_APPLICATION;
            case ShopRoute.SellerWorkspace ignored -> SELLER_WORKSPACE;
            case ShopRoute.AdminWorkspace ignored -> ADMIN_WORKSPACE;
        };
    }
}
