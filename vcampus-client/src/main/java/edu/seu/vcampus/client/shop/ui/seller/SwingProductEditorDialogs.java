package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.CreateProductCommand;
import edu.seu.vcampus.common.shop.ProductView;
import edu.seu.vcampus.common.shop.UpdateProductCommand;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.Optional;

/** Default Swing modal implementation; business submission remains in the management panel. */
public final class SwingProductEditorDialogs implements ProductEditorDialogPort {
    record DialogSpec(Dimension initialSize, Dimension minimumSize, boolean resizable) { }
    @FunctionalInterface
    interface Presenter {
        boolean confirm(Component parent, ProductEditorPanel editor, String title, DialogSpec spec);
    }

    private static final DialogSpec SPEC = new DialogSpec(
            new Dimension(900, 650), new Dimension(720, 520), true);
    private final ShopUiKit uiKit;
    private final Presenter presenter;

    public SwingProductEditorDialogs(ShopUiKit uiKit) {
        this(uiKit, SwingProductEditorDialogs::showDialog);
    }

    SwingProductEditorDialogs(ShopUiKit uiKit, Presenter presenter) {
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
    }

    @Override
    public Optional<CreateProductCommand> create(Component parent, String category) {
        ProductEditorPanel editor = new ProductEditorPanel(uiKit);
        editor.clear(category);
        return presenter.confirm(parent, editor, "创建商品", SPEC)
                ? Optional.of(editor.createCommand()) : Optional.empty();
    }

    @Override
    public Optional<UpdateProductCommand> update(Component parent, ProductView product) {
        ProductEditorPanel editor = new ProductEditorPanel(uiKit);
        editor.load(product);
        return presenter.confirm(parent, editor, "修改商品信息", SPEC)
                ? Optional.of(editor.updateCommand()) : Optional.empty();
    }

    private static boolean showDialog(Component parent, ProductEditorPanel editor,
            String title, DialogSpec spec) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] confirmed = {false};
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JScrollPane form = new JScrollPane(editor,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        content.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton confirm = new JButton("确定");
        JButton cancel = new JButton("取消");
        confirm.addActionListener(event -> { confirmed[0] = true; dialog.dispose(); });
        cancel.addActionListener(event -> dialog.dispose());
        actions.add(confirm); actions.add(cancel);
        content.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(confirm);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setResizable(spec.resizable());
        dialog.setMinimumSize(spec.minimumSize());
        dialog.setSize(spec.initialSize());
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return confirmed[0];
    }
}
