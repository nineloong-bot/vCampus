package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import javax.swing.*;
import java.awt.BorderLayout;
import java.util.List;
import java.util.function.Consumer;

/** Store and product governance workspaces backed by authenticated Socket requests. */
final class GovernancePages {
    final CommercePanel ui;
    GovernancePages(CommercePanel ui) { this.ui=ui; }
    void shops() {
        list("店铺管理","SHOP",new Query("ADMIN",null),values->{
            JTable table=table(values);
            JPanel actions=CommerceTheme.row(
                    CommerceTheme.button("查看详情",()->selected(table,values,this::detail)),
                    CommerceTheme.button("警告",()->selected(table,values,v->new GovernanceForms(this).action(v.id(),"WARN",null,List.of(),this::shops))),
                    CommerceTheme.button("暂停营业",()->selected(table,values,v->new GovernanceForms(this).action(v.id(),"SUSPEND",null,List.of(),this::shops))),
                    CommerceTheme.button("开店申请",this::applications),
                    CommerceTheme.button("资质审核",()->qualifications(true)),
                    CommerceTheme.button("举报与恢复申请",()->cases(false)),
                    CommerceTheme.button("历史记录",()->audit(null)));
            display("店铺管理",ManagementLayout.table(table),actions,ui::home,true);
        });
    }
    void products() { new GovernanceProducts(this).open(); }
    void applications() {
        reviewList("开店申请","APPLICATIONS",new Query("ADMIN",null),"REVIEW_APPLICATION",this::applications);
    }
    void qualifications(boolean admin) {
        if(admin) reviewList("经营资质审核 · 课程模拟规则","QUALIFICATIONS",new Query("ADMIN",null),"REVIEW_QUALIFICATION",()->qualifications(true));
        else list("经营资质 · 课程模拟规则","QUALIFICATIONS",new Query("SELF",null),values->{
            JTable table=table(values);
            display("经营资质 · 课程模拟规则",ManagementLayout.table(table),CommerceTheme.row(
                    CommerceTheme.button("提交或续期资质",()->new GovernanceForms(this).qualification()),
                    CommerceTheme.button("查看详情",()->selected(table,values,this::detail))),ui::home,false);
        });
    }
    void cases(boolean seller) {
        new GovernanceCasePages(this).open(seller);
    }
    void notices() { cases(true); }
    void report(String kind,String id) { new GovernanceForms(this).report(kind,id); }
    void requestReopen(String shopId) { new GovernanceForms(this).recovery("REOPEN",shopId); }
    void requestRemediation(String productId) { new GovernanceForms(this).recovery("REMEDIATION",productId); }
    void audit(String objectId) {
        ui.display("处理历史",new JLabel("正在加载…"),null,this::shops);
        ui.fetch("SHOP2_GOV_AUDIT",new Query("ADMIN",objectId),data->{
            Audits audits=(Audits)data;
            Object[][] rows=audits.items().stream().map(a->new Object[]{a.occurredAt(),a.actorId(),a.objectId(),a.action(),a.reason(),a.beforeState(),a.afterState(),a.linkedId()}).toArray(Object[][]::new);
            display("处理历史",ManagementLayout.table(CommerceTheme.table(new String[]{"时间","操作人","对象","动作","原因","之前状态","之后状态","关联举报/申请"},rows)),CommerceTheme.row(CommerceTheme.button("返回店铺管理",this::shops)),this::shops,true);
        });
    }
    void list(String title,String route,Query query,Consumer<List<View>> callback) {
        ui.display(title,new JLabel("正在加载…"),null,ui::home);
        ui.fetch("SHOP2_GOV_"+route,query,data->callback.accept(((Views)data).items()));
    }
    private void reviewList(String title,String route,Query query,String reviewRoute,Runnable refresh) {
        list(title,route,query,values->{
            JTable table=table(values);
            display(title,ManagementLayout.table(table),CommerceTheme.row(
                    CommerceTheme.button("查看申请资料",()->selected(table,values,this::detail)),
                    CommerceTheme.button("通过",()->selected(table,values,v->new GovernanceForms(this).review(v,reviewRoute,true,refresh))),
                    CommerceTheme.button("驳回",()->selected(table,values,v->new GovernanceForms(this).review(v,reviewRoute,false,refresh)))),this::shops,true);
        });
    }
    JTable table(List<View> values) {
        Object[][] rows=values.stream().map(v->new Object[]{v.title(),GovernanceLabels.kind(v.kind()),GovernanceLabels.state(v.state()),v.subjectName(),v.licenseNumber(),v.expiresOn(),v.reason()}).toArray(Object[][]::new);
        JTable table=CommerceTheme.table(new String[]{"名称 / 事项","类型","状态","主体","执照 / 资质编号","有效期","处理说明"},rows);
        ManagementLayout.badges(table,2);return table;
    }
    void selected(JTable table,List<View> values,Consumer<View> action) {
        int index=table.getSelectedRow();
        if(index<0) { ui.notice("请先选择一条记录"); return; }
        action.accept(values.get(table.convertRowIndexToModel(index)));
    }
    void detail(View v) {
        JTextArea text=new JTextArea("事项："+v.title()+"\n类型："+GovernanceLabels.kind(v.kind())+"\n状态："+GovernanceLabels.state(v.state())+"\n对象编号："+v.objectId()+"\n主体："+blank(v.subjectName())+"\n执照/资质编号："+blank(v.licenseNumber())+"\n有效期："+(v.expiresOn()==null?"—":v.expiresOn())+"\n说明："+blank(v.description())+"\n处理结果："+blank(v.reason()),13,48);
        text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);
        ui.modal("记录详情",CommerceTheme.scroll(text),null,ui::closeModal,720);
    }
    void display(String title,JComponent main,JComponent footer,Runnable previous,boolean admin) {
        JPanel body=new JPanel(new BorderLayout(0,16));body.setOpaque(false);body.add(main);
        if(footer!=null)body.add(footer,BorderLayout.SOUTH);
        String section=title.contains("资质")?"资质审核":title.equals("开店申请")?"开店审核":title.equals("处理历史")?"处理记录":title.equals("店铺管理")?"店铺列表":title;
        ui.display(title,admin?ManagementLayout.admin(this,body,section):ManagementLayout.seller(ui,body,title.contains("资质")?"经营资质":"店铺设置"),null,previous);
    }
    private static String blank(String value) { return value==null?"—":value; }
}
