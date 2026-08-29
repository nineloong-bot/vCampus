package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

/** Attaches the authenticated buyer Shop flow to the application shell extension points. */
public final class ShopUiInstaller {
    private ShopUiInstaller() { }

    /** Installs one semantic Shop sidebar entry and the corresponding fixed page coordinator. */
    public static void install(MainFrame frame, ShopClientPort client, ShopUiKit uiKit,
            Runnable sessionExpired) {
        install(frame, client, uiKit, sessionExpired, ShopPageCoordinator::new);
    }

    static void install(MainFrame frame, ShopClientPort client, ShopUiKit uiKit,
            Runnable sessionExpired, CoordinatorFactory factory) {
        requireEdt();
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(uiKit, "uiKit");
        Objects.requireNonNull(sessionExpired, "sessionExpired");
        InstalledCoordinator coordinator = Objects.requireNonNull(factory, "factory")
                .create(frame.pageNavigator(), client, uiKit, sessionExpired);
        JButton entry = uiKit.navigationButton("shop.navigation", "校园商城");
        entry.addActionListener(event -> coordinator.navigator().open(new ShopRoute.Home(
                new HomeProductQuery(null, null, ProductSortMode.SALES_DESC, 0, 20))));
        frame.navigation().add(entry);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                coordinator.dispose();
            }
        });
    }

    @FunctionalInterface
    interface CoordinatorFactory {
        InstalledCoordinator create(edu.seu.vcampus.client.core.navigation.PageNavigator pages,
                ShopClientPort client, ShopUiKit uiKit, Runnable sessionExpired);
    }

    interface InstalledCoordinator {
        edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator navigator();
        void dispose();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Shop UI must be installed on the EDT");
        }
    }
}
