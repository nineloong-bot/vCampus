package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.ImportRow;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class XlsxCatalogWriter {
    private XlsxCatalogWriter() { }
    static void write(Path path, List<ImportRow> rows) throws Exception {
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            entry(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
                    + "<sheet name=\"商品导入\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            var sheet = new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            row(sheet,1,XlsxCatalogFiles.HEADERS);
            int number=2;
            for (var r : rows) row(sheet,number++,List.of(r.group(),r.name(),r.description(),r.category(),r.imageId(),r.skuName(),r.price(),r.stock(),r.defaultFlag()));
            entry(zip,"xl/worksheets/sheet1.xml",sheet.append("</sheetData></worksheet>").toString());
        }
    }
    private static void row(StringBuilder xml,int number,List<String> cells) {
        xml.append("<row r=\"").append(number).append("\">");
        for (int i=0;i<cells.size();i++) xml.append("<c r=\"").append((char)('A'+i)).append(number)
                .append("\" t=\"inlineStr\"><is><t>").append(escape(cells.get(i))).append("</t></is></c>");
        xml.append("</row>");
    }
    private static String escape(String value) { return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
    private static void entry(ZipOutputStream zip,String path,String text) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+text).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
}
