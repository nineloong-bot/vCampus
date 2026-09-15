package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import javax.swing.*;
import java.awt.*;

/** Centered detail sheet with artwork, variants and bounded purchase quantity. */
final class ProductPage {
    private final CommercePanel ui;
    ProductPage(CommercePanel ui) { this.ui=ui; }
    void open(String id) {
        JPanel content=CommerceTheme.form();
        ui.modal("商品详情",content,null,ui.snapshot(),620);
        ui.fetch("SHOP2_CATALOG_DETAIL",id,data->show(content,(Product)data));
    }
    private void show(JPanel content,Product product) {
        JPanel art=ProductArt.panel(product.imageId(),200,140);
        art.setMaximumSize(new Dimension(Integer.MAX_VALUE,200));content.add(art);
        content.add(Box.createVerticalStrut(14));content.add(CommerceTheme.heading(product.name(),22));
        content.add(CommerceTheme.row(CommerceTheme.button(product.shopName(),()->new CatalogPage(ui).shop(product.shopId())),
                CommerceTheme.muted("已售 "+product.sales())));
        JTextArea description=new JTextArea(product.description());description.setEditable(false);
        description.setLineWrap(true);description.setWrapStyleWord(true);description.setOpaque(false);
        description.setForeground(new Color(0x7a897e));content.add(description);
        var skus=product.skus().stream().filter(Sku::active).toList();
        JComboBox<String> variants=new JComboBox<>(skus.stream().map(s->s.name()+" · ¥"+s.price()).toArray(String[]::new));
        for(int i=0;i<skus.size();i++)if(skus.get(i).id().equals(product.defaultSkuId()))variants.setSelectedIndex(i);
        JSpinner quantity=new JSpinner(new SpinnerNumberModel(1,1,1,1));quantity.setName("product.detail.quantity");
        JLabel stock=CommerceTheme.muted("");JLabel amount=CommerceTheme.heading("",19);
        JButton add=CommerceTheme.primary(new JButton("加入购物车"));
        JButton minus=CommerceTheme.button("−",()->{if((Integer)quantity.getValue()>1)quantity.setValue((Integer)quantity.getValue()-1);});
        JButton plus=CommerceTheme.button("＋",()->{Object next=quantity.getNextValue();if(next!=null)quantity.setValue(next);});
        Runnable refresh=()->{
            int index=variants.getSelectedIndex();Sku sku=index<0?null:skus.get(index);
            int available=sku==null||sku.totalStock()==null?0:Math.max(0,sku.totalStock()-sku.reservedStock());
            int count=(Integer)quantity.getValue();add.setEnabled(available>0&&count<=available);
            quantity.setEnabled(available>0);minus.setEnabled(available>0&&count>1);plus.setEnabled(count<available);
            stock.setText("库存可用 "+available+" 件");amount.setText("本次金额  "+(sku==null?"—":"¥"+sku.price().multiply(java.math.BigDecimal.valueOf(count))));
        };
        Runnable change=()->{
            int i=variants.getSelectedIndex();int available=i<0||skus.get(i).totalStock()==null?0:Math.max(0,skus.get(i).totalStock()-skus.get(i).reservedStock());
            quantity.setModel(new SpinnerNumberModel(1,1,Math.max(1,available),1));refresh.run();
        };
        variants.addActionListener(e->change.run());quantity.addChangeListener(e->refresh.run());change.run();
        add.addActionListener(e->{
            try{quantity.commitEdit();}catch(java.text.ParseException ex){ui.notice("请输入有效购买数量");return;}
            ui.write("SHOP2_CART_CHANGE",new CartChange("",null,skus.get(variants.getSelectedIndex()).id(),(Integer)quantity.getValue()),add,result->{
                CartResult cart=(CartResult)result;ui.updateCart(!cart.lines().isEmpty());
                ui.notice("已加入购物车"+(cart.notices().isEmpty()?"":"；"+String.join("；",cart.notices())));refresh.run();
            });
        });
        CommerceTheme.field(content,"规格",variants);
        CommerceTheme.field(content,"购买数量",CommerceTheme.row(minus,quantity,plus));
        content.add(stock);content.add(Box.createVerticalStrut(12));content.add(amount);
        content.add(CommerceTheme.row(add,CommerceTheme.button("举报商品",()->new GovernancePages(ui).report("REPORT_PRODUCT",product.id()))));
        content.revalidate();content.repaint();
    }
}
