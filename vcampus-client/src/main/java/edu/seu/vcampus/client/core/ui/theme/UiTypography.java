package edu.seu.vcampus.client.core.ui.theme;

import java.awt.Font;

/** Shared serif typography tokens for the client interface. */
public final class UiTypography {
    public static final Font DISPLAY = font(Font.BOLD, 26);
    public static final Font PAGE_TITLE = font(Font.BOLD, 22);
    public static final Font SECTION_TITLE = font(Font.BOLD, 16);
    public static final Font BODY = font(Font.PLAIN, 14);
    public static final Font CAPTION = font(Font.PLAIN, 12);

    private UiTypography() { }

    private static Font font(int style, int size) {
        String[] candidates = {"Source Han Serif SC", "SimSun", "Songti SC", Font.SERIF};
        String[] available = java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String candidate : candidates) {
            for (String installed : available) {
                if (installed.equalsIgnoreCase(candidate)) return new Font(installed, style, size);
            }
        }
        return new Font(Font.SERIF, style, size);
    }
}
