package edu.seu.vcampus.client.shop.commerce;
import edu.seu.vcampus.common.shop.order.*;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
/** Order lifecycle pages; every transition uses the authenticated server transaction. */
final class OrderPages {
 private final CommercePanel ui;
 OrderPages(CommercePanel ui){this.ui=ui;}
 void checkout(List<OrderLine> lines,boolean fromCart){
  JPanel body=CommerceTheme.form();JLabel balance=new JLabel("余额加载中…");body.add(balance);
  ui.modal("确认结算",body,null,()->new CartPage(ui).open(),720);
  ui.fetch("WALLET_GET_BALANCE",edu.seu.vcampus.common.protocol.EmptyRequest.INSTANCE,v->balance.setText("可用余额 "+CommerceTheme.money(((edu.seu.vcampus.common.wallet.WalletBalance)v).balanceCents())),()->balance.setText("余额加载失败，请返回后重试"));
  ui.fetch("SHOP2_ORDER_QUOTE",new CheckoutRequest(lines,fromCart),data->{CheckoutQuote quote=(CheckoutQuote)data;
   body.add(new JLabel(quote.notice()));body.add(new JLabel("商品种数 "+quote.lines().size()+" · 合计 ¥"+quote.amount()));
   body.add(new JLabel("提交后为待付款订单，30分钟内完成支付"));
   JButton submit=CommerceTheme.primary(new JButton("提交订单"));submit.setEnabled(!quote.lines().isEmpty());
   submit.addActionListener(e->ui.write("SHOP2_ORDER_CHECKOUT",new CheckoutRequest(quote.lines(),fromCart),submit,result->{OrderResult orders=(OrderResult)result;ui.refreshCartDot();showOrders(orders,false);}));
   body.add(CommerceTheme.row(submit,CommerceTheme.button("查看余额 / 充值",()->new WalletPage(ui).open())));body.revalidate();
  });
 }
 void list(boolean seller){list(seller,"ALL");}
 private void list(boolean seller,String state){
  JPanel content=new JPanel(new BorderLayout(0,12));content.setOpaque(false);
  String[] labels={"全部","待付款","待发货","待收货","已完成","退款 / 已关闭"};
  String[] states={"ALL","PENDING_PAYMENT","PAID","SHIPPED","COMPLETED","CLOSED"};
  JPanel top=CommerceTheme.form();JPanel tabs=CommerceTheme.row();
  for(int i=0;i<states.length;i++){String filter=states[i];JButton tab=CommerceTheme.button(labels[i],()->list(seller,filter));
   if(filter.equals(state))CommerceTheme.primary(tab);tabs.add(tab);}
  top.add(tabs);content.add(top,BorderLayout.NORTH);
  if(seller)ui.display("店铺订单",ManagementLayout.seller(ui,content,"订单管理"),null,()->new AccountPages(ui).open());
  else ui.modal("我的订单",content,null,()->new AccountPages(ui).open(),960);
  ui.fetch(seller?"SHOP2_ORDER_SELLER_LIST":"SHOP2_ORDER_BUYER_LIST",new OrderQuery(state,null),data->mount(content,(OrderResult)data,seller));
 }
 private void showOrders(OrderResult result,boolean seller){JPanel p=new JPanel(new BorderLayout());p.setOpaque(false);ui.modal("订单",p,null,()->list(seller),960);mount(p,result,seller);}
 private void mount(JPanel container,OrderResult result,boolean seller){
  JPanel cards=CommerceTheme.form();cards.setName("order.list");java.util.Map<JCheckBox,String> selected=new java.util.LinkedHashMap<>();
  for(OrderView order:result.orders()){
   JPanel card=CommerceTheme.card(Color.WHITE,18);card.setName("order.card."+order.orderId());card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS));
   JPanel head=new JPanel(new BorderLayout());head.setOpaque(false);head.add(CommerceTheme.heading(order.shopName(),16));
   head.add(CommerceTheme.muted(label(order.state())),BorderLayout.EAST);card.add(head);card.add(CommerceTheme.gap(10));
   card.add(CommerceTheme.muted("订单 "+order.orderId()+(seller?" · 买家昵称："+order.buyerNickname():"")));
   card.add(items(order));
   if(order.refundReason()!=null&&!order.refundReason().isBlank())card.add(CommerceTheme.muted("退款说明："+order.refundReason()));
   JPanel actions=CommerceTheme.row(CommerceTheme.heading("合计 ¥"+order.amount(),16),CommerceTheme.button("查看明细",()->detail(order,seller)));
   if(!seller&&"PENDING_PAYMENT".equals(order.state())){
    JCheckBox check=new JCheckBox("选择付款");check.setOpaque(false);selected.put(check,order.orderId());actions.add(check);
    actions.add(CommerceTheme.primary(CommerceTheme.button("余额付款",()->pay(List.of(order.orderId())))));
   }
   if(seller&&"PAID".equals(order.state()))actions.add(action("发货","SHIP",order,true,false));
   card.add(actions);cards.add(card);cards.add(CommerceTheme.gap(14));
  }
  if(result.orders().isEmpty()){cards.add(CommerceTheme.gap(36));cards.add(CommerceTheme.heading("暂无订单",20));cards.add(CommerceTheme.muted("从购物车提交订单后，可在这里查看进度。"));}
  container.add(CommerceTheme.scroll(cards),BorderLayout.CENTER);JPanel buttons=CommerceTheme.row(CommerceTheme.button("刷新",()->list(seller)));
  if(!seller)buttons.add(CommerceTheme.button("返回我的",()->new AccountPages(ui).open()));
  if(!seller)buttons.add(CommerceTheme.primary(CommerceTheme.button("支付所选待付款订单",()->{
   List<String> ids=selected.entrySet().stream().filter(e->e.getKey().isSelected()).map(java.util.Map.Entry::getValue).toList();if(!ids.isEmpty())pay(ids);
  })));
  container.add(buttons,BorderLayout.SOUTH);ui.notice(result.notice());container.revalidate();container.repaint();
 }
 private void detail(OrderView order,boolean seller){
  JPanel body=CommerceTheme.form();
  body.add(CommerceTheme.muted("订单 "+order.orderId()+" · "+order.shopName()));body.add(CommerceTheme.heading(label(order.state()),24));
  if(seller)body.add(CommerceTheme.muted("买家昵称："+order.buyerNickname()));
  body.add(items(order));body.add(CommerceTheme.heading("合计 ¥"+order.amount(),20));
  JPanel history=CommerceTheme.card(new Color(0xf3f6f2),14);history.setLayout(new BoxLayout(history,BoxLayout.Y_AXIS));
  history.add(CommerceTheme.muted("创建时间："+order.createdAt()));
  if(order.refundReason()!=null&&!order.refundReason().isBlank())history.add(new JLabel("退款说明："+order.refundReason()));
  body.add(history);
  JPanel actions=CommerceTheme.row(CommerceTheme.button("返回订单列表",()->list(seller)));String state=order.state();
  if(!seller&&state.equals("PENDING_PAYMENT")){actions.add(CommerceTheme.primary(CommerceTheme.button("支付",()->pay(List.of(order.orderId())))));actions.add(action("取消订单","CANCEL",order,seller,false));}
  if(!seller&&state.equals("PAID"))actions.add(action("申请整单退款","REFUND_REQUEST",order,seller,true));
  if(!seller&&state.equals("SHIPPED"))actions.add(action("确认收货","RECEIVE",order,seller,false));
  if(seller&&state.equals("PAID"))actions.add(action("发货","SHIP",order,seller,false));
  if(seller&&state.equals("REFUND_PENDING")){actions.add(action("同意退款","REFUND_APPROVE",order,seller,false));actions.add(action("驳回退款","REFUND_REJECT",order,seller,true));}
  ui.modal("订单明细",body,actions,()->list(seller),960);
 }
 private JPanel items(OrderView order){
  JPanel lines=CommerceTheme.form();
  for(OrderItem item:order.items()){
   JPanel line=new JPanel(new BorderLayout(18,0));line.setOpaque(false);line.setBorder(BorderFactory.createEmptyBorder(12,0,12,0));
   JPanel info=CommerceTheme.form();info.add(new JLabel(item.productName()));
   info.add(CommerceTheme.muted(item.skuName()+" · ¥"+item.unitPrice()+" × "+item.quantity()+(item.valid()?"":" · 已失效，不结算")));
   line.add(info);line.add(new JLabel("¥"+item.lineAmount()),BorderLayout.EAST);lines.add(line);
  }
  return lines;
 }
 private JButton action(String title,String action,OrderView order,boolean seller,boolean needsReason){
  JButton b=CommerceTheme.button(title,()->{});b.addActionListener(e->{
   if(needsReason){reasonEditor(title,action,order,seller);return;}
   if(!ui.confirm("确定"+title+"？"))return;
   submitAction(action,order,seller,"",b);
  });return b;
 }
 private void reasonEditor(String title,String action,OrderView order,boolean seller){
  JPanel form=CommerceTheme.form();JTextArea reason=new JTextArea(4,32);reason.setLineWrap(true);reason.setWrapStyleWord(true);
  CommerceTheme.field(form,"原因（必填）",new JScrollPane(reason));JButton submit=CommerceTheme.primary(new JButton("确认"+title));
  submit.addActionListener(e->{if(reason.getText().isBlank()){ui.notice("请填写原因");return;}
   submitAction(action,order,seller,reason.getText().strip(),submit);});
  ui.modal(title,form,CommerceTheme.row(CommerceTheme.button("取消",ui::closeModal),submit),ui::closeModal,620);
 }
 private void submitAction(String action,OrderView order,boolean seller,String reason,JButton button){
  ui.write("SHOP2_ORDER_"+action,new OrderAction(List.of(order.orderId()),reason),button,
          data->showOrders((OrderResult)data,seller));
 }
 private void pay(List<String> ids){
  ui.fetch("SHOP2_ORDER_VALIDATE",new OrderAction(ids,""),data->{OrderResult result=(OrderResult)data;
   List<OrderView> valid=result.orders().stream().filter(o->o.state().equals("PENDING_PAYMENT")).toList();
   if(valid.isEmpty()){showOrders(result,false);return;}BigDecimal amount=valid.stream().map(OrderView::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
   JPanel content=CommerceTheme.form();content.add(new JLabel(result.notice()));content.add(new JLabel("本次购买 "+valid.size()+" 个店铺订单，合计 ¥"+amount));
   JLabel balance=new JLabel("余额加载中…");content.add(balance);JButton pay=CommerceTheme.primary(new JButton("确认余额支付"));
   pay.addActionListener(e->ui.write("SHOP2_ORDER_PAY",new OrderAction(valid.stream().map(OrderView::orderId).toList(),""),pay,response->showOrders((OrderResult)response,false)));
   ui.modal("确认付款",content,CommerceTheme.row(CommerceTheme.button("返回订单列表",()->list(false)),pay,CommerceTheme.button("去充值",()->new WalletPage(ui).recharge())),()->list(false),640);
   ui.fetch("WALLET_GET_BALANCE",edu.seu.vcampus.common.protocol.EmptyRequest.INSTANCE,v->balance.setText("可用余额 "+CommerceTheme.money(((edu.seu.vcampus.common.wallet.WalletBalance)v).balanceCents())),()->balance.setText("余额加载失败，请返回后重试"));
  });
 }
 static String label(String state){if(state.startsWith("LEGACY_"))return "历史订单 · "+label(state.substring(7));return switch(state){case "PENDING_PAYMENT"->"待付款";case "PAID"->"待发货";case "SHIPPED"->"待收货";case "COMPLETED"->"已完成";case "REFUND_PENDING"->"退款审核中";case "REFUNDED"->"已退款";case "CANCELLED"->"已取消";default->state;};}
}
