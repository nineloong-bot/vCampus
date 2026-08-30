package edu.seu.vcampus.client.core.ui.theme;

import javax.swing.UIManager;

/** Installs the shared academic look-and-feel defaults before windows are created. */
public final class UiThemeInstaller {
    private UiThemeInstaller() { }

    /** Applies shared fonts, colors, and square control borders. */
    public static void install() {
        for (Object key : UIManager.getDefaults().keySet()) {
            if (key.toString().endsWith(".font")) UIManager.put(key, UiTypography.BODY);
        }
        UIManager.put("Panel.background", UiColors.BACKGROUND_PAGE);
        UIManager.put("Label.foreground", UiColors.TEXT_PRIMARY);
        UIManager.put("TextField.background", UiColors.BACKGROUND_PAGE);
        UIManager.put("PasswordField.background", UiColors.BACKGROUND_PAGE);
        UIManager.put("TextField.border", UiBorders.LINE);
        UIManager.put("PasswordField.border", UiBorders.LINE);
        UIManager.put("Button.font", UiTypography.BODY);
        UIManager.put("Button.focus", UiColors.FOCUS);
    }
}
