package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Transport-free generic product and multi-row SKU editor. */
public final class ProductEditorPanel extends JPanel {
    private final JTextField name = named(new JTextField(), "seller.editor.name");
    private final JTextField category = named(new JTextField(), "seller.editor.category");
    private final JTextArea description = named(new JTextArea(3, 20), "seller.editor.description");
    private final JTextField cover = named(new JTextField(), "seller.editor.cover");
    private final DefaultTableModel skus = new DefaultTableModel(
            new Object[]{"SKU ID", "名称", "单价", "库存", "启用", "版本"}, 0);
    private final JTable table = named(new JTable(skus), "seller.editor.skus");
    private ProductView current;

    public ProductEditorPanel(ShopUiKit uiKit) {
        super(new BorderLayout(8, 8));
        category.setEditable(false);
        JPanel fields = uiKit.filterPanel("seller.editor.fields", new GridLayout(0, 2, 8, 4));
        row(fields, "商品名称", name); row(fields, "类别", category);
        row(fields, "说明", new JScrollPane(description)); row(fields, "封面 HTTPS URL", cover);
        JButton addSku = uiKit.secondaryButton("seller.editor.add-sku", "添加 SKU");
        addSku.addActionListener(event -> skus.addRow(new Object[]{null, "", "0.00", 0L, true, 0L}));
        add(fields, BorderLayout.NORTH); add(new JScrollPane(table), BorderLayout.CENTER);
        add(addSku, BorderLayout.SOUTH); clear("");
    }

    public void clear(String inheritedCategory) {
        current = null; name.setText(""); category.setText(inheritedCategory);
        description.setText(""); cover.setText(""); skus.setRowCount(0);
        skus.addRow(new Object[]{null, "", "0.00", 0L, true, 0L});
    }

    public void load(ProductView product) {
        current = product; name.setText(product.productName()); category.setText(product.category());
        description.setText(product.description()); cover.setText(product.coverImageUrl() == null
                ? "" : product.coverImageUrl()); skus.setRowCount(0);
        for (ProductSkuView sku : product.skus()) skus.addRow(new Object[]{sku.skuId(), sku.skuName(),
                sku.unitPrice().toPlainString(), sku.stockQuantity(), sku.active(), sku.rowVersion()});
    }

    public CreateProductCommand createCommand() {
        return new CreateProductCommand(name.getText(), category.getText(), description.getText(),
                nullableCover(), createSkus());
    }

    public UpdateProductCommand updateCommand() {
        if (current == null) throw new IllegalStateException("No product loaded");
        return new UpdateProductCommand(current.productId(), name.getText(), category.getText(),
                description.getText(), nullableCover(), updateSkus(), current.rowVersion());
    }

    public void setWritable(boolean writable) {
        name.setEnabled(writable); description.setEnabled(writable); cover.setEnabled(writable);
        table.setEnabled(writable);
    }

    private List<CreateSkuCommand> createSkus() {
        List<CreateSkuCommand> values = new ArrayList<>();
        for (int row = 0; row < skus.getRowCount(); row++) values.add(new CreateSkuCommand(
                text(row, 1), new BigDecimal(text(row, 2)), number(row, 3), bool(row, 4)));
        return values;
    }
    private List<UpsertSkuCommand> updateSkus() {
        List<UpsertSkuCommand> values = new ArrayList<>();
        for (int row = 0; row < skus.getRowCount(); row++) values.add(new UpsertSkuCommand(
                blankToNull(text(row, 0)), text(row, 1), new BigDecimal(text(row, 2)),
                number(row, 3), bool(row, 4), number(row, 5)));
        return values;
    }
    private String nullableCover() { return blankToNull(cover.getText()); }
    private String text(int row, int col) { return String.valueOf(skus.getValueAt(row, col)); }
    private long number(int row, int col) { return Long.parseLong(text(row, col)); }
    private boolean bool(int row, int col) { return Boolean.parseBoolean(text(row, col)); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static void row(JPanel panel, String label, JComponent component) {
        panel.add(new JLabel(label)); panel.add(component);
    }
    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name); return component;
    }
}
