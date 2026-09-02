package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.SellerApplicationView;
import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.Optional;

/** Seller-application summary. Editing is owned by an application-modal dialog. */
public final class SellerApplicationPanel extends JPanel {
    /** Compatibility for navigation callers compiled against the former inline form. */
    public enum LeaveChoice { SAVE, DISCARD, CANCEL }
    @FunctionalInterface public interface LeavePrompt {
        LeaveChoice choose(SellerApplicationPanel parent);
    }
    private final SellerShopClientPort port;
    private final Runnable sessionExpired;
    private final SellerApplicationDialogPort dialog;
    private final LatestRequest requests = new LatestRequest();
    private final JLabel status = named(new JLabel("未申请"), "seller.application.status");
    private final JLabel reason = named(new JLabel(), "seller.application.reason");
    private final JLabel name = named(new JLabel(), "seller.application.summary-name");
    private final JButton refresh;
    private final JButton edit;
    private Optional<SellerApplicationView> current = Optional.empty();
    private boolean disposed;

    public SellerApplicationPanel(SellerShopClientPort port, ShopUiKit uiKit, Runnable sessionExpired) {
        this(port, uiKit, sessionExpired, new SwingSellerApplicationDialog(port, uiKit, sessionExpired));
    }

    SellerApplicationPanel(SellerShopClientPort port, ShopUiKit uiKit, Runnable sessionExpired,
            SellerApplicationDialogPort dialog) {
        super(new BorderLayout(8, 8));
        this.port = Objects.requireNonNull(port); this.sessionExpired = Objects.requireNonNull(sessionExpired);
        this.dialog = Objects.requireNonNull(dialog); Objects.requireNonNull(uiKit);
        JPanel summary = uiKit.filterPanel("seller.application.summary", new GridLayout(0, 2, 8, 8));
        addRow(summary, "店铺名称", name); addRow(summary, "申请状态", status);
        addRow(summary, "审核意见", reason);
        refresh = uiKit.secondaryButton("seller.application.refresh", "刷新");
        edit = uiKit.primaryButton("seller.application.edit", "填写申请");
        refresh.addActionListener(event -> load());
        edit.addActionListener(event -> dialog.open(this, current, this::load));
        JPanel actions = uiKit.filterPanel("seller.application.actions", new FlowLayout());
        actions.add(refresh); actions.add(edit);
        add(summary, BorderLayout.CENTER); add(actions, BorderLayout.SOUTH);
    }

    public SellerApplicationPanel(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired, LeavePrompt ignored) {
        this(port, uiKit, sessionExpired,
                new SwingSellerApplicationDialog(port, uiKit, sessionExpired));
    }

    public void load() {
        if (disposed) return;
        long request = requests.begin(); refresh.setEnabled(false);
        port.getMyApplication().whenComplete((value, failure) -> SwingUtilities.invokeLater(() -> {
            if (!requests.accepts(request)) return;
            refresh.setEnabled(true);
            if (failure != null) {
                String code = ShopUiErrors.code(failure); status.setText(ShopUiErrors.message(code));
                if (ShopUiErrors.sessionExpired(code)) sessionExpired.run();
            } else render(value);
        }));
    }

    public void requestLeave(Runnable proceed) { proceed.run(); }
    public void disposePage() { disposed = true; requests.dispose(); }

    private void render(Optional<SellerApplicationView> value) {
        current = value;
        SellerApplicationView application = value.orElse(null);
        name.setText(application == null ? "尚未填写" : application.shopName());
        status.setText(application == null ? "未申请" : statusText(application.status()));
        reason.setText(application == null || application.reviewReason() == null ? "" : application.reviewReason());
        edit.setText(application == null ? "填写申请"
                : application.status() == SellerApplicationStatus.DRAFT
                || application.status() == SellerApplicationStatus.REJECTED ? "修改申请" : "查看申请");
        edit.setEnabled(true);
    }

    private static String statusText(SellerApplicationStatus value) {
        return switch (value) { case DRAFT -> "草稿"; case PENDING -> "待审核";
            case APPROVED -> "已通过"; case REJECTED -> "已驳回"; };
    }
    private static void addRow(JPanel panel, String label, JLabel value) {
        panel.add(new JLabel(label)); panel.add(value);
    }
    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name); return component;
    }
}
