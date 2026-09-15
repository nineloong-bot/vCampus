package edu.seu.vcampus.server.shop.governance;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import static edu.seu.vcampus.server.shop.governance.GovernanceSql.*;

/** Buyer reports, seller remediation and approval-only reopening. */
final class GovernanceCases {
    private final Clock clock;
    GovernanceCases(Clock clock) { this.clock=clock; }
    String submit(Connection c,String actor,SubmitCase request) throws SQLException {
        String kind=request.kind();
        if(!List.of("REPORT_PRODUCT","REPORT_SHOP","REOPEN","REMEDIATION").contains(kind)) throw new IllegalArgumentException("Invalid case type");
        String reason=text(request.reason(),256);
        String explanation=text(request.description(),4000);
        String shop=List.of("REPORT_PRODUCT","REMEDIATION").contains(kind)?required(c,"SELECT shopId FROM tblProduct WHERE productId=?",request.objectId()):request.objectId();
        String owner=required(c,"SELECT ownerUserId FROM tblShop WHERE shopId=?",shop);
        if(!kind.startsWith("REPORT_")) {
            if(!actor.equals(owner)) throw new SecurityException("Shop owner required");
            if("REOPEN".equals(kind)&&!"SUSPENDED".equals(required(c,"SELECT shopStatus FROM tblShop WHERE shopId=?",shop)))
                throw new IllegalArgumentException("Shop is not suspended");
            if("REMEDIATION".equals(kind)&&!exists(c,"SELECT productId FROM tblShopProductRestriction WHERE productId=? AND emergencyBlocked=TRUE",request.objectId()))
                throw new IllegalArgumentException("Product is not emergency restricted");
            if(exists(c,"SELECT caseId FROM tblShopGovCase WHERE caseKind=? AND objectId=? AND caseStatus='PENDING'",kind,request.objectId()))
                throw new IllegalArgumentException("Review already pending");
        } else if(!List.of("涉嫌禁售商品","经营资质问题","商品信息虚假或误导","店铺违规经营","其他问题","PROHIBITED","QUALIFICATION","MISLEADING","SHOP_VIOLATION","OTHER").contains(reason))
            throw new IllegalArgumentException("Choose a report reason");
        String id=id();
        update(c,"INSERT INTO tblShopGovCase VALUES (?,?,?,?,?,?,?,'PENDING',NULL,?)",id,actor,kind,request.objectId(),shop,reason,explanation,clock.instant());
        GovernanceAudit.record(c,actor,request.objectId(),"SUBMIT_"+kind,explanation,clock.instant(),"","PENDING",id);
        return id;
    }
    String review(Connection c,String actor,Review review) throws SQLException {
        String reason=text(review.reason(),256);
        var list=rows(c,"SELECT caseKind,objectId,shopId,caseStatus FROM tblShopGovCase WHERE caseId=?",review.id());
        if(list.isEmpty()) throw new IllegalArgumentException("Case not found");
        var row=list.getFirst();
        if(!List.of("REOPEN","REMEDIATION").contains(row.get(0))||!"PENDING".equals(row.get(3))) throw new IllegalArgumentException("Pending recovery case required");
        if(review.approved()) {
            if("REOPEN".equals(row.get(0))) {
                if(update(c,"UPDATE tblShop SET shopStatus='ACTIVE',suspensionReason=NULL,suspendedByUserId=NULL,suspendedAt=NULL,updatedAt=?,rowVersion=rowVersion+1 WHERE shopId=? AND shopStatus='SUSPENDED'",clock.instant(),row.get(2))!=1)
                    throw new IllegalArgumentException("Shop is not suspended");
            } else {
                if(update(c,"UPDATE tblShopProductRestriction SET emergencyBlocked=FALSE,expiryRestoreEligible=FALSE WHERE productId=? AND emergencyBlocked=TRUE",row.get(1))!=1)
                    throw new IllegalArgumentException("Product is not restricted");
            }
        }
        String state=review.approved()?"APPROVED":"REJECTED";
        update(c,"UPDATE tblShopGovCase SET caseStatus=?,resultText=? WHERE caseId=?",state,reason,review.id());
        GovernanceAudit.record(c,actor,row.get(1),"REVIEW_"+row.get(0),reason,clock.instant(),"PENDING",state,review.id());
        return review.id();
    }
}
