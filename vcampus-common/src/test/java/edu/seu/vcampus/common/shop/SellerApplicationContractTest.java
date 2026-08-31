package edu.seu.vcampus.common.shop;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SellerApplicationContractTest {
    @Test
    void draftCarriesApplicationStatementThroughSerialization() throws Exception {
        var command = new SaveSellerDraftCommand(null, "校园文具店", "服务师生", "文具",
                "13800000000", "诚信经营计划", 0);

        assertThat(roundTrip(command)).isEqualTo(command);
        assertThat(((SaveSellerDraftCommand) roundTrip(command)).applicationStatement())
                .isEqualTo("诚信经营计划");
    }

    @Test
    void applicationViewCarriesApplicationStatementThroughSerialization() throws Exception {
        var view = new SellerApplicationView("application-1", "student-1", "校园文具店",
                "服务师生", "文具", "13800000000", "诚信经营计划",
                SellerApplicationStatus.PENDING, null, null, Instant.EPOCH, null, 2);

        assertThat(roundTrip(view)).isEqualTo(view);
    }

    @Test
    void adminShopQueryAndSummaryRoundTripWithoutLosingPagingOrCounts() throws Exception {
        var query = new ShopAdminQuery("文具", ShopStatus.ACTIVE, 2, 25);
        var summary = new ShopAdminSummary("shop-1", "student-1", "校园文具店", "文具",
                ShopStatus.ACTIVE, 12, 3);

        assertThat(roundTrip(query)).isEqualTo(query);
        assertThat(roundTrip(summary)).isEqualTo(summary);
    }

    @Test
    void governanceErrorsHaveStablePublicSymbols() {
        assertThat(ShopErrorCode.valueOf("SHOP_NAME_EXISTS"))
                .isEqualTo(ShopErrorCode.SHOP_NAME_EXISTS);
        assertThat(ShopErrorCode.valueOf("SHOP_CONCURRENT_MODIFICATION"))
                .isEqualTo(ShopErrorCode.SHOP_CONCURRENT_MODIFICATION);
    }

    private static Object roundTrip(Object value) throws Exception {
        byte[] bytes;
        try (var buffer = new ByteArrayOutputStream();
             var output = new ObjectOutputStream(buffer)) {
            output.writeObject(value);
            bytes = buffer.toByteArray();
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }
}
