package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/** Administrator page for inspecting and cancelling reservation queues. */
public final class ReservationAdminPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private final JTextField keyword = new JTextField(12);
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[]{
            "全部状态", "WAITING", "READY", "FULFILLED", "EXPIRED", "CANCELLED"});
    private final LibraryPagination pagination = new LibraryPagination(this::loadPage);
    private List<BookReservationView> reservations = List.of();

    public ReservationAdminPanel(LibraryClientService service) {
        super("library.reservation-admin", "预约管理",
                "查询全校预约队列；取消预约后副本会顺延给下一位读者或恢复可借。",
                "预约号", "预约人", "书目", "条码", "状态", "排队位置", "保留到期", "预约时间");
        this.service = Objects.requireNonNull(service, "service");
        JButton refresh = new JButton("查询");
        refresh.addActionListener(event -> refresh());
        JButton cancel = new JButton("取消预约");
        cancel.addActionListener(event -> cancelSelected());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(new JLabel("关键字（书名/条码/账号）")); actions.add(keyword);
        actions.add(statusFilter); actions.add(refresh); actions.add(cancel); actions.add(pagination);
        add(actions, BorderLayout.SOUTH);
        keyword.addActionListener(event -> refresh());
    }

    public void refresh() { loadPage(1); }

    private void loadPage(int pageNumber) {
        long request = beginRequest();
        status.setText("正在加载预约记录……");
        String selected = (String) statusFilter.getSelectedItem();
        ReservationStatus filter = "全部状态".equals(selected) ? null : ReservationStatus.valueOf(selected);
        String text = keyword.getText().trim();
        pagination.setLoading(true);
        service.searchReservations(new AdminReservationSearchQuery(text.isEmpty() ? null : text,
                filter, pageNumber, 20)).whenComplete((page, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    pagination.setLoading(false);
                    if (failure != null) {
                        LibraryFeedback.failure(this, status, failure, "预约记录加载失败，请重试。");
                        return;
                    }
                    pagination.showPage(page.page(), page.pageSize(), page.total());
                    reservations = List.copyOf(page.items());
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.setRowCount(0);
                    for (BookReservationView reservation : reservations) model.addRow(new Object[]{
                            shortId(reservation.reservationId()),
                            readable(reservation.reserverLoginId(), reservation.userId()),
                            readable(reservation.bookTitle(), reservation.bookId()),
                            readable(reservation.copyBarcode(), reservation.copyId()),
                            ReservationUiText.status(reservation.status()),
                            reservation.queuePosition() <= 0 ? "-" : reservation.queuePosition(),
                            reservation.expiresAt() == null ? "-" : reservation.expiresAt().toString(),
                            reservation.reservedAt().toString()});
                    status.setText(page.items().isEmpty() ? "未找到预约记录"
                            : "共 " + page.total() + " 条预约记录");
                }));
    }

    public void cancelSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= reservations.size()) { status.setText("请先选择一条预约记录"); return; }
        BookReservationView reservation = reservations.get(table.convertRowIndexToModel(row));
        if (reservation.status() != ReservationStatus.WAITING
                && reservation.status() != ReservationStatus.READY) {
            status.setText("该预约已结束，不能取消"); return;
        }
        long request = beginMutation();
        status.setText("正在取消预约……");
        service.adminCancelReservation(new AdminCancelReservationCommand(reservation.reservationId(),
                        reservation.rowVersion()))
                .whenComplete((cancelled, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!acceptsMutation(request)) return;
                    if (failure != null) {
                        LibraryFeedback.failure(this, status, failure, "取消失败，请刷新后重试。");
                        return;
                    }
                    status.setText("预约已取消，队列已顺延");
                    refresh();
                    mutationSucceeded();
                }));
    }

    private static String shortId(String value) {
        if (value == null) return "-";
        return value.length() <= 8 ? value : value.substring(0, 8).toUpperCase();
    }

    private static String readable(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
