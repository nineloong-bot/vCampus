package edu.seu.vcampus.client.core.ui.theme;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

/** Shared square, fine-line borders for client components. */
public final class UiBorders {
    public static final Border LINE = BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT);
    public static final Border FOCUS = BorderFactory.createLineBorder(UiColors.FOCUS, 2);

    private UiBorders() { }

    /** Creates a standard page inset without duplicating spacing values. */
    public static Border pageInset() {
        return BorderFactory.createEmptyBorder(UiSpacing.SPACE_6, UiSpacing.SPACE_6,
                UiSpacing.SPACE_6, UiSpacing.SPACE_6);
    }
}
