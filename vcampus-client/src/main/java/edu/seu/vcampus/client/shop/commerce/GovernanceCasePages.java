package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import edu.seu.vcampus.common.shop.governance.GovernanceDtos;
import edu.seu.vcampus.common.shop.catalog.CatalogDtos.Product;
import javax.swing.*;
import java.util.List;

/** Privacy-safe seller notices and administrator report and recovery queues. */
final class GovernanceCasePages {
    private final GovernancePages pages;
    private final CommercePanel ui;
    GovernanceCasePages(GovernancePages pages) { this.pages=pages;ui=pages.ui; }
    void open(boolean seller) {
        pages.list(seller?"经营通知与恢复申请":"举报与恢复申请","CASES",new Query(seller?"SHOP":"ADMIN",null),values->{
            JTable table=pages.table(values);
            JPanel actions=CommerceTheme.row(CommerceTheme.button("查看详情",()->pages.selected(table,values,pages::detail)));
            if(!seller) {
                actions.add(CommerceTheme.button("处理举报",()->pages.selected(table,values,this::resolve)));
                actions.add(CommerceTheme.button("恢复审核通过",()->pages.selected(table,values,v->reviewRecovery(v,true))));
                actions.add(CommerceTheme.button("驳回恢复申请",()->pages.selected(table,values,v->reviewRecovery(v,false))));
                actions.add(CommerceTheme.button("审计历史",()->pages.selected(table,values,v->pages.audit(v.objectId()))));
            } else {
                actions.add(CommerceTheme.button("申请恢复营业",()->ui.fetch("SHOP2_GOV_SHOP",new Query("SELF",null),data->{
                    var shops=((Views)data).items();
                    if(shops.isEmpty()){ui.notice("未找到店铺");return;}
                    pages.requestReopen(shops.getFirst().id());
                })));
                actions.add(CommerceTheme.button("商品整改复核",()->pages.selected(table,values,v->{
                    if(!"NOTICE".equals(v.kind())||!"EMERGENCY".equals(v.title())){ui.notice("请选择商品紧急下架通知");return;}
                    pages.requestRemediation(v.objectId());
                })));
            }
            pages.display(seller?"经营通知与恢复申请":"举报与恢复申请",ManagementLayout.table(table),actions,seller?ui::home:pages::shops,!seller);
        });
    }
    private void reviewRecovery(View view,boolean approved) {
        if(!List.of("REOPEN","REMEDIATION").contains(view.kind())){ui.notice("请选择店铺恢复或商品整改申请");return;}
        new GovernanceForms(pages).review(view,"REVIEW_CASE",approved,()->open(false));
    }
    private void resolve(View view) {
        if(!view.kind().startsWith("REPORT_")||!"PENDING".equals(view.state())){ui.notice("请选择待处理举报");return;}
        String[] labels={"警告","商品紧急下架","暂停店铺","未发现违规"};
        JComboBox<String> choice=new JComboBox<>(labels);JTextField productId=new JTextField();JTextArea reason=new JTextArea(4,32);
        JPanel form=CommerceTheme.form();CommerceTheme.field(form,"处理结果",choice);
        CommerceTheme.field(form,"紧急下架商品编号",productId);CommerceTheme.field(form,"原因与整改要求",new JScrollPane(reason));
        JButton submit=CommerceTheme.primary(new JButton("确认处理"));submit.addActionListener(event->{
            String action=new String[]{"WARN","EMERGENCY","SUSPEND","NO_VIOLATION"}[choice.getSelectedIndex()];
            if(reason.getText().isBlank()){ui.notice("请填写原因与整改要求");return;}
            if("REPORT_SHOP".equals(view.kind())&&"EMERGENCY".equals(action)&&productId.getText().isBlank()){
                ui.notice("请填写需要紧急下架的商品编号");return;
            }
            resolve(view,action,productId.getText().strip(),reason.getText().strip(),submit);
        });
        ui.modal("处理举报",form,CommerceTheme.row(CommerceTheme.button("取消",ui::closeModal),submit),ui::closeModal,680);
    }
    private void resolve(View view,String action,String productId,String reason,JButton submit) {
        if("REPORT_PRODUCT".equals(view.kind())) {
            ui.fetch("SHOP2_PRODUCT_ADMIN_DETAIL",view.objectId(),data->{
                Product p=(Product)data;
                write("EMERGENCY".equals(action)?p.id():p.shopId(),action,reason,view.id(),
                        "WARN".equals(action)?List.of(p.id()):List.of(),submit);
            });
        } else write("EMERGENCY".equals(action)?productId:view.objectId(),action,reason,view.id(),List.of(),submit);
    }
    private void write(String objectId,String action,String reason,String reportId,List<String> ids,JButton submit) {
        ui.write("SHOP2_GOV_ACTION",new GovernanceDtos.Action(objectId,action,reason,ids,reportId),submit,
                data->{open(false);ui.notice("处置已记录，商家可查看通知");});
    }
}
