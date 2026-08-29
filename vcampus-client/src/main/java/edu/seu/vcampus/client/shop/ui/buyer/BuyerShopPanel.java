package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.ShopDetail;
import edu.seu.vcampus.common.shop.ShopProductQuery;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.Objects;

/** Storefront page showing shop information and its catalog. */
public final class BuyerShopPanel extends JPanel {
    private final ShopClientPort client;
    private final Runnable sessionExpired;
    private final ShopUiKit uiKit;
    private final LatestRequest latest = new LatestRequest();
    private final JLabel shopName = new JLabel();
    private final JLabel error = new JLabel();
    private final ProductCardsPanel cards;

    public BuyerShopPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.cards = new ProductCardsPanel(Objects.requireNonNull(navigator, "navigator"), uiKit);
        shopName.setName("shop-name");
        error.setName("error");
        add(shopName, BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);
        add(error, BorderLayout.SOUTH);
    }

    public void load(String shopId) {
        long request = latest.begin();
        client.getShop(Objects.requireNonNull(shopId, "shopId"))
                .whenComplete((shop, failure) -> afterShop(request, shopId, shop, failure));
    }

    public void dispose() {
        latest.dispose();
    }

    private void afterShop(long request, String shopId, ShopDetail shop, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!latest.accepts(request)) {
                return;
            }
            if (failure != null) {
                showFailure(failure);
                return;
            }
            shopName.setText(shop.shopName());
            client.getShopProducts(new ShopProductQuery(shopId, null, null, null, null,
                    ProductSortMode.SALES_DESC, 0, 20))
                    .whenComplete((products, productFailure) -> finish(request, products, productFailure));
        });
    }

    private void finish(long request, PageResult<ProductSummary> products, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!latest.accepts(request)) {
                return;
            }
            if (failure != null) {
                showFailure(failure);
                return;
            }
            cards.showProducts(products.items());
        });
    }

    private void showFailure(Throwable failure) {
        String code = failureCode(failure);
        if ("AUTH_SESSION_EXPIRED".equals(code)) {
            sessionExpired.run();
        } else {
            error.setText(code);
        }
    }

    private static String failureCode(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "COMMON_INTERNAL_ERROR" : cause.getMessage();
    }
}
