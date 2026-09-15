package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Rounded surface painted independently of the platform look and feel. */
final class RoundedSurface extends JPanel {
    private final int radius;
    RoundedSurface(Color background,int radius,int padding) {
        super(new BorderLayout());this.radius=radius;setOpaque(false);setBackground(background);
        setBorder(BorderFactory.createEmptyBorder(padding,padding,padding,padding));
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getBackground());g.fillRoundRect(0,0,getWidth(),getHeight(),radius,radius);
        if(Color.WHITE.equals(getBackground())){g.setColor(CommerceTheme.LINE);g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,radius,radius);}g.dispose();
    }
    @Override protected void paintChildren(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();
        g.clip(new RoundRectangle2D.Double(0,0,getWidth(),getHeight(),radius,radius));super.paintChildren(g);g.dispose();
    }
}
