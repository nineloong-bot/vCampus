package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import java.awt.*;

/** Width-tracking natural-height form inside modal scroll viewports. */
final class CommerceForm extends JPanel implements Scrollable {
    CommerceForm(){super(new FormLayout());setOpaque(false);}
    @Override public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
    @Override public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 18;}
    @Override public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(18,r.height-36);}
    @Override public boolean getScrollableTracksViewportWidth(){return true;}
    @Override public boolean getScrollableTracksViewportHeight(){return false;}
}
