package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SwingProductEditorDialogsTest {
    @Test
    void productionDialogUsesSemanticKitButtonsInsteadOfRawConfirmationButtons() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/edu/seu/vcampus/client/shop/ui/seller/SwingProductEditorDialogs.java"));

        assertThat(source).doesNotContain("new JButton(\"确定\")", "new JButton(\"取消\")");
        assertThat(source).contains("uiKit.primaryButton", "uiKit.secondaryButton");
    }

    @Test
    void createAndUpdateUseTheConfirmedResizableWindowSpecification() throws Exception {
        AtomicReference<SwingProductEditorDialogs.DialogSpec> captured = new AtomicReference<>();
        SwingProductEditorDialogs dialogs = new SwingProductEditorDialogs(new DefaultShopUiKit(),
                (parent, editor, title, spec) -> {
                    captured.set(spec);
                    return false;
                });

        ShopSwingTestSupport.onEdt(() -> dialogs.create(null, "文具"));

        assertThat(captured.get()).isEqualTo(new SwingProductEditorDialogs.DialogSpec(
                new Dimension(900, 650), new Dimension(720, 520), true));
    }
}
