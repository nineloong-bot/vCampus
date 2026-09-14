package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import edu.seu.vcampus.common.shop.order.OrderLine;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Basket modal with right-hand selection and a fixed checkout summary. */
final class CartPage {
    private final CommercePanel ui;
    private final Set<String> selected=new HashSet<>();
    private boolean first=true, updating;
    private List<CartLine> lines=List.of();
    private final JLabel amount=CommerceTheme.heading("",27);
    private final JLabel selectedCount=CommerceTheme.muted("");
    private final JLabel overview=CommerceTheme.muted("");
    private final JCheckBox all=new JCheckBox("全选商品");
    private final JButton checkout;
    private JPanel rows;
    CartPage(CommercePanel ui) {
        this.ui=ui;checkout=CommerceTheme.primary(CommerceTheme.button("去结算   →",this::checkout));
    }
    void open() {
        JPanel main=new JPanel(new BorderLayout(0,12));main.setOpaque(false);rows=CommerceTheme.form();
        JPanel top=CommerceTheme.form();JPanel overviewRow=new JPanel(new BorderLayout());overviewRow.setOpaque(false);
        overviewRow.setBorder(BorderFactory.createEmptyBorder(4,0,8,0));
        all.setName("cart.select-all");all.setOpaque(false);all.setHorizontalTextPosition(SwingConstants.LEFT);
        overviewRow.add(overview,BorderLayout.WEST);overviewRow.add(all,BorderLayout.EAST);top.add(overviewRow);
        all.addActionListener(e->{if(updating)return;selected.clear();if(all.isSelected())lines.forEach(l->selected.add(l.id()));render();});
        JPanel columns=new JPanel(new BorderLayout());columns.setBackground(new Color(0xf3f6f0));
        columns.setBorder(BorderFactory.createEmptyBorder(10,16,10,16));columns.add(CommerceTheme.muted("商品 / 规格"),BorderLayout.WEST);
        columns.add(CommerceTheme.muted("小计                操作       选择"),BorderLayout.EAST);top.add(columns);
        main.add(top,BorderLayout.NORTH);main.add(CommerceTheme.scroll(rows),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(15,0,4,0));
        JPanel summary=ProductArt.stack(5);summary.add(selectedCount);summary.add(amount);footer.add(summary,BorderLayout.WEST);
        checkout.setPreferredSize(new Dimension(156,52));footer.add(checkout,BorderLayout.EAST);
        footer.setName("cart.fixed-checkout");ui.modal("购物车",main,footer,ui.snapshot(),900);
        ui.fetch("SHOP2_CART_GET",EmptyRequest.INSTANCE,data->accept((CartResult)data));
    }
    private void accept(CartResult result) {
        lines=result.lines();if(first){lines.forEach(l->selected.add(l.id()));first=false;}
        selected.retainAll(lines.stream().map(CartLine::id).toList());ui.updateCart(!lines.isEmpty());
        render();ui.notice(String.join("；",result.notices()));
    }
    private void render() {
        rows.removeAll();for(CartLine line:lines)rows.add(row(line));
        if(lines.isEmpty()){JLabel empty=CommerceTheme.muted("购物车还是空的，去挑选一些好物吧。");empty.setBorder(BorderFactory.createEmptyBorder(65,35,65,35));rows.add(empty);}
        overview.setText("购物清单   "+lines.size());recompute();rows.revalidate();rows.repaint();
    }
    private JComponent row(CartLine line) {
        Product product=line.product();JPanel panel=new JPanel(new BorderLayout(18,0)) {
            @Override public Dimension getPreferredSize() {
                Dimension size=super.getPreferredSize();size.height=Math.max(142,size.height);return size;
            }
        };
        panel.setBackground(new Color(selected.contains(line.id())?0xf5f8f2:0xffffff));

        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,new Color(0xe4e9e0)),BorderFactory.createEmptyBorder(18,16,18,16)));
        JPanel art=ProductArt.panel(product.imageId(),88,62);art.setPreferredSize(new Dimension(78,88));
        JPanel imageColumn=new JPanel(new GridBagLayout());imageColumn.setOpaque(false);imageColumn.add(art);panel.add(imageColumn,BorderLayout.WEST);
        JPanel info=ProductArt.stack(4);info.add(CommerceTheme.muted(product.shopName()));info.add(CommerceTheme.heading(product.name(),15));
        List<Sku> skus=product.skus().stream().filter(Sku::active).toList();
        if(skus.isEmpty()){info.add(CommerceTheme.muted("规格已不可用"));panel.add(info);return panel;}
        JComboBox<String> variants=new JComboBox<>(skus.stream().map(s->s.name()+" · ¥"+s.price()).toArray(String[]::new));
        int index=0;for(int i=0;i<skus.size();i++)if(skus.get(i).id().equals(line.skuId()))index=i;
        variants.setSelectedIndex(index);Sku current=skus.get(index);
        int available=current.totalStock()==null?line.quantity():Math.max(line.quantity(),current.totalStock()-current.reservedStock());
        JSpinner quantity=new JSpinner(new SpinnerNumberModel(line.quantity(),1,Math.max(1,available),1));
        JButton save=CommerceTheme.button("更新",()->{});
        save.addActionListener(e->{
            try{quantity.commitEdit();}catch(java.text.ParseException ex){ui.notice("请输入有效数量");return;}
            ui.write("SHOP2_CART_CHANGE",new CartChange("",line.id(),skus.get(variants.getSelectedIndex()).id(),(Integer)quantity.getValue()),save,data->accept((CartResult)data));
        });
                variants.setPreferredSize(new Dimension(230,32));
        JPanel variantRow=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));variantRow.setOpaque(false);
        variantRow.add(variants);info.add(variantRow);
        info.add(CommerceTheme.row(CommerceTheme.muted("数量"),quantity,save));panel.add(info,BorderLayout.CENTER);
        JCheckBox choose=new JCheckBox();choose.setName("cart.choose."+line.id());choose.setSelected(selected.contains(line.id()));choose.setOpaque(false);
        choose.addActionListener(e->{if(choose.isSelected())selected.add(line.id());else selected.remove(line.id());
            panel.setBackground(new Color(choose.isSelected()?0xf5f8f2:0xffffff));recompute();});
        JButton remove=CommerceTheme.button("移除",()->{});
        remove.addActionListener(e->ui.write("SHOP2_CART_CHANGE",new CartChange("",line.id(),line.skuId(),0),remove,data->accept((CartResult)data)));
        JLabel subtotal=CommerceTheme.heading("¥"+current.price().multiply(java.math.BigDecimal.valueOf(line.quantity())),14);subtotal.setForeground(CommerceTheme.ACCENT);
        JPanel actions=CommerceTheme.row(subtotal,remove,choose);panel.add(actions,BorderLayout.EAST);return panel;
    }
    private void recompute() {
        java.math.BigDecimal sum=java.math.BigDecimal.ZERO;int count=0;
        for(CartLine line:lines)if(selected.contains(line.id())){
            Sku sku=line.product().skus().stream().filter(x->x.id().equals(line.skuId())).findFirst().orElseThrow();
            sum=sum.add(sku.price().multiply(java.math.BigDecimal.valueOf(line.quantity())));count+=line.quantity();
        }
        selectedCount.setText("已选 "+count+" 件商品");amount.setText("合计  ¥"+sum);checkout.setEnabled(!selected.isEmpty());
        updating=true;all.setSelected(!lines.isEmpty()&&selected.size()==lines.size());all.setEnabled(!lines.isEmpty());updating=false;
    }
    private void checkout() {
        List<OrderLine> chosen=new ArrayList<>();for(CartLine line:lines)if(selected.contains(line.id())){
            Sku sku=line.product().skus().stream().filter(x->x.id().equals(line.skuId())).findFirst().orElseThrow();
            chosen.add(new OrderLine(sku.id(),line.quantity(),sku.price()));
        }
        new OrderPages(ui).checkout(chosen,true);
    }
}
