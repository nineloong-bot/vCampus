package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.user.UserView;

/**
 * Installs fixed buyer pages and renders routes through the Shop-owned card navigator.
 *
 * <p>The route contracts, page factories, buyer page set and lifecycle behaviour are
 * implemented by the package-private segment chain this class extends, keeping the
 * public constructors and nested contract types unchanged.</p>
 */
public final class ShopPageCoordinator extends ShopPageCoordinatorBase {

    /** Creates and registers every stable Shop page. This must run on the EDT. */
    public ShopPageCoordinator(ShopModulePanel pages, UserView user, ShopClientPort client,
            ShopUiKit uiKit,
            Runnable sessionExpired) {
        this((CardNavigator) pages, user, new BuyerPageFactory(client), uiKit, sessionExpired);
    }

    ShopPageCoordinator(ShopModulePanel pages, UserView user, PageFactory factory, ShopUiKit uiKit,
            Runnable sessionExpired) {
        this((CardNavigator) pages, user, factory, uiKit, sessionExpired);
    }

    ShopPageCoordinator(CardNavigator cards, UserView user, PageFactory factory, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(cards, user, factory, uiKit, sessionExpired);
    }
}
