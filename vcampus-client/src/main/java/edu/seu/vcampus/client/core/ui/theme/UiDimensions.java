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

    private UiDimensions() { }
}
