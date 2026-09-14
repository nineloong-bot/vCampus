package edu.seu.vcampus.client.shop.commerce;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/** Layout and visual tokens transcribed from the approved browser Demo. */
public final class CommerceTheme {
    /** Primary action green. */
    public static final Color ACCENT=new Color(0x306448);
    /** Main storefront background. */
    public static final Color BACKGROUND=new Color(0xf4f6f3);
    /** Primary text color. */
    public static final Color INK=new Color(0x25382f);
    /** Subtle card and field outline. */
    public static final Color LINE=new Color(0xdbe2dc);
    private CommerceTheme() { }
    /** Uses a Chinese-capable sans serif font consistently at logical pixel sizes. */
    public static Font font(int style,int size){return new Font("Microsoft YaHei",style,size);}
    /** Spaced inline actions. */
    public static JPanel row(Component... items){
        JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,10,6));p.setOpaque(false);
        for(Component c:items)p.add(c);return p;
    }
    /** Bordered rounded action button. */
    public static JButton button(String text,Runnable action){
        JButton b=new JButton(text);CommerceButtonUI.apply(b);b.addActionListener(e->action.run());return b;
    }
    /** Green primary action. */
    public static JButton primary(JButton b){CommerceButtonUI.apply(b);b.setBackground(ACCENT);b.setForeground(Color.WHITE);return b;}
    /** A white or tinted rounded card with inner padding. */
    public static JPanel card(Color color,int padding){return new RoundedSurface(color,24,padding);}
    /** Hierarchical heading. */
    public static JLabel heading(String text,int size){JLabel label=new JLabel(text);label.setFont(font(Font.BOLD,size));label.setForeground(INK);return label;}
    /** Secondary explanatory copy. */
    public static JLabel muted(String text){JLabel label=new JLabel(text);label.setFont(font(Font.PLAIN,12));label.setForeground(new Color(0x879589));return label;}
    /** Fixed vertical gap. */
    public static JComponent gap(int size){JPanel p=new JPanel();p.setOpaque(false);p.setPreferredSize(new Dimension(1,size));p.setMinimumSize(new Dimension(1,size));p.setMaximumSize(new Dimension(Integer.MAX_VALUE,size));return p;}
    /** Full-width action card with description and trailing arrow. */
    public static JButton actionCard(String title,String description,Runnable action){
        JButton b=button("",action);b.setLayout(new BorderLayout(12,0));b.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        JPanel labels=form();labels.add(heading(title,16));labels.add(muted(description));
        b.add(labels,BorderLayout.CENTER);b.add(new JLabel("→"),BorderLayout.EAST);b.setHorizontalAlignment(SwingConstants.LEFT);return b;
    }
    /** Read-only management table with quiet horizontal rules. */
    public static JTable table(String[] headers,Object[][] rows){
        JTable t=new JTable(new DefaultTableModel(rows,headers){@Override public boolean isCellEditable(int r,int c){return false;}});
        t.setRowHeight(48);t.setFont(font(Font.PLAIN,14));t.setForeground(INK);t.setBackground(Color.WHITE);
        t.setShowVerticalLines(false);t.setGridColor(new Color(0xe4e9e1));t.setSelectionBackground(new Color(0xeaf1e9));t.setSelectionForeground(INK);
        t.getTableHeader().setFont(font(Font.BOLD,12));t.getTableHeader().setBackground(BACKGROUND);t.getTableHeader().setForeground(new Color(0x6e8475));
        t.getTableHeader().setDefaultRenderer((table,value,selected,focus,row,column)->{
            JLabel label=new JLabel(String.valueOf(value));label.setOpaque(true);label.setBackground(BACKGROUND);
            label.setForeground(new Color(0x6e8475));label.setFont(font(Font.BOLD,12));
            label.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,LINE),BorderFactory.createEmptyBorder(8,10,8,10)));return label;
        });
        t.getTableHeader().setPreferredSize(new Dimension(1,40));t.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);t.setFillsViewportHeight(true);return t;
    }
    /** Exact virtual currency formatting. */
    public static String money(long cents){return "¥"+java.math.BigDecimal.valueOf(cents,2).toPlainString();}
    /** Top-aligned form that retains natural child heights. */
    public static JPanel form(){return new CommerceForm();}
    /** Label-above-control form field, matching the Demo. */
    public static void field(JPanel p,String label,JComponent value){
        JPanel row=new JPanel(new BorderLayout(0,8));row.setOpaque(false);row.setBorder(BorderFactory.createEmptyBorder(5,0,5,0));
        JLabel name=new JLabel(label);name.setFont(font(Font.PLAIN,14));name.setForeground(INK);row.add(name,BorderLayout.NORTH);
        row.add(value,BorderLayout.CENTER);p.add(row);
    }
    /** Quiet scrolling surface that follows available width. */
    public static JScrollPane scroll(JComponent content){
        JScrollPane pane=new JScrollPane(content);pane.setBorder(BorderFactory.createEmptyBorder());pane.setOpaque(false);pane.getViewport().setOpaque(false);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);pane.getVerticalScrollBar().setUnitIncrement(18);return pane;
    }
}
