package edu.seu.vcampus.client.core.ui.theme;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Set;

/** Serif typography roles and platform fallback selection. */
public final class UiTypography {
    private static final String[] PREFERRED_FAMILIES = {
            "Source Han Serif SC", "SimSun", "Songti SC", "Serif"
    };
    public static final String FAMILY = chooseFamily(Set.copyOf(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())));
    public static final Font DISPLAY = new Font(FAMILY, Font.BOLD, 20);
    public static final Font PAGE_TITLE = new Font(FAMILY, Font.BOLD, 22);
    public static final Font SECTION_TITLE = new Font(FAMILY, Font.BOLD, 16);
    public static final Font BODY = new Font(FAMILY, Font.PLAIN, 14);
    public static final Font BODY_BOLD = new Font(FAMILY, Font.BOLD, 14);
    public static final Font CAPTION = new Font(FAMILY, Font.PLAIN, 12);

    private UiTypography() {
    }

    /** Chooses the first available family in the required fallback order. */
    public static String chooseFamily(Set<String> availableFamilies) {
        for (String family : PREFERRED_FAMILIES) {
            if (availableFamilies.contains(family)) {
                return family;
            }
        }
        return Font.SERIF;
    }
}
