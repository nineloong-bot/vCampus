package edu.seu.vcampus.client.shop.commerce;

import java.awt.*;

/** Natural-height vertical flow; extra viewport height never separates form fields. */
final class FormLayout implements LayoutManager {
    @Override public void addLayoutComponent(String name,Component component) { }
    @Override public void removeLayoutComponent(Component component) { }
    @Override public Dimension preferredLayoutSize(Container parent) {
        Insets in=parent.getInsets();int width=0,height=0,count=0;
        for(Component child:parent.getComponents())if(child.isVisible()) {
            Dimension d=child.getPreferredSize();width=Math.max(width,d.width);height+=d.height;count++;
        }
        return new Dimension(width+in.left+in.right,height+Math.max(0,count-1)*10+in.top+in.bottom);
    }
    @Override public Dimension minimumLayoutSize(Container parent) {return preferredLayoutSize(parent);}
    @Override public void layoutContainer(Container parent) {
        Insets in=parent.getInsets();int y=in.top,width=Math.max(0,parent.getWidth()-in.left-in.right);
        for(Component child:parent.getComponents())if(child.isVisible()) {
            int height=child.getPreferredSize().height;
            child.setBounds(in.left,y,width,height);y+=height+10;
        }
    }
}
