package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryRequestException;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Prominent, client-safe feedback for failed library operations. */
final class LibraryFeedback {
    private LibraryFeedback() { }

    static void failure(Component owner, JLabel status, Throwable failure, String fallback) {
        Throwable cause = unwrap(failure);
        String message = fallback;
        String title = "操作失败";
        if (cause instanceof LibraryRequestException request) {
            if ("COMMON_CONCURRENT_MODIFICATION".equals(request.code())) {
                title = "数据冲突";
                message = "数据已被其他操作修改，请刷新后重试。";
            } else if ("AUTH_SESSION_EXPIRED".equals(request.code())) {
                title = "登录已失效";
                message = "登录会话已过期，请重新登录。";
            } else if ("AUTH_FORBIDDEN".equals(request.code())) {
                title = "权限不足";
                message = "当前账号没有执行此操作的权限。";
            } else if (request.getMessage() != null && !request.getMessage().isBlank()) {
                message = request.getMessage();
            }
        }
        status.setText(message);
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(owner, message, title, JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable result = failure;
        while ((result instanceof CompletionException || result instanceof ExecutionException)
                && result.getCause() != null) result = result.getCause();
        return result;
    }
}
