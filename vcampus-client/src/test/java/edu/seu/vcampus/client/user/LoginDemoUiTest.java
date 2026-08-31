package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.service.UserClientException;
import edu.seu.vcampus.client.user.ui.InitialPasswordChangeDialog;
import edu.seu.vcampus.client.user.ui.LoginFrame;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LoginDemoUiTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @AfterEach
    void disposeWindowsOnEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Frame.getFrames())
                .forEach(Frame::dispose));
    }

    @Test
    void userClientSendsLoginCommandAndStoresReturnedSessionToken() {
        ClientConnection connection = mock(ClientConnection.class);
        LoginResult expected = loginResult();
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(expected)))
                .when(connection).send(eq("USER_LOGIN"), any(LoginCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(
                connection, "demo-client", TIMEOUT);
        char[] submitted = "DemoPassword7".toCharArray();

        LoginResult actual = service.login("demo_admin", submitted).join();

        ArgumentCaptor<LoginCommand> command = ArgumentCaptor.forClass(LoginCommand.class);
        verify(connection).send(eq("USER_LOGIN"), command.capture(), eq(TIMEOUT));
        verify(connection).setSessionToken("demo-session-token");
        assertThat(command.getValue().loginId()).isEqualTo("demo_admin");
        assertThat(command.getValue().clientInstanceId()).isEqualTo("demo-client");
        assertThat(submitted).containsOnly('\0');
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void userCommandsReturnFromEdtBeforeABlockingConnectionSendCompletes() throws Exception {
        assertSendRunsAfterEdtReturns("USER_LOGIN", loginResult(),
                service -> service.login("demo_admin", "DemoPassword7".toCharArray()));
        assertSendRunsAfterEdtReturns("USER_CHANGE_PASSWORD", EmptyResponse.INSTANCE,
                service -> service.changePassword("InitialPassword7".toCharArray(),
                        "ReplacementPassword8".toCharArray()));
        assertSendRunsAfterEdtReturns("USER_LOGOUT", EmptyResponse.INSTANCE,
                UserClientService::logout);
    }

    @Test
    void passwordChangeSendsCommandClearsPasswordsAndClearsTokenAfterServerSuccess() {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(EmptyResponse.INSTANCE)))
                .when(connection).send(eq("USER_CHANGE_PASSWORD"),
                        any(ChangePasswordCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        char[] oldPassword = "InitialPassword7".toCharArray();
        char[] newPassword = "ReplacementPassword8".toCharArray();

        service.changePassword(oldPassword, newPassword).join();

        verify(connection).send(eq("USER_CHANGE_PASSWORD"),
                any(ChangePasswordCommand.class), eq(TIMEOUT));
        verify(connection).setSessionToken(null);
        assertThat(oldPassword).containsOnly('\0');
        assertThat(newPassword).containsOnly('\0');
    }

    @Test
    void rejectedPasswordChangeKeepsTokenForRetryAndStillClearsPasswords() {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.failure(
                "AUTH_PASSWORD_REJECTED", "密码不符合要求", null)))
                .when(connection).send(eq("USER_CHANGE_PASSWORD"),
                        any(ChangePasswordCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        char[] oldPassword = "InitialPassword7".toCharArray();
        char[] newPassword = "ReplacementPassword8".toCharArray();

        assertThatThrownBy(() -> service.changePassword(oldPassword, newPassword).join())
                .hasCauseInstanceOf(UserClientException.class);

        verify(connection, never()).setSessionToken(null);
        assertThat(oldPassword).containsOnly('\0');
        assertThat(newPassword).containsOnly('\0');
    }

    @Test
    void logoutClearsTokenOnlyAfterServerSuccess() {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(EmptyResponse.INSTANCE)))
                .when(connection).send(eq("USER_LOGOUT"), eq(EmptyResponse.INSTANCE), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);

        service.logout().join();

        verify(connection).send(eq("USER_LOGOUT"), eq(EmptyResponse.INSTANCE), eq(TIMEOUT));
        verify(connection).setSessionToken(null);
    }

    @Test
    void rejectedLogoutKeepsTokenForARestrictedUserRetry() {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.failure(
                "AUTH_SESSION_EXPIRED", "会话仍可重试", null)))
                .when(connection).send(eq("USER_LOGOUT"), eq(EmptyResponse.INSTANCE), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);

        assertThatThrownBy(() -> service.logout().join())
                .hasCauseInstanceOf(UserClientException.class);

        verify(connection, never()).setSessionToken(null);
    }

    @Test
    void userClientPreservesStableAuthenticationFailureCode() {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.failure(
                "AUTH_ACCOUNT_DISABLED", "账户已停用", null)))
                .when(connection).send(eq("USER_LOGOUT"), eq(EmptyResponse.INSTANCE), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);

        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> service.logout().join());
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        assertThat(failure).isInstanceOfSatisfying(UserClientException.class,
                error -> assertThat(error.code()).isEqualTo("AUTH_ACCOUNT_DISABLED"));
    }

    @Test
    void terminalPasswordChangeFailureClearsTokenDisposesAndCompletesExactlyOnceOnEdt() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.failure(
                "AUTH_SESSION_EXPIRED", "expired", null)))
                .when(connection).send(eq("USER_CHANGE_PASSWORD"),
                        any(ChangePasswordCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        AtomicInteger completions = new AtomicInteger();
        AtomicBoolean completedOnEdt = new AtomicBoolean();
        InitialPasswordChangeDialog[] dialog = new InitialPasswordChangeDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            dialog[0] = new InitialPasswordChangeDialog(service, () -> {
                completions.incrementAndGet();
                completedOnEdt.set(SwingUtilities.isEventDispatchThread());
            });
            dialog[0].setVisible(true);
            component(dialog[0], "password-change.old", JPasswordField.class).setText("InitialPassword7");
            component(dialog[0], "password-change.new", JPasswordField.class).setText("ReplacementPassword8");
            component(dialog[0], "password-change.confirm", JPasswordField.class).setText("ReplacementPassword8");
            component(dialog[0], "password-change.submit", AbstractButton.class).doClick();
        });

        awaitEdt(() -> completions.get() == 1);

        verify(connection).setSessionToken(null);
        assertThat(dialog[0].isDisplayable()).isFalse();
        assertThat(completions).hasValue(1);
        assertThat(completedOnEdt).isTrue();
    }

    @Test
    void terminalLogoutAndRepeatedWindowCloseCompleteOnlyOnce() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.failure(
                "AUTH_ACCOUNT_DISABLED", "disabled", null)))
                .when(connection).send(eq("USER_LOGOUT"), eq(EmptyResponse.INSTANCE), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        AtomicInteger completions = new AtomicInteger();
        InitialPasswordChangeDialog[] dialog = new InitialPasswordChangeDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            dialog[0] = new InitialPasswordChangeDialog(service, completions::incrementAndGet);
            dialog[0].setVisible(true);
            dialog[0].dispatchEvent(new WindowEvent(dialog[0], WindowEvent.WINDOW_CLOSING));
            dialog[0].dispatchEvent(new WindowEvent(dialog[0], WindowEvent.WINDOW_CLOSING));
        });

        awaitEdt(() -> completions.get() == 1);

        verify(connection).setSessionToken(null);
        assertThat(dialog[0].isDisplayable()).isFalse();
        assertThat(completions).hasValue(1);
    }

    @Test
    void restrictedLoginOpensPasswordChangeFlowWithoutCreatingMainFrame() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(restrictedLoginResult())))
                .when(connection).send(eq("USER_LOGIN"), any(LoginCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        AtomicBoolean mainCreated = new AtomicBoolean();
        AtomicBoolean restrictedFlowOpened = new AtomicBoolean();
        LoginFrame[] login = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            login[0] = new LoginFrame(service,
                    result -> mainCreated.set(true),
                    result -> restrictedFlowOpened.set(true));
            login[0].setVisible(true);
        });

        submit(login[0], "DEMO_ADMIN", "InitialPassword7");
        awaitEdt(restrictedFlowOpened::get);

        assertThat(login[0].isDisplayable()).isFalse();
        assertThat(mainCreated).isFalse();
        assertThat(restrictedFlowOpened).isTrue();
        assertThat(showingMainFrames()).isEmpty();
    }

    @Test
    void twoArgumentLoginFrameNeverRoutesARestrictedResultToNormalSuccess() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(restrictedLoginResult())))
                .when(connection).send(eq("USER_LOGIN"), any(LoginCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        AtomicBoolean normalSuccess = new AtomicBoolean();
        LoginFrame[] login = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            login[0] = new LoginFrame(service, result -> normalSuccess.set(true));
            login[0].setVisible(true);
        });

        submit(login[0], "DEMO_ADMIN", "InitialPassword7");
        awaitEdt(() -> component(login[0], "login.submit", AbstractButton.class).isEnabled());

        assertThat(normalSuccess).isFalse();
        assertThat(login[0].isShowing()).isTrue();
        assertThat(component(login[0], "login.submit", AbstractButton.class).isEnabled()).isTrue();
        assertThat(component(login[0], "login.error", JLabel.class).getText())
                .contains("请先修改初始密码");
        assertThat(showingMainFrames()).isEmpty();
    }

    @Test
    void initialPasswordChangeKeepsEdtFreeThenClosesAndRequestsNewLoginAfterSuccess()
            throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        CompletableFuture<ResponseBody<EmptyResponse>> pending = new CompletableFuture<>();
        doReturn(pending).when(connection).send(eq("USER_CHANGE_PASSWORD"),
                any(ChangePasswordCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean completedOnEdt = new AtomicBoolean();
        InitialPasswordChangeDialog[] dialog = new InitialPasswordChangeDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            dialog[0] = new InitialPasswordChangeDialog(service, () -> {
                completed.set(true);
                completedOnEdt.set(SwingUtilities.isEventDispatchThread());
            });
            dialog[0].setVisible(true);
            component(dialog[0], "password-change.old", JPasswordField.class)
                    .setText("InitialPassword7");
            component(dialog[0], "password-change.new", JPasswordField.class)
                    .setText("ReplacementPassword8");
            component(dialog[0], "password-change.confirm", JPasswordField.class)
                    .setText("ReplacementPassword8");
            component(dialog[0], "password-change.submit", AbstractButton.class).doClick();
        });

        assertThat(component(dialog[0], "password-change.submit", AbstractButton.class).isEnabled())
                .isFalse();
        assertThat(component(dialog[0], "password-change.old", JPasswordField.class).getPassword())
                .isEmpty();
        assertThat(component(dialog[0], "password-change.new", JPasswordField.class).getPassword())
                .isEmpty();
        assertThat(component(dialog[0], "password-change.confirm", JPasswordField.class).getPassword())
                .isEmpty();
        verify(connection, never()).setSessionToken(null);

        pending.complete(ResponseBody.success(EmptyResponse.INSTANCE));
        awaitEdt(completed::get);

        verify(connection).send(eq("USER_CHANGE_PASSWORD"),
                any(ChangePasswordCommand.class), eq(TIMEOUT));
        verify(connection).setSessionToken(null);
        assertThat(dialog[0].isDisplayable()).isFalse();
        assertThat(completed).isTrue();
        assertThat(completedOnEdt).isTrue();
        assertThat(showingMainFrames()).isEmpty();
    }

    @Test
    void dialogLogoutWaitsForServerSuccessThenClearsTokenAndReopensLogin() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        CompletableFuture<ResponseBody<EmptyResponse>> pending = new CompletableFuture<>();
        doReturn(pending).when(connection).send(eq("USER_LOGOUT"),
                eq(EmptyResponse.INSTANCE), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        AtomicBoolean completed = new AtomicBoolean();
        InitialPasswordChangeDialog[] dialog = new InitialPasswordChangeDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            dialog[0] = new InitialPasswordChangeDialog(service, () -> completed.set(true));
            dialog[0].setVisible(true);
            component(dialog[0], "password-change.old", JPasswordField.class)
                    .setText("InitialPassword7");
            component(dialog[0], "password-change.new", JPasswordField.class)
                    .setText("ReplacementPassword8");
            component(dialog[0], "password-change.confirm", JPasswordField.class)
                    .setText("ReplacementPassword8");
            component(dialog[0], "password-change.logout", AbstractButton.class).doClick();
        });

        assertThat(component(dialog[0], "password-change.submit", AbstractButton.class).isEnabled())
                .isFalse();
        assertThat(component(dialog[0], "password-change.logout", AbstractButton.class).isEnabled())
                .isFalse();
        assertPasswordFieldsEmpty(dialog[0]);
        verify(connection, never()).setSessionToken(null);

        pending.complete(ResponseBody.success(EmptyResponse.INSTANCE));
        awaitEdt(completed::get);

        verify(connection).setSessionToken(null);
        assertThat(dialog[0].isDisplayable()).isFalse();
        assertThat(completed).isTrue();
    }

    @Test
    void rejectedDialogLogoutKeepsDialogUsableTokenAndSensitiveFieldsCleared() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.failure(
                "AUTH_LOGOUT_REJECTED", "服务器详情不能显示", null)))
                .when(connection).send(eq("USER_LOGOUT"), eq(EmptyResponse.INSTANCE), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        AtomicBoolean completed = new AtomicBoolean();
        InitialPasswordChangeDialog[] dialog = new InitialPasswordChangeDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            dialog[0] = new InitialPasswordChangeDialog(service, () -> completed.set(true));
            dialog[0].setVisible(true);
            component(dialog[0], "password-change.old", JPasswordField.class)
                    .setText("InitialPassword7");
            component(dialog[0], "password-change.new", JPasswordField.class)
                    .setText("ReplacementPassword8");
            component(dialog[0], "password-change.confirm", JPasswordField.class)
                    .setText("ReplacementPassword8");
            component(dialog[0], "password-change.logout", AbstractButton.class).doClick();
        });
        awaitEdt(() -> component(dialog[0], "password-change.logout", AbstractButton.class)
                .isEnabled());

        verify(connection, never()).setSessionToken(null);
        assertThat(dialog[0].isShowing()).isTrue();
        assertThat(completed).isFalse();
        assertThat(component(dialog[0], "password-change.logout", AbstractButton.class).isEnabled())
                .isTrue();
        assertPasswordFieldsEmpty(dialog[0]);
        assertThat(component(dialog[0], "password-change.error", JLabel.class).getText())
                .contains("退出失败")
                .doesNotContain("AUTH_LOGOUT_REJECTED", "服务器详情不能显示");
    }

    @Test
    void windowCloseUsesLogoutBeforeClosingAndRequestingNewLogin() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(EmptyResponse.INSTANCE)))
                .when(connection).send(eq("USER_LOGOUT"), eq(EmptyResponse.INSTANCE), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        AtomicBoolean completed = new AtomicBoolean();
        InitialPasswordChangeDialog[] dialog = new InitialPasswordChangeDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            dialog[0] = new InitialPasswordChangeDialog(service, () -> completed.set(true));
            dialog[0].setVisible(true);
            component(dialog[0], "password-change.old", JPasswordField.class)
                    .setText("InitialPassword7");
            component(dialog[0], "password-change.new", JPasswordField.class)
                    .setText("ReplacementPassword8");
            component(dialog[0], "password-change.confirm", JPasswordField.class)
                    .setText("ReplacementPassword8");
            dialog[0].dispatchEvent(new WindowEvent(dialog[0], WindowEvent.WINDOW_CLOSING));
        });
        awaitEdt(completed::get);

        verify(connection).send(eq("USER_LOGOUT"), eq(EmptyResponse.INSTANCE), eq(TIMEOUT));
        verify(connection).setSessionToken(null);
        assertPasswordFieldsEmpty(dialog[0]);
        assertThat(dialog[0].isDisplayable()).isFalse();
        assertThat(completed).isTrue();
    }

    @Test
    void initialPasswordChangeRejectsMismatchedPasswordsWithoutSendingSecrets() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        InitialPasswordChangeDialog[] dialog = new InitialPasswordChangeDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            dialog[0] = new InitialPasswordChangeDialog(service, () -> {
            });
            dialog[0].setVisible(true);
            component(dialog[0], "password-change.old", JPasswordField.class)
                    .setText("InitialPassword7");
            component(dialog[0], "password-change.new", JPasswordField.class)
                    .setText("ReplacementPassword8");
            component(dialog[0], "password-change.confirm", JPasswordField.class)
                    .setText("DifferentPassword9");
            component(dialog[0], "password-change.submit", AbstractButton.class).doClick();
        });

        verify(connection, never()).send(anyString(), any(), any());
        String error = component(dialog[0], "password-change.error", JLabel.class).getText();
        assertThat(error).contains("两次输入的密码不一致")
                .doesNotContain("InitialPassword7", "ReplacementPassword8", "DifferentPassword9");
        assertThat(component(dialog[0], "password-change.submit", AbstractButton.class).isEnabled())
                .isTrue();
        assertThat(component(dialog[0], "password-change.old", JPasswordField.class).getPassword())
                .isEmpty();
        assertThat(component(dialog[0], "password-change.new", JPasswordField.class).getPassword())
                .isEmpty();
        assertThat(component(dialog[0], "password-change.confirm", JPasswordField.class).getPassword())
                .isEmpty();
    }

    @Test
    void successfulLoginClosesLoginAndShowsIdentityAndPlaceholders() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(loginResult())))
                .when(connection).send(eq("USER_LOGIN"), any(LoginCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        AtomicReference<MainFrame> main = new AtomicReference<>();
        AtomicBoolean handoffOnEdt = new AtomicBoolean();
        LoginFrame[] login = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            login[0] = new LoginFrame(service, result -> {
                handoffOnEdt.set(SwingUtilities.isEventDispatchThread());
                MainFrame frame = new MainFrame(result.user());
                main.set(frame);
                frame.setVisible(true);
            });
            login[0].setVisible(true);
        });
        assertThat(showingMainFrames()).isEmpty();

        submit(login[0], "DEMO_ADMIN", "DemoPassword7");
        awaitEdt(() -> main.get() != null);

        verify(connection).send(eq("USER_LOGIN"), any(LoginCommand.class), eq(TIMEOUT));
        assertThat(login[0].isDisplayable()).isFalse();
        assertThat(main.get()).isNotNull();
        assertThat(main.get().isShowing()).isTrue();
        assertThat(handoffOnEdt).isTrue();
        String text = visibleText(main.get());
        assertThat(text).contains("DEMO_ADMIN", "ADMIN", "学籍", "选课",
                "图书馆", "商城", "建设中");
    }

    @Test
    void failedLoginStaysUsableAndShowsOnlySafeMessage() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.failure(
                "AUTH_INVALID_CREDENTIALS", "凭据错误", null)))
                .when(connection).send(eq("USER_LOGIN"), any(LoginCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        AtomicBoolean mainCreated = new AtomicBoolean();
        AtomicBoolean errorUpdatedOnEdt = new AtomicBoolean();
        LoginFrame[] login = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            login[0] = new LoginFrame(service, result -> mainCreated.set(true));
            component(login[0], "login.error", JLabel.class)
                    .addPropertyChangeListener("text", event ->
                            errorUpdatedOnEdt.set(SwingUtilities.isEventDispatchThread()));
            login[0].setVisible(true);
        });

        submit(login[0], "DEMO_ADMIN", "SecretPassword7");
        awaitEdt(() -> component(login[0], "login.submit", AbstractButton.class).isEnabled());

        assertThat(login[0].isShowing()).isTrue();
        assertThat(mainCreated).isFalse();
        assertThat(showingMainFrames()).isEmpty();
        assertThat(errorUpdatedOnEdt).isTrue();
        assertThat(component(login[0], "login.loginId", JTextField.class).getText())
                .isEqualTo("DEMO_ADMIN");
        assertThat(component(login[0], "login.password", JPasswordField.class).getPassword())
                .isEmpty();
        assertThat(component(login[0], "login.submit", AbstractButton.class).isEnabled())
                .isTrue();
        String error = component(login[0], "login.error", JLabel.class).getText();
        assertThat(error).contains("用户名或密码错误")
                .doesNotContain("SecretPassword7", "hash", "salt",
                        "IllegalArgumentException", "AUTH_INVALID_CREDENTIALS");
    }

    private static void submit(LoginFrame frame, String loginId, String password)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            component(frame, "login.loginId", JTextField.class).setText(loginId);
            component(frame, "login.password", JPasswordField.class).setText(password);
            component(frame, "login.submit", AbstractButton.class).doClick();
        });
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static void awaitEdt(BooleanSupplier completed) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            flushEdt();
            if (completed.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for asynchronous UI completion");
    }

    private static void assertPasswordFieldsEmpty(InitialPasswordChangeDialog dialog) {
        assertThat(component(dialog, "password-change.old", JPasswordField.class).getPassword())
                .isEmpty();
        assertThat(component(dialog, "password-change.new", JPasswordField.class).getPassword())
                .isEmpty();
        assertThat(component(dialog, "password-change.confirm", JPasswordField.class).getPassword())
                .isEmpty();
    }

    private static <T extends java.io.Serializable> void assertSendRunsAfterEdtReturns(
            String command, T responseData, UserRequest request) throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        CountDownLatch sendEntered = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        doAnswer(invocation -> {
            sendEntered.countDown();
            if (!releaseSend.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test did not release connection send");
            }
            return CompletableFuture.completedFuture(ResponseBody.success(responseData));
        }).when(connection).send(eq(command), any(), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);
        ExecutorService testThread = Executors.newSingleThreadExecutor();
        try {
            Future<CompletableFuture<?>> returned = testThread.submit(() -> {
                AtomicReference<CompletableFuture<?>> future = new AtomicReference<>();
                SwingUtilities.invokeAndWait(() -> future.set(request.send(service)));
                return future.get();
            });

            assertThat(returned.get(500, TimeUnit.MILLISECONDS)).isNotNull();
            assertThat(sendEntered.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseSend.countDown();
            testThread.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface UserRequest {
        CompletableFuture<?> send(UserClientService service);
    }

    private static <T extends Component> T component(
            Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container nested) {
                try {
                    return component(nested, name, type);
                } catch (IllegalArgumentException ignored) {
                    // Continue searching siblings.
                }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    private static String visibleText(Container root) {
        StringBuilder text = new StringBuilder();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) {
                text.append(label.getText()).append(' ');
            }
            if (child instanceof AbstractButton button) {
                text.append(button.getText()).append(' ');
            }
            if (child instanceof Container nested) {
                text.append(visibleText(nested));
            }
        }
        return text.toString();
    }

    private static Frame[] showingMainFrames() {
        return Arrays.stream(Frame.getFrames())
                .filter(MainFrame.class::isInstance).filter(Frame::isShowing)
                .toArray(Frame[]::new);
    }

    private static LoginResult loginResult() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 28, 12, 0);
        UserView user = new UserView("demo-user", "DEMO_ADMIN", ADMIN, ACTIVE,
                false, now, 0, now, now);
        return new LoginResult("demo-session-token", user, Set.of(), false);
    }

    private static LoginResult restrictedLoginResult() {
        LoginResult regular = loginResult();
        return new LoginResult(regular.sessionToken(), regular.user(), regular.permissions(), true);
    }
}
