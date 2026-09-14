package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.*;

/** Product editor preserving SKU identities and allowing incomplete name-only drafts. */
final class ProductEditorPage {
    private final CommercePanel ui;
    private final JTextField name=new JTextField();
    private final JTextArea description=new JTextArea(3,25);
    private final JComboBox<String> category=new JComboBox<>(new String[]{"","ordinary","licensed"});
    private final JComboBox<String> image=new JComboBox<>(PresetImages.IDS);
    private final JPanel skuPanel=CommerceTheme.form();
    private final List<SkuRow> skus=new ArrayList<>();
    private final ButtonGroup defaults=new ButtonGroup();
    private String productId="";
    private record SkuRow(String id,JTextField name,JTextField price,JTextField stock,int reserved,
                          JRadioButton selected,JCheckBox active) { }
    ProductEditorPage(CommercePanel ui){
        this.ui=ui;
        category.setRenderer(new DefaultListCellRenderer(){
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focused){
                String label=switch(String.valueOf(value)){case "ordinary"->"普通白名单商品";case "licensed"->"专项许可类目（模拟）";default->"请选择准入类目";};
                return super.getListCellRendererComponent(list,label,index,selected,focused);
            }
        });
    }
    void open(String id){
        if(id!=null){ui.fetch("SHOP2_PRODUCT_DETAIL",id,data->{load((Product)data);render();});}
        else{addSku(new Sku(UUID.randomUUID().toString(),"标准款",null,null,0,true),true);render();}
    }
    private void load(Product p){
        productId=p.id();name.setText(p.name());description.setText(p.description());category.setSelectedItem(p.category());image.setSelectedItem(p.imageId());
        for(Sku s:p.skus())addSku(s,s.id().equals(p.defaultSkuId()));
    }
    private void render(){
        JPanel fields=CommerceTheme.form();fields.add(CommerceTheme.heading("01 / 基本信息",16));
        CommerceTheme.field(fields,"商品名称",name);CommerceTheme.field(fields,"商品介绍",new JScrollPane(description));
        fields.add(CommerceTheme.heading("选择商品封面",16));fields.add(CommerceTheme.gap(12));
        fields.add(presetGrid());fields.add(CommerceTheme.gap(24));
        fields.add(CommerceTheme.heading("02 / 准入类目",16));CommerceTheme.field(fields,"经营准入",category);
        fields.add(CommerceTheme.muted("专项许可类目须先提交文字经营资质并通过模拟审核。"));
        fields.add(CommerceTheme.gap(24));fields.add(CommerceTheme.heading("03 / 规格与库存",16));
        fields.add(CommerceTheme.muted("填写总库存，不得少于已预占。移除规格请取消“启用”，历史编号将保留。"));
        fields.add(skuPanel);
        fields.add(CommerceTheme.button("添加规格",()->{addSku(new Sku(UUID.randomUUID().toString(),"",null,null,0,true),skus.isEmpty());skuPanel.revalidate();}));
        JButton save=CommerceTheme.primary(CommerceTheme.button("保存商品",()->{}));
        save.addActionListener(e->save(save));
        ui.modal(productId.isEmpty()?"创建商品草稿":"编辑商品",CommerceTheme.scroll(fields),
                CommerceTheme.row(CommerceTheme.button("返回商品管理",ui::closeModal),save),ui::closeModal,1000);
    }
    private JPanel presetGrid(){
        JPanel grid=new JPanel(new java.awt.GridLayout(1,6,12,0));grid.setOpaque(false);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE,150));ButtonGroup choices=new ButtonGroup();
        String[] names={"书本","文具","水杯","提包","衣物","收纳"};
        for(int i=1;i<PresetImages.IDS.length;i++){
            String id=PresetImages.IDS[i];JToggleButton option=new JToggleButton(names[i-1],PresetImages.icon(id,72));
            option.setName("preset."+id);option.setVerticalTextPosition(SwingConstants.BOTTOM);
            option.setHorizontalTextPosition(SwingConstants.CENTER);option.setBackground(java.awt.Color.WHITE);
            option.setSelected(id.equals(image.getSelectedItem()));choices.add(option);
            option.addActionListener(e->image.setSelectedItem(id));grid.add(option);
        }
        return grid;
    }
    private void addSku(Sku s,boolean selected){
        JTextField label=new JTextField(s.name(),12),price=new JTextField(s.price()==null?"":s.price().toPlainString(),9);
        JTextField stock=new JTextField(s.totalStock()==null?"":s.totalStock().toString(),7);
        JRadioButton def=new JRadioButton("默认",selected);defaults.add(def);
        JCheckBox active=new JCheckBox("启用",s.active());
        var row=new SkuRow(s.id(),label,price,stock,s.reservedStock(),def,active);skus.add(row);
        JPanel panel=CommerceTheme.row(def,label,new JLabel("价格"),price,new JLabel("总库存"),stock,
                new JLabel("已预占 "+s.reservedStock()),active);panel.setMaximumSize(new Dimension(Integer.MAX_VALUE,55));skuPanel.add(panel);
    }
    private void save(JButton button){
        try{
            var values=new ArrayList<Sku>();String defaultId="";
            for(SkuRow row:skus){
                if(row.selected().isSelected())defaultId=row.id();
                BigDecimal price=row.price().getText().isBlank()?null:new BigDecimal(row.price().getText().trim());
                Integer stock=row.stock().getText().isBlank()?null:Integer.valueOf(row.stock().getText().trim());
                values.add(new Sku(row.id(),row.name().getText(),price,stock,row.reserved(),row.active().isSelected()));
            }
            SaveProduct command=new SaveProduct("",productId,name.getText(),description.getText(),(String)category.getSelectedItem(),
                    (String)image.getSelectedItem(),defaultId,values);
            ui.write("SHOP2_PRODUCT_SAVE",command,button,data->{new SellerCatalogPage(ui).open();ui.notice("商品已保存");});
        }catch(NumberFormatException error){ui.notice("价格须为数字，库存须为整数；草稿可留空");}
    }
}
