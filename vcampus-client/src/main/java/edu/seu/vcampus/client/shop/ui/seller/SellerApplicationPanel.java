package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.*;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.Optional;

/** Transport-independent seller application form and state renderer. */
public final class SellerApplicationPanel extends JPanel {
    private final SellerShopClientPort port;
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final LatestRequest requests = new LatestRequest();
    private final JTextField name = named(new JTextField(), "seller.application.name");
    private final JTextArea description = named(new JTextArea(3, 24), "seller.application.description");
    private final JComboBox<String> category = named(new JComboBox<>(ShopCategories.ALL.toArray(String[]::new)),
            "seller.application.category");
    private final JTextField contact = named(new JTextField(), "seller.application.contact");
    private final JTextArea statement = named(new JTextArea(4, 24), "seller.application.statement");
    private final JLabel reason = named(new JLabel(), "seller.application.reason");
    private final JLabel status = named(new JLabel(), "seller.application.status");
    private final JButton save;
    private final JButton submit;
    private SellerApplicationView current;
    private boolean disposed;

    public SellerApplicationPanel(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.port = Objects.requireNonNull(port, "port");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        JPanel form = uiKit.filterPanel("seller.application.form", new GridLayout(0, 2, 8, 6));
        addRow(form, "店铺名称", name);
        addRow(form, "店铺简介", new JScrollPane(description));
        addRow(form, "店铺类别", category);
        addRow(form, "联系方式", contact);
        addRow(form, "经营计划", new JScrollPane(statement));
        addRow(form, "状态", status);
        addRow(form, "审核意见", reason);
        save = uiKit.secondaryButton("seller.application.save", "保存草稿");
        submit = uiKit.primaryButton("seller.application.submit", "提交审核");
        JPanel actions = uiKit.filterPanel("seller.application.actions", new java.awt.FlowLayout());
        actions.add(save);
        actions.add(submit);
        save.addActionListener(event -> save());
        submit.addActionListener(event -> submit());
        add(form, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    public void load() {
        if (disposed) return;
        long request = requests.begin();
        port.getMyApplication().whenComplete((value, failure) -> SwingUtilities.invokeLater(() -> {
            if (!requests.accepts(request)) return;
            if (failure != null) {
                fail(failure);
                return;
            }
            render(value);
        }));
    }

    public void disposePage() {
        disposed = true;
        requests.dispose();
    }

    private void render(Optional<SellerApplicationView> value) {
        current = value.orElse(null);
        if (current == null) {
            name.setText(""); description.setText(""); contact.setText(""); statement.setText("");
            category.setSelectedIndex(0); status.setText("未申请"); reason.setText("");
            editable(true);
            return;
        }
        name.setText(current.shopName());
        description.setText(current.description());
        category.setSelectedItem(current.category());
        contact.setText(current.contact());
        statement.setText(current.applicationStatement());
        status.setText(current.status().name());
        reason.setText(current.reviewReason() == null ? "" : current.reviewReason());
        editable(current.status() == SellerApplicationStatus.DRAFT
                || current.status() == SellerApplicationStatus.REJECTED);
    }

    private void editable(boolean value) {
        name.setEnabled(value); description.setEnabled(value); category.setEnabled(value);
        contact.setEnabled(value); statement.setEnabled(value); save.setEnabled(value);
        submit.setEnabled(value && current != null);
    }

    private void save() {
        SaveSellerDraftCommand command = new SaveSellerDraftCommand(
                current == null ? null : current.applicationId(), name.getText(), description.getText(),
                Objects.toString(category.getSelectedItem(), ""), contact.getText(), statement.getText(),
                current == null ? 0 : current.rowVersion());
        port.saveApplication(command).whenComplete(this::finishMutation);
    }

    private void submit() {
        if (current == null) return;
        port.submitApplication(new SubmitSellerApplicationCommand(current.applicationId(),
                current.rowVersion())).whenComplete(this::finishMutation);
    }

    private void finishMutation(SellerApplicationView value, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (disposed) return;
            if (failure != null) fail(failure); else render(Optional.of(value));
        });
    }

    private void fail(Throwable failure) {
        String code = ShopUiErrors.code(failure);
        status.setText(code);
        if (ShopUiErrors.sessionExpired(code)) sessionExpired.run();
    }

    private static void addRow(JPanel panel, String label, JComponent component) {
        panel.add(new JLabel(label));
        panel.add(component);
    }

    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name);
        return component;
    }
}
