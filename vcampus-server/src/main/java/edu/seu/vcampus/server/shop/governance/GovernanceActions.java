package edu.seu.vcampus.server.shop.governance;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import static edu.seu.vcampus.server.shop.governance.GovernanceSql.*;

/** Applies moderation effects and resolves linked reports atomically. */
final class GovernanceActions {
    private final GovernanceEffects effects;
    private final Clock clock;
    GovernanceActions(GovernanceEffects effects,Clock clock) { this.effects=effects; this.clock=clock; }
    String apply(Connection c,String actor,Action a) throws SQLException {
        String reason=text(a.reason(),256);
        String action=text(a.action(),32);
        if(!List.of("WARN","SUSPEND","EMERGENCY","NO_VIOLATION").contains(action)) throw new IllegalArgumentException("Invalid action");
        String shop="EMERGENCY".equals(action)?required(c,"SELECT shopId FROM tblProduct WHERE productId=?",a.objectId()):a.objectId();
        String before=required(c,"SELECT shopStatus FROM tblShop WHERE shopId=?",shop);
        validateReport(c,a,shop);
        if(a.productIds().size()>100) throw new IllegalArgumentException("Too many associated products");
        for(String product:a.productIds()) {
            if(!shop.equals(required(c,"SELECT shopId FROM tblProduct WHERE productId=?",product)))
                throw new IllegalArgumentException("Product belongs to a different shop");
        }
        String after=before;
        if("SUSPEND".equals(action)) {
            if(!"ACTIVE".equals(before)) throw new IllegalArgumentException("Shop is already suspended");
            after="SUSPENDED";
            update(c,"UPDATE tblShop SET shopStatus='SUSPENDED',suspensionReason=?,suspendedByUserId=?,suspendedAt=?,updatedAt=?,rowVersion=rowVersion+1 WHERE shopId=?",reason,actor,clock.instant(),clock.instant(),shop);
            for(var p:rows(c,"SELECT productId FROM tblProduct WHERE shopId=?",shop))
                update(c,"UPDATE tblShopProductRestriction SET expiryRestoreEligible=FALSE WHERE productId=?",p.get(0));
            effects.shopSuspended(c,shop);
        } else if("EMERGENCY".equals(action)) {
            before=required(c,"SELECT productStatus FROM tblProduct WHERE productId=?",a.objectId());
            GovernancePolicy.ensureRestriction(c,a.objectId());
            if(exists(c,"SELECT productId FROM tblShopProductRestriction WHERE productId=? AND emergencyBlocked=TRUE",a.objectId()))
                throw new IllegalArgumentException("Product already restricted");
            update(c,"UPDATE tblShopProductRestriction SET emergencyBlocked=TRUE,expiryRestoreEligible=FALSE WHERE productId=?",a.objectId());
            update(c,"UPDATE tblProduct SET productStatus='INACTIVE',updatedAt=?,rowVersion=rowVersion+1 WHERE productId=?",clock.instant(),a.objectId());
            effects.productRestricted(c,a.objectId());
            after="EMERGENCY_BLOCKED";
        }
        GovernanceAudit.record(c,actor,a.objectId(),action,reason,clock.instant(),before,after,a.reportId());
        for(String product:a.productIds())
            GovernanceAudit.record(c,actor,product,"WARNING_PRODUCT",reason,clock.instant(),"UNCHANGED","UNCHANGED",a.reportId());
        String notification=id();
        update(c,"INSERT INTO tblShopGovCase VALUES (?,?,'NOTICE',?,?,?,?, 'PROCESSED',?,?)",notification,actor,a.objectId(),shop,action,reason,reason+(a.productIds().isEmpty()?"":"; products: "+String.join(",",a.productIds())),clock.instant());
        if(a.reportId()!=null&&!a.reportId().isBlank())
            update(c,"UPDATE tblShopGovCase SET caseStatus='PROCESSED',resultText=? WHERE caseId=?",action+": "+reason,a.reportId());
        return notification;
    }
    private void validateReport(Connection c,Action a,String shop) throws SQLException {
        if(a.reportId()==null||a.reportId().isBlank()) return;
        var rows=rows(c,"SELECT shopId,caseStatus,caseKind,objectId FROM tblShopGovCase WHERE caseId=?",a.reportId());
        if(rows.isEmpty()) throw new IllegalArgumentException("Report not found");
        var r=rows.getFirst();
        if(!shop.equals(r.get(0))||!"PENDING".equals(r.get(1))||!r.get(2).startsWith("REPORT_"))
            throw new IllegalArgumentException("Pending matching report required");
        if("EMERGENCY".equals(a.action())&&"REPORT_PRODUCT".equals(r.get(2))&&!a.objectId().equals(r.get(3)))
            throw new IllegalArgumentException("Reported product does not match");
    }
}
