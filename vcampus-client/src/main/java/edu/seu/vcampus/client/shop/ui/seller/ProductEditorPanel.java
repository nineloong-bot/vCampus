package edu.seu.vcampus.client.shop.ui.seller;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Product editor exposing business fields while retaining SKU identity internally. */
public final class ProductEditorPanel extends JPanel {
    private final JTextField name = named(new JTextField(), "seller.editor.name");
    private final JTextField category = named(new JTextField(), "seller.editor.category");
    private final JTextArea description = named(new JTextArea(3, 20), "seller.editor.description");
    private final CoverPresetPickerPanel covers = new CoverPresetPickerPanel();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"规格名称", "单价", "库存", "状态", "操作"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = named(new JTable(model), "seller.editor.skus");
    private final java.util.List<EditableSku> skus = new ArrayList<>();
    private ProductView current;
    private boolean writable = true;

    public ProductEditorPanel(ShopUiKit uiKit) {
        super(new BorderLayout(8, 8)); category.setEditable(false);
        JPanel fields = uiKit.filterPanel("seller.editor.fields", new GridLayout(0, 2, 8, 4));
        row(fields, "商品名称", name); row(fields, "类别", category);
        row(fields, "说明", new JScrollPane(description)); row(fields, "封面", covers);
        JButton addSku = uiKit.secondaryButton("seller.editor.add-sku", "添加规格");
        addSku.addActionListener(event -> editSku(-1));
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && writable) editSku(table.rowAtPoint(event.getPoint()));
            }
        });
        add(fields, BorderLayout.NORTH); add(new JScrollPane(table), BorderLayout.CENTER); add(addSku, BorderLayout.SOUTH);
        clear("文具");
    }
    public void clear(String inheritedCategory) {
        current = null; name.setText(""); category.setText(inheritedCategory); description.setText("");
        covers.setCategory(inheritedCategory); covers.select(null); skus.clear();
        skus.add(new EditableSku(null, "", java.math.BigDecimal.ZERO, 0, true, 0)); refreshSkus();
    }
    public void load(ProductView product) {
        current = product; name.setText(product.productName()); category.setText(product.category());
        description.setText(product.description()); covers.setCategory(product.category()); covers.select(product.coverImageUrl());
        skus.clear(); for (ProductSkuView sku : product.skus()) skus.add(new EditableSku(sku.skuId(), sku.skuName(),
                sku.unitPrice(), sku.stockQuantity(), sku.active(), sku.rowVersion())); refreshSkus();
    }
    public CreateProductCommand createCommand() { return new CreateProductCommand(name.getText(), category.getText(),
            description.getText(), covers.selectedCoverId(), skus.stream().map(EditableSku::create).toList()); }
    public UpdateProductCommand updateCommand() {
        if (current == null) throw new IllegalStateException("No product loaded");
        return new UpdateProductCommand(current.productId(), name.getText(), category.getText(), description.getText(),
                covers.selectedCoverId(), skus.stream().map(EditableSku::update).toList(), current.rowVersion());
    }
    public void setWritable(boolean writable) { this.writable = writable; name.setEnabled(writable);
        description.setEnabled(writable); covers.setWritable(writable); table.setEnabled(writable); }
    private void editSku(int index) {
        EditableSku old = index >= 0 && index < skus.size() ? skus.get(index) : null;
        SkuEditorDialog.show(this, old == null ? null : old.result()).ifPresent(result -> {
            EditableSku value = old == null ? EditableSku.created(result) : old.with(result);
            if (old == null) skus.add(value); else skus.set(index, value); refreshSkus();
        });
    }
    private void refreshSkus() { model.setRowCount(0); for (EditableSku sku : skus) model.addRow(new Object[]{
            sku.name, sku.price, sku.stock, sku.active ? "启用" : "停用", "双击编辑"}); }
    private record EditableSku(String id, String name, java.math.BigDecimal price, long stock, boolean active, long version) {
        static EditableSku created(SkuEditorDialog.Result v) { return new EditableSku(null, v.name(), v.unitPrice(), v.stockQuantity(), v.active(), 0); }
        EditableSku with(SkuEditorDialog.Result v) { return new EditableSku(id, v.name(), v.unitPrice(), v.stockQuantity(), v.active(), version); }
        SkuEditorDialog.Result result() { return new SkuEditorDialog.Result(name, price, stock, active); }
        CreateSkuCommand create() { return new CreateSkuCommand(name, price, stock, active); }
        UpsertSkuCommand update() { return new UpsertSkuCommand(id, name, price, stock, active, version); }
    }
    private static void row(JPanel panel, String label, JComponent component) { panel.add(new JLabel(label)); panel.add(component); }
    private static <T extends JComponent> T named(T value, String name) { value.setName(name); return value; }
}
