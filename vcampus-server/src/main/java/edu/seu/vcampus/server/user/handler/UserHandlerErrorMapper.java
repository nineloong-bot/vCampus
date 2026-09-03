package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.error.ErrorDetail;
import edu.seu.vcampus.common.protocol.ResponseBody;

import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class UserHandlerErrorMapper {
    private static final String INTERNAL_ERROR = "COMMON_INTERNAL_ERROR";
    private static final Set<String> STABLE_CODES = Set.of(
            "AUTH_SESSION_EXPIRED", "AUTH_SESSION_REVOKED_PASSWORD_RESET",
            "AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED",
            "AUTH_FORBIDDEN", "AUTH_INVALID_CREDENTIALS", "AUTH_PASSWORD_POLICY_VIOLATION",
            "AUTH_ACCOUNT_PENDING", "AUTH_ACCOUNT_DISABLED", "AUTH_ACCOUNT_LOCKED",
            "USER_LOGIN_ID_EXISTS", "USER_LAST_ADMIN_PROTECTED", "USER_ROLE_CONFLICT",
            "USER_STATUS_CONFLICT", "USER_NOT_FOUND");

    private UserHandlerErrorMapper() {
    }

    static <T extends Serializable> ResponseBody<T> failure(
            RuntimeException error, String safeMessage) {
        String code = code(error);
        ErrorDetail detail = INTERNAL_ERROR.equals(code)
                ? new ErrorDetail(code, safeMessage, Map.of(), traceId(), false)
                : null;
        return ResponseBody.failure(code, safeMessage, detail);
    }

    private static String code(RuntimeException error) {
        if (error instanceof ConcurrentModificationException) {
            return "COMMON_CONCURRENT_MODIFICATION";
        }
        String errorCode = error.getMessage();
        if (errorCode != null && STABLE_CODES.contains(errorCode)) {
            return errorCode;
        }
        if (error instanceof IllegalArgumentException) {
            return "COMMON_VALIDATION_FAILED";
        }
        return INTERNAL_ERROR;
    }

    private static String traceId() {
        return UUID.randomUUID().toString();
    }
}
