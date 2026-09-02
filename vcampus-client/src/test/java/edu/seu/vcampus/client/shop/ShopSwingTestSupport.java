package edu.seu.vcampus.client.shop;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Swing helpers for asserting catalog components from the EDT. */
public final class ShopSwingTestSupport {
    private ShopSwingTestSupport() { }

    public static <T> T onEdt(Callable<T> action) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                value.set(action.call());
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return value.get();
    }

    public static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    public static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    public static <T extends Component> T component(Container root, String name,
            Class<T> type) {
        T match = componentOrNull(root, name, type);
        if (match != null) {
            return match;
        }
        throw new AssertionError("Missing component: " + name);
    }

    public static <T extends Component> T awaitComponent(Container root, String name,
            Class<T> type) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            T match = onEdt(() -> componentOrNull(root, name, type));
            if (match != null) {
                return match;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for component: " + name);
    }

    private static <T extends Component> T componentOrNull(Container root, String name,
            Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container nested) {
                T match = componentOrNull(nested, name, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
