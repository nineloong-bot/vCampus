package edu.seu.vcampus.common.shop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShopCoverPresetsTest {
    @Test
    void exposesFourUniqueBuiltinCoversForEveryShopCategory() {
        assertThat(ShopCoverPresets.all()).hasSize(20)
                .extracting(ShopCoverPreset::id).doesNotHaveDuplicates();
        for (String category : ShopCategories.ALL) {
            assertThat(ShopCoverPresets.forCategory(category)).hasSize(4)
                    .allMatch(preset -> preset.category().equals(category))
                    .allMatch(preset -> preset.id().startsWith("builtin://shop/"));
        }
    }
}
