package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.ShopComponentStyle;
import edu.seu.vcampus.common.shop.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.Optional;

/** Application-modal editor with explicit draft and submit actions. */
public final class SwingSellerApplicationDialog implements SellerApplicationDialogPort {
    public enum CloseChoice { SAVE, DISCARD, CANCEL }
    @FunctionalInterface interface ClosePrompt { CloseChoice choose(Component parent); }

    private final SellerShopClientPort port;
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final ClosePrompt closePrompt;

    public SwingSellerApplicationDialog(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired) {
        this(port, uiKit, sessionExpired, parent -> {
            Object[] choices = {"保存草稿", "不保存", "取消"};
            int selected = JOptionPane.showOptionDialog(parent, "申请内容尚未保存，是否保存草稿？",
                    "关闭开店申请", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, choices, choices[0]);
            return selected == 0 ? CloseChoice.SAVE
                    : selected == 1 ? CloseChoice.DISCARD : CloseChoice.CANCEL;
        });
    }

    SwingSellerApplicationDialog(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired, ClosePrompt closePrompt) {
        this.port = Objects.requireNonNull(port); this.uiKit = Objects.requireNonNull(uiKit);
        this.sessionExpired = Objects.requireNonNull(sessionExpired);
        this.closePrompt = Objects.requireNonNull(closePrompt);
    }

