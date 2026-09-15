package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.ShopComponentStyle;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.common.shop.*;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.function.Consumer;

/** Seller profile form; the approved category is intentionally immutable. */
public final class ShopProfilePanel extends JPanel {
    private final SellerShopClientPort port;
    private final Runnable sessionExpired;
    private final Consumer<ShopView> loaded;
    private final LatestRequest requests = new LatestRequest();
    private final JTextField name = named(new JTextField(), "seller.profile.name");
    private final JTextArea description = named(new JTextArea(3, 24), "seller.profile.description");
    private final JTextField category = named(new JTextField(), "seller.profile.category");
    private final JTextField contact = named(new JTextField(), "seller.profile.contact");
    private final JLabel status = named(new JLabel(), "seller.profile.status");
    private final JLabel suspension = named(new JLabel(), "seller.profile.suspension");
    private final JLabel categorySummary = named(new JLabel(), "seller.profile.category-summary");
    private final JButton save;
    private final JButton edit;
    private ShopView current;
    private boolean disposed;
    private EmbeddedEditorHost editorHost;
    private String initialState = "";

    public ShopProfilePanel(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired, Consumer<ShopView> loaded) {
        super(new BorderLayout(8, 8));
        ShopComponentStyle.pagePanel(this);
        ShopComponentStyle.styleTextComponent(name);
        ShopComponentStyle.styleTextComponent(description);
        ShopComponentStyle.styleTextComponent(category);
        ShopComponentStyle.styleTextComponent(contact);
        this.port = Objects.requireNonNull(port, "port");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.loaded = Objects.requireNonNull(loaded, "loaded");
        category.setEditable(false);
        JPanel form = uiKit.filterPanel("seller.profile.form", new GridLayout(0, 2, 8, 6));
        row(form, "店铺名称", name); row(form, "简介", new JScrollPane(description));
        row(form, "批准类别", category); row(form, "联系方式", contact);
        save = uiKit.primaryButton("seller.profile.save", "保存资料");
        save.addActionListener(event -> save());
        JPanel editor = new JPanel(new BorderLayout(8, 8));
        editor.add(form); editor.add(save, BorderLayout.SOUTH);
        JPanel summary = uiKit.filterPanel("seller.profile.summary", new GridLayout(0, 2, 8, 6));
        row(summary, "批准类别", categorySummary);
        row(summary, "状态", status); row(summary, "停业原因", suspension);
        edit = uiKit.primaryButton("seller.profile.edit", "编辑资料");
        edit.addActionListener(event -> {
            if (current != null && current.status() == ShopStatus.ACTIVE) {
                editorHost.showEditor(new ShopProfileEditorPanel(editor, this));
            }
        });
        JPanel list = new JPanel(new BorderLayout(8, 8)); list.add(summary); list.add(edit, BorderLayout.SOUTH);
        editorHost = new EmbeddedEditorHost(list); add(editorHost, BorderLayout.CENTER);
    }

    public void load() {
        if (disposed) return;
        long request = requests.begin();
        port.getOwnedShop().whenComplete((shop, failure) -> SwingUtilities.invokeLater(() -> {
            if (!requests.accepts(request)) return;
            if (failure != null) { fail(failure); return; }
            render(shop); loaded.accept(shop);
        }));
    }

    public void disposePage() { disposed = true; requests.dispose(); }

    private void render(ShopView shop) {
        current = shop; name.setText(shop.shopName()); description.setText(shop.description());
        category.setText(shop.category()); contact.setText(shop.contact());
        categorySummary.setText(shop.category());
        status.setText(shop.status().name());
        suspension.setText(shop.suspensionReason() == null ? "" : shop.suspensionReason());
        boolean writable = shop.status() == ShopStatus.ACTIVE;
        name.setEnabled(writable); description.setEnabled(writable); contact.setEnabled(writable);
        save.setEnabled(writable); edit.setEnabled(writable);
        initialState = state();
    }

    private void save() {
        if (current == null) return;
        port.updateOwnedShop(new UpdateShopCommand(name.getText(), description.getText(),
                current.category(), contact.getText(), current.rowVersion()))
                .whenComplete((shop, failure) -> SwingUtilities.invokeLater(() -> {
                    if (disposed) return;
                    if (failure != null) fail(failure); else {
                        render(shop); editorHost.completeAndClose(); loaded.accept(shop);
                    }
                }));
    }

    private void fail(Throwable failure) {
        String code = ShopUiErrors.code(failure); status.setText(ShopUiErrors.message(code));
        if (ShopUiErrors.sessionExpired(code)) sessionExpired.run();
    }

    private static void row(JPanel form, String label, JComponent component) {
        form.add(new JLabel(label)); form.add(component);
    }
    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name); return component;
    }
    boolean hasChanges() { return !initialState.equals(state()); }
    private String state() { return name.getText() + "\0" + description.getText() + "\0" + contact.getText(); }
}
