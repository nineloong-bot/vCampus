package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.ResetStudentPasswordCommand;
import edu.seu.vcampus.common.user.ResetTeacherPasswordCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSummary;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Runs account mutations while keeping the management view lifecycle-safe. */
final class UserManagementActions {
    private final UserClientService users;
    private final JComponent owner;
    private final BooleanSupplier closed;
    private final Consumer<Boolean> busy;
    private final Consumer<String> reload;
    private final Consumer<String> state;

    UserManagementActions(UserClientService users, JComponent owner,
            BooleanSupplier closed, Consumer<Boolean> busy,
            Consumer<String> reload, Consumer<String> state) {
        this.users = users;
        this.owner = owner;
        this.closed = closed;
        this.busy = busy;
        this.reload = reload;
        this.state = state;
    }

    void changeStatus(UserSummary selected) {
        AccountStatus next = nextStatus(selected.accountStatus());
        if (!ordinary(selected) || next == null) {
            state.accept("当前状态不能在此变更");
            return;
        }
        busy.accept(true);
        CompletableFuture<?> response;
        try {
            response = users.changeStatus(new ChangeUserStatusCommand(selected.userId(), next,
                    "管理员账户管理操作", selected.rowVersion()));
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        complete(response, "状态修改失败，请稍后重试");
    }

    void confirmPasswordReset(UserSummary selected) {
        if (selected.role() == UserRole.STUDENT) {
            new StudentPasswordResetConfirmationDialog(
                    SwingUtilities.getWindowAncestor(owner), () -> resetStudent(selected))
                    .setVisible(true);
        } else if (selected.role() == UserRole.TEACHER) {
            new TeacherPasswordResetConfirmationDialog(
                    SwingUtilities.getWindowAncestor(owner), () -> resetTeacher(selected))
                    .setVisible(true);
        }
    }

    static boolean ordinary(UserSummary user) {
        return user.role() == UserRole.STUDENT || user.role() == UserRole.TEACHER;
    }

    static AccountStatus nextStatus(AccountStatus current) {
        return switch (current) {
            case DISABLED -> AccountStatus.ACTIVE;
            case ACTIVE -> AccountStatus.DISABLED;
            case PENDING, CANCELLED -> null;
        };
    }

    private void resetStudent(UserSummary selected) {
        busy.accept(true);
        CompletableFuture<?> response;
        try {
            response = users.resetStudentPassword(new ResetStudentPasswordCommand(
                    selected.userId(), selected.rowVersion()));
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        complete(response, "密码初始化失败，请稍后重试");
    }

    private void resetTeacher(UserSummary selected) {
        busy.accept(true);
        CompletableFuture<?> response;
        try {
            response = users.resetTeacherPassword(new ResetTeacherPasswordCommand(
                    selected.userId(), selected.rowVersion()));
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        complete(response, "密码初始化失败，请稍后重试");
    }

    private void complete(CompletableFuture<?> response, String fallback) {
        response.whenComplete((ignored, failure) -> onEdt(() -> {
            if (closed.getAsBoolean()) return;
            if (failure == null) reload.accept(null);
            else if (UserErrorMessages.isConcurrentModification(failure)) {
                reload.accept("账户信息已更新，请重新选择后重试");
            } else {
                busy.accept(false);
                state.accept(UserErrorMessages.operation(failure, fallback));
            }
        }));
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
