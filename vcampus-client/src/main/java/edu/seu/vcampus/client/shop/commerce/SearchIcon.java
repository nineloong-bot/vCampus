package edu.seu.vcampus.client.shop.commerce;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Font-independent magnifying glass for the approved search navigation. */
final class SearchIcon implements Icon {
    @Override public int getIconWidth() { return 18; }
    @Override public int getIconHeight() { return 18; }
    @Override public void paintIcon(Component component,Graphics graphics,int x,int y) {
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(CommerceTheme.ACCENT);g.setStroke(new BasicStroke(1.8f));
        g.drawOval(x+2,y+1,10,10);g.drawLine(x+11,y+10,x+16,y+16);g.dispose();
    }
}
