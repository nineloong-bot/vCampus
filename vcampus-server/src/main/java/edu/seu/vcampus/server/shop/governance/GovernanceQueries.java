package edu.seu.vcampus.server.shop.governance;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import static edu.seu.vcampus.server.shop.governance.GovernanceSql.*;

/** Privacy-safe projections, deliberately excluding all case actor identifiers. */
final class GovernanceQueries {
    Views applications(Connection c,String actor) throws SQLException {
        var result=new ArrayList<View>();
        String filter=actor==null?"":" WHERE a.applicantUserId=?";
        Object[] args=actor==null?new Object[0]:new Object[]{actor};
        for(var r:rows(c,"SELECT a.applicationId,a.applicationStatus,a.shopName,a.reviewReason,m.subjectName,m.licenseNumber FROM tblSellerApplication a LEFT JOIN tblShopGovApplication m ON a.applicationId=m.applicationId"+filter,args))
            result.add(new View(r.get(0),"APPLICATION",r.get(0),r.get(1),r.get(2),"",r.get(3),r.get(4),r.get(5),null));
        return new Views(result);
    }
    Views shops(Connection c,String actor) throws SQLException {
        var result=new ArrayList<View>();
        String filter=actor==null?"":" WHERE s.ownerUserId=?";
        Object[] args=actor==null?new Object[0]:new Object[]{actor};
        for(var r:rows(c,"SELECT s.shopId,s.shopStatus,s.shopName,s.description,s.suspensionReason,m.subjectName,m.licenseNumber FROM (tblShop s LEFT JOIN tblSellerApplication a ON s.ownerUserId=a.applicantUserId) LEFT JOIN tblShopGovApplication m ON a.applicationId=m.applicationId"+filter,args))
            result.add(new View(r.get(0),"SHOP",r.get(0),r.get(1),r.get(2),r.get(3),r.get(4),r.get(5),r.get(6),null));
        return new Views(result);
    }
    Views qualifications(Connection c,String shopId) throws SQLException {
        var result=new ArrayList<View>();
        String filter=shopId==null?"":" WHERE shopId=?";
        Object[] args=shopId==null?new Object[0]:new Object[]{shopId};
        for(var r:rows(c,"SELECT qualificationId,shopId,licenseType,licenseNumber,expiresOn,qualificationStatus,reviewReason FROM tblShopQualification"+filter,args))
            result.add(new View(r.get(0),"QUALIFICATION",r.get(1),r.get(5),r.get(2),"",r.get(6),"",r.get(3),date(r.get(4))));
        return new Views(result);
    }
    Views cases(Connection c,String actor,String shopId) throws SQLException {
        var result=new ArrayList<View>();
        String filter=actor!=null?" WHERE actorId=?":shopId!=null?" WHERE shopId=? AND caseKind NOT IN ('REPORT_PRODUCT','REPORT_SHOP')":"";
        Object[] args=actor!=null?new Object[]{actor}:shopId!=null?new Object[]{shopId}:new Object[0];
        for(var r:rows(c,"SELECT caseId,caseKind,objectId,caseStatus,reason,explanation,resultText FROM tblShopGovCase"+filter+" ORDER BY createdAt DESC",args))
            result.add(new View(r.get(0),r.get(1),r.get(2),r.get(3),r.get(4),r.get(5),r.get(6),"","",null));
        return new Views(result);
    }
    static LocalDate date(String value) { return LocalDate.parse(value.substring(0,10)); }
}
