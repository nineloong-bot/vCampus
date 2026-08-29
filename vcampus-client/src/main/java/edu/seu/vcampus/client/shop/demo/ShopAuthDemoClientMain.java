package edu.seu.vcampus.client.shop.demo;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.shop.service.ShopClientService;
import edu.seu.vcampus.client.shop.ui.ShopUiInstaller;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.LoginFrame;
import edu.seu.vcampus.common.user.LoginResult;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.UUID;

/** Starts the authenticated buyer Shop demo against the local composed server. */
public final class ShopAuthDemoClientMain {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 19090;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private ShopAuthDemoClientMain() {
    }

    /** Connects off the EDT and then opens the real user login and Shop UI flow. */
    public static void main(String[] args) {
        ClientConnection connection = new ClientConnection(DEFAULT_HOST, DEFAULT_PORT);
        try {
            connection.connect(TIMEOUT);
            Runtime.getRuntime().addShutdownHook(
                    new Thread(connection::close, "shop-auth-demo-client-close"));
            UserClientService users = new UserClientService(
                    connection, UUID.randomUUID().toString(), TIMEOUT);
            ShopClientService shop = new ShopClientService(connection, TIMEOUT);
            SwingUtilities.invokeLater(() -> showLogin(users, shop, connection));
        } catch (Exception error) {
            connection.close();
            System.err.println("Shop Demo 客户端启动失败：" + error.getMessage());
            System.exit(2);
        }
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
        MainFrame main = new MainFrame(result.user());
        ShopUiInstaller.install(main, shop, new DefaultShopUiKit(), () -> {
            main.dispose();
            connection.setSessionToken(null);
            showLogin(users, shop, connection);
        });
        main.setVisible(true);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Shop Demo UI must run on the EDT");
        }
    }
}
