package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.common.student.majortransfer.*;

/** Central Chinese dictionary for visible major-transfer lifecycle text. */
final class MajorTransferStatusText {
    private MajorTransferStatusText() { }

    static String status(MajorTransferStatus value) {
        if (value == null) return "未知状态";
        return switch (value) {
            case DRAFT -> "草稿";
            case SUBMITTED -> "待转出学院审核";
            case SOURCE_APPROVED -> "待转入学院审核";
            case QUALIFIED -> "待考核与成绩录入";
            case ASSESSED -> "待教务终审";
            case PENDING_EFFECTIVE -> "待生效";
            case EFFECTIVE -> "已生效";
            case REJECTED -> "已驳回";
            case CANCELLED -> "已取消";
            case EXECUTION_FAILED -> "执行失败";
        };
    }

    static String batchStatus(MajorTransferBatchStatus value) {
        if (value == null) return "未知状态";
        return switch (value) {
            case DRAFT -> "草稿";
            case OPEN -> "开放报名";
            case CLOSED -> "已关闭";
            case EFFECTIVE -> "已终审生效";
        };
    }

    static String decision(MajorTransferDecision value) {
        if (value == null) return "未知决定";
        return switch (value) {
            case APPROVE -> "已通过";
            case REJECT -> "已驳回";
        };
    }

    static String reviewStage(MajorTransferReviewStage value) {
        if (value == null) return "未知阶段";
        return switch (value) {
            case SOURCE_REVIEW -> "转出学院审核";
            case QUALIFICATION_REVIEW -> "转入学院资格审核";
            case ASSESSMENT -> "考核与成绩录入";
            case FINAL_APPROVAL -> "终审";
            case EXECUTION -> "转专业生效";
        };
    }
}
