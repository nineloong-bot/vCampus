package edu.seu.vcampus.server.shop.catalog;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import static edu.seu.vcampus.server.shop.catalog.CatalogSql.*;

/** Pure server-side spreadsheet validation retaining original row numbers. */
public final class ImportValidator {
    /** Creates the stateless validator. */
    public ImportValidator() { }
    /** Validates entire groups, marking innocent rows affected by a sibling failure. */
    public ImportPreview validate(List<ImportRow> rows, Set<String> existingNames) {
        require(rows != null && !rows.isEmpty() && rows.size() <= 1000, "每批须为1至1000个规格行");
        var groups = new LinkedHashMap<String, List<Integer>>();
        var errors = new ArrayList<List<String>>();
        var lineNumbers = new HashSet<Integer>();
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            var e = new ArrayList<String>();
            errors.add(e);
            if (r.line() < 2 || !lineNumbers.add(r.line())) e.add("原始行号无效或重复");
            if (text(r.group()).isEmpty()) e.add("缺少商品分组编号");
            if (text(r.name()).isEmpty() || text(r.name()).length() > 200) e.add("商品名称须为1至200字");
            if (text(r.description()).isEmpty()) e.add("缺少商品介绍");
            if (!Set.of("ordinary", "普通白名单商品").contains(text(r.category()))) e.add("仅允许普通白名单商品");
            if (!text(r.imageId()).isEmpty() && !ProductRules.IMAGES.contains(text(r.imageId()))) e.add("图片编号不存在");
            if (text(r.skuName()).isEmpty() || text(r.skuName()).length() > 128) e.add("规格名称须为1至128字");
            try {
                require(text(r.price()).matches("[0-9]+(\\.[0-9]{1,2})?"), "价格格式错误");
                ProductRules.price(new BigDecimal(text(r.price())));
            } catch (RuntimeException ex) { e.add("价格须大于0且最多两位小数"); }
            try {
                require(text(r.stock()).matches("[0-9]+"), "库存格式错误");
                Integer.parseInt(text(r.stock()));
            } catch (RuntimeException ex) { e.add("库存须为非负整数"); }
            if (!Set.of("是", "否").contains(text(r.defaultFlag()))) e.add("默认规格只能填写是或否");
            String key = text(r.group()).isEmpty() ? "\u0000" + i : text(r.group());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(i);
        }
        int valid = 0;
        var affected = new HashSet<Integer>();
        for (var indices : groups.values()) {
            var first = rows.get(indices.getFirst());
            long defaults = indices.stream().filter(i -> text(rows.get(i).defaultFlag()).equals("是")).count();
            var names = new HashSet<String>();
            for (int i : indices) {
                var r = rows.get(i);
                if (!text(r.name()).equals(text(first.name())) || !text(r.description()).equals(text(first.description()))
                        || !text(r.category()).equals(text(first.category())) || !text(r.imageId()).equals(text(first.imageId())))
                    errors.get(i).add("同组商品基本信息不一致");
                if (defaults != 1) errors.get(i).add("同组必须恰好有一个默认规格");
                if (!names.add(text(r.skuName()))) errors.get(i).add("同组规格名称重复");
            }
            if (indices.stream().anyMatch(i -> !errors.get(i).isEmpty())) {
                for (int i : indices) if (errors.get(i).isEmpty()) affected.add(i);
            } else valid++;
        }
        var results = new ArrayList<ImportRowResult>();
        for (int i = 0; i < rows.size(); i++) {
            String status = !errors.get(i).isEmpty() ? "ERROR" : affected.contains(i) ? "AFFECTED" : "VALID";
            if (affected.contains(i)) errors.get(i).add("同组其他行有错误，整组不导入");
            String warning = existingNames.contains(text(rows.get(i).name())) ? "店内已有同名商品，请检查" : "";
            results.add(new ImportRowResult(rows.get(i), status, errors.get(i), warning));
        }
        int failed = (int) results.stream().filter(r -> !r.status().equals("VALID")).count();
        return new ImportPreview(results, rows.size(), groups.size(), valid, failed);
    }
}
