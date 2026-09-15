package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.ImportRow;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class XlsxCatalogFilesTest {
    @TempDir Path directory;
    @Test void templateAndSpecialCharactersRoundTrip() throws Exception {
        Path file = directory.resolve("模板.xlsx");
        var row = new ImportRow(2,"A","书<&","介绍","ordinary","book","标准","2.30","0","是");
        XlsxCatalogFiles.write(file, List.of(row));
        assertEquals(List.of(row), XlsxCatalogFiles.read(file));
    }
    @Test void rejectsFormulasBeforeSendingStructuredRows() throws Exception {
        Path original=directory.resolve("source.xlsx"), changed=directory.resolve("formula.xlsx");
        XlsxCatalogFiles.write(original,List.of(new ImportRow(2,"A","书","介绍","ordinary","book","标准","2","0","是")));
        try(var in=new java.util.zip.ZipInputStream(java.nio.file.Files.newInputStream(original));
            var out=new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(changed))){
            java.util.zip.ZipEntry entry;
            while((entry=in.getNextEntry())!=null){
                byte[] bytes=in.readAllBytes();
                if(entry.getName().equals("xl/worksheets/sheet1.xml"))bytes=new String(bytes,java.nio.charset.StandardCharsets.UTF_8)
                        .replace("</sheetData>","<row r=\"3\"><c r=\"G3\"><f>1+1</f><v>2</v></c></row></sheetData>")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));out.write(bytes);out.closeEntry();
            }
        }
        var error=assertThrows(IllegalArgumentException.class,()->XlsxCatalogFiles.read(changed));
        assertTrue(error.getMessage().contains("公式"));
    }
}
