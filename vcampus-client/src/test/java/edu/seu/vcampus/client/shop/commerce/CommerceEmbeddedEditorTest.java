package edu.seu.vcampus.client.shop.commerce;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceEmbeddedEditorTest {
    @Test
    void productEditorIsHiddenUntilRequestedThenOpensInsideThePage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CommercePanel ui = panel();
            JLabel list = new JLabel("商品列表"); list.setName("commerce.test.list");
            ui.display("商品管理", list, null, ui::home);
            assertThat(find(ui, JSplitPane.class)).isNull();

            new ProductEditorPage(ui).open(null);

            assertThat(find(ui, JSplitPane.class)).isNotNull();
            assertThat(findNamed(ui, "commerce.modal")).isNull();
            assertThat(findNamed(ui, "preset.book")).isNotNull();
        });
    }

    @Test
    void compactFormsUseTheSharedEmbeddedHost() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CommercePanel ui = panel();
            ui.display("首页", new JLabel("列表"), null, ui::home);
            new WalletPage(ui).recharge();
            assertThat(find(ui, JSplitPane.class)).isNotNull();
            assertThat(find(ui, JTextField.class)).isNotNull();
        });
    }

    private static CommercePanel panel() {
        return new CommercePanel((command, body, key) -> new CompletableFuture<>(), false);
    }

    private static Component findNamed(Component root, String name) {
        if (name.equals(root.getName())) return root;
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            Component found = findNamed(child, name); if (found != null) return found;
        }
        return null;
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            T found = find(child, type); if (found != null) return found;
        }
        return null;
    }
}
