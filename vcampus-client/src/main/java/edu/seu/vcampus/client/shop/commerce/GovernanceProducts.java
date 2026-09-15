package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import edu.seu.vcampus.common.shop.governance.GovernanceDtos;
import javax.swing.*;
import java.awt.BorderLayout;
import java.util.List;
import java.util.function.Consumer;

/** Administrator product list and immutable product evidence. */
final class GovernanceProducts {
    private final GovernancePages pages;
    private final CommercePanel ui;
    private int page=1;
    private String keyword="";
    GovernanceProducts(GovernancePages pages) { this.pages=pages;ui=pages.ui; }
    void open() {
        ui.display("商品管理",new JLabel("正在加载…"),null,pages::shops);
        ui.fetch("SHOP2_PRODUCT_ADMIN_LIST",new Query(keyword,null,"DEFAULT",page,20),data->{
            Page result=(Page)data;
            ui.fetch("SHOP2_GOV_CASES",new GovernanceDtos.Query("ADMIN",null),reports->render(result,((GovernanceDtos.Views)reports).items()));
        });
    }
    private void render(Page result,List<GovernanceDtos.View> cases) {
        Object[][] rows=result.items().stream().map(p->new Object[]{p.name(),p.shopName(),p.deleted()?"已删除":GovernanceLabels.state(p.status()),p.category(),cases.stream().filter(v->"REPORT_PRODUCT".equals(v.kind())&&p.id().equals(v.objectId())).count()}).toArray(Object[][]::new);
        JTable table=CommerceTheme.table(new String[]{"商品","所属店铺","状态","准入类目","举报数"},rows);
        ManagementLayout.badges(table,2);
        JTextField search=new JTextField(keyword,20);
        JPanel content=new JPanel(new BorderLayout());content.setOpaque(false);
        content.add(CommerceTheme.row(search,CommerceTheme.button("搜索",()->{keyword=search.getText();page=1;open();})),BorderLayout.NORTH);
        content.add(ManagementLayout.table(table));
        JPanel actions=CommerceTheme.row(
                CommerceTheme.button("商品资料",()->selected(table,result.items(),this::detail)),
                CommerceTheme.button("警告",()->selected(table,result.items(),p->new GovernanceForms(pages).action(p.shopId(),"WARN",null,List.of(p.id()),this::open))),
                CommerceTheme.button("紧急下架",()->selected(table,result.items(),p->new GovernanceForms(pages).action(p.id(),"EMERGENCY",null,List.of(),this::open))),
                CommerceTheme.button("举报 / 整改复核",()->pages.cases(false)),
                CommerceTheme.button("历史",()->selected(table,result.items(),p->pages.audit(p.id()))),
                CommerceTheme.button("上一页",()->{if(page>1){page--;open();}}),
                new JLabel(page+" / "+Math.max(1,(result.total()+19)/20)),
                CommerceTheme.button("下一页",()->{if(page*20<result.total()){page++;open();}}));
        pages.display("商品管理",content,actions,pages::shops,true);
    }
    private void selected(JTable table,List<Product> products,Consumer<Product> action) {
        int selected=table.getSelectedRow();
        if(selected<0){ui.notice("请先选择商品");return;}
        action.accept(products.get(table.convertRowIndexToModel(selected)));
    }
    private void detail(Product product) {
        ui.fetch("SHOP2_PRODUCT_ADMIN_DETAIL",product.id(),data->{
            Product p=(Product)data;
            JPanel form=CommerceTheme.form();
            form.add(new JLabel(PresetImages.icon(p.imageId(),160)));
            CommerceTheme.field(form,"商品名称",new JLabel(p.name()));
            CommerceTheme.field(form,"所属店铺",new JLabel(p.shopName()));
            CommerceTheme.field(form,"商品编号",new JLabel(p.id()));
            CommerceTheme.field(form,"状态",new JLabel(p.deleted()?"已删除":GovernanceLabels.state(p.status())));
            CommerceTheme.field(form,"准入类目",new JLabel(p.category()));
            JTextArea description=new JTextArea(p.description(),4,45);description.setEditable(false);description.setLineWrap(true);description.setWrapStyleWord(true);
            CommerceTheme.field(form,"商品介绍",new JScrollPane(description));
            Object[][] rows=p.skus().stream().map(s->new Object[]{s.name(),s.price(),s.totalStock(),s.reservedStock(),s.active()?"启用":"停用",s.id().equals(p.defaultSkuId())?"默认":""}).toArray(Object[][]::new);
            form.add(new JScrollPane(CommerceTheme.table(new String[]{"规格","价格","总库存","已预占","状态","默认规格"},rows)));
            ui.modal("商品资料",CommerceTheme.scroll(form),CommerceTheme.row(CommerceTheme.button("返回商品列表",ui::closeModal),CommerceTheme.button("处理历史",()->pages.audit(p.id()))),ui::closeModal,900);
        });
    }
}
