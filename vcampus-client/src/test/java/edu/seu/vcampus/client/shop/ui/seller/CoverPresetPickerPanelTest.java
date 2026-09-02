package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.common.shop.ShopCategories;
import edu.seu.vcampus.common.shop.ShopCoverPresets;
import org.junit.jupiter.api.Test;

import javax.swing.JToggleButton;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CoverPresetPickerPanelTest {
    @Test
    void everyCategoryShowsExactlyFourMatchingCovers() throws Exception {
        CoverPresetPickerPanel picker = ShopSwingTestSupport.onEdt(CoverPresetPickerPanel::new);
        for (String category : ShopCategories.ALL) {
            ShopSwingTestSupport.onEdt(() -> picker.setCategory(category));
            assertThat(Arrays.stream(picker.getComponents()).map(component ->
                    ((JToggleButton) component).getClientProperty("shop.cover.id")))
                    .containsExactlyElementsOf(ShopCoverPresets.forCategory(category).stream()
                            .map(value -> (Object) value.id()).toList());
        }
    }

    @Test
    void changingCategoryClearsAnIncompatibleSelection() throws Exception {
        CoverPresetPickerPanel picker = ShopSwingTestSupport.onEdt(CoverPresetPickerPanel::new);
        String stationery = ShopCoverPresets.forCategory("文具").getFirst().id();
        ShopSwingTestSupport.onEdt(() -> { picker.setCategory("文具"); picker.select(stationery); picker.setCategory("图书"); });
        assertThat(picker.selectedCoverId()).isNull();
    }
}
