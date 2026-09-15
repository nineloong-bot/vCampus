package edu.seu.vcampus.client.shop.commerce;

/** Human-readable governance state and case names. */
final class GovernanceLabels {
    private GovernanceLabels() { }
    static String state(String state) {
        return switch(state) {
            case "PENDING" -> "待审核 / 待处理";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            case "EXPIRED","QUALIFICATION_EXPIRED" -> "资质已到期";
            case "EMERGENCY_BLOCKED" -> "商品紧急限制";
            case "ACTIVE" -> "正常";
            case "INACTIVE" -> "已下架";
            case "DRAFT" -> "草稿";
            case "SUSPENDED" -> "暂停营业";
            case "PROCESSED" -> "已处理";
            default -> state;
        };
    }
    static String kind(String kind) {
        return switch(kind) {
            case "SHOP" -> "店铺";
            case "APPLICATION" -> "开店申请";
            case "QUALIFICATION" -> "专项资质";
            case "REOPEN" -> "店铺恢复申请";
            case "REMEDIATION" -> "商品整改复核";
            case "REPORT_SHOP" -> "店铺举报";
            case "REPORT_PRODUCT" -> "商品举报";
            case "NOTICE" -> "处置通知";
            default -> kind;
        };
    }
}
