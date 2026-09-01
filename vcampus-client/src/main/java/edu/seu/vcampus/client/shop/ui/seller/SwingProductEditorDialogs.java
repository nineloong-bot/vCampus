package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.CreateProductCommand;
import edu.seu.vcampus.common.shop.ProductView;
import edu.seu.vcampus.common.shop.UpdateProductCommand;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Objects;
import java.util.Optional;

/** Default Swing modal implementation; business submission remains in the management panel. */
final class SwingProductEditorDialogs implements ProductEditorDialogPort {
    private final ShopUiKit uiKit;

    SwingProductEditorDialogs(ShopUiKit uiKit) {
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
    }

    @Override
    public Optional<CreateProductCommand> create(Component parent, String category) {
        ProductEditorPanel editor = new ProductEditorPanel(uiKit);
        editor.clear(category);
        return confirm(parent, editor, "创建商品")
                ? Optional.of(editor.createCommand()) : Optional.empty();
    }

    @Override
    public Optional<UpdateProductCommand> update(Component parent, ProductView product) {
        ProductEditorPanel editor = new ProductEditorPanel(uiKit);
        editor.load(product);
        return confirm(parent, editor, "更新商品")
                ? Optional.of(editor.updateCommand()) : Optional.empty();
    }

    private static boolean confirm(Component parent, ProductEditorPanel editor, String title) {
        JScrollPane content = new JScrollPane(editor);
        content.setPreferredSize(new Dimension(760, 560));
        return JOptionPane.showConfirmDialog(parent, content, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }
}
