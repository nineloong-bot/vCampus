package edu.seu.vcampus.server.shop.composition;
import edu.seu.vcampus.server.persistence.*;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.shop.adapter.FoundationShopUserAdapter;
import edu.seu.vcampus.server.shop.catalog.*;
import edu.seu.vcampus.server.shop.governance.*;
import edu.seu.vcampus.server.shop.order.*;
import edu.seu.vcampus.server.shop.payment.ReservationExpiryJob;
import edu.seu.vcampus.server.wallet.service.WalletPostingService;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.concurrent.*;
/** Composes catalog, governance, orders and wallet with one transaction boundary. */
public final class CommerceRuntime implements AutoCloseable {
 private final CatalogService catalog;
 private final GovernanceService governance;
 private final OrderService orders;
 private final ReservationExpiryJob legacyExpiry;
 private ScheduledExecutorService maintenance;
 /**
  * Registers the commerce protocol using the shared transaction boundary.
  * Application resource stripes are deliberately isolated: student provisioning
  * acquires its stripes inside that transaction, while commerce acquires first.
  * The application lock argument remains a composition compatibility parameter.
  */
 public CommerceRuntime(MessageRouter router,TransactionManager tx,ResourceLockManager applicationLocks,SessionRegistry sessions,Clock clock){
  java.util.Objects.requireNonNull(applicationLocks,"applicationLocks");
  ResourceLockManager locks=new StripedResourceLockManager();
  CatalogService[] products=new CatalogService[1];OrderService[] orderPort=new OrderService[1];
  governance=new GovernanceService(tx,locks,clock,new GovernanceEffects(){
   @Override public void shopSuspended(Connection c,String shop)throws SQLException{
    orderPort[0].cancelPendingForShop(new TransactionContext(c),shop);
    clearCart(c,"SELECT productId FROM tblProduct WHERE shopId=?",shop);
   }
   @Override public void productRestricted(Connection c,String product)throws SQLException{
    clearCart(c,"SELECT productId FROM tblProduct WHERE productId=?",product);
   }
   @Override public boolean mayRestore(Connection c,String product)throws SQLException{return products[0].mayRestore(c,product);}
  });
  GovernancePolicy policy=governance.policy();
  catalog=new CatalogService(tx,locks,clock,new ProductPolicy(){
   @Override public boolean mayPublish(Connection c,String shop,String category)throws SQLException{return policy.mayPublish(c,shop,category);}
   @Override public boolean mayBuy(Connection c,String product)throws SQLException{return policy.mayBuy(c,product);}
   @Override public void manualOff(Connection c,String product)throws SQLException{policy.manualOff(c,product);}
   @Override public String effectiveStatus(Connection c,String product,String base)throws SQLException{return policy.effectiveStatus(c,product,base);}
  });products[0]=catalog;
  orders=new OrderService(tx,new WalletPostingService(clock),clock,catalog::purchasable);orderPort[0]=orders;
  var users=new FoundationShopUserAdapter(new AuthorizationService(sessions),token->sessions.requireSnapshot(token).restricted());
  new CatalogHandlers(router,catalog,new CartService(catalog),new ImportService(catalog),users);
  new GovernanceHandlers(router,governance,users);new OrderHandlers(router,orders,sessions);
  legacyExpiry=new ReservationExpiryJob(tx,locks,clock);
 }
 private static void clearCart(Connection c,String products,String key)throws SQLException{
  try(var s=c.prepareStatement("DELETE FROM tblCartItem WHERE skuId IN (SELECT skuId FROM tblProductSku WHERE productId IN ("+products+"))")){
   s.setString(1,key);s.executeUpdate();
  }
 }
 /** Makes catalog services available to composition tests and trusted adapters. */
 public CatalogService catalog(){return catalog;}
 /** Returns the shared transactional order boundary. */
 public OrderService orders(){return orders;}
 /** Returns the governance boundary for trusted composition. */
 public GovernanceService governance(){return governance;}
 /** Runs all recovery tasks once, including legacy reservations that still own stock. */
 public void recover(){governance.maintain();orders.expirePending();legacyExpiry.expirePendingPayments();}
 /** Starts production maintenance; test compositions remain idle unless explicitly started. */
 public synchronized void start(){
  if(maintenance!=null)return;recover();maintenance=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"commerce-maintenance");t.setDaemon(true);return t;});
  maintenance.scheduleWithFixedDelay(()->{try{recover();}catch(RuntimeException e){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Commerce recovery will retry",e);}},60,60,TimeUnit.SECONDS);
 }
 /** Stops maintenance before application shutdown. */
 @Override public synchronized void close(){if(maintenance!=null){maintenance.shutdownNow();maintenance=null;}}
}
