package edu.seu.vcampus.server.shop.governance;

import edu.seu.vcampus.common.shop.governance.GovernanceDtos.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import static edu.seu.vcampus.server.shop.governance.GovernanceSql.*;

final class GovernanceAudit {
    private GovernanceAudit() { }
    static void record(Connection c,String actor,String object,String action,String reason,Instant time,
            String before,String after,String linked) throws SQLException {
        update(c,"INSERT INTO tblShopGovAudit VALUES (?,?,?,?,?,?,?,?,?)",id(),actor,object,action,reason,time,before,after,linked);
    }
    static Audits list(Connection c,String objectId) throws SQLException {
        var result=new ArrayList<Audit>();
        String filter=objectId==null||objectId.isBlank()?"":" WHERE objectId=?";
        Object[] args=filter.isEmpty()?new Object[0]:new Object[]{objectId};
        for(var r:rows(c,"SELECT * FROM tblShopGovAudit"+filter+" ORDER BY occurredAt DESC",args))
            result.add(new Audit(r.get(0),r.get(1),r.get(2),r.get(3),r.get(4),r.get(5),r.get(6),r.get(7),r.get(8)));
        return new Audits(result);
    }
}
