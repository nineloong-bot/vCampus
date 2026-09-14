package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.admin.ShopAdminPanel;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;

/** Owns the administrator's management workspace and its request lifecycle. */
final class AdminShopCoordinator implements ShopUiInstaller.InstalledCoordinator {
    private final ShopModulePanel module;
    private final ShopAdminPanel workspace;
    private final ShopNavigator navigator;
    private boolean disposed;

    AdminShopCoordinator(ShopModulePanel module, AdminShopClientPort client,
            ShopUiKit uiKit, Runnable sessionExpired) {
        this.module = module;
        workspace = new ShopAdminPanel(client, uiKit, sessionExpired);
        module.register(ShopPageCoordinator.ADMIN_WORKSPACE, workspace);
        navigator = new ShopNavigator(route -> {
            if (!(route instanceof ShopRoute.AdminWorkspace)) {
                throw new IllegalArgumentException("Unsupported administrator route");
            }
            if (!disposed) {
                workspace.load();
                module.show(ShopPageCoordinator.ADMIN_WORKSPACE);
            }
        });
    }

    @Override public ShopNavigator navigator() { return navigator; }

    @Override public void enter() { goHome(); }

    @Override public void goHome() {
        if (!disposed) navigator.open(new ShopRoute.AdminWorkspace());
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        workspace.disposePage();
    }
}
