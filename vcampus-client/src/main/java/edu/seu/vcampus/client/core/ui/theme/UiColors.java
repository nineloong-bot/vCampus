package edu.seu.vcampus.client.core.ui.theme;

import java.awt.Color;

/** Shared color tokens for the traditional academic client interface. */
public final class UiColors {
    public static final Color BACKGROUND_PAGE = color("#FBF7EF");
    public static final Color BACKGROUND_SUBTLE = color("#ECE3D1");
    public static final Color BACKGROUND_NAV = color("#E3D8C2");
    public static final Color PRIMARY = color("#163B33");
    public static final Color PRIMARY_HOVER = color("#214C43");
    public static final Color ACCENT = color("#AD4432");
    public static final Color TEXT_PRIMARY = color("#2E2C26");
    public static final Color TEXT_SECONDARY = color("#6D6556");
    public static final Color TEXT_ON_PRIMARY = color("#F8F1E4");
    public static final Color BORDER_DEFAULT = color("#C6B896");
    public static final Color FOCUS = color("#AD4432");
    public static final Color SUCCESS_BG = color("#DCE6D8");
    public static final Color SUCCESS_FG = color("#27533F");
    public static final Color ERROR_BG = color("#EED6D0");
    public static final Color ERROR_FG = color("#7D3028");
    public static final Color DISABLED_BG = color("#E4DED2");
    public static final Color DISABLED_FG = color("#8B8477");

    private UiColors() { }

    private static Color color(String value) {
        return Color.decode(value);
    }
}
