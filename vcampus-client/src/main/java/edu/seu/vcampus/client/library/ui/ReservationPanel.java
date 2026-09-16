package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/** Borrower page listing own reservations, queue position, and hold deadline. */
public final class ReservationPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private List<BookReservationView> reservations = List.of();

    public ReservationPanel(LibraryClientService service) {
        super("library.my-reservations", "我的预约",
                "查询本人预约与排队位置；到馆后请在保留期内借阅，逾期自动取消。",
                "书目", "条码", "状态", "排队位置", "保留到期", "预约时间");
        this.service = Objects.requireNonNull(service, "service");
        JButton refresh = new JButton("刷新");
        refresh.addActionListener(event -> refresh());
        JButton cancel = new JButton("取消预约");
        cancel.addActionListener(event -> cancelSelected());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(refresh);
        actions.add(cancel);
        add(actions, BorderLayout.SOUTH);
    }

    public void refresh() {
        long request = beginRequest();
        status.setText("正在加载我的预约……");
        service.getMyReservations().whenComplete((list, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request)) return;
            if (failure != null) {
                LibraryFeedback.failure(this, status, failure, "预约记录加载失败，请重试。");
                return;
            }
            reservations = List.copyOf(list);
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            model.setRowCount(0);
            for (BookReservationView reservation : reservations) model.addRow(new Object[]{
                    readable(reservation.bookTitle(), reservation.bookId()),
                    readable(reservation.copyBarcode(), reservation.copyId()),
                    ReservationUiText.status(reservation.status()),
                    reservation.queuePosition() <= 0 ? "-" : reservation.queuePosition(),
                    reservation.expiresAt() == null ? "-" : reservation.expiresAt().toString(),
                    reservation.reservedAt().toString()});
            status.setText(reservations.isEmpty() ? "暂无预约记录" : "共 " + reservations.size() + " 条预约记录");
        }));
    }

    public void cancelReservation() {
        cancelSelected();
    }

    private void cancelSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= reservations.size()) { status.setText("请先选择一条预约记录"); return; }
        BookReservationView reservation = reservations.get(table.convertRowIndexToModel(row));
        if (reservation.status() != ReservationStatus.WAITING
                && reservation.status() != ReservationStatus.READY) {
            status.setText("该预约已结束，不能取消"); return;
        }
        long request = beginMutation();
        status.setText("正在取消预约……");
        service.cancelReservation(new CancelReservationCommand(reservation.reservationId(),
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

    private static String readable(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
