package edu.seu.vcampus.client.shop.commerce;
import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.protocol.*;
import edu.seu.vcampus.common.user.*;
import edu.seu.vcampus.server.persistence.*;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.shop.composition.*;
import edu.seu.vcampus.server.wallet.WalletSchemaInitializer;
import edu.seu.vcampus.server.wallet.handler.WalletHandlers;
import edu.seu.vcampus.server.wallet.service.WalletService;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class CommerceTestEnvironment implements AutoCloseable {
 final Path directory=Files.createTempDirectory("commerce-acceptance-");
 final Path database=directory.resolve("commerce.accdb");
 final ConnectionProvider connections=()->DriverManager.getConnection("jdbc:ucanaccess://"+database+";newDatabaseVersion=V2010");
 final SessionRegistry sessions=new SessionRegistry();
 final CommerceRuntime commerce;final WalletService wallet;final SocketServer server;final Thread serving;
 final List<ClientConnection> clients=new ArrayList<>();
 CommerceTestEnvironment()throws Exception {
  try(var c=connections.open();var s=c.createStatement()){
   s.execute("CREATE TABLE tblUser (userId VARCHAR(36) PRIMARY KEY)");
   for(String id:List.of("buyer","seller","admin"))s.execute("INSERT INTO tblUser VALUES ('"+id+"')");
   for(String sql:Files.readString(Path.of("../vcampus-database/schema/050_shop.sql")).split(";"))if(!sql.isBlank())s.execute(sql);
  }
  Path schema=Path.of("../vcampus-database/schema");new WalletSchemaInitializer(schema.resolve("051_shop_wallet.sql")).initialize(connections);
  new CommerceSchemaInitializer(schema).initialize(connections);
  var tx=new TransactionManager(connections);var locks=new StripedResourceLockManager();var router=new MessageRouter(Map.of());
  wallet=new WalletService(tx,locks,Clock.systemUTC());new WalletHandlers(router,wallet,sessions);
  commerce=new CommerceRuntime(router,tx,locks,sessions,Clock.systemUTC());
  server=new SocketServer(0,6,20,router);serving=new Thread(()->{try{server.serve();}catch(Exception e){if(!Thread.currentThread().isInterrupted())throw new RuntimeException(e);}});serving.start();
 }
 ClientConnection client(String id)throws Exception{
  var c=new ClientConnection("127.0.0.1",server.localPort());c.connect(Duration.ofSeconds(5));
  c.setSessionToken(sessions.create(new UserIdentity(id,id,id.equals("admin")?UserRole.SHOP_ADMIN:UserRole.STUDENT,AccountStatus.ACTIVE)));
  clients.add(c);return c;
 }
 <T extends Serializable>T call(ClientConnection client,String cmd,Serializable body)throws Exception{return call(client,cmd,body,UUID.randomUUID().toString());}
 @SuppressWarnings("unchecked") <T extends Serializable>T call(ClientConnection client,String cmd,Serializable body,String id)throws Exception{
  var response=client.<Serializable>send(cmd,body,Duration.ofSeconds(10),id).get(15,TimeUnit.SECONDS);
  if(!response.success())throw new AssertionError(cmd+": "+response.code()+" / "+response.message());
  return (T)response.data();
 }
 long number(String sql)throws Exception{try(var c=connections.open();var s=c.createStatement();var rows=s.executeQuery(sql)){rows.next();return rows.getLong(1);}}
 @Override public void close()throws Exception{
  for(var c:clients)c.close();commerce.close();server.close();serving.join(3000);
  try(var files=Files.walk(directory)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
 }
}
