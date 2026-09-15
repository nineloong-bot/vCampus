package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SwingProductEditorDialogsTest {
    @Test
    void productionProductEditingNoLongerHasADialogWrapper() {
        assertThat(Files.exists(Path.of(
                "src/main/java/edu/seu/vcampus/client/shop/ui/seller/SwingProductEditorDialogs.java")))
                .isFalse();
    }

    @Test
    void productWorkspaceUsesWideEmbeddedLayout() {
        ProductEditorPanel editor = new ProductEditorPanel(new DefaultShopUiKit());
        ProductEditorWorkspace workspace = new ProductEditorWorkspace(editor, true,
                ignored -> { }, ignored -> { }, () -> { });
        assertThat(workspace.size()).isEqualTo(EditorSize.WIDE);
        assertThat(workspace.component()).isNotNull();
    }
}
