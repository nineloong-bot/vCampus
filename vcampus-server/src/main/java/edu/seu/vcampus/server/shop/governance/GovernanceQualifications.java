package edu.seu.vcampus.server.shop.governance;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import static edu.seu.vcampus.server.shop.governance.GovernanceSql.*;

/** Qualification review and expiration run in the caller's business transaction. */
final class GovernanceQualifications {
    private final Clock clock;
    private final GovernancePolicy policy;
    private final GovernanceEffects effects;
    GovernanceQualifications(Clock clock,GovernancePolicy policy,GovernanceEffects effects) {
        this.clock=clock;
        this.policy=policy;
        this.effects=effects;
    }
    String submit(Connection c,String shop,Qualification q) throws SQLException {
        String type=text(q.type(),64);
        if(!"SPECIAL".equals(type)||q.expiresOn()==null||q.expiresOn().isBefore(LocalDate.now(clock)))
            throw new IllegalArgumentException("Valid simulated SPECIAL qualification required");
        if(exists(c,"SELECT qualificationId FROM tblShopQualification WHERE shopId=? AND licenseType=? AND qualificationStatus='PENDING'",shop,type))
            throw new IllegalArgumentException("Qualification review already pending");
        String id=id();
        update(c,"INSERT INTO tblShopQualification VALUES (?,?,?,?,?,'PENDING',NULL,?)",id,shop,type,text(q.number(),128),Date.valueOf(q.expiresOn()),clock.instant());
        return id;
    }
    String review(Connection c,String actor,Review review) throws SQLException {
        String reason=text(review.reason(),256);
        var list=rows(c,"SELECT shopId,qualificationStatus,expiresOn FROM tblShopQualification WHERE qualificationId=?",review.id());
        if(list.isEmpty()||!"PENDING".equals(list.getFirst().get(1))) throw new IllegalArgumentException("Pending qualification required");
        var row=list.getFirst();
        if(review.approved()&&GovernanceQueries.date(row.get(2)).isBefore(LocalDate.now(clock))) throw new IllegalArgumentException("Qualification expired");
        expire(c);
        String state=review.approved()?"APPROVED":"REJECTED";
        update(c,"UPDATE tblShopQualification SET qualificationStatus=?,reviewReason=? WHERE qualificationId=?",state,reason,review.id());
        GovernanceAudit.record(c,actor,review.id(),"REVIEW_QUALIFICATION",reason,clock.instant(),"PENDING",state,review.id());
        if(review.approved()) restore(c,actor,row.get(0),review.id());
        return review.id();
    }
    void expire(Connection c) throws SQLException {
        clearObsoleteBlocks(c);
        var expired=rows(c,"SELECT qualificationId,shopId FROM tblShopQualification WHERE qualificationStatus='APPROVED' AND expiresOn<?",Date.valueOf(LocalDate.now(clock)));
        for(var q:expired) {
            update(c,"UPDATE tblShopQualification SET qualificationStatus='EXPIRED' WHERE qualificationId=?",q.get(0));
            GovernanceAudit.record(c,"SYSTEM",q.get(0),"QUALIFICATION_EXPIRED","Qualification validity ended",clock.instant(),"APPROVED","EXPIRED",q.get(0));
            if(policy.validLicense(c,q.get(1))) continue;
            for(var p:rows(c,"SELECT productId,productStatus FROM tblProduct WHERE shopId=? AND (category='licensed' OR category='专项许可商品')",q.get(1))) {
                GovernancePolicy.ensureRestriction(c,p.get(0));
                if(exists(c,"SELECT productId FROM tblShopProductRestriction WHERE productId=? AND qualificationBlocked=TRUE",p.get(0))) continue;
                boolean eligible="ACTIVE".equals(p.get(1));
                update(c,"UPDATE tblShopProductRestriction SET qualificationBlocked=TRUE,expiryRestoreEligible=? WHERE productId=?",eligible,p.get(0));
                effects.productRestricted(c,p.get(0));
                GovernanceAudit.record(c,"SYSTEM",p.get(0),"QUALIFICATION_BLOCK","Qualification expired",clock.instant(),p.get(1),"QUALIFICATION_BLOCKED",q.get(0));
            }
        }
    }
    private void clearObsoleteBlocks(Connection c) throws SQLException {
        var restrictions=rows(c,"SELECT p.productId,p.category,p.productStatus FROM tblProduct p INNER JOIN tblShopProductRestriction r ON p.productId=r.productId WHERE r.qualificationBlocked=TRUE OR r.expiryRestoreEligible=TRUE");
        for(var product:restrictions) {
            if("licensed".equals(GovernancePolicy.category(product.get(1)))) continue;
            update(c,"UPDATE tblShopProductRestriction SET qualificationBlocked=FALSE,expiryRestoreEligible=FALSE WHERE productId=?",product.get(0));
            GovernanceAudit.record(c,"SYSTEM",product.get(0),"QUALIFICATION_DEPENDENCY_REMOVED",
                    "Current category no longer requires this qualification",clock.instant(),
                    "QUALIFICATION_BLOCKED",policy.effectiveStatus(c,product.get(0),product.get(2)),null);
        }
    }
    private void restore(Connection c,String actor,String shop,String qualificationId) throws SQLException {
        for(var p:rows(c,"SELECT r.productId,r.expiryRestoreEligible,r.emergencyBlocked FROM tblShopProductRestriction r INNER JOIN tblProduct p ON r.productId=p.productId WHERE p.shopId=? AND r.qualificationBlocked=TRUE",shop)) {
            update(c,"UPDATE tblShopProductRestriction SET qualificationBlocked=FALSE WHERE productId=?",p.get(0));
            boolean eligible=Boolean.parseBoolean(p.get(1))&&!Boolean.parseBoolean(p.get(2));
            boolean restored=eligible&&policy.mayBuy(c,p.get(0))&&effects.mayRestore(c,p.get(0));
            if(!restored) update(c,"UPDATE tblProduct SET productStatus='INACTIVE',rowVersion=rowVersion+1,updatedAt=? WHERE productId=? AND productStatus='ACTIVE'",clock.instant(),p.get(0));
            update(c,"UPDATE tblShopProductRestriction SET expiryRestoreEligible=FALSE WHERE productId=?",p.get(0));
            GovernanceAudit.record(c,actor,p.get(0),"QUALIFICATION_RENEWED","Qualification approved",clock.instant(),"QUALIFICATION_BLOCKED",restored?"ACTIVE":"INACTIVE",qualificationId);
        }
    }
}
