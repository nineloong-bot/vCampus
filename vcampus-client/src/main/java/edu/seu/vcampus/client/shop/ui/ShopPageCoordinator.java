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
public final class ShopPageCoordinator implements ShopRouteHost {
    static final String HOME = "shop.home";
    static final String SEARCH = "shop.search";
    static final String PRODUCT = "shop.product";
    static final String STOREFRONT = "shop.storefront";
    static final String CART = "shop.cart";
    static final String CHECKOUT = "shop.checkout";
    static final String PAYMENT_RESULT = "shop.payment-result";

    private final PageNavigator pages;
    private final ShopNavigator navigator = new ShopNavigator(this);
    private final ShopHomePanel home;
    private final ProductSearchPanel search;
    private final ProductDetailPanel product;
    private final BuyerShopPanel storefront;
    private final CartPanel cart;
    private final CheckoutPanel checkout;
    private final PaymentResultHost paymentResult;
    private boolean disposed;

    /** Creates and registers every stable Shop page. This must run on the EDT. */
    public ShopPageCoordinator(PageNavigator pages, ShopClientPort client, ShopUiKit uiKit,
            Runnable sessionExpired) {
        requireEdt();
        this.pages = Objects.requireNonNull(pages, "pages");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(uiKit, "uiKit");
        Objects.requireNonNull(sessionExpired, "sessionExpired");
        home = new ShopHomePanel(client, navigator, uiKit, sessionExpired);
        search = new ProductSearchPanel(client, navigator, uiKit, sessionExpired);
        product = new ProductDetailPanel(client, navigator, uiKit, sessionExpired);
        storefront = new BuyerShopPanel(client, navigator, uiKit, sessionExpired);
        cart = new CartPanel(client, navigator, uiKit, sessionExpired);
        checkout = new CheckoutPanel(client, navigator, uiKit, dialogs(), sessionExpired);
        paymentResult = new PaymentResultHost(navigator, uiKit);
        register(HOME, home);
        register(SEARCH, search);
        register(PRODUCT, product);
        register(STOREFRONT, storefront);
        register(CART, cart);
        register(CHECKOUT, checkout);
        register(PAYMENT_RESULT, paymentResult);
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
                home.load(query);
                yield HOME;
            }
            case ShopRoute.Search(var query) -> {
                search.search(query);
                yield SEARCH;
            }
            case ShopRoute.Product(var productId) -> {
                product.load(productId);
                yield PRODUCT;
            }
            case ShopRoute.Storefront(var shopId) -> {
                storefront.load(shopId);
                yield STOREFRONT;
            }
            case ShopRoute.Cart ignored -> {
                cart.load();
                yield CART;
            }
            case ShopRoute.Checkout ignored -> {
                checkout.load();
                yield CHECKOUT;
            }
            case ShopRoute.PaymentResult(var payment) -> {
                paymentResult.load(payment);
                yield PAYMENT_RESULT;
            }
        };
        pages.show(pageId);
    }

    /** Invalidates every page lifecycle and any active cashier. This operation is idempotent. */
    public void dispose() {
        requireEdt();
        if (disposed) {
            return;
        }
        disposed = true;
        home.dispose();
        search.dispose();
        product.dispose();
        storefront.dispose();
        cart.disposePage();
        checkout.disposePage();
    }

    private void register(String pageId, JPanel page) {
        page.setName(pageId);
        pages.register(pageId, page);
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
