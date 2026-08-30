package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.buyer.BuyerShopPanel;
import edu.seu.vcampus.client.shop.ui.buyer.CartPanel;
import edu.seu.vcampus.client.shop.ui.buyer.CheckoutPanel;
import edu.seu.vcampus.client.shop.ui.buyer.PaymentResultPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ProductDetailPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ProductSearchPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ShopHomePanel;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRouteHost;
import edu.seu.vcampus.client.shop.ui.navigation.HomeViewState;
import edu.seu.vcampus.client.shop.ui.navigation.SearchViewState;
import edu.seu.vcampus.client.shop.ui.navigation.StorefrontViewState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.PaymentStatus;
import edu.seu.vcampus.common.shop.ProductSortMode;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.Objects;

/** Installs fixed buyer pages and renders routes through the Shop-owned card navigator. */
public final class ShopPageCoordinator implements ShopRouteHost, ShopUiInstaller.InstalledCoordinator {
    static final String HOME = "shop.home";
    static final String SEARCH = "shop.search";
    static final String PRODUCT = "shop.product";
    static final String STOREFRONT = "shop.storefront";
    static final String CART = "shop.cart";
    static final String CHECKOUT = "shop.checkout";
    static final String PAYMENT_RESULT = "shop.payment-result";
    static final String MY = "shop.my";

    private final CardNavigator cards;
    private final PageSet pages;
    private final ShopNavigator navigator;
    private final CartCountModel cartCount = new CartCountModel();
    private boolean disposed;

    /** Creates and registers every stable Shop page. This must run on the EDT. */
    public ShopPageCoordinator(ShopModulePanel pages, ShopClientPort client, ShopUiKit uiKit,
            Runnable sessionExpired) {
        this((CardNavigator) pages, new BuyerPageFactory(client), uiKit, sessionExpired);
    }

    ShopPageCoordinator(ShopModulePanel pages, PageFactory factory, ShopUiKit uiKit,
            Runnable sessionExpired) {
        this((CardNavigator) pages, factory, uiKit, sessionExpired);
    }

    ShopPageCoordinator(CardNavigator cards, PageFactory factory, ShopUiKit uiKit,
            Runnable sessionExpired) {
        requireEdt();
        this.cards = Objects.requireNonNull(cards, "cards");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(uiKit, "uiKit");
        Objects.requireNonNull(sessionExpired, "sessionExpired");
        navigator = new ShopNavigator(this);
        cards.installToolbar(new ShopToolbar(navigator, cartCount, uiKit));
        factory.setCartCountModel(cartCount);
        this.pages = factory.create(navigator, uiKit, sessionExpired, sessionExpired, sessionExpired,
                sessionExpired, sessionExpired, sessionExpired);
        register(HOME, this.pages.home());
        register(SEARCH, this.pages.search());
        register(PRODUCT, this.pages.product());
        register(STOREFRONT, this.pages.storefront());
        register(CART, this.pages.cart());
        register(CHECKOUT, this.pages.checkout());
        register(PAYMENT_RESULT, this.pages.paymentResult());
        JPanel my = new JPanel(new BorderLayout());
        my.add(new JLabel("我的商城即将开放"), BorderLayout.CENTER);
        register(MY, my);
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
        ShopRoute current = navigator.current().orElse(null);
        if (current == null) {
            navigator.open(new ShopRoute.Home(defaultHome()));
            return;
        }
        cards.show(pageId(current));
    }

    /** Loads the target fixed page before displaying its card. This must run on the EDT. */
    @Override
    public void render(ShopRoute route) {
        requireEdt();
        if (disposed) {
            return;
        }
        ShopRoute requested = Objects.requireNonNull(route, "route");
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
            case ShopRoute.My ignored -> { }
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

    interface CardNavigator {
        void register(String pageId, JPanel page);
        void show(String pageId);
        default void installToolbar(ShopToolbar toolbar) { }
    }

    @FunctionalInterface
    interface PageFactory {
        PageSet create(ShopNavigator navigator, ShopUiKit uiKit, Runnable homeSessionExpired,
                Runnable searchSessionExpired, Runnable productSessionExpired,
                Runnable storefrontSessionExpired, Runnable cartSessionExpired,
                Runnable checkoutSessionExpired);
        default void setCartCountModel(CartCountModel cartCount) { }
    }

    interface PageSet {
        JPanel home();
        JPanel search();
        JPanel product();
        JPanel storefront();
        JPanel cart();
        JPanel checkout();
        JPanel paymentResult();
        void loadHome(HomeViewState state);
        void search(SearchViewState state);
        void loadProduct(String productId);
        void loadStorefront(StorefrontViewState state);
        void loadCart();
        void loadCheckout();
        void loadPaymentResult(PaymentView payment);
        HomeViewState captureHome(HomeViewState state);
        SearchViewState captureSearch(SearchViewState state);
        StorefrontViewState captureStorefront(StorefrontViewState state);
        void dispose();
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
        };
    }

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
        public PageSet create(ShopNavigator navigator, ShopUiKit uiKit, Runnable homeSessionExpired,
                Runnable searchSessionExpired, Runnable productSessionExpired,
                Runnable storefrontSessionExpired, Runnable cartSessionExpired,
                Runnable checkoutSessionExpired) {
            return new BuyerPageSet(client, navigator, cartCount, uiKit, homeSessionExpired, searchSessionExpired,
                    productSessionExpired, storefrontSessionExpired, cartSessionExpired,
                    checkoutSessionExpired, checkoutFactory, callbackObserver);
        }

