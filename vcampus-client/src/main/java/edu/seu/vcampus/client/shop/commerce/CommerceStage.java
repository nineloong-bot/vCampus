package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/** Centered in-workspace dialog with dimmed storefront and a fixed close affordance. */
final class CommerceStage extends JLayeredPane {
    private final JComponent base;
    private final JPanel veil;
    private JPanel dialog;
    private int width=620;
    private final Runnable close;
    private final JLabel message=CommerceTheme.muted(" ");
    CommerceStage(JComponent base,Runnable close){
        this.base=base;this.close=close;setLayout(null);add(base,DEFAULT_LAYER);
        veil=new JPanel(null){
            @Override protected void paintComponent(Graphics graphics){
                Graphics2D g=(Graphics2D)graphics.create();g.setColor(new Color(0x1a,0x2f,0x28,85));g.fillRect(0,0,getWidth(),getHeight());
                if(dialog!=null){Rectangle r=dialog.getBounds();for(int i=18;i>0;i--){g.setColor(new Color(0,0,0,2));g.fillRoundRect(r.x-i,r.y-i/2,r.width+i*2,r.height+i*2,36,36);}}g.dispose();
            }
        };
        veil.setOpaque(false);veil.setVisible(false);veil.addMouseListener(new MouseAdapter(){});add(veil,MODAL_LAYER);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"close-modal");
        getActionMap().put("close-modal",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){if(veil.isVisible())close.run();}});
    }
    void showDialog(String title,JComponent main,JComponent footer,int requestedWidth){
        width=requestedWidth;veil.removeAll();dialog=CommerceTheme.card(Color.WHITE,24);dialog.setName("commerce.modal");dialog.setFocusCycleRoot(true);
        JPanel header=new JPanel(new BorderLayout(12,0));header.setOpaque(false);header.add(CommerceTheme.heading(title,22));
        JButton cross=CommerceTheme.button("×",close);cross.putClientProperty("commerce.nav",true);cross.setName("commerce.modal.close");cross.getAccessibleContext().setAccessibleName("关闭");header.add(cross,BorderLayout.EAST);header.setBorder(BorderFactory.createEmptyBorder(0,0,14,0));
        dialog.add(header,BorderLayout.NORTH);
        dialog.add(main instanceof JScrollPane||main.getLayout() instanceof BorderLayout?main:CommerceTheme.scroll(main),BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout());bottom.setOpaque(false);
        if(footer!=null){footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,CommerceTheme.LINE),BorderFactory.createEmptyBorder(12,0,0,0)));bottom.add(footer,BorderLayout.CENTER);}
        message.setText(" ");bottom.add(message,BorderLayout.SOUTH);dialog.add(bottom,BorderLayout.SOUTH);
        veil.add(dialog);veil.setVisible(true);CommerceStyle.apply(dialog);revalidate();repaint();cross.requestFocusInWindow();
    }
    boolean isModal(){return veil.isVisible();}
    void hideDialog(){veil.setVisible(false);veil.removeAll();dialog=null;revalidate();repaint();}
    void notice(String value){message.setText(value);}
    @Override public void doLayout(){
        base.setBounds(0,0,getWidth(),getHeight());veil.setBounds(0,0,getWidth(),getHeight());
        if(dialog!=null){int w=Math.min(width,Math.max(1,(int)(getWidth()*.90)));int h=Math.min(Math.max(180,dialog.getPreferredSize().height),(int)(getHeight()*.82));dialog.setBounds((getWidth()-w)/2,(getHeight()-h)/2,w,Math.max(1,h));}
    }
}
