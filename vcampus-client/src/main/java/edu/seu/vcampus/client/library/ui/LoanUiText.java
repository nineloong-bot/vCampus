package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.library.LoanView;

final class LoanUiText {
    private LoanUiText() { }

    static String title(LoanView loan) { return readable(loan.bookTitle(), loan.bookId()); }
    static String barcode(LoanView loan) { return readable(loan.copyBarcode(), loan.copyId()); }
    static String status(LoanStatus status) {
        return switch (status) {
            case ACTIVE -> "借阅中";
            case OVERDUE -> "已逾期";
            case RETURNED -> "已归还";
            case LOST -> "已遗失";
        };
    }
    private static String readable(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
