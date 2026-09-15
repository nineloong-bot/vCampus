package edu.seu.vcampus.server.shop.governance;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

final class GovernanceFixture implements AutoCloseable {
    final ShopTestDatabase db=new ShopTestDatabase();
    final ShopUser owner=new ShopUser("owner-1",ShopUserKind.STUDENT,true);
    final ShopUser buyer=new ShopUser("student-1",ShopUserKind.STUDENT,true);
    final ShopUser admin=new ShopUser("admin-1",ShopUserKind.ADMINISTRATOR,true);
    final Clock clock=Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"),ZoneOffset.UTC);
    final GovernanceService service;
    int suspended;
    int restricted;
    GovernanceFixture() throws Exception {
        try(var c=db.connections().open()) {
            for(String sql:Files.readString(Path.of("..","vcampus-database","schema","054_shop_governance.sql")).split(";"))
                if(!sql.isBlank()) c.createStatement().execute(sql);
        }
        service=new GovernanceService(new TransactionManager(db.connections()),new StripedResourceLockManager(),clock,new GovernanceEffects() {
            public void shopSuspended(Connection c,String shop) { suspended++; }
            public void productRestricted(Connection c,String product) { restricted++; }
            public boolean mayRestore(Connection c,String product) { return true; }
        });
    }
    View write(ShopUser user,String command,Serializable body) {
        return (View)service.execute(user,command,body,java.util.UUID.randomUUID().toString());
    }
    Views read(ShopUser user,String command,Serializable body) {
        return (Views)service.execute(user,command,body,"query");
    }
    String createShop() {
        String application=write(owner,"APPLY",new Apply("Campus books","Campus operator","SIM-001",true)).id();
        write(admin,"REVIEW_APPLICATION",new Review(application,true,"Verified simulated information"));
        return read(owner,"SELF",EmptyRequest.INSTANCE).items().getFirst().id();
    }
    void product(String shop,String id,String category) throws SQLException {
        try(var c=db.connections().open()) {
            GovernanceSql.update(c,"INSERT INTO tblProduct (productId,shopId,productName,normalizedProductName,category,description,productStatus,createdAt,updatedAt) VALUES (?,?,?,?,?,'description','ACTIVE',?,?)",id,shop,id,id,category,clock.instant(),clock.instant());
        }
    }
    String scalar(String sql,Object... args) throws SQLException {
        try(var c=db.connections().open()) { return GovernanceSql.scalar(c,sql,args); }
    }
    @Override public void close() throws Exception { db.close(); }
}
