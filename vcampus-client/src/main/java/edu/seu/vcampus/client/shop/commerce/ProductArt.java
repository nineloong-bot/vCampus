package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import java.awt.*;

/** Shared pastel artwork surface used by catalog, detail and basket. */
final class ProductArt {
    private ProductArt() { }
    static JPanel stack(int gap) {
        JPanel panel = new JPanel(new LayoutManager() {
            @Override public void addLayoutComponent(String name,Component component) { }
            @Override public void removeLayoutComponent(Component component) { }
            @Override public Dimension preferredLayoutSize(Container parent) {
                Insets in=parent.getInsets();int width=0,height=0;
                for(Component child:parent.getComponents())if(child.isVisible()) {
                    Dimension size=child.getPreferredSize();width=Math.max(width,size.width);height+=size.height+gap;
                }
                return new Dimension(width+in.left+in.right,Math.max(0,height-gap)+in.top+in.bottom);
            }
            @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
            @Override public void layoutContainer(Container parent) {
                Insets in=parent.getInsets();int y=in.top;
                for(Component child:parent.getComponents())if(child.isVisible()) {
                    int height=child.getPreferredSize().height;
                    child.setBounds(in.left,y,Math.max(0,parent.getWidth()-in.left-in.right),height);y+=height+gap;
                }
            }
        });
        panel.setOpaque(false);return panel;
    }
    static JPanel panel(String image, int height, int iconSize) {
        JPanel art = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Icon icon = PresetImages.icon(image,iconSize);
                if (icon != null) icon.paintIcon(this, graphics,
                        (getWidth()-icon.getIconWidth())/2,(getHeight()-icon.getIconHeight())/2);
            }
        };
        int color = switch (image == null ? "" : image) {
            case "book" -> 0xeee8da;
            case "pen" -> 0xe0e7e9;
            case "cup" -> 0xe9eddf;
            case "bag" -> 0xe4e9e3;
            case "shirt" -> 0xebe0dc;
            default -> 0xede6d9;
        };
        art.setBackground(new Color(color));
        art.setPreferredSize(new Dimension(iconSize+20,height));
        return art;
    }
}
