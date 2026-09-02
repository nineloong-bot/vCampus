package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
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
    private final JButton save;
    private ShopView current;
    private boolean disposed;

    public ShopProfilePanel(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired, Consumer<ShopView> loaded) {
        super(new BorderLayout(8, 8));
        this.port = Objects.requireNonNull(port, "port");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.loaded = Objects.requireNonNull(loaded, "loaded");
        category.setEditable(false);
        JPanel form = uiKit.filterPanel("seller.profile.form", new GridLayout(0, 2, 8, 6));
        row(form, "店铺名称", name); row(form, "简介", new JScrollPane(description));
        row(form, "批准类别", category); row(form, "联系方式", contact);
        row(form, "状态", status); row(form, "停业原因", suspension);
        save = uiKit.primaryButton("seller.profile.save", "保存资料");
        save.addActionListener(event -> save());
        add(form, BorderLayout.CENTER); add(save, BorderLayout.SOUTH);
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
        status.setText(shop.status().name());
        suspension.setText(shop.suspensionReason() == null ? "" : shop.suspensionReason());
        boolean writable = shop.status() == ShopStatus.ACTIVE;
        name.setEnabled(writable); description.setEnabled(writable); contact.setEnabled(writable);
        save.setEnabled(writable);
    }

    private void save() {
        if (current == null) return;
        port.updateOwnedShop(new UpdateShopCommand(name.getText(), description.getText(),
                current.category(), contact.getText(), current.rowVersion()))
                .whenComplete((shop, failure) -> SwingUtilities.invokeLater(() -> {
                    if (disposed) return;
                    if (failure != null) fail(failure); else { render(shop); loaded.accept(shop); }
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
}
