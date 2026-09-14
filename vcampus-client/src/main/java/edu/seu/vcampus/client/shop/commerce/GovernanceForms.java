package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import edu.seu.vcampus.common.shop.governance.GovernanceDtos.Action;
import javax.swing.*;
import java.util.List;
import java.time.LocalDate;

/** Text forms for licensed categories, moderation and recovery requests. */
final class GovernanceForms {
    private final GovernancePages pages;
    private final CommercePanel ui;
    GovernanceForms(GovernancePages pages) { this.pages=pages;ui=pages.ui; }
    void review(View record,String route,boolean approved,Runnable refresh) {
        if(!"PENDING".equals(record.state())) { ui.notice("请选择待审核记录");return; }
        JPanel form=CommerceTheme.form();
        form.add(new JLabel(record.title()+" · "+GovernanceLabels.kind(record.kind())));
        JTextArea reason=area();CommerceTheme.field(form,"审核说明（必填）",new JScrollPane(reason));
        JButton submit=new JButton(approved?"确认通过":"确认驳回");
        submit.addActionListener(e->{
            if(!required(reason.getText(),256))return;
            ui.write("SHOP2_GOV_"+route,new Review(record.id(),approved,reason.getText().strip()),submit,data->{refresh.run();ui.notice("审核结果已保存");});
        });
        ui.modal(approved?"审核通过":"驳回申请",form,CommerceTheme.row(CommerceTheme.primary(submit),CommerceTheme.button("返回",ui::closeModal)),ui::closeModal,680);
    }
    void qualification() {
        JPanel form=CommerceTheme.form();
        form.add(new JLabel("课程模拟准入：普通白名单可经营；专项许可商品需 SPECIAL 资质审核通过。"));
        JTextField number=new JTextField();JTextField expiry=new JTextField("2027-12-31");
        CommerceTheme.field(form,"资质类型",new JLabel("SPECIAL · 模拟专项许可"));
        CommerceTheme.field(form,"资质编号",number);CommerceTheme.field(form,"有效期 yyyy-MM-dd",expiry);
        JButton submit=new JButton("提交审核");
        submit.addActionListener(e->{
            if(!required(number.getText(),128))return;
            try {
                LocalDate date=LocalDate.parse(expiry.getText().strip());
                if(date.isBefore(LocalDate.now())){ui.notice("有效期不能早于今天");return;}
                ui.write("SHOP2_GOV_SUBMIT_QUALIFICATION",new Qualification("SPECIAL",number.getText().strip(),date),submit,data->pages.qualifications(false));
            } catch(java.time.format.DateTimeParseException error) {ui.notice("请按 yyyy-MM-dd 填写有效期");}
        });
        ui.modal("提交经营资质",form,CommerceTheme.row(CommerceTheme.primary(submit),CommerceTheme.button("返回",ui::closeModal)),ui::closeModal,720);
    }
    void report(String kind,String objectId) {
        JPanel form=CommerceTheme.form();
        JComboBox<String> reasons=new JComboBox<>(new String[]{"涉嫌禁售商品","经营资质问题","商品信息虚假或误导","店铺违规经营","其他问题"});
        JTextArea description=area();
        CommerceTheme.field(form,"举报原因",reasons);CommerceTheme.field(form,"具体说明（必填）",new JScrollPane(description));
        JButton submit=new JButton("提交举报");
        submit.addActionListener(e->{
            if(!required(description.getText(),4000))return;
            ui.write("SHOP2_GOV_SUBMIT_CASE",new SubmitCase(kind,objectId,(String)reasons.getSelectedItem(),description.getText().strip()),submit,data->{
                ui.home();ui.notice("举报已提交，可在我的举报查看处理结果");
            });
        });
        ui.modal("REPORT_PRODUCT".equals(kind)?"举报商品":"举报店铺",form,CommerceTheme.row(CommerceTheme.primary(submit),CommerceTheme.button("取消",ui::closeModal)),ui::closeModal,680);
    }
    void recovery(String kind,String objectId) {
        JPanel form=CommerceTheme.form();JTextArea explanation=area();
        form.add(new JLabel("REOPEN".equals(kind)?"管理员审核通过后恢复营业。":"复核通过后可重新申请上架，届时将检查商品和经营资质。"));
        CommerceTheme.field(form,"整改说明（必填）",new JScrollPane(explanation));
        JButton submit=new JButton("提交恢复申请");
        submit.addActionListener(e->{
            if(!required(explanation.getText(),4000))return;
            ui.write("SHOP2_GOV_SUBMIT_CASE",new SubmitCase(kind,objectId,"整改完成申请复核",explanation.getText().strip()),submit,data->pages.notices());
        });
        ui.modal("REOPEN".equals(kind)?"申请恢复营业":"商品整改复核",form,CommerceTheme.row(CommerceTheme.primary(submit),CommerceTheme.button("返回",ui::closeModal)),ui::closeModal,720);
    }
    void action(String objectId,String action,String reportId,List<String> productIds,Runnable refresh) {
        JPanel form=CommerceTheme.form();JTextArea reason=area();
        JTextField products=new JTextField(String.join(",",productIds));
        String title=switch(action){case "SUSPEND"->"暂停店铺营业";case "EMERGENCY"->"商品紧急下架";case "WARN"->"经营警告";default->"未发现违规";};
        CommerceTheme.field(form,"原因与整改要求",new JScrollPane(reason));
        if("WARN".equals(action))CommerceTheme.field(form,"关联商品编号（逗号分隔）",products);
        if(reportId!=null)CommerceTheme.field(form,"关联举报",new JLabel(reportId));
        if("SUSPEND".equals(action))form.add(new JLabel("暂停后将清理购物车并取消该店待付款订单，已付款订单继续履约。"));
        JButton submit=new JButton("确认处理");
        submit.addActionListener(e->{
            if(!required(reason.getText(),256))return;
            List<String> ids="WARN".equals(action)?java.util.Arrays.stream(products.getText().split("[,，]" )).map(String::strip).filter(s->!s.isEmpty()).distinct().toList():productIds;
            ui.write("SHOP2_GOV_ACTION",new Action(objectId,action,reason.getText().strip(),ids,reportId),submit,data->{refresh.run();ui.notice("处置已记录，商家可查看通知");});
        });
        ui.modal(title,form,CommerceTheme.row(CommerceTheme.primary(submit),CommerceTheme.button("取消",ui::closeModal)),ui::closeModal,720);
    }
    private boolean required(String value,int max) {
        if(value==null||value.isBlank()||value.length()>max){ui.notice("请输入必填内容，最多 "+max+" 字");return false;}
        return true;
    }
    private static JTextArea area() { JTextArea area=new JTextArea(4,40);area.setLineWrap(true);area.setWrapStyleWord(true);return area; }
}
