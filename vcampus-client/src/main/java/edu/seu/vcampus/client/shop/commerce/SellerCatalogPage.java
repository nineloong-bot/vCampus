package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;

/** Seller inventory list with lifecycle controls and atomic batch image assignment. */
final class SellerCatalogPage {
    private final CommercePanel ui;
    private final JTextField keyword = new JTextField(22);
    private int page = 1;
    private JTable table;
    private List<Product> products = List.of();
    private JButton edit, publish, off, delete, images;
    SellerCatalogPage(CommercePanel ui) { this.ui = ui; }
    void open() {
        JPanel main = new JPanel(new BorderLayout(8,8));main.setOpaque(false);
        JButton search = CommerceTheme.button("搜索", () -> {page=1;open();});
        main.add(CommerceTheme.row(keyword,search,CommerceTheme.primary(CommerceTheme.button("创建商品",()->new ProductEditorPage(ui).open(null))),
                CommerceTheme.button("Excel 导入",()->new ImportPage(ui).open())),BorderLayout.NORTH);
        table = CommerceTheme.table(new String[]{"商品","状态","默认规格价格","总库存","净销量"},new Object[0][0]);
        table.setRowHeight(72);table.getColumnModel().getColumn(0).setPreferredWidth(320);
        ManagementLayout.badges(table,1);
        table.getColumnModel().getColumn(0).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer(){
            @Override public java.awt.Component getTableCellRendererComponent(JTable grid,Object value,boolean selected,boolean focused,int row,int col){
                JLabel label=(JLabel)super.getTableCellRendererComponent(grid,value,selected,focused,row,col);
                label.setIcon(row<products.size()?PresetImages.icon(products.get(row).imageId(),48):null);
                label.setIconTextGap(16);label.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));return label;
            }
        });
        main.add(ManagementLayout.table(table));
        edit = CommerceTheme.button("编辑",()->new ProductEditorPage(ui).open(selected().id()));
        publish = action("上架","PUBLISH");off = action("下架","OFF");delete = action("删除","DELETE");
        images = CommerceTheme.button("批量选图",this::images);
        JPanel footer = CommerceTheme.form();
        footer.add(CommerceTheme.row(edit,publish,off,delete,images));
        JButton previous=CommerceTheme.button("上一页",()->{page--;open();});previous.setEnabled(page>1);
        JButton next=CommerceTheme.button("下一页",()->{page++;open();});next.setEnabled(false);
        JLabel count = new JLabel();footer.add(CommerceTheme.row(previous,count,next));
        main.add(footer,BorderLayout.SOUTH);
        ui.display("商品管理",ManagementLayout.seller(ui,main,"商品管理"),null,ui::home);
        table.getSelectionModel().addListSelectionListener(this::selection);
        table.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){
            if(e.getClickCount()==2&&table.getSelectedRow()>=0)new ProductEditorPage(ui).open(selected().id());
        }});
        selection(null);
        ui.fetch("SHOP2_PRODUCT_LIST",new Query(keyword.getText(),"","DEFAULT",page,20),data->{
            Page result=(Page)data;products=result.items();
            var model=(javax.swing.table.DefaultTableModel)table.getModel();
            for(Product p:products){
                Sku d=p.skus().stream().filter(s->s.id().equals(p.defaultSkuId())).findFirst().orElse(null);
                model.addRow(new Object[]{p.name(),status(p.status()),d==null||d.price()==null?"待填写":"¥"+d.price(),
                        p.skus().stream().filter(Sku::active).mapToInt(s->s.totalStock()==null?0:s.totalStock()).sum(),p.sales()});
            }
            count.setText("第 "+page+" 页 / 共 "+result.total()+" 件");next.setEnabled(page*20<result.total());
        });
    }
    private JButton action(String label,String action) {
        JButton button=CommerceTheme.button(label,()->{});
        button.addActionListener(e->{
            Product p=selected();
            if(action.equals("DELETE")&&!ui.confirm("删除后无法恢复。确认删除“"+p.name()+"”？"))return;
            ui.write("SHOP2_PRODUCT_ACTION",new ProductAction("",p.id(),action),button,data->open());
        });return button;
    }
    private Product selected(){return products.get(table.getSelectedRow());}
    private void selection(ListSelectionEvent ignored) {
        boolean one=table.getSelectedRowCount()==1;
        edit.setEnabled(one);publish.setEnabled(one);off.setEnabled(one);delete.setEnabled(one);
        images.setEnabled(table.getSelectedRowCount()>0);
        if(one){boolean active=selected().status().equals("ACTIVE");publish.setEnabled(!active);delete.setEnabled(!active);delete.setToolTipText(active?"商品在售，请先下架后再删除":null);off.setEnabled(!selected().status().equals("DRAFT"));}
    }
    private void images(){
        JComboBox<String> picker=new JComboBox<>(new String[]{"book","pen","cup","bag","shirt","box"});
        if(JOptionPane.showConfirmDialog(ui,picker,"选择预置图片编号",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        var ids=java.util.Arrays.stream(table.getSelectedRows()).mapToObj(i->products.get(i).id()).toList();
        ui.write("SHOP2_PRODUCT_IMAGES",new Images("",ids,(String)picker.getSelectedItem()),images,data->open());
    }
    static String status(String value){return switch(value){case "ACTIVE"->"在售";case "DRAFT"->"草稿";
        case "INACTIVE"->"已下架";case "QUALIFICATION_EXPIRED"->"资质到期";case "EMERGENCY_BLOCKED"->"紧急下架";default->value;};}
}
