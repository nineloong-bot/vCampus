package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.common.library.BookCopyView;
import edu.seu.vcampus.common.library.CopyStatus;

final class LibraryStatusText {
    private LibraryStatusText() { }

    /** Copy status label, including the reserving reader's card number when the copy is held. */
    static String copy(BookCopyView copy) {
        if (copy.status() == CopyStatus.RESERVED) {
            String holder = copy.reservedForLoginId();
            return holder == null || holder.isBlank() ? "已预约" : "已预约：" + holder;
        }
        return copy(copy.status());
    }

    static String copy(CopyStatus status) {
        return switch (status) {
            case AVAILABLE -> "可借";
            case BORROWED -> "已借出";
            case RESERVED -> "已预约";
            case LOST -> "已遗失";
            case DAMAGED -> "已损坏";
        };
    }
}
