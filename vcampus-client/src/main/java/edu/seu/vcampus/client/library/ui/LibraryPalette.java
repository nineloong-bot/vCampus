package edu.seu.vcampus.client.library.ui;

import java.awt.*;

/** Library-local visual tokens; does not alter the shared user-module theme. */
final class LibraryPalette {
    static final Color PAGE = Color.decode("#F5F7FB");
    static final Color SURFACE = Color.WHITE;
    static final Color SUBTLE = Color.decode("#EEF2F7");
    static final Color PRIMARY = Color.decode("#2563EB");
    static final Color PRIMARY_HOVER = Color.decode("#1D4ED8");
    static final Color TEXT = Color.decode("#111827");
    static final Color MUTED = Color.decode("#64748B");
    static final Color BORDER = Color.decode("#D8E0EA");
    static final Color SELECTION = Color.decode("#DBEAFE");

    static final Font TITLE = font(Font.BOLD, 23);
    static final Font SECTION = font(Font.BOLD, 15);
    static final Font BODY = font(Font.PLAIN, 14);
    static final Font CAPTION = font(Font.PLAIN, 12);

    private LibraryPalette() { }

    private static Font font(int style, int size) {
        String[] candidates = {"Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
                "Noto Sans CJK SC", "Segoe UI", Font.SANS_SERIF};
        for (String candidate : candidates) {
            if (!new Font(candidate, style, size).getFamily().equalsIgnoreCase(Font.DIALOG))
                return new Font(candidate, style, size);
        }
        return new Font(Font.SANS_SERIF, style, size);
    }
}
