package edu.seu.vcampus.client.core.ui.theme;

import java.awt.Dimension;

/** Shared dimensions for the authentication windows and application shell. */
public final class UiDimensions {
    public static final Dimension LOGIN_WINDOW = new Dimension(760, 480);
    public static final Dimension MAIN_WINDOW = new Dimension(1280, 800);
    public static final Dimension MAIN_MINIMUM = new Dimension(1024, 680);
    public static final Dimension PASSWORD_DIALOG = new Dimension(520, 430);
    public static final int HEADER_HEIGHT = 56;
    public static final int NAVIGATION_WIDTH = 184;
    public static final int STATUS_HEIGHT = 28;
    public static final int CONTROL_HEIGHT = 32;
    public static final int NAVIGATION_ITEM_HEIGHT = 44;
    public static final int TABLE_ROW_HEIGHT = 40;

    // Course screens use these aliases while the shared shell uses Dimension tokens above.
    public static final int WINDOW_WIDTH = MAIN_WINDOW.width;
    public static final int WINDOW_HEIGHT = MAIN_WINDOW.height;
    public static final int WINDOW_MIN_WIDTH = MAIN_MINIMUM.width;
    public static final int WINDOW_MIN_HEIGHT = MAIN_MINIMUM.height;
    public static final int STATUS_BAR_HEIGHT = STATUS_HEIGHT;

    private UiDimensions() { }
}
