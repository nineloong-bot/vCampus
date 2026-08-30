package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Attaches the authenticated buyer Shop flow to the application shell extension points. */
public final class ShopUiInstaller {
    private ShopUiInstaller() { }

    /** Installs the Shop module into the shared Shop entry and placeholder page. */
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
        AbstractButton entry = requiredComponent(frame, "navigation.shop", AbstractButton.class);
        Container placeholder = requiredComponent(frame, "page.shop", Container.class);
        ShopModulePanel module = new ShopModulePanel();
        placeholder.add(module, BorderLayout.CENTER);
        InstalledCoordinator coordinator = Objects.requireNonNull(factory, "factory")
                .create(module, client, uiKit, sessionExpired);
        entry.addActionListener(event -> coordinator.enter());
        AtomicBoolean disposed = new AtomicBoolean();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (disposed.compareAndSet(false, true)) {
                    coordinator.dispose();
                }
            }
        });
    }

    @FunctionalInterface
    interface CoordinatorFactory {
        InstalledCoordinator create(ShopModulePanel module,
                ShopClientPort client, ShopUiKit uiKit, Runnable sessionExpired);
    }

    interface InstalledCoordinator {
        edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator navigator();
        void enter();
        void dispose();
    }

    private static <T extends Component> T requiredComponent(Container root, String name,
            Class<T> componentType) {
        List<Component> matches = new ArrayList<>();
        collectNamedComponents(root, name, matches);
        if (matches.size() != 1) {
            throw new IllegalStateException("Expected exactly one component named " + name
                    + ", found " + matches.size());
        }
        Component component = matches.getFirst();
        if (!componentType.isInstance(component)) {
            throw new IllegalStateException("Component named " + name + " must be a "
                    + componentType.getSimpleName());
        }
        return componentType.cast(component);
    }

    private static void collectNamedComponents(Container root, String name,
            List<Component> matches) {
        if (name.equals(root.getName())) {
            matches.add(root);
        }
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) {
                collectNamedComponents(nested, name, matches);
            } else if (name.equals(child.getName())) {
                matches.add(child);
            }
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Shop UI must be installed on the EDT");
        }
    }
}
