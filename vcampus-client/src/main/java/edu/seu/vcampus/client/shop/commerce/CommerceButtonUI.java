package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

/** Predictable rounded button rendering without native bevels. */
final class CommerceButtonUI extends BasicButtonUI {
    @Override public void paint(Graphics graphics,JComponent component) {
        AbstractButton button=(AbstractButton)component;
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        boolean nav=Boolean.TRUE.equals(button.getClientProperty("commerce.nav"));
        boolean primary=CommerceTheme.ACCENT.equals(button.getBackground());
        if(!button.isEnabled())g.setComposite(AlphaComposite.SrcOver.derive(.42f));
        Color fill=button.getBackground();
        if(button.getModel().isRollover())fill=primary?new Color(0x27583d):new Color(0xe9efe9);
        if(!nav||button.getModel().isRollover()||button.isSelected()) {
            g.setColor(button.isSelected()?new Color(0xeaf1e9):fill);
            g.fillRoundRect(0,0,button.getWidth()-1,button.getHeight()-1,12,12);
        }
        if(!nav){g.setColor(primary?CommerceTheme.ACCENT:CommerceTheme.LINE);g.drawRoundRect(0,0,button.getWidth()-1,button.getHeight()-1,12,12);}
        if(button.hasFocus()){g.setColor(CommerceTheme.ACCENT);g.drawRoundRect(2,2,button.getWidth()-5,button.getHeight()-5,10,10);}
        super.paint(g,component);g.dispose();
    }
    static void apply(AbstractButton button) {
        if(Boolean.TRUE.equals(button.getClientProperty("commerce.button")))return;
        button.putClientProperty("commerce.button",true);button.setUI(new CommerceButtonUI());
        button.setOpaque(false);button.setContentAreaFilled(false);button.setFocusPainted(false);button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createEmptyBorder(10,15,10,15));
        button.setFont(CommerceTheme.font(Font.PLAIN,14));
        if(!CommerceTheme.ACCENT.equals(button.getBackground()))button.setBackground(Color.WHITE);
        button.setForeground(CommerceTheme.ACCENT.equals(button.getBackground())?Color.WHITE:CommerceTheme.INK);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
