package edu.seu.vcampus.client.shop.ui.admin;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.seller.ProductEditorPanel;
import edu.seu.vcampus.client.shop.ui.seller.ProductEditorWorkspace;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.*;

import javax.swing.*;
import java.util.function.BiConsumer;

/** Coordinates embedded administrator product creation and editing. */
final class AdminProductEditorCoordinator {
    private final AdminShopClientPort port;
    private final ShopUiKit uiKit;
    private final EmbeddedEditorHost host;
    private final JLabel status;
    private final BiConsumer<String, Throwable> finished;

    AdminProductEditorCoordinator(AdminShopClientPort port, ShopUiKit uiKit,
            EmbeddedEditorHost host, JLabel status, BiConsumer<String, Throwable> finished) {
        this.port = port; this.uiKit = uiKit; this.host = host;
        this.status = status; this.finished = finished;
    }

    void create(ShopAdminSummary shop) {
        ProductEditorPanel editor = new ProductEditorPanel(uiKit); editor.clear(shop.category());
        host.showEditor(new ProductEditorWorkspace(editor, true,
                command -> submitCreate(shop, command), ignored -> { }, host::requestClose));
    }

    void update(ShopAdminSummary shop, ProductView product) {
        ProductEditorPanel editor = new ProductEditorPanel(uiKit); editor.load(product);
        host.showEditor(new ProductEditorWorkspace(editor, false,
                ignored -> { }, command -> submitUpdate(shop, command), host::requestClose));
    }

    private void submitCreate(ShopAdminSummary shop, CreateProductCommand command) {
        try {
            port.createProduct(new AdminCreateProductCommand(shop.shopId(), command))
                    .whenComplete((ignored, failure) -> finished.accept(shop.shopId(), failure));
        } catch (RuntimeException failure) { validationFailure(); }
    }

    private void submitUpdate(ShopAdminSummary shop, UpdateProductCommand command) {
        try {
            port.updateProduct(new AdminUpdateProductCommand(shop.shopId(), command))
                    .whenComplete((ignored, failure) -> finished.accept(shop.shopId(), failure));
        } catch (RuntimeException failure) { validationFailure(); }
    }

    private void validationFailure() {
        status.setText(ShopUiErrors.message("COMMON_VALIDATION_FAILED"));
    }
}
