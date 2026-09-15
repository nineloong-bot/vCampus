package edu.seu.vcampus.server.shop.catalog;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.ImportRow;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImportValidationTest {
    @Test void rejectsWholeGroupAndRetainsOriginalLine() {
        var rows = List.of(new ImportRow(4,"A","书","介绍","ordinary","book","标准","12.00","3","是"),
                new ImportRow(7,"A","书","介绍","ordinary","book","第二款","-1","3","否"),
                new ImportRow(9,"B","书","介绍","ordinary","","标准","1","0","是"));
        var result = new ImportValidator().validate(rows, Set.of("书"));
        assertEquals(1, result.validGroups());
        assertEquals("AFFECTED", result.rows().getFirst().status());
        assertEquals(7, result.rows().get(1).row().line());
        assertEquals(2, result.failedRows());
        assertFalse(result.rows().getLast().warning().isBlank());
    }
}
