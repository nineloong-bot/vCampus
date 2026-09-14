package edu.seu.vcampus.client.shop.commerce;
import edu.seu.vcampus.common.shop.catalog.CatalogDtos;
import edu.seu.vcampus.common.shop.governance.GovernanceDtos;
import edu.seu.vcampus.common.shop.order.*;
import edu.seu.vcampus.common.wallet.*;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CommerceAcceptanceTest {
 @Test void realSocketPurchaseSettlementModerationAndScreens()throws Exception{
  try(var env=new CommerceTestEnvironment()){
   var seller=env.client("seller");var admin=env.client("admin");var buyer=env.client("buyer");
   GovernanceDtos.View submitted=env.call(seller,"SHOP2_GOV_APPLY",new GovernanceDtos.Apply("校园生活商店","校园模拟主体","DEMO-001",true));
   env.call(admin,"SHOP2_GOV_REVIEW_APPLICATION",new GovernanceDtos.Review(submitted.id(),true,"课程模拟审核通过"));
   GovernanceDtos.Views own=env.call(seller,"SHOP2_GOV_SELF",EmptyRequest.INSTANCE);String shop=own.items().getFirst().id();
   List<CatalogDtos.Product> products=new ArrayList<>();String[] names={"校园笔记本","彩色水笔","陶瓷马克杯","轻便帆布包","柔软棉质短袖","桌面收纳盒"};String[] images={"book","pen","cup","bag","shirt","box"};
   for(int i=0;i<6;i++){
    String sku=UUID.randomUUID().toString();CatalogDtos.Product p=env.call(seller,"SHOP2_PRODUCT_SAVE",new CatalogDtos.SaveProduct("",null,names[i],"校园生活常用商品，课程展示资料。","ordinary",images[i],sku,List.of(new CatalogDtos.Sku(sku,"标准款",BigDecimal.TEN,10,0,true))));
    p=env.call(seller,"SHOP2_PRODUCT_ACTION",new CatalogDtos.ProductAction("",p.id(),"PUBLISH"));products.add(p);
   }
   env.call(buyer,"WALLET_RECHARGE",new RechargeCommand(new BigDecimal("100")),"recharge-acceptance");
   env.call(buyer,"WALLET_RECHARGE",new RechargeCommand(new BigDecimal("100")),"recharge-acceptance");
   CatalogDtos.Product first=products.getFirst();
   env.call(buyer,"SHOP2_CART_CHANGE",new CatalogDtos.CartChange("",null,first.defaultSkuId(),2));
   for(int i=1;i<products.size();i++)env.call(buyer,"SHOP2_CART_CHANGE",new CatalogDtos.CartChange("",null,products.get(i).defaultSkuId(),1));
   CommercePanel[] view=new CommercePanel[1];SwingUtilities.invokeAndWait(()->{view[0]=new CommercePanel(new SocketCommerceTransport(buyer),false);view[0].setSize(1240,820);view[0].home();});
   await(view[0],JList.class);capture(view[0],"buyer");
   SwingUtilities.invokeAndWait(()->new ProductPage(view[0]).open(first.id()));awaitNamed(view[0],"product.detail.quantity");capture(view[0],"product-detail");SwingUtilities.invokeAndWait(view[0]::closeModal);
   SwingUtilities.invokeAndWait(()->new AccountPages(view[0]).open());waitFor(view[0],c->c instanceof JLabel l&&"account.balance".equals(l.getName())&&"¥100.00".equals(l.getText()));capture(view[0],"my");
   SwingUtilities.invokeAndWait(()->view[0].setSize(900,650));capture(view[0],"my-compact");SwingUtilities.invokeAndWait(()->view[0].setSize(1240,820));
   SwingUtilities.invokeAndWait(view[0]::closeModal);
   SwingUtilities.invokeAndWait(()->new CartPage(view[0]).open());awaitNamed(view[0],"cart.choose.");capture(view[0],"cart");
   SwingUtilities.invokeAndWait(()->new WalletPage(view[0]).open());waitFor(view[0],c->c instanceof JLabel l&&"¥100.00".equals(l.getText()));awaitNamed(view[0],"wallet.entry.");capture(view[0],"wallet");
   CommercePanel[] management=new CommercePanel[1];SwingUtilities.invokeAndWait(()->{management[0]=new CommercePanel(new SocketCommerceTransport(seller),false);management[0].setSize(1240,820);new SellerCatalogPage(management[0]).open();});
   waitFor(management[0],c->c instanceof JTable t&&t.getRowCount()==6);capture(management[0],"seller-products");
   SwingUtilities.invokeAndWait(()->new ProductEditorPage(management[0]).open(first.id()));awaitNamed(management[0],"preset.book");capture(management[0],"product-editor");
   SwingUtilities.invokeAndWait(()->new ImportPage(management[0]).open());capture(management[0],"import");
   var request=new CheckoutRequest(List.of(new OrderLine(first.defaultSkuId(),2,BigDecimal.TEN)),true);
   CheckoutQuote quote=env.call(buyer,"SHOP2_ORDER_QUOTE",request);assertThat(quote.amount()).isEqualByComparingTo("20");
   OrderResult created=env.call(buyer,"SHOP2_ORDER_CHECKOUT",new CheckoutRequest(quote.lines(),true));String order=created.orders().getFirst().orderId();
   OrderAction action=new OrderAction(List.of(order),"");env.call(buyer,"SHOP2_ORDER_PAY",action,"pay-once");env.call(buyer,"SHOP2_ORDER_PAY",action,"pay-once");
   assertThat(env.wallet.getBalance("buyer").balanceCents()).isEqualTo(8000);assertThat(env.wallet.getBalance("seller").pendingCents()).isEqualTo(2000);
   env.call(seller,"SHOP2_ORDER_SHIP",action);env.call(buyer,"SHOP2_ORDER_RECEIVE",action,"receive-once");env.call(buyer,"SHOP2_ORDER_RECEIVE",action,"receive-once");
   assertThat(env.wallet.getBalance("seller").balanceCents()).isEqualTo(2000);
   SwingUtilities.invokeAndWait(()->new OrderPages(view[0]).list(false));awaitNamed(view[0],"order.card.");capture(view[0],"orders");
   assertThat(env.number("SELECT reservedQuantity FROM tblProductSku WHERE skuId='"+first.defaultSkuId()+"'")).isZero();
   assertThat(env.number("SELECT stockQuantity FROM tblProductSku WHERE skuId='"+first.defaultSkuId()+"'")).isEqualTo(8);
   GovernanceDtos.View report=env.call(buyer,"SHOP2_GOV_SUBMIT_CASE",new GovernanceDtos.SubmitCase("REPORT_PRODUCT",first.id(),"商品信息虚假或误导","课程演示：请核查介绍"));
   env.call(admin,"SHOP2_GOV_ACTION",new GovernanceDtos.Action(first.id(),"EMERGENCY","核查期间紧急下架",List.of(first.id()),report.id()));
   CatalogDtos.Page publicProducts=env.call(buyer,"SHOP2_CATALOG_LIST",new CatalogDtos.Query("",null,"DEFAULT",1,12));assertThat(publicProducts.items()).hasSize(5);
   OrderResult history=env.call(buyer,"SHOP2_ORDER_BUYER_LIST",new OrderQuery("ALL",null));assertThat(history.orders().getFirst().state()).isEqualTo("COMPLETED");
   CommercePanel[] oversight=new CommercePanel[1];SwingUtilities.invokeAndWait(()->{oversight[0]=new CommercePanel(new SocketCommerceTransport(admin),true);oversight[0].setSize(1240,820);oversight[0].home();});
   await(oversight[0],JTable.class);capture(oversight[0],"admin-shops");
   env.call(admin,"SHOP2_GOV_ACTION",new GovernanceDtos.Action(shop,"SUSPEND","课程模拟停业",List.of(),null));
   CatalogDtos.Shop stopped=env.call(buyer,"SHOP2_CATALOG_SHOP",shop);assertThat(stopped.status()).isEqualTo("SUSPENDED");
   assertThat(env.number("SELECT SUM(deltaCents) FROM tblWalletEntry")).isZero();
  }
 }
 private static void await(CommercePanel panel,Class<?> type)throws Exception{waitFor(panel,c->type.isInstance(c));}
 private static void awaitNamed(CommercePanel panel,String prefix)throws Exception{waitFor(panel,c->c.getName()!=null&&c.getName().startsWith(prefix));}
 private static void waitFor(CommercePanel panel,java.util.function.Predicate<Component> condition)throws Exception{
  for(int i=0;i<100;i++){boolean[] ready={false};SwingUtilities.invokeAndWait(()->ready[0]=find(panel,condition));if(ready[0])return;Thread.sleep(50);}throw new AssertionError("UI response not rendered");
 }
 private static boolean find(Component c,java.util.function.Predicate<Component> p){if(p.test(c))return true;if(c instanceof Container box)for(Component child:box.getComponents())if(find(child,p))return true;return false;}
 private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layout(nested);}
 private static void capture(CommercePanel panel,String name)throws Exception{
  Path directory=Path.of("target/commerce-ui-review");Files.createDirectories(directory);BufferedImage image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);
  SwingUtilities.invokeAndWait(()->{panel.addNotify();layout(panel);panel.validate();});
  SwingUtilities.invokeAndWait(()->{layout(panel);Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();});javax.imageio.ImageIO.write(image,"png",directory.resolve(name+".png").toFile());
 }
}
