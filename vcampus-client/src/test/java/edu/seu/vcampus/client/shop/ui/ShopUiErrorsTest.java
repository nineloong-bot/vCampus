package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.common.shop.ShopErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ShopUiErrorsTest {
    @ParameterizedTest
    @EnumSource(ShopErrorCode.class)
    void everyShopErrorCodeHasChineseUserText(ShopErrorCode code) {
        assertThat(ShopUiErrors.message(code.name()))
                .isNotBlank()
                .doesNotContain(code.name())
                .matches(".*[\\u4e00-\\u9fff].*");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AUTH_SESSION_EXPIRED", "COMMON_VALIDATION_FAILED", "COMMON_INTERNAL_ERROR",
            "NETWORK_TIMEOUT", "NETWORK_CONNECTION_FAILED"
    })
    void commonFailuresShownByShopHaveChineseUserText(String code) {
        assertThat(ShopUiErrors.message(code))
                .isNotBlank()
                .doesNotContain(code)
                .matches(".*[\\u4e00-\\u9fff].*");
    }

    @Test
    void unknownFailureKeepsDiagnosticCodeButUsesChineseFallback() {
        RuntimeException failure = new RuntimeException(
                "wrapper", new IllegalStateException("UNRECOGNIZED_CODE"));

        assertThat(ShopUiErrors.code(failure)).isEqualTo("UNRECOGNIZED_CODE");
        assertThat(ShopUiErrors.message(failure)).isEqualTo("操作失败，请稍后重试");
    }
}