        @Override
        public void setCartCountModel(CartCountModel cartCount) {
            this.cartCount = Objects.requireNonNull(cartCount, "cartCount");
        }
    }

    @FunctionalInterface
    interface CheckoutPageFactory {
        CheckoutPanel create(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
                Runnable sessionExpired);
    }

    @FunctionalInterface
    interface CallbackObserver {
        void passedTo(String page, Runnable callback);
    }

    static final class BuyerPageSet implements PageSet {
        private final ShopHomePanel home;
        private final ProductSearchPanel search;
        private final ProductDetailPanel product;
        private final BuyerShopPanel storefront;
        private final CartPanel cart;
        private final CheckoutPanel checkout;
        private final PaymentResultHost paymentResult;
        private final CartCountModel cartCount;

        private BuyerPageSet(ShopClientPort client, ShopNavigator navigator, CartCountModel cartCount,
                ShopUiKit uiKit,
                Runnable homeSessionExpired, Runnable searchSessionExpired,
                Runnable productSessionExpired, Runnable storefrontSessionExpired,
                Runnable cartSessionExpired, Runnable checkoutSessionExpired,
                CheckoutPageFactory checkoutFactory, CallbackObserver callbackObserver) {
            this.cartCount = cartCount;
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
        }

        @Override public JPanel home() { return home; }
        @Override public JPanel search() { return search; }
        @Override public JPanel product() { return product; }
        @Override public JPanel storefront() { return storefront; }
        @Override public JPanel cart() { return cart; }
        @Override public JPanel checkout() { return checkout; }
        @Override public JPanel paymentResult() { return paymentResult; }
        @Override public void loadHome(HomeViewState state) { home.load(state); }
        @Override public void search(SearchViewState state) { search.search(state); }
        @Override public void loadProduct(String productId) { product.load(productId); }
        @Override public void loadStorefront(StorefrontViewState state) { storefront.load(state); }
        @Override public void loadCart() { cart.load(); }
        @Override public void loadCheckout() { checkout.load(); }
        @Override public void loadPaymentResult(PaymentView payment) {
            if (payment.status() == PaymentStatus.SUCCEEDED) cartCount.clear();
            paymentResult.load(payment);
        }
        @Override public HomeViewState captureHome(HomeViewState state) { return home.capture(state); }
        @Override public SearchViewState captureSearch(SearchViewState state) { return search.capture(state); }
        @Override public StorefrontViewState captureStorefront(StorefrontViewState state) {
            return storefront.capture(state);
        }
        @Override public void dispose() {
            home.dispose();
            search.dispose();
            product.dispose();
            storefront.dispose();
            cart.disposePage();
            checkout.disposePage();
        }
    }

    private static ShopDialogs dialogs() {
        return new ShopDialogs() {
            @Override
            public void showError(String code) {
                JOptionPane.showMessageDialog(null, code, "校园商城", JOptionPane.ERROR_MESSAGE);
            }

            @Override
            public void confirm(String code, Runnable accepted) {
                if (JOptionPane.showConfirmDialog(null, code, "校园商城",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                    accepted.run();
                }
            }
        };
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Shop UI must be constructed and mutated on the EDT");
        }
    }

    /** Fixed card that accepts a new immutable receipt for each payment-result route. */
    private static final class PaymentResultHost extends JPanel {
        private final ShopNavigator navigator;
        private final ShopUiKit uiKit;

        private PaymentResultHost(ShopNavigator navigator, ShopUiKit uiKit) {
            super(new BorderLayout());
            this.navigator = navigator;
            this.uiKit = uiKit;
        }

        private void load(PaymentView payment) {
            removeAll();
            add(new PaymentResultPanel(navigator, uiKit, payment), BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }
}
