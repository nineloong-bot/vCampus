package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
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
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.PaymentView;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.Objects;

/** Installs fixed buyer pages and renders routes through the shared card navigator. */
public final class ShopPageCoordinator implements ShopRouteHost, ShopUiInstaller.InstalledCoordinator {
    static final String HOME = "shop.home";
    static final String SEARCH = "shop.search";
    static final String PRODUCT = "shop.product";
    static final String STOREFRONT = "shop.storefront";
    static final String CART = "shop.cart";
    static final String CHECKOUT = "shop.checkout";
    static final String PAYMENT_RESULT = "shop.payment-result";

    private final CardNavigator cards;
    private final PageSet pages;
    private final ShopNavigator navigator;
    private boolean disposed;

    /** Creates and registers every stable Shop page. This must run on the EDT. */
    public ShopPageCoordinator(PageNavigator pages, ShopClientPort client, ShopUiKit uiKit,
            Runnable sessionExpired) {
        this(new PageNavigatorCards(pages), new BuyerPageFactory(client), uiKit, sessionExpired);
    }

    ShopPageCoordinator(CardNavigator cards, PageFactory factory, ShopUiKit uiKit,
            Runnable sessionExpired) {
        requireEdt();
        this.cards = Objects.requireNonNull(cards, "cards");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(uiKit, "uiKit");
        Objects.requireNonNull(sessionExpired, "sessionExpired");
        navigator = new ShopNavigator(this);
        this.pages = factory.create(navigator, uiKit, sessionExpired, sessionExpired, sessionExpired,
                sessionExpired, sessionExpired, sessionExpired);
        register(HOME, this.pages.home());
        register(SEARCH, this.pages.search());
        register(PRODUCT, this.pages.product());
        register(STOREFRONT, this.pages.storefront());
        register(CART, this.pages.cart());
        register(CHECKOUT, this.pages.checkout());
        register(PAYMENT_RESULT, this.pages.paymentResult());
    }

    /** Returns the sole Shop history owner used by page actions and the sidebar entry. */
    public ShopNavigator navigator() {
        return navigator;
    }

    /** Loads the target fixed page before displaying its card. This must run on the EDT. */
    @Override
    public void render(ShopRoute route) {
        requireEdt();
        if (disposed) {
            return;
        }
        String pageId = switch (Objects.requireNonNull(route, "route")) {
            case ShopRoute.Home(var query) -> {
                pages.loadHome(query);
                yield HOME;
            }
            case ShopRoute.Search(var query) -> {
                pages.search(query);
                yield SEARCH;
            }
            case ShopRoute.Product(var productId) -> {
                pages.loadProduct(productId);
                yield PRODUCT;
            }
            case ShopRoute.Storefront(var shopId) -> {
                pages.loadStorefront(shopId);
                yield STOREFRONT;
            }
            case ShopRoute.Cart ignored -> {
                pages.loadCart();
                yield CART;
            }
            case ShopRoute.Checkout ignored -> {
                pages.loadCheckout();
                yield CHECKOUT;
            }
            case ShopRoute.PaymentResult(var payment) -> {
                pages.loadPaymentResult(payment);
                yield PAYMENT_RESULT;
            }
        };
        cards.show(pageId);
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
    }

    @FunctionalInterface
    interface PageFactory {
        PageSet create(ShopNavigator navigator, ShopUiKit uiKit, Runnable homeSessionExpired,
                Runnable searchSessionExpired, Runnable productSessionExpired,
                Runnable storefrontSessionExpired, Runnable cartSessionExpired,
                Runnable checkoutSessionExpired);
    }

    interface PageSet {
        JPanel home();
        JPanel search();
        JPanel product();
        JPanel storefront();
        JPanel cart();
        JPanel checkout();
        JPanel paymentResult();
        void loadHome(edu.seu.vcampus.common.shop.HomeProductQuery query);
        void search(edu.seu.vcampus.common.shop.ProductSearchQuery query);
        void loadProduct(String productId);
        void loadStorefront(String shopId);
        void loadCart();
        void loadCheckout();
        void loadPaymentResult(PaymentView payment);
        void dispose();
    }

    private static final class PageNavigatorCards implements CardNavigator {
        private final PageNavigator pages;

        private PageNavigatorCards(PageNavigator pages) {
            this.pages = Objects.requireNonNull(pages, "pages");
        }

        @Override public void register(String pageId, JPanel page) { pages.register(pageId, page); }
        @Override public void show(String pageId) { pages.show(pageId); }
    }

    private static final class BuyerPageFactory implements PageFactory {
        private final ShopClientPort client;

        private BuyerPageFactory(ShopClientPort client) {
            this.client = Objects.requireNonNull(client, "client");
        }

        @Override
        public PageSet create(ShopNavigator navigator, ShopUiKit uiKit, Runnable homeSessionExpired,
                Runnable searchSessionExpired, Runnable productSessionExpired,
                Runnable storefrontSessionExpired, Runnable cartSessionExpired,
                Runnable checkoutSessionExpired) {
            return new BuyerPageSet(client, navigator, uiKit, homeSessionExpired, searchSessionExpired,
                    productSessionExpired, storefrontSessionExpired, cartSessionExpired,
                    checkoutSessionExpired);
        }
    }

    private static final class BuyerPageSet implements PageSet {
        private final ShopHomePanel home;
        private final ProductSearchPanel search;
        private final ProductDetailPanel product;
        private final BuyerShopPanel storefront;
        private final CartPanel cart;
        private final CheckoutPanel checkout;
        private final PaymentResultHost paymentResult;

        private BuyerPageSet(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
                Runnable homeSessionExpired, Runnable searchSessionExpired,
                Runnable productSessionExpired, Runnable storefrontSessionExpired,
                Runnable cartSessionExpired, Runnable checkoutSessionExpired) {
            home = new ShopHomePanel(client, navigator, uiKit, homeSessionExpired);
            search = new ProductSearchPanel(client, navigator, uiKit, searchSessionExpired);
            product = new ProductDetailPanel(client, navigator, uiKit, productSessionExpired);
            storefront = new BuyerShopPanel(client, navigator, uiKit, storefrontSessionExpired);
            cart = new CartPanel(client, navigator, uiKit, cartSessionExpired);
            checkout = new CheckoutPanel(client, navigator, uiKit, dialogs(), checkoutSessionExpired);
            paymentResult = new PaymentResultHost(navigator, uiKit);
        }

        @Override public JPanel home() { return home; }
        @Override public JPanel search() { return search; }
        @Override public JPanel product() { return product; }
        @Override public JPanel storefront() { return storefront; }
        @Override public JPanel cart() { return cart; }
        @Override public JPanel checkout() { return checkout; }
        @Override public JPanel paymentResult() { return paymentResult; }
        @Override public void loadHome(edu.seu.vcampus.common.shop.HomeProductQuery query) { home.load(query); }
        @Override public void search(edu.seu.vcampus.common.shop.ProductSearchQuery query) { search.search(query); }
        @Override public void loadProduct(String productId) { product.load(productId); }
        @Override public void loadStorefront(String shopId) { storefront.load(shopId); }
        @Override public void loadCart() { cart.load(); }
        @Override public void loadCheckout() { checkout.load(); }
        @Override public void loadPaymentResult(PaymentView payment) { paymentResult.load(payment); }
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
