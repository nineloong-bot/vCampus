package edu.seu.vcampus.client.core.ui.theme;

import java.awt.Font;

/** Shared serif typography tokens for the client interface. */
public final class UiTypography {
    public static final Font DISPLAY = font(Font.BOLD, 26);
    public static final Font PAGE_TITLE = font(Font.BOLD, 22);
    public static final Font SECTION_TITLE = font(Font.BOLD, 16);
    public static final Font BODY = font(Font.PLAIN, 14);
    public static final Font BODY_BOLD = font(Font.BOLD, 14);
    public static final Font CAPTION = font(Font.PLAIN, 12);

    private UiTypography() { }

    private static Font font(int style, int size) {
        return new Font(Font.SERIF, style, size);
    }

    /** Selects the first installed serif font from the shared fallback order. */
    public static String chooseFamily(java.util.Set<String> availableFamilies) {
        for (String candidate : new String[]{"Source Han Serif SC", "SimSun", "Songti SC", "Serif"}) {
            if (availableFamilies.contains(candidate)) return candidate;
        }
        return Font.SERIF;
    }
}
