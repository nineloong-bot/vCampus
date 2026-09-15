package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.ImportRow;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Bounded local XLSX reader; only raw row data crosses the Socket boundary. */
public final class XlsxCatalogFiles {
    static final List<String> HEADERS = List.of("商品分组编号","商品名称","商品介绍","准入类目","图片编号","规格名称","价格","库存","默认规格");
    private XlsxCatalogFiles() { }
    /** Reads the 商品导入 sheet, refusing formulas, external XML entities and oversized archives. */
    public static List<ImportRow> read(Path path) throws Exception {
        if (Files.size(path) > 8_000_000) throw new IllegalArgumentException("文件不能超过8MB");
        Map<String,byte[]> files = new HashMap<>();
        long total = 0;
        try (var zip = new ZipInputStream(Files.newInputStream(path))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (files.size() > 200) throw new IllegalArgumentException("Excel文件结构过大");
                byte[] data = zip.readNBytes(8_000_001);
                total += data.length;
                if (total > 8_000_000) throw new IllegalArgumentException("Excel解压内容超过8MB");
                files.put(entry.getName(), data);
            }
        }
        Document workbook = xml(files.get("xl/workbook.xml"));
        String relationship = null;
        var sheets = workbook.getElementsByTagNameNS("*", "sheet");
        for (int i=0;i<sheets.getLength();i++) {
            Element sheet = (Element) sheets.item(i);
            if (sheet.getAttribute("name").equals("商品导入")) relationship = sheet.getAttributeNS(
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
        }
        if (relationship == null) throw new IllegalArgumentException("缺少“商品导入”工作表");
        String target = null;
        var links = xml(files.get("xl/_rels/workbook.xml.rels")).getElementsByTagNameNS("*", "Relationship");
        for (int i=0;i<links.getLength();i++) {
            Element link = (Element) links.item(i);
            if (link.getAttribute("Id").equals(relationship) && !link.getAttribute("TargetMode").equals("External"))
                target = link.getAttribute("Target");
        }
        if (target == null || target.contains("..")) throw new IllegalArgumentException("工作表引用无效");
        String key = target.startsWith("/") ? target.substring(1) : "xl/"+target;
        var strings = new ArrayList<String>();
        if (files.containsKey("xl/sharedStrings.xml")) {
            var values = xml(files.get("xl/sharedStrings.xml")).getElementsByTagNameNS("*", "si");
            for (int i=0;i<values.getLength();i++) strings.add(values.item(i).getTextContent());
        }
        Document sheet = xml(files.get(key));
        if (sheet.getElementsByTagNameNS("*", "f").getLength() != 0) throw new IllegalArgumentException("文件含公式，请替换为实际值");
        var result = new ArrayList<ImportRow>();
        var rows = sheet.getElementsByTagNameNS("*", "row");
        boolean header = false;
        for (int i=0;i<rows.getLength();i++) {
            Element row = (Element) rows.item(i);
            int number = Integer.parseInt(row.getAttribute("r"));
            String[] values = new String[9]; java.util.Arrays.fill(values, "");
            var cells = row.getElementsByTagNameNS("*", "c");
            for (int j=0;j<cells.getLength();j++) {
                Element cell = (Element) cells.item(j);
                String reference = cell.getAttribute("r");
                int column = column(reference);
                if (column < 0 || column >= values.length) continue;
                String value;
                if (cell.getAttribute("t").equals("inlineStr")) value = cell.getTextContent();
                else {
                    var v = cell.getElementsByTagNameNS("*", "v");
                    value = v.getLength()==0 ? "" : v.item(0).getTextContent();
                    if (cell.getAttribute("t").equals("s")) value = strings.get(Integer.parseInt(value));
                }
                values[column] = value.trim();
            }
            if (number == 1) {
                if (!List.of(values).equals(HEADERS)) throw new IllegalArgumentException("第一行列名不匹配，请使用模板");
                header = true; continue;
            }
            if (java.util.Arrays.stream(values).allMatch(String::isBlank)) continue;
            if (result.size() >= 1000) throw new IllegalArgumentException("每批最多1000个规格行");
            result.add(new ImportRow(number,values[0],values[1],values[2],values[3],values[4],values[5],values[6],values[7],values[8]));
        }
        if (!header) throw new IllegalArgumentException("缺少模板列名");
        return List.copyOf(result);
    }
    /** Writes a reusable template or sample workbook using ordinary string cells. */
    public static void write(Path path, List<ImportRow> rows) throws Exception { XlsxCatalogWriter.write(path, rows); }
    private static int column(String ref) {
        int result = 0;
        for (char c : ref.toCharArray()) {
            if (c < 'A' || c > 'Z') break;
            result = result * 26 + c - 'A' + 1;
        }
        return result-1;
    }
    private static Document xml(byte[] data) throws Exception {
        if (data == null) throw new IllegalArgumentException("Excel文件结构不完整");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(data));
    }
}
