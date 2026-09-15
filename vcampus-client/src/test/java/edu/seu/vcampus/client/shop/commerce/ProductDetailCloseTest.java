package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class ProductDetailCloseTest {
    @Test
    void changingPurchaseQuantityAndVariantDoesNotRequireDiscardConfirmation() throws Exception {
        CommercePanel[] panel = new CommercePanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new CommercePanel((command, body, key) ->
                    CompletableFuture.completedFuture(product()), false);
            panel[0].display("首页", new JLabel("商品列表"), null, panel[0]::home);
            new ProductPage(panel[0]).open("product");
        });
        SwingUtilities.invokeAndWait(() -> {
            CommercePanel ui = panel[0];
            find(ui, JComboBox.class).setSelectedIndex(1);
            find(ui, JSpinner.class).setValue(4);
            EmbeddedEditorHost host = find(ui, EmbeddedEditorHost.class);
            assertThat(currentEditor(host).isDirty()).isFalse();
            ui.closeModal();
            assertThat(host.isEditorOpen()).isFalse();
        });
    }

    @Test
    void actualEditableFormsStillWarnBeforeDiscardingChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel form = new JPanel();
            JTextField name = new JTextField("原店名");
            form.add(name);
            CommerceEditorBridge editor = new CommerceEditorBridge("编辑资料", form,
                    null, new JLabel(), 620, () -> { });
            editor.onOpened();
            name.setText("修改后的店名");
            assertThat(editor.isDirty()).isTrue();
        });
    }

    private static Product product() {
        return new Product("product", "shop", "校园店铺", "笔记本", "商品介绍",
                "ordinary", "book", "PUBLISHED", "sku1", false, 0,
                List.of(new Sku("sku1", "标准款", BigDecimal.TEN, 100, 0, true),
                        new Sku("sku2", "双件装", BigDecimal.valueOf(18), 50, 0, true)));
    }

    private static EmbeddedEditor currentEditor(EmbeddedEditorHost host) {
        try {
            Field field = EmbeddedEditorHost.class.getDeclaredField("editor");
            field.setAccessible(true);
            return (EmbeddedEditor) field.get(host);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container parent) {
            for (Component child : parent.getComponents()) {
                T result = find(child, type);
                if (result != null) return result;
            }
        }
        return null;
    }
}
