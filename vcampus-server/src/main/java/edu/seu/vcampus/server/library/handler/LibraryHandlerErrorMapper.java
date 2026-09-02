package edu.seu.vcampus.server.library.handler;

import edu.seu.vcampus.common.error.ErrorDetail;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.library.service.*;
import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Converts library/domain failures to stable, client-safe response codes. */
final class LibraryHandlerErrorMapper {
    private LibraryHandlerErrorMapper() { }

    static <T extends Serializable> ResponseBody<T> failure(RuntimeException error) {
        String code = code(error);
        String message = message(code);
        ErrorDetail detail = "COMMON_INTERNAL_ERROR".equals(code)
                ? new ErrorDetail(code, message, Map.of(), UUID.randomUUID().toString(), false) : null;
        return ResponseBody.failure(code, message, detail);
    }

    private static String code(RuntimeException error) {
        if (error instanceof CopyUnavailableException) return "LIBRARY_COPY_UNAVAILABLE";
        if (error instanceof CopyHasActiveLoanException) return "LIBRARY_COPY_HAS_ACTIVE_LOAN";
        if (error instanceof InactiveBookException) return "LIBRARY_BOOK_INACTIVE";
        if (error instanceof DuplicateIsbnException) return "LIBRARY_DUPLICATE_ISBN";
        if (error instanceof DuplicateBarcodeException) return "LIBRARY_DUPLICATE_BARCODE";
        if (error instanceof UserHasOverdueLoansException) return "LIBRARY_USER_OVERDUE";
        if (error instanceof LoanLimitReachedException) return "LIBRARY_LOAN_LIMIT_REACHED";
        if (error instanceof RenewalLimitReachedException) return "LIBRARY_RENEWAL_LIMIT_REACHED";
        if (error instanceof LoanOverdueException) return "LIBRARY_LOAN_OVERDUE";
        if (error instanceof LoanAlreadyReturnedException || error instanceof LoanNotActiveException)
            return "LIBRARY_LOAN_NOT_ACTIVE";
        if (error instanceof LoanOwnershipException || "AUTH_FORBIDDEN".equals(error.getMessage()))
            return "AUTH_FORBIDDEN";
        if ("AUTH_SESSION_EXPIRED".equals(error.getMessage())) return "AUTH_SESSION_EXPIRED";
        if (error instanceof ConcurrentModificationException) {
            String message = error.getMessage();
            if (message != null && message.startsWith("Book copy changed:")) return "LIBRARY_COPY_STALE";
            if (message != null && message.startsWith("Book changed:")) return "LIBRARY_BOOK_STALE";
            if (message != null && message.startsWith("Loan changed:")) return "LIBRARY_LOAN_STALE";
            if (message != null && message.startsWith("Library policy changed:")) return "LIBRARY_POLICY_STALE";
            return "COMMON_CONCURRENT_MODIFICATION";
        }
        if (error instanceof NoSuchElementException) return "LIBRARY_NOT_FOUND";
        if (error instanceof IllegalArgumentException || "COMMON_VALIDATION_FAILED".equals(error.getMessage()))
            return "COMMON_VALIDATION_FAILED";
        return "COMMON_INTERNAL_ERROR";
    }

    private static String message(String code) {
        return switch (code) {
            case "LIBRARY_COPY_UNAVAILABLE" -> "该馆藏副本当前不可借，请刷新后重试";
            case "LIBRARY_COPY_HAS_ACTIVE_LOAN" -> "该副本仍有有效借阅，请到借阅管理中办理归还或标记遗失";
            case "LIBRARY_BOOK_INACTIVE" -> "该书目已停用，不能新增副本、借阅或续借";
            case "LIBRARY_DUPLICATE_ISBN" -> "该 ISBN 已存在，请编辑现有书目";
            case "LIBRARY_DUPLICATE_BARCODE" -> "该馆藏条码已存在，请使用新的条码";
            case "LIBRARY_USER_OVERDUE" -> "存在逾期借阅，暂不能新增借阅";
            case "LIBRARY_LOAN_LIMIT_REACHED" -> "已达到当前角色的最大在借数量";
            case "LIBRARY_RENEWAL_LIMIT_REACHED" -> "该借阅已达到最大续借次数";
            case "LIBRARY_LOAN_OVERDUE" -> "逾期借阅不能续借";
            case "LIBRARY_LOAN_NOT_ACTIVE" -> "该借阅记录已不再有效，请刷新";
            case "AUTH_FORBIDDEN" -> "没有执行此操作的权限";
            case "AUTH_SESSION_EXPIRED" -> "会话已过期，请重新登录";
            case "COMMON_CONCURRENT_MODIFICATION" -> "记录已被修改，请刷新后重试";
            case "LIBRARY_BOOK_STALE" -> "书目信息已被其他管理员修改，请刷新书目后重试";
            case "LIBRARY_COPY_STALE" -> "副本状态已发生变化，请刷新副本后重试";
            case "LIBRARY_LOAN_STALE" -> "借阅状态已发生变化，请刷新借阅记录后重试";
            case "LIBRARY_POLICY_STALE" -> "借阅设置已被其他管理员修改，请刷新设置后重试";
            case "LIBRARY_NOT_FOUND" -> "未找到指定的图书馆记录";
            case "COMMON_VALIDATION_FAILED" -> "请求内容不完整或格式不正确";
            default -> "图书馆请求未能完成";
        };
    }
}
