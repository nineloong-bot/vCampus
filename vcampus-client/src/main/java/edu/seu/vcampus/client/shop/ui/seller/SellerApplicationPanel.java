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
    private final LeavePrompt leavePrompt;
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
    private FormState savedSnapshot;
    private boolean mutationInFlight;
    private boolean disposed;

    public enum LeaveChoice { SAVE, DISCARD, CANCEL }

    @FunctionalInterface
    public interface LeavePrompt { LeaveChoice choose(SellerApplicationPanel parent); }

    public SellerApplicationPanel(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired) {
        this(port, uiKit, sessionExpired, parent -> {
            Object[] choices = {"保存并离开", "不保存并离开", "取消"};
            int selected = JOptionPane.showOptionDialog(parent, "申请内容尚未保存，是否保存草稿？",
                    "离开开店申请", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, choices, choices[0]);
            return selected == 0 ? LeaveChoice.SAVE
                    : selected == 1 ? LeaveChoice.DISCARD : LeaveChoice.CANCEL;
        });
    }

    public SellerApplicationPanel(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired, LeavePrompt leavePrompt) {
        super(new BorderLayout(8, 8));
        this.port = Objects.requireNonNull(port, "port");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.leavePrompt = Objects.requireNonNull(leavePrompt, "leavePrompt");
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

    public void requestLeave(Runnable proceed) {
        Objects.requireNonNull(proceed, "proceed");
        if (!dirty()) {
            proceed.run();
            return;
        }
        switch (leavePrompt.choose(this)) {
            case DISCARD -> proceed.run();
            case CANCEL -> { }
            case SAVE -> saveLatest(proceed);
        }
    }

    private void render(Optional<SellerApplicationView> value) {
        current = value.orElse(null);
        if (current == null) {
            name.setText(""); description.setText(""); contact.setText(""); statement.setText("");
            category.setSelectedIndex(0); status.setText("未申请"); reason.setText("");
            savedSnapshot = captureForm();
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
        savedSnapshot = captureForm();
        editable(current.status() == SellerApplicationStatus.DRAFT
                || current.status() == SellerApplicationStatus.REJECTED);
    }

    private void editable(boolean value) {
        name.setEnabled(value); description.setEnabled(value); category.setEnabled(value);
        contact.setEnabled(value); statement.setEnabled(value); save.setEnabled(value);
        submit.setEnabled(value && !mutationInFlight);
    }

    private void save() {
        saveLatest(null);
    }

    private void submit() {
        if (mutationInFlight) return;
        FormState form = captureForm();
        if (!form.valid()) {
            status.setText("请完整填写申请信息");
            return;
        }
        setMutationInFlight(true);
        port.saveApplication(form.toCommand(current)).whenComplete((saved, saveFailure) ->
                SwingUtilities.invokeLater(() -> {
                    if (disposed) return;
                    if (saveFailure != null) {
                        setMutationInFlight(false);
                        fail(saveFailure);
                        return;
                    }
                    current = saved;
                    savedSnapshot = form;
                    port.submitApplication(new SubmitSellerApplicationCommand(
                                    saved.applicationId(), saved.rowVersion()))
                            .whenComplete(this::finishMutation);
                }));
    }

    private void finishMutation(SellerApplicationView value, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (disposed) return;
            setMutationInFlight(false);
            if (failure != null) fail(failure); else render(Optional.of(value));
        });
    }

    private void saveLatest(Runnable afterSave) {
        if (mutationInFlight) return;
        FormState form = captureForm();
        setMutationInFlight(true);
        port.saveApplication(form.toCommand(current)).whenComplete((value, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (disposed) return;
                    setMutationInFlight(false);
                    if (failure != null) {
                        fail(failure);
                        return;
                    }
                    current = value;
                    savedSnapshot = form;
                    render(Optional.of(value));
                    if (afterSave != null) afterSave.run();
                }));
    }

    private void setMutationInFlight(boolean value) {
        mutationInFlight = value;
        boolean formEditable = current == null || current.status() == SellerApplicationStatus.DRAFT
                || current.status() == SellerApplicationStatus.REJECTED;
        editable(formEditable && !value);
    }

    private boolean dirty() {
        return savedSnapshot != null && !savedSnapshot.equals(captureForm());
    }

    private FormState captureForm() {
        return new FormState(name.getText().trim(), description.getText().trim(),
                Objects.toString(category.getSelectedItem(), "").trim(), contact.getText().trim(),
                statement.getText().trim());
    }

    private record FormState(String shopName, String description, String category,
            String contact, String statement) {
        SaveSellerDraftCommand toCommand(SellerApplicationView current) {
            return new SaveSellerDraftCommand(current == null ? null : current.applicationId(),
                    shopName, description, category, contact, statement,
                    current == null ? 0 : current.rowVersion());
        }

        boolean valid() {
            return !shopName.isBlank() && !description.isBlank() && !category.isBlank()
                    && !contact.isBlank() && !statement.isBlank();
        }
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