    @Override public void open(Component parent, Optional<SellerApplicationView> application,
            Runnable changed) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = owner instanceof Frame frame ? new JDialog(frame, "开店申请", true)
                : new JDialog((Frame) null, "开店申请", true);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        Form form = createForm(application, changed, dialog::dispose);
        dialog.setContentPane(form);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { form.requestClose(); }
        });
        dialog.pack(); dialog.setMinimumSize(new Dimension(620, 520));
        dialog.setLocationRelativeTo(parent); dialog.setVisible(true);
    }

    Form createForm(Optional<SellerApplicationView> application, Runnable changed, Runnable closed) {
        return new Form(application.orElse(null), changed, closed);
    }

    final class Form extends JPanel {
        private final JTextField name = LimitedTextInput.field("seller.application.name",
                "店铺名称不能超过 50 字", SellerApplicationLimits.SHOP_NAME);
        private final JTextArea description = named(new JTextArea(3, 24), "seller.application.description");
        private final JComboBox<String> category = named(new JComboBox<>(ShopCategories.ALL.toArray(String[]::new)),
                "seller.application.category");
        private final JTextField contact = LimitedTextInput.field("seller.application.contact",
                "联系方式不能超过 50 字", SellerApplicationLimits.CONTACT);
        private final JTextArea statement = LimitedTextInput.area("seller.application.statement",
                "经营计划不能超过 500 字", SellerApplicationLimits.APPLICATION_STATEMENT, 4, 24);
        private final JLabel status = named(new JLabel(), "seller.application.dialog-status");
        private final JLabel reason = named(new JLabel(), "seller.application.reason");
        private final JButton save = uiKit.secondaryButton("seller.application.save", "保存草稿");
        private final JButton submit = uiKit.primaryButton("seller.application.submit", "直接提交");
        private final Runnable changed;
        private final Runnable closed;
        private SellerApplicationView current;
        private State saved;
        private boolean mutation;

        Form(SellerApplicationView application, Runnable changed, Runnable closed) {
            super(new BorderLayout(8, 8));
            ShopComponentStyle.styleDialogContent(this);
            ShopComponentStyle.styleTextComponent(name);
            ShopComponentStyle.styleTextComponent(description);
            ShopComponentStyle.styleTextComponent(category);
            ShopComponentStyle.styleTextComponent(contact);
            ShopComponentStyle.styleTextComponent(statement);
            this.current = application;
            this.changed = Objects.requireNonNull(changed); this.closed = Objects.requireNonNull(closed);
            JPanel fields = uiKit.filterPanel("seller.application.form", new GridLayout(0, 2, 8, 6));
            row(fields, "店铺名称", LimitedTextInput.wrap(name, "seller.application.name", SellerApplicationLimits.SHOP_NAME));
            row(fields, "店铺简介", new JScrollPane(description)); row(fields, "店铺类别", category);
            row(fields, "联系方式", LimitedTextInput.wrap(contact, "seller.application.contact", SellerApplicationLimits.CONTACT));
            row(fields, "经营计划", LimitedTextInput.wrap(new JScrollPane(statement), statement,
                    "seller.application.statement", SellerApplicationLimits.APPLICATION_STATEMENT));
            row(fields, "审核意见", reason); row(fields, "提示", status);
            JPanel actions = uiKit.filterPanel("seller.application.dialog-actions", new FlowLayout());
            actions.add(save); actions.add(submit); add(fields, BorderLayout.CENTER); add(actions, BorderLayout.SOUTH);
            loadApplication(application); save.addActionListener(e -> save(false));
            submit.addActionListener(e -> submit());
        }

        void requestClose() {
            if (mutation) return;
            if (saved.equals(capture())) { closed.run(); return; }
            switch (closePrompt.choose(this)) {
                case CANCEL -> { }
                case DISCARD -> closed.run();
                case SAVE -> save(true);
            }
        }

        private void loadApplication(SellerApplicationView value) {
            if (value != null) {
                name.setText(value.shopName()); description.setText(value.description());
                category.setSelectedItem(value.category()); contact.setText(value.contact());
                statement.setText(value.applicationStatement());
                reason.setText(value.reviewReason() == null ? "" : value.reviewReason());
            }
            saved = capture();
            boolean editable = value == null || value.status() == SellerApplicationStatus.DRAFT
                    || value.status() == SellerApplicationStatus.REJECTED;
            setEditable(editable);
        }

        private void save(boolean closeAfter) {
            if (mutation) return;
            State snapshot = capture(); setMutation(true);
            port.saveApplication(snapshot.command(current)).whenComplete((value, failure) ->
                    SwingUtilities.invokeLater(() -> {
                        setMutation(false);
                        if (failure != null) { fail(failure); return; }
                        current = value; saved = snapshot; changed.run();
                        closed.run();
                    }));
        }

        private void submit() {
            if (mutation) return;
            State snapshot = capture();
            if (!snapshot.valid()) { status.setText("请完整填写申请信息"); return; }
            setMutation(true);
            port.saveApplication(snapshot.command(current)).whenComplete((value, failure) ->
                    SwingUtilities.invokeLater(() -> {
                        if (failure != null) { setMutation(false); fail(failure); return; }
                        current = value;
                        port.submitApplication(new SubmitSellerApplicationCommand(
                                value.applicationId(), value.rowVersion())).whenComplete((submitted, submitFailure) ->
                                SwingUtilities.invokeLater(() -> {
                                    setMutation(false);
                                    if (submitFailure != null) { fail(submitFailure); return; }
                                    current = submitted; saved = snapshot; changed.run(); closed.run();
                                }));
                    }));
        }

        private void setMutation(boolean value) {
            mutation = value; boolean editable = current == null || current.status() == SellerApplicationStatus.DRAFT
                    || current.status() == SellerApplicationStatus.REJECTED;
            setEditable(editable && !value);
        }
        private void setEditable(boolean value) {
            name.setEnabled(value); description.setEnabled(value); category.setEnabled(value);
            contact.setEnabled(value); statement.setEnabled(value); save.setEnabled(value); submit.setEnabled(value);
        }
        private State capture() { return new State(name.getText().trim(), description.getText().trim(),
                Objects.toString(category.getSelectedItem(), "").trim(), contact.getText().trim(), statement.getText().trim()); }
        private void fail(Throwable failure) { String code = ShopUiErrors.code(failure);
            status.setText(ShopUiErrors.message(code)); if (ShopUiErrors.sessionExpired(code)) sessionExpired.run(); }
    }

    private record State(String name, String description, String category, String contact, String statement) {
        boolean valid() { return !name.isBlank() && !description.isBlank() && !category.isBlank()
                && !contact.isBlank() && !statement.isBlank(); }
        SaveSellerDraftCommand command(SellerApplicationView current) { return new SaveSellerDraftCommand(
                current == null ? null : current.applicationId(), name, description, category, contact, statement,
                current == null ? 0 : current.rowVersion()); }
    }
    private static void row(JPanel panel, String label, JComponent value) { panel.add(new JLabel(label)); panel.add(value); }
    private static <T extends JComponent> T named(T value, String name) { value.setName(name); return value; }
}
