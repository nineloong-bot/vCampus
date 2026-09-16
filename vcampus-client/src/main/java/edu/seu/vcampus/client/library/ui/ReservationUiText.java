package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.common.library.ReservationStatus;

final class ReservationUiText {
    private ReservationUiText() { }

    static String status(ReservationStatus status) {
        return switch (status) {
            case WAITING -> "排队中";
            case READY -> "已到馆（待借阅）";
            case FULFILLED -> "已完成";
            case EXPIRED -> "已过期";
            case CANCELLED -> "已取消";
        };
    }
}
