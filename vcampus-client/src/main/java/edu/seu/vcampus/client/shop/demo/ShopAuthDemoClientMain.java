package edu.seu.vcampus.client.shop.demo;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.shop.service.ShopClientService;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiInstaller;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.LoginFrame;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/** Starts the authenticated buyer Shop demo against the selected composed server. */
public final class ShopAuthDemoClientMain {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 19090;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private ShopAuthDemoClientMain() {
    }

    /** Connects off the EDT and then opens the real user login and Shop UI flow. */
    public static void main(String[] args) {
        ServerAddress server = serverAddress(args);
        ClientConnection connection = new ClientConnection(server.host(), server.port());
        try {
            connection.connect(TIMEOUT);
            Runtime.getRuntime().addShutdownHook(
                    new Thread(connection::close, "shop-auth-demo-client-close"));
            UserClientService users = asynchronousUsers(
                    connection,
                    UUID.randomUUID().toString(),
                    TIMEOUT,
                    ForkJoinPool.commonPool());
            ShopClientService shop = new ShopClientService(connection, TIMEOUT);
            SwingUtilities.invokeLater(() -> showLogin(users, shop, connection));
        } catch (Exception error) {
            connection.close();
            System.err.println("Shop Demo 客户端启动失败：" + error.getMessage());
            System.exit(2);
        }
    }

    static ServerAddress serverAddress(String[] args) {
        Objects.requireNonNull(args, "args");
        if (args.length > 2) {
            throw new IllegalArgumentException(
                    "Shop Demo 客户端最多接受 2 个参数：host [port]");
        }

        String host = args.length >= 1 ? args[0] : DEFAULT_HOST;
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Shop Demo 服务器地址不能为空");
        }

        int port = DEFAULT_PORT;
        if (args.length == 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(
                        "Shop Demo 服务器端口必须是数字：" + args[1], error);
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(
                        "Shop Demo 服务器端口必须在 1..65535 范围内：" + port);
            }
        }
        return new ServerAddress(host, port);
    }

    static UserClientService asynchronousUsers(
            ClientConnection connection,
            String clientInstanceId,
            Duration timeout,
            Executor executor) {
        return new AsyncUserClientService(connection, clientInstanceId, timeout, executor);
    }

    private static void showLogin(
            UserClientService users,
            ShopClientService shop,
            ClientConnection connection) {
        requireEdt();
        LoginFrame login = new LoginFrame(
                users, result -> showMain(result, users, shop, connection));
        login.setVisible(true);
    }

    private static void showMain(
            LoginResult result,
            UserClientService users,
            ShopClientService shop,
            ClientConnection connection) {
        requireEdt();
        MainFrame main = authenticatedMain(result.user());
        Runnable sessionTransition = oneShotSessionTransition(
                () -> fireWindowClosing(main),
                main::dispose,
                () -> connection.setSessionToken(null),
                () -> showLogin(users, shop, connection));
        installAuthenticatedShop(main, result.user(), shop, sessionTransition);
        main.setVisible(true);
    }

    static void installAuthenticatedShop(MainFrame main, UserView user, ShopClientPort shop,
            Runnable sessionExpired) {
        ShopUiInstaller.install(main, user, shop, new DefaultShopUiKit(), sessionExpired);
    }

    static MainFrame authenticatedMain(UserView user) {
        requireEdt();
        Objects.requireNonNull(user, "user");
        MainFrame main = new MainFrame();
        JLabel identity = new JLabel(
                "当前用户：" + user.loginId() + "（" + user.role().name() + "）");
        identity.setName("shop-demo.identity");
        main.header().add(identity, BorderLayout.CENTER);
        return main;
    }

    static Runnable oneShotSessionTransition(
            Runnable shopCleanup,
            Runnable dispose,
            Runnable clearToken,
            Runnable showLogin) {
        Objects.requireNonNull(shopCleanup, "shopCleanup");
        Objects.requireNonNull(dispose, "dispose");
        Objects.requireNonNull(clearToken, "clearToken");
        Objects.requireNonNull(showLogin, "showLogin");
        AtomicBoolean started = new AtomicBoolean();
        return () -> {
            if (!started.compareAndSet(false, true)) {
                return;
            }
            Runnable transition = () -> {
                shopCleanup.run();
                dispose.run();
                clearToken.run();
                showLogin.run();
            };
            if (SwingUtilities.isEventDispatchThread()) {
                transition.run();
            } else {
                SwingUtilities.invokeLater(transition);
            }
        };
    }

    private static void fireWindowClosing(MainFrame main) {
        WindowEvent event = new WindowEvent(main, WindowEvent.WINDOW_CLOSING);
        for (WindowListener listener : main.getWindowListeners()) {
            listener.windowClosing(event);
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Shop Demo UI must run on the EDT");
        }
    }

    record ServerAddress(String host, int port) { }

    private static final class AsyncUserClientService extends UserClientService {
        private final Executor executor;

        private AsyncUserClientService(
                ClientConnection connection,
                String clientInstanceId,
                Duration timeout,
                Executor executor) {
            super(connection, clientInstanceId, timeout);
            this.executor = Objects.requireNonNull(executor, "executor");
        }

        @Override
        public CompletableFuture<LoginResult> login(String loginId, char[] password) {
            Objects.requireNonNull(password, "password");
            char[] deferredPassword = password.clone();
            Arrays.fill(password, '\0');
            try {
                return CompletableFuture
                        .supplyAsync(() -> super.login(loginId, deferredPassword), executor)
                        .thenCompose(response -> response)
                        .whenComplete((ignored, error) -> Arrays.fill(deferredPassword, '\0'));
            } catch (RuntimeException error) {
                Arrays.fill(deferredPassword, '\0');
                return CompletableFuture.failedFuture(error);
            }
        }
    }
}
