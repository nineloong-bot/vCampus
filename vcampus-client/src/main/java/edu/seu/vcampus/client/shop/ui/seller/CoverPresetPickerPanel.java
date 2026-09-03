package edu.seu.vcampus.client.shop.ui.seller;
import edu.seu.vcampus.common.shop.*;
import edu.seu.vcampus.client.shop.ui.style.ShopComponentStyle;
import javax.swing.*;
import java.awt.FlowLayout;
import java.util.Objects;
public final class CoverPresetPickerPanel extends JPanel {
    private final ButtonGroup group = new ButtonGroup();
    public CoverPresetPickerPanel() { super(new FlowLayout(FlowLayout.LEFT, 6, 4));
        ShopComponentStyle.styleTextComponent(this); setName("seller.editor.cover-picker"); }
    public void setCategory(String category) {
        String selected = selectedCoverId(); removeAll(); group.clearSelection();
        for (ShopCoverPreset preset : ShopCoverPresets.forCategory(category)) {
            JToggleButton button = new JToggleButton("▧ " + preset.displayName());
            button.putClientProperty("shop.cover.id", preset.id()); button.setName("seller.editor.cover-preset");
            group.add(button); add(button); if (Objects.equals(selected, preset.id())) button.setSelected(true);
        }
        revalidate(); repaint();
    }
    public String selectedCoverId() {
        var buttons = group.getElements(); while (buttons.hasMoreElements()) { AbstractButton button = buttons.nextElement();
            if (button.isSelected()) return (String) button.getClientProperty("shop.cover.id"); }
        return null;
    }
    public void select(String id) { group.clearSelection(); if (id == null) return; var buttons = group.getElements();
        while (buttons.hasMoreElements()) { AbstractButton button = buttons.nextElement();
            if (id.equals(button.getClientProperty("shop.cover.id"))) button.setSelected(true); } }
    public void setWritable(boolean writable) { for (java.awt.Component child : getComponents()) child.setEnabled(writable); }
}
