package edu.seu.vcampus.server.shop.governance;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.sql.Date;
import static edu.seu.vcampus.server.shop.governance.GovernanceSql.*;

/** Simulated category permissions and independent product restrictions. */
public final class GovernancePolicy {
    private final Clock clock;
    /** Uses the runtime clock. */
    public GovernancePolicy() { this(Clock.systemDefaultZone()); }
    /** Uses the specified clock for deterministic expiry checks. */
    public GovernancePolicy(Clock clock) { this.clock=clock; }
    /** Returns ordinary, licensed, or prohibited for the simulated catalog. */
    public static String category(String value) {
        if (value != null && java.util.List.of("ordinary", "普通白名单商品", "图书", "文具", "家居", "服装", "数码", "日用品").contains(value)) return "ordinary";
        if ("licensed".equals(value)||"专项许可商品".equals(value)) return "licensed";
        return "prohibited";
    }
    /** Checks current store state and category authorization in the caller transaction. */
    public boolean mayPublish(Connection c,String shopId,String category) throws SQLException {
        if(!"ACTIVE".equals(scalar(c,"SELECT shopStatus FROM tblShop WHERE shopId=?",shopId))) return false;
        return switch(category(category)) {
            case "ordinary" -> true;
            case "licensed" -> validLicense(c,shopId);
            default -> false;
        };
    }
    /** Checks governance only; catalog separately checks ACTIVE, deletion and SKU stock. */
    public boolean mayBuy(Connection c,String productId) throws SQLException {
        var rows=rows(c,"SELECT shopId,category FROM tblProduct WHERE productId=?",productId);
        if(rows.isEmpty()) return false;
        return mayPublish(c,rows.getFirst().get(0),rows.getFirst().get(1))
                && !exists(c,"SELECT productId FROM tblShopProductRestriction WHERE productId=? AND emergencyBlocked=TRUE",productId)
                && (!"licensed".equals(category(rows.getFirst().get(1)))
                    || !exists(c,"SELECT productId FROM tblShopProductRestriction WHERE productId=? AND qualificationBlocked=TRUE",productId));
    }
    /** Records an explicit seller off action, preventing later automatic restoration. */
    public void manualOff(Connection c,String productId) throws SQLException {
        update(c,"UPDATE tblShopProductRestriction SET expiryRestoreEligible=FALSE WHERE productId=?",productId);
    }
    /** Projects independent restrictions for seller and administrator display. */
    public String effectiveStatus(Connection c,String productId,String baseStatus) throws SQLException {
        if(exists(c,"SELECT productId FROM tblShopProductRestriction WHERE productId=? AND emergencyBlocked=TRUE",productId))
            return "EMERGENCY_BLOCKED";
        if("licensed".equals(category(scalar(c,"SELECT category FROM tblProduct WHERE productId=?",productId)))
                && exists(c,"SELECT productId FROM tblShopProductRestriction WHERE productId=? AND qualificationBlocked=TRUE",productId))
            return "QUALIFICATION_EXPIRED";
        return baseStatus;
    }
    boolean validLicense(Connection c,String shopId) throws SQLException {
        return exists(c,"SELECT qualificationId FROM tblShopQualification WHERE shopId=? AND licenseType='SPECIAL' AND qualificationStatus='APPROVED' AND expiresOn>=?",shopId,Date.valueOf(java.time.LocalDate.now(clock)));
    }
    static void ensureRestriction(Connection c,String productId) throws SQLException {
        if(!exists(c,"SELECT productId FROM tblShopProductRestriction WHERE productId=?",productId))
            update(c,"INSERT INTO tblShopProductRestriction (productId,emergencyBlocked,qualificationBlocked,expiryRestoreEligible) VALUES (?,FALSE,FALSE,FALSE)",productId);
    }
}
