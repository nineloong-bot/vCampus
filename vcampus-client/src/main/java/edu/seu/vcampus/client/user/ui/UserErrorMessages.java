package edu.seu.vcampus.client.user.ui;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/** Converts stable user-module failures into safe actionable Chinese messages. */
final class UserErrorMessages {
    private static final Set<Class<?>> NETWORK_FAILURES = Set.of(
            ConnectException.class, SocketException.class,
            SocketTimeoutException.class, TimeoutException.class);

    private UserErrorMessages() { }

    static String login(Throwable failure) {
        if (hasCode(failure, "AUTH_INVALID_CREDENTIALS")) return "用户名或密码错误";
        if (hasCode(failure, "AUTH_ACCOUNT_PENDING")) return "账户正在审核，暂不能登录";
        if (hasCode(failure, "AUTH_ACCOUNT_DISABLED")) return "账户已停用，请联系管理员";
        if (hasCode(failure, "AUTH_ACCOUNT_LOCKED")) return "登录失败次数过多，请稍后再试";
        if (isNetworkFailure(failure)) return "无法连接服务器，请检查服务端是否启动";
        return "登录暂时不可用，请稍后再试";
    }

    static boolean isAccountLocked(Throwable failure) {
        return hasCode(failure, "AUTH_ACCOUNT_LOCKED");
    }

    static boolean isConcurrentModification(Throwable failure) {
        return hasCode(failure, "COMMON_CONCURRENT_MODIFICATION");
    }

    static String operation(Throwable failure, String fallback) {
        if (hasCode(failure, "AUTH_INVALID_CREDENTIALS")) return "当前密码不正确";
        if (hasCode(failure, "AUTH_PASSWORD_POLICY_VIOLATION")) {
            return "密码需为 8–64 位，并同时包含字母和数字";
        }
        if (hasCode(failure, "USER_LAST_ADMIN_PROTECTED")) return "不能修改最后一名在用管理员";
        if (hasCode(failure, "USER_ROLE_CONFLICT")) return "该账户角色不允许这样调整";
        if (hasCode(failure, "COMMON_CONCURRENT_MODIFICATION")) return "记录已被修改，请刷新后重试";
        if (isNetworkFailure(failure)) return "无法连接服务器，请稍后重试";
        return fallback;
    }

    private static boolean hasCode(Throwable failure, String code) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (code.equals(current.getMessage())) return true;
        }
        return false;
    }

    private static boolean isNetworkFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            for (Class<?> type : NETWORK_FAILURES) {
                if (type.isInstance(current)) return true;
            }
        }
        return false;
    }
}
