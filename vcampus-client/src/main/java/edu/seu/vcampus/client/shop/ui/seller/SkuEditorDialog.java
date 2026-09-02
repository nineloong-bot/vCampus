package edu.seu.vcampus.client.shop.ui.seller;
import javax.swing.*;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.Optional;
public final class SkuEditorDialog {
    public record Result(String name, BigDecimal unitPrice, long stockQuantity, boolean active) { }
    public static Optional<Result> show(java.awt.Component parent, Result initial) {
        JTextField name = new JTextField(initial == null ? "" : initial.name());
        JTextField price = new JTextField(initial == null ? "0.00" : initial.unitPrice().toPlainString());
        JSpinner stock = new JSpinner(new SpinnerNumberModel(initial == null ? 0L : initial.stockQuantity(), 0L, Long.MAX_VALUE, 1L));
        JCheckBox active = new JCheckBox("启用", initial == null || initial.active());
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("商品种类名称")); form.add(name); form.add(new JLabel("单价")); form.add(price);
        form.add(new JLabel("库存")); form.add(stock); form.add(new JLabel("状态")); form.add(active);
        if (JOptionPane.showConfirmDialog(parent, form, initial == null ? "添加商品种类" : "编辑商品种类",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return Optional.empty();
        return Optional.of(new Result(name.getText().strip(), new BigDecimal(price.getText().strip()),
                ((Number) stock.getValue()).longValue(), active.isSelected()));
    }
    private SkuEditorDialog() { }
}
