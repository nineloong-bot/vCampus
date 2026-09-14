package edu.seu.vcampus.server.shop.governance;

import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GovernancePolicyTest {
    @Test void legacyShopAndIndependentRestrictionsUseAccess() throws Exception {
        try (var db = new ShopTestDatabase(); var c = db.connections().open()) {
            for (String sql : Files.readString(Path.of("..", "vcampus-database", "schema", "054_shop_governance.sql")).split(";")) {
                if (!sql.isBlank()) c.createStatement().execute(sql);
            }
            c.createStatement().execute("INSERT INTO tblShop (shopId,ownerUserId,shopName,normalizedShopName,description,category,contact,shopStatus,createdAt,updatedAt) VALUES ('s','owner-1','Store','store','','ordinary','','ACTIVE',Now(),Now())");
            c.createStatement().execute("INSERT INTO tblProduct (productId,shopId,productName,normalizedProductName,category,description,productStatus,createdAt,updatedAt) VALUES ('p','s','Book','book','ordinary','','ACTIVE',Now(),Now())");
            var policy = new GovernancePolicy();
            assertTrue(policy.mayBuy(c,"p"));
            assertTrue(policy.mayPublish(c,"s","ordinary"));
            for(String legacy : java.util.List.of("图书","文具","家居","服装","数码","日用品")) assertTrue(policy.mayPublish(c,"s",legacy));
            assertFalse(policy.mayPublish(c,"s","unknown"));
            assertFalse(policy.mayPublish(c,"s","licensed"));
            assertFalse(policy.mayPublish(c,"s","prohibited"));
            c.createStatement().execute("INSERT INTO tblShopProductRestriction VALUES ('p',TRUE,FALSE,FALSE)");
            assertFalse(policy.mayBuy(c,"p"));
            c.createStatement().execute("UPDATE tblShopProductRestriction SET emergencyBlocked=FALSE,qualificationBlocked=TRUE,expiryRestoreEligible=TRUE");
            policy.manualOff(c,"p");
            var rs=c.createStatement().executeQuery("SELECT expiryRestoreEligible FROM tblShopProductRestriction");
            assertTrue(rs.next());
            assertFalse(rs.getBoolean(1));
        }
    }
    @Test void categoryChangeDropsOnlyTheObsoleteQualificationRestriction() throws Exception {
        try (var f = new GovernanceFixture()) {
            String shop = f.createShop();
            f.product(shop, "changed", "licensed");
            try (var c = f.db.connections().open()) {
                GovernanceSql.update(c, "INSERT INTO tblShopProductRestriction VALUES ('changed',FALSE,TRUE,TRUE)");
                assertFalse(f.service.policy().mayBuy(c, "changed"));
                f.service.policy().manualOff(c, "changed");
                GovernanceSql.update(c, "UPDATE tblProduct SET productStatus='INACTIVE',category='ordinary' WHERE productId='changed'");
                assertTrue(f.service.policy().mayPublish(c, shop, "ordinary"));
                assertTrue(f.service.policy().mayBuy(c, "changed"));
                assertEquals("INACTIVE", f.service.policy().effectiveStatus(c, "changed", "INACTIVE"));
                GovernanceSql.update(c, "UPDATE tblShopProductRestriction SET emergencyBlocked=TRUE WHERE productId='changed'");
                assertFalse(f.service.policy().mayBuy(c, "changed"));
                assertEquals("EMERGENCY_BLOCKED", f.service.policy().effectiveStatus(c, "changed", "INACTIVE"));
            }
            f.service.maintain();
            assertFalse(Boolean.parseBoolean(f.scalar("SELECT qualificationBlocked FROM tblShopProductRestriction WHERE productId='changed'")));
            assertFalse(Boolean.parseBoolean(f.scalar("SELECT expiryRestoreEligible FROM tblShopProductRestriction WHERE productId='changed'")));
            assertTrue(Boolean.parseBoolean(f.scalar("SELECT emergencyBlocked FROM tblShopProductRestriction WHERE productId='changed'")));
            assertEquals("INACTIVE", f.scalar("SELECT productStatus FROM tblProduct WHERE productId='changed'"));
        }
    }
}
