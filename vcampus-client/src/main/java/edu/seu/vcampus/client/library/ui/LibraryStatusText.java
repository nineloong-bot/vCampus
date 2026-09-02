package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.common.library.CopyStatus;

final class LibraryStatusText {
    private LibraryStatusText() { }

    static String copy(CopyStatus status) {
        return switch (status) {
            case AVAILABLE -> "可借";
            case BORROWED -> "已借出";
            case LOST -> "已遗失";
            case DAMAGED -> "已损坏";
        };
    }
}
