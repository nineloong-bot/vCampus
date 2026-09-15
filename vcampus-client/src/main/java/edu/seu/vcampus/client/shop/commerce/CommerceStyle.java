package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.JTextComponent;
import java.awt.*;

/** Applies shared Demo controls to asynchronously mounted Swing component trees. */
final class CommerceStyle {
    private CommerceStyle() { }
    static void apply(Component component){
        if(component instanceof JComponent c&&!Boolean.TRUE.equals(c.getClientProperty("commerce.styled"))){
            c.putClientProperty("commerce.styled",true);
            Font old=c.getFont();int size=old==null?14:old instanceof javax.swing.plaf.UIResource?14:old.getSize();
            c.setFont(CommerceTheme.font(old==null?Font.PLAIN:old.getStyle(),size));
            if(c instanceof JButton b)CommerceButtonUI.apply(b);
            else if(c instanceof JToggleButton b&&!(b instanceof JCheckBox)&&!(b instanceof JRadioButton))CommerceButtonUI.apply(b);
            else if(c instanceof JSpinner spinner){spinner.setBorder(BorderFactory.createEmptyBorder());spinner.setOpaque(false);}
            else if(c instanceof JTextComponent text&&text.isEditable()){text.setFont(CommerceTheme.font(Font.PLAIN,14));text.setForeground(CommerceTheme.INK);text.setBackground(Color.WHITE);text.setBorder(new InputBorder());}
            else if(c instanceof JComboBox<?> combo){CommerceComboUI.apply(combo);}
            else if(c instanceof JCheckBox check){check.setOpaque(false);check.setForeground(CommerceTheme.INK);check.setIcon(new TickIcon());check.setSelectedIcon(new TickIcon());}
            else if(c instanceof JScrollPane pane){pane.setBorder(BorderFactory.createEmptyBorder());pane.getViewport().setBackground(Color.WHITE);pane.getVerticalScrollBar().setUnitIncrement(18);}
            else if(c instanceof JScrollBar bar){bar.setUI(new QuietScrollBar());bar.setPreferredSize(new Dimension(8,8));}
            else if(c instanceof JLabel label&&label.getForeground() instanceof javax.swing.plaf.UIResource)label.setForeground(CommerceTheme.INK);
        }
        if(component instanceof Container container)for(Component child:container.getComponents())apply(child);
    }
    private static final class InputBorder extends AbstractBorder {
        @Override public Insets getBorderInsets(Component c){return new Insets(10,12,10,12);}
        @Override public Insets getBorderInsets(Component c,Insets in){in.set(10,12,10,12);return in;}
        @Override public void paintBorder(Component c,Graphics graphics,int x,int y,int w,int h){
            Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c.hasFocus()?CommerceTheme.ACCENT:CommerceTheme.LINE);g.drawRoundRect(x,y,w-1,h-1,12,12);g.dispose();
        }
    }
    private static final class TickIcon implements Icon {
        @Override public int getIconWidth(){return 18;}
        @Override public int getIconHeight(){return 18;}
        @Override public void paintIcon(Component c,Graphics graphics,int x,int y){
            Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            boolean selected=c instanceof AbstractButton b&&b.isSelected();g.setColor(selected?CommerceTheme.ACCENT:Color.WHITE);g.fillRoundRect(x,y,16,16,4,4);
            g.setColor(selected?CommerceTheme.ACCENT:new Color(0xcad4c8));g.drawRoundRect(x,y,16,16,4,4);
            if(selected){g.setStroke(new BasicStroke(2));g.setColor(Color.WHITE);g.drawLine(x+4,y+8,x+7,y+11);g.drawLine(x+7,y+11,x+13,y+4);}g.dispose();
        }
    }
    private static final class QuietScrollBar extends BasicScrollBarUI {
        @Override protected JButton createDecreaseButton(int orientation){return invisible();}
        @Override protected JButton createIncreaseButton(int orientation){return invisible();}
        private JButton invisible(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));return b;}
        @Override protected void paintTrack(Graphics g,JComponent c,Rectangle r){g.setColor(CommerceTheme.BACKGROUND);g.fillRect(r.x,r.y,r.width,r.height);}
        @Override protected void paintThumb(Graphics g,JComponent c,Rectangle r){g.setColor(new Color(0xbfcac0));g.fillRoundRect(r.x,r.y,r.width,r.height,8,8);}
    }
}
