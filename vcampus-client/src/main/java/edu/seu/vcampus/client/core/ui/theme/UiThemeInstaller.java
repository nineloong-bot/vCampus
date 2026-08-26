package edu.seu.vcampus.client.core.ui.theme;

import javax.swing.UIManager;
import java.util.concurrent.atomic.AtomicBoolean;

/** Installs common Swing defaults once before windows are created. */
public final class UiThemeInstaller {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private UiThemeInstaller() {
    }

    /** Applies the shared academic visual defaults. */
    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        UIManager.put("Label.font", UiTypography.BODY);
        UIManager.put("Button.font", UiTypography.BODY);
        UIManager.put("TextField.font", UiTypography.BODY);
        UIManager.put("PasswordField.font", UiTypography.BODY);
        UIManager.put("Panel.background", UiColors.BACKGROUND_PAGE);
        UIManager.put("OptionPane.messageFont", UiTypography.BODY);
        UIManager.put("OptionPane.buttonFont", UiTypography.BODY);
    }
}
