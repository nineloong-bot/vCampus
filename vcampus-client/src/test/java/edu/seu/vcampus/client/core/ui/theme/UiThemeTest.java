package edu.seu.vcampus.client.core.ui.theme;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UiThemeTest {
    @Test
    void exposesReviewedPaletteAndDimensions() {
        assertThat(UiColors.BACKGROUND_PAGE).isEqualTo(Color.decode("#FBF7EF"));
        assertThat(UiColors.PRIMARY).isEqualTo(Color.decode("#163B33"));
        assertThat(UiColors.ACCENT).isEqualTo(Color.decode("#AD4432"));
        assertThat(UiDimensions.WINDOW_WIDTH).isEqualTo(1280);
        assertThat(UiDimensions.WINDOW_HEIGHT).isEqualTo(800);
        assertThat(UiDimensions.CONTROL_HEIGHT).isEqualTo(32);
        assertThat(UiDimensions.TABLE_ROW_HEIGHT).isEqualTo(40);
        assertThat(UiSpacing.PAGE_PADDING).isEqualTo(24);
    }

    @Test
    void choosesFirstAvailableSerifFamilyInSpecifiedOrder() {
        assertThat(UiTypography.chooseFamily(Set.of("SimSun", "Serif")))
                .isEqualTo("SimSun");
    }
}
