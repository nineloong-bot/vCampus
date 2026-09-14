package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.common.library.CopyStatus;/**
 * 图书馆藏与借阅状态国际化及本地化中文显示文本映射工具类。
 */


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
