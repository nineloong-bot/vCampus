package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class OrderSchemaRestartTest {
    @TempDir Path temporary;

    @Test void indexSurvivesClosingAndReopeningTheAccessDatabase() throws Exception {
        Path file=temporary.resolve("restart.accdb");
        try(var db=DatabaseBuilder.create(Database.FileFormat.V2010,file.toFile())) { }
        ConnectionProvider connections=()->DriverManager.getConnection(
                "jdbc:ucanaccess://"+file+";immediatelyReleaseResources=true");
        try(var c=connections.open();var s=c.createStatement()) {
            s.execute("CREATE TABLE tblOrder (orderId VARCHAR(36) PRIMARY KEY)");
            s.execute("CREATE TABLE tblOrderItem (orderItemId VARCHAR(36) PRIMARY KEY)");
            s.execute("INSERT INTO tblOrder VALUES ('preserved')");
        }
        var initializer=new OrderSchemaInitializer(Path.of("../vcampus-database/schema/053_shop_orders.sql"));
        try(var keepAlive=connections.open()) {
            assertFalse(keepAlive.isClosed());
            initializer.initialize(connections);
            initializer.initialize(connections);
        }
        for(int attempt=0;attempt<2;attempt++) {
            initializer.initialize(connections);
            try(var c=connections.open();var s=c.createStatement();var r=s.executeQuery("SELECT orderId FROM tblOrder")) {
                assertTrue(r.next());assertEquals("preserved",r.getString(1));assertFalse(r.next());
            }
        }
        try(var db=new DatabaseBuilder(file.toFile()).open()) {
            assertEquals(1,db.getTable("tblShopOrderState").getIndexes().stream()
                    .filter(i->i.getName().equalsIgnoreCase("idx_shop_order_expiry")).count());
        }
    }
}
