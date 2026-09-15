package edu.seu.vcampus.server.shop.governance;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceServiceTest {
    @Test void applicationsAreAtomicDeduplicatedAndOnlyAdminMayApprove() throws Exception {
        try(var f=new GovernanceFixture()) {
            var request=new Apply("Campus store","Operator","SIM-123",true);
            var first=f.service.execute(f.owner,"APPLY",request,"same-request");
            assertEquals(first,f.service.execute(f.owner,"APPLY",request,"same-request"));
            assertThrows(IllegalArgumentException.class,()->f.service.execute(f.owner,"APPLY",new Apply("Other","Operator","SIM-123",true),"same-request"));
            String id=((View)first).id();
            assertThrows(SecurityException.class,()->f.write(f.buyer,"REVIEW_APPLICATION",new Review(id,true,"Approve")));
            f.write(f.admin,"REVIEW_APPLICATION",new Review(id,true,"Verified"));
            assertEquals("1",f.scalar("SELECT COUNT(*) FROM tblShop"));
            assertThrows(IllegalArgumentException.class,()->f.write(f.admin,"REVIEW_APPLICATION",new Review(id,true,"Again")));
            assertThrows(IllegalArgumentException.class,()->f.write(f.owner,"APPLY",request));
        }
    }
    @Test void reportEmergencyAndRecoveryNeverAutoPublishOrDiscloseReporter() throws Exception {
        try(var f=new GovernanceFixture()) {
            String shop=f.createShop();
            f.product(shop,"p","ordinary");
            String report=f.write(f.buyer,"SUBMIT_CASE",new SubmitCase("REPORT_PRODUCT","p","OTHER","Incorrect information")).id();
            f.write(f.admin,"ACTION",new Action("p","EMERGENCY","Please correct",List.of(),report));
            assertEquals("INACTIVE",f.scalar("SELECT productStatus FROM tblProduct WHERE productId='p'"));
            assertEquals(1,f.restricted);
            assertThrows(IllegalArgumentException.class,()->f.write(f.admin,"ACTION",new Action("p","EMERGENCY","Again",List.of(),report)));
            var seller=f.read(f.owner,"CASES",new Query("SHOP",shop));
            assertEquals(1,seller.items().size());
            assertEquals("NOTICE",seller.items().getFirst().kind());
            assertFalse(seller.toString().contains(f.buyer.userId()));
            String recovery=f.write(f.owner,"SUBMIT_CASE",new SubmitCase("REMEDIATION","p","Corrected","Updated description")).id();
            f.write(f.admin,"REVIEW_CASE",new Review(recovery,true,"Accepted"));
            assertEquals("INACTIVE",f.scalar("SELECT productStatus FROM tblProduct WHERE productId='p'"));
            try(var c=f.db.connections().open()) { assertTrue(f.service.policy().mayBuy(c,"p")); }
            assertEquals("PROCESSED",f.read(f.buyer,"CASES",new Query("SELF",null)).items().getFirst().state());
            f.write(f.admin,"ACTION",new Action(shop,"SUSPEND","Shop issue",List.of(),null));
            assertEquals(1,f.suspended);
            assertThrows(SecurityException.class,()->f.write(f.buyer,"SUBMIT_CASE",new SubmitCase("REOPEN",shop,"Fixed","Fixed")));
            String reopen=f.write(f.owner,"SUBMIT_CASE",new SubmitCase("REOPEN",shop,"Fixed","Fixed")).id();
            f.write(f.admin,"REVIEW_CASE",new Review(reopen,true,"Approved"));
            assertEquals("ACTIVE",f.scalar("SELECT shopStatus FROM tblShop WHERE shopId=?",shop));
        }
    }
    @Test void expiryAndRenewalPreserveIndependentRestrictions() throws Exception {
        try(var f=new GovernanceFixture()) {
            String shop=f.createShop();
            for(String id:List.of("restore","manual","emergency","ordinary")) f.product(shop,id,id.equals("ordinary")?"ordinary":"licensed");
            try(var c=f.db.connections().open()) {
                GovernanceSql.update(c,"INSERT INTO tblShopQualification VALUES ('expired',?,'SPECIAL','SIM-OLD',?,'APPROVED',NULL,?)",shop,java.sql.Date.valueOf("2026-09-13"),f.clock.instant());
            }
            f.service.maintain();
            assertEquals(3,f.restricted);
            try(var c=f.db.connections().open()) {
                assertFalse(f.service.policy().mayBuy(c,"restore"));
                assertTrue(f.service.policy().mayBuy(c,"ordinary"));
                f.service.policy().manualOff(c,"manual");
                GovernanceSql.update(c,"UPDATE tblProduct SET productStatus='INACTIVE' WHERE productId='manual'");
            }
            f.write(f.admin,"ACTION",new Action("emergency","EMERGENCY","Issue",List.of(),null));
            String renewal=f.write(f.owner,"SUBMIT_QUALIFICATION",new Qualification("SPECIAL","SIM-NEW",LocalDate.of(2027,1,1))).id();
            f.write(f.admin,"REVIEW_QUALIFICATION",new Review(renewal,true,"Renewed"));
            assertEquals("ACTIVE",f.scalar("SELECT productStatus FROM tblProduct WHERE productId='restore'"));
            assertEquals("INACTIVE",f.scalar("SELECT productStatus FROM tblProduct WHERE productId='manual'"));
            assertEquals("INACTIVE",f.scalar("SELECT productStatus FROM tblProduct WHERE productId='emergency'"));
            try(var c=f.db.connections().open()) {
                assertTrue(f.service.policy().mayBuy(c,"restore"));
                assertFalse(f.service.policy().mayBuy(c,"emergency"));
            }
        }
    }
    @Test void renewalDuringSuspensionCannotReappearAfterReopening() throws Exception {
        try(var f=new GovernanceFixture()) {
            String shop=f.createShop();
            f.product(shop,"licensed-product","licensed");
            try(var c=f.db.connections().open()) {
                GovernanceSql.update(c,"INSERT INTO tblShopQualification VALUES ('old',?,'SPECIAL','OLD',?,'APPROVED',NULL,?)",shop,java.sql.Date.valueOf("2026-09-13"),f.clock.instant());
            }
            f.service.maintain();
            f.write(f.admin,"ACTION",new Action(shop,"SUSPEND","Needs correction",List.of(),null));
            String renewal=f.write(f.owner,"SUBMIT_QUALIFICATION",new Qualification("SPECIAL","NEW",LocalDate.of(2027,1,1))).id();
            f.write(f.admin,"REVIEW_QUALIFICATION",new Review(renewal,true,"Renewed"));
            assertEquals("INACTIVE",f.scalar("SELECT productStatus FROM tblProduct WHERE productId='licensed-product'"));
            String reopen=f.write(f.owner,"SUBMIT_CASE",new SubmitCase("REOPEN",shop,"Corrected","Complete remediation")).id();
            f.write(f.admin,"REVIEW_CASE",new Review(reopen,true,"Reopen accepted"));
            assertEquals("INACTIVE",f.scalar("SELECT productStatus FROM tblProduct WHERE productId='licensed-product'"));
        }
    }}
