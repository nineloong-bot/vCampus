package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import java.awt.*;

/** Library-local visual tokens; does not alter the shared user-module theme. */
final class LibraryPalette {
    static final Color PAGE = UiColors.BACKGROUND_PAGE;
    static final Color SURFACE = Color.WHITE;
    static final Color SUBTLE = UiColors.BACKGROUND_SUBTLE;
    static final Color PRIMARY = UiColors.PRIMARY;
    static final Color PRIMARY_HOVER = UiColors.PRIMARY_HOVER;
    static final Color TEXT = UiColors.TEXT_PRIMARY;
    static final Color MUTED = UiColors.TEXT_SECONDARY;
    static final Color BORDER = UiColors.BORDER_DEFAULT;
    static final Color SELECTION = UiColors.BACKGROUND_SUBTLE;

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
