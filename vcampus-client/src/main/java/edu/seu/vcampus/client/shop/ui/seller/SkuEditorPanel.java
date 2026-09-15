package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.core.ui.editor.*;
import edu.seu.vcampus.client.shop.ui.style.ShopComponentStyle;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.function.Consumer;

/** Compact embedded editor for one product variety. */
public final class SkuEditorPanel implements EmbeddedEditor {
    /** Editable product-variety values. */
    public record Result(String name, BigDecimal unitPrice, long stockQuantity, boolean active) { }
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JTextField name = new JTextField();
    private final JTextField price = new JTextField();
    private final JSpinner stock;
    private final JCheckBox active;
    private final JLabel status = new JLabel(" ");
    private final Result initial;

    /** Creates an add or edit variety workspace. */
    public SkuEditorPanel(Result value, Consumer<Result> submit, Runnable close) {
        initial = value == null ? new Result("", BigDecimal.ZERO, 0, true) : value;
        name.setText(initial.name()); price.setText(initial.unitPrice().toPlainString());
        stock = new JSpinner(new SpinnerNumberModel(initial.stockQuantity(), 0L, Long.MAX_VALUE, 1L));
        active = new JCheckBox("启用", initial.active());
        ShopComponentStyle.styleTextComponent(name); ShopComponentStyle.styleTextComponent(price);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("商品种类名称")); form.add(name); form.add(new JLabel("单价")); form.add(price);
        form.add(new JLabel("库存")); form.add(stock); form.add(new JLabel("状态")); form.add(active);
        root.add(form);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("取消"); cancel.addActionListener(event -> close.run());
        JButton save = new JButton("保存商品种类"); save.addActionListener(event -> {
            try {
                Result result = capture();
                if (result.name().isBlank() || result.unitPrice().signum() < 0) {
                    status.setText("请填写有效的名称和单价"); return;
                }
                submit.accept(result); close.run();
            } catch (NumberFormatException failure) { status.setText("单价格式不正确"); }
        });
        actions.add(status); actions.add(cancel); actions.add(save); root.add(actions, BorderLayout.SOUTH);
    }

    private Result capture() {
        return new Result(name.getText().strip(), new BigDecimal(price.getText().strip()),
                ((Number) stock.getValue()).longValue(), active.isSelected());
    }
    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return !initial.equals(captureSafely()); }
    private Result captureSafely() { try { return capture(); } catch (RuntimeException ignored) { return null; } }
}
