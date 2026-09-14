package edu.seu.vcampus.server.shop.governance;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import static edu.seu.vcampus.server.shop.governance.GovernanceSql.*;

/** Persists applications using the original application and shop identities. */
final class GovernanceApplications {
    String apply(Connection c,String actor,Apply request,Instant now) throws SQLException {
        String name=text(request.shopName(),128);
        String subject=text(request.subjectName(),128);
        String license=text(request.licenseNumber(),128);
        if(!request.rulesAccepted()) throw new IllegalArgumentException("Accept platform rules");
        if(exists(c,"SELECT shopId FROM tblShop WHERE ownerUserId=?",actor))
            throw new IllegalArgumentException("Shop already exists");
        String id=scalar(c,"SELECT applicationId FROM tblSellerApplication WHERE applicantUserId=?",actor);
        if(id==null) {
            id=id();
            update(c,"INSERT INTO tblSellerApplication (applicationId,applicantUserId,shopName,description,category,contact,applicationStatement,applicationStatus,submittedAt,rowVersion) VALUES (?,?,?,'','ordinary','','Platform rules accepted','PENDING',?,0)",id,actor,name,now);
        } else {
            String state=required(c,"SELECT applicationStatus FROM tblSellerApplication WHERE applicationId=?",id);
            if(!"REJECTED".equals(state)&&!"DRAFT".equals(state)) throw new IllegalArgumentException("Application is not editable");
            update(c,"UPDATE tblSellerApplication SET shopName=?,applicationStatus='PENDING',reviewReason=NULL,reviewerUserId=NULL,reviewedAt=NULL,submittedAt=?,rowVersion=rowVersion+1 WHERE applicationId=?",name,now,id);
        }
        update(c,"DELETE FROM tblShopGovApplication WHERE applicationId=?",id);
        update(c,"INSERT INTO tblShopGovApplication VALUES (?,?,?)",id,subject,license);
        return id;
    }
    String review(Connection c,String actor,Review review,Instant now) throws SQLException {
        String reason=text(review.reason(),256);
        var row=rows(c,"SELECT applicantUserId,shopName,applicationStatus FROM tblSellerApplication WHERE applicationId=?",review.id());
        if(row.isEmpty()||!"PENDING".equals(row.getFirst().get(2))) throw new IllegalArgumentException("Pending application required");
        String state=review.approved()?"APPROVED":"REJECTED";
        if(review.approved()) {
            var r=row.getFirst();
            if(exists(c,"SELECT shopId FROM tblShop WHERE ownerUserId=?",r.get(0))) throw new IllegalArgumentException("Shop already exists");
            if(!exists(c,"SELECT applicationId FROM tblShopGovApplication WHERE applicationId=?",review.id()))
                throw new IllegalArgumentException("Text subject and license information required");
            update(c,"INSERT INTO tblShop (shopId,ownerUserId,shopName,normalizedShopName,description,category,contact,shopStatus,createdAt,updatedAt,rowVersion) VALUES (?,?,?,?,'','ordinary','','ACTIVE',?,?,0)",id(),r.get(0),r.get(1),r.get(1).strip().toLowerCase(java.util.Locale.ROOT),now,now);
        }
        update(c,"UPDATE tblSellerApplication SET applicationStatus=?,reviewReason=?,reviewerUserId=?,reviewedAt=?,rowVersion=rowVersion+1 WHERE applicationId=?",state,reason,actor,now,review.id());
        GovernanceAudit.record(c,actor,review.id(),"REVIEW_APPLICATION",reason,now,"PENDING",state,review.id());
        return review.id();
    }
}
