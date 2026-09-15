package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import java.awt.*;

/** Responsive central column with the same maximum width and inset as the Demo. */
final class CommerceViewport extends JPanel {
    private final JComponent content;
    CommerceViewport(JComponent content){this.content=content;setOpaque(false);setLayout(null);add(content);}
    @Override public void doLayout(){int w=Math.min(1450,getWidth()),pad=getWidth()<850?16:38;content.setBounds((getWidth()-w)/2+pad,0,Math.max(1,w-2*pad),getHeight());}
}
