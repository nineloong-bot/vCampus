package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.Product;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Rounded product cards with preset art and the prototype selection indicator. */
final class ProductTile implements ListCellRenderer<Product> {
    @Override public Component getListCellRendererComponent(JList<? extends Product> list,
            Product product, int index, boolean selected, boolean focus) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.WHITE);
                g.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 26, 26);
                g.dispose();
            }
            @Override protected void paintChildren(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.clip(new RoundRectangle2D.Float(1, 1, getWidth()-2, getHeight()-2, 25, 25));
                super.paintChildren(g);
                g.dispose();
                g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(selected ? CommerceTheme.ACCENT : new Color(0xe0e6de));
                g.setStroke(new BasicStroke(selected ? 2 : 1));
                g.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 26, 26);
                g.dispose();
            }
        };
        card.setOpaque(false);
        JPanel art = ProductArt.panel(product.imageId(), 146, 96);
        art.setName("product.art");
        JLabel tick = new JLabel(selected ? "✓" : "", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(selected ? CommerceTheme.ACCENT : new Color(0xffffff));
                g.fillOval(0, 0, 20, 20);
                g.setColor(new Color(0xcad4c8));g.drawOval(0, 0, 20, 20);g.dispose();
                super.paintComponent(graphics);
            }
        };
        tick.setName("product.selection");tick.setForeground(Color.WHITE);
        tick.setPreferredSize(new Dimension(21,21));
        JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT,12,12));
        corner.setOpaque(false);corner.add(tick);art.add(corner,BorderLayout.NORTH);
        card.add(art,BorderLayout.NORTH);
        JPanel info = ProductArt.stack(0);
        info.setBorder(BorderFactory.createEmptyBorder(12,15,10,15));
        info.add(CommerceTheme.heading(product.name(),14));
        info.add(Box.createVerticalStrut(5));
        info.add(CommerceTheme.muted(product.shopName()+" ↗"));
        info.add(Box.createVerticalStrut(12));
        var sku = product.skus().stream().filter(s -> s.id().equals(product.defaultSkuId())).findFirst().orElse(null);
        JPanel bottom = new JPanel(new BorderLayout());bottom.setOpaque(false);
        JLabel price = CommerceTheme.heading(sku == null || sku.price() == null ? "待完善" : "¥ " + sku.price().toPlainString(),21);
        price.setForeground(CommerceTheme.ACCENT);bottom.add(price,BorderLayout.WEST);
        bottom.add(CommerceTheme.muted("已售 "+product.sales()),BorderLayout.EAST);
        info.add(bottom);
        info.add(CommerceTheme.muted(sku == null ? "" : sku.name()));
        card.add(info,BorderLayout.CENTER);
        JPanel outer = new JPanel(new BorderLayout());outer.setOpaque(false);
        outer.setBorder(BorderFactory.createEmptyBorder(9,9,9,9));outer.add(card);
        return outer;
    }
}
