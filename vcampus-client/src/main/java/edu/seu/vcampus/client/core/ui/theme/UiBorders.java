package edu.seu.vcampus.client.core.ui.theme;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

/** Shared borders used by the application shell. */
public final class UiBorders {
    public static final Border HEADER_BOTTOM = BorderFactory.createMatteBorder(
            0, 0, 1, 0, UiColors.PRIMARY_HOVER);
    public static final Border NAVIGATION_RIGHT = BorderFactory.createMatteBorder(
            0, 0, 0, 1, UiColors.BORDER_DEFAULT);
    public static final Border STATUS_TOP = BorderFactory.createMatteBorder(
            1, 0, 0, 0, UiColors.BORDER_DEFAULT);
    public static final Border SECTION = BorderFactory.createMatteBorder(
            1, 0, 1, 0, UiColors.BORDER_DEFAULT);

    private UiBorders() {
    }
}
