package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;

/** Rounded, flat select control matching the Demo rather than native Windows bevels. */
final class CommerceComboUI extends BasicComboBoxUI {
    @Override protected JButton createArrowButton(){
        JButton arrow=new JButton(){
            @Override protected void paintComponent(Graphics graphics){
                Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(CommerceTheme.INK);g.setStroke(new BasicStroke(1.5f));int x=getWidth()/2,y=getHeight()/2;
                g.drawLine(x-4,y-2,x,y+2);g.drawLine(x,y+2,x+4,y-2);g.dispose();
            }
        };
        arrow.putClientProperty("commerce.styled",true);arrow.setOpaque(false);arrow.setBorder(BorderFactory.createEmptyBorder());return arrow;
    }
    @Override public void paint(Graphics graphics,JComponent component){
        Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);g.fillRoundRect(0,0,component.getWidth()-1,component.getHeight()-1,10,10);
        g.setColor(CommerceTheme.LINE);g.drawRoundRect(0,0,component.getWidth()-1,component.getHeight()-1,10,10);g.dispose();super.paint(graphics,component);
    }
    @Override public void paintCurrentValueBackground(Graphics g,Rectangle bounds,boolean focus){ }
    static void apply(JComboBox<?> combo){
        combo.setUI(new CommerceComboUI());combo.setOpaque(false);combo.setFont(CommerceTheme.font(Font.PLAIN,14));combo.setForeground(CommerceTheme.INK);
        combo.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));
        combo.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                JLabel label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
                label.setBorder(BorderFactory.createEmptyBorder(1,2,1,2));label.setFont(CommerceTheme.font(Font.PLAIN,14));
                label.setForeground(CommerceTheme.INK);label.setBackground(selected?new Color(0xeaf1e9):Color.WHITE);return label;
            }
        });
    }
}
