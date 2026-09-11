package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.server.security.AccountDisabledException;
import edu.seu.vcampus.server.security.AccountLockedException;
import edu.seu.vcampus.server.security.AccountPendingException;
import edu.seu.vcampus.server.security.ForbiddenException;
import edu.seu.vcampus.server.security.InitialPasswordChangeRequiredException;
import edu.seu.vcampus.server.security.InvalidCredentialsException;
import edu.seu.vcampus.server.security.PasswordResetSessionRevokedException;
import edu.seu.vcampus.server.security.SessionExpiredException;

import java.util.ConcurrentModificationException;
import java.util.Set;

/** Maps user-module failures to stable audit-only result codes. */
final class UserAuditResultCodes {
    private static final Set<String> STABLE_CODES = Set.of(
            "AUTH_SESSION_EXPIRED", "AUTH_SESSION_REVOKED_PASSWORD_RESET",
            "AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED",
            "AUTH_FORBIDDEN", "AUTH_INVALID_CREDENTIALS",
            "AUTH_PASSWORD_POLICY_VIOLATION", "AUTH_ACCOUNT_PENDING",
            "AUTH_ACCOUNT_DISABLED", "AUTH_ACCOUNT_LOCKED",
            "USER_LOGIN_ID_EXISTS", "USER_LAST_ADMIN_PROTECTED",
            "USER_ROLE_CONFLICT", "USER_STATUS_CONFLICT", "USER_NOT_FOUND");

    private UserAuditResultCodes() { }

    static String from(RuntimeException error) {
        if (error instanceof ConcurrentModificationException) {
            return "COMMON_CONCURRENT_MODIFICATION";
        }
        if (error instanceof IllegalArgumentException) {
            return "COMMON_VALIDATION_FAILED";
        }
        if (error instanceof InvalidCredentialsException) return "AUTH_INVALID_CREDENTIALS";
        if (error instanceof AccountPendingException) return "AUTH_ACCOUNT_PENDING";
        if (error instanceof AccountDisabledException) return "AUTH_ACCOUNT_DISABLED";
        if (error instanceof AccountLockedException) return "AUTH_ACCOUNT_LOCKED";
        if (error instanceof PasswordResetSessionRevokedException) {
            return "AUTH_SESSION_REVOKED_PASSWORD_RESET";
        }
        if (error instanceof SessionExpiredException) return "AUTH_SESSION_EXPIRED";
        if (error instanceof InitialPasswordChangeRequiredException) {
            return "AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED";
        }
        if (error instanceof ForbiddenException) return "AUTH_FORBIDDEN";
        String message = error.getMessage();
        return message != null && STABLE_CODES.contains(message)
                ? message : "COMMON_INTERNAL_ERROR";
    }
}
