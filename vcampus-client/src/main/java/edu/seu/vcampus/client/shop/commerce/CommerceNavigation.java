package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import java.awt.*;

/** Symmetric 76-pixel masthead with centered campus-market identity. */
final class CommerceNavigation extends JPanel {
    final JPanel left=CommerceTheme.row();
    final JPanel right=CommerceTheme.row();
    private final JPanel brand=CommerceTheme.form();
    CommerceNavigation(boolean admin){
        setLayout(null);setBackground(Color.WHITE);setPreferredSize(new Dimension(1,76));
        setBorder(BorderFactory.createMatteBorder(0,0,1,0,CommerceTheme.LINE));
        JLabel name=CommerceTheme.heading(admin?"校园集 · 管理":"校园集",24);name.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel english=CommerceTheme.muted("C A M P U S   M A R K E T");english.setFont(CommerceTheme.font(Font.BOLD,8));english.setHorizontalAlignment(SwingConstants.CENTER);
        brand.setLayout(new BoxLayout(brand,BoxLayout.Y_AXIS));name.setAlignmentX(.5f);english.setAlignmentX(.5f);brand.add(name);brand.add(english);
        add(left);add(right);add(brand);
    }
    void addLeft(JButton b){b.putClientProperty("commerce.nav",true);left.add(b);}
    void addRight(JButton b){b.putClientProperty("commerce.nav",true);right.add(b);}
    @Override public void doLayout(){
        int pad=getWidth()<850?12:26;Dimension l=left.getPreferredSize(),r=right.getPreferredSize();
        left.setBounds(pad,(76-l.height)/2,l.width,l.height);right.setBounds(getWidth()-pad-r.width,(76-r.height)/2,r.width,r.height);
        brand.setVisible(getWidth()>=850);brand.setBounds((getWidth()-250)/2,14,250,49);
    }
}
