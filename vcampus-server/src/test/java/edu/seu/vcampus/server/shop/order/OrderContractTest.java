package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.common.shop.order.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderContractTest {
    @Test void rejectsDuplicateSkuAndInvalidQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new OrderLine("sku", 0, BigDecimal.ONE));
        var line = new OrderLine("sku", 1, BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> new CheckoutRequest(List.of(line, line), true));
    }
    @Test void rejectsEmptySelectionAndMutableInput() {
        assertThrows(IllegalArgumentException.class, () -> new CheckoutRequest(List.of(), true));
        var ids = new java.util.ArrayList<>(List.of("order"));
        var action = new OrderAction(ids, "");
        ids.clear();
        assertEquals(List.of("order"), action.orderIds());
    }
}
