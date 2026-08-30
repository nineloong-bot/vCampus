package edu.seu.vcampus.client.shop.demo;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.shop.service.ShopClientService;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiInstaller;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutItem;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.PaymentAttemptStatus;
import edu.seu.vcampus.common.shop.PaymentChannel;
import edu.seu.vcampus.common.shop.PaymentStatus;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.shop.demo.ShopAuthDemoDatabase;
import edu.seu.vcampus.server.shop.demo.ShopAuthDemoRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.CardLayout;
import java.awt.GraphicsEnvironment;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopAuthEndToEndTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String PAYMENT_REQUEST_ID = "shop-auth-e2e-payment-request";

    @TempDir
    Path temp;

    @Test
    void sessionExpirationRunsShopCleanupOnceBeforeOpeningLogin() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicBoolean transitionRanOnEdt = new AtomicBoolean();
        Runnable transition = ShopAuthDemoClientMain.oneShotSessionTransition(
                () -> {
                    transitionRanOnEdt.set(javax.swing.SwingUtilities.isEventDispatchThread());
                    events.add("shop-cleanup");
                },
                () -> events.add("dispose"),
                () -> events.add("clear-token"),
                () -> events.add("show-login"));

        transition.run();
        transition.run();
        edu.seu.vcampus.client.shop.ShopSwingTestSupport.flushEdt();

        assertThat(transitionRanOnEdt).isTrue();
        assertThat(events).containsExactly(
                "shop-cleanup", "dispose", "clear-token", "show-login");
    }

    @Test
    void demoLoginCopiesPasswordBeforeDispatchAndSendsOffEdt() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        Executor deferred = task -> assertThat(scheduled.compareAndSet(null, task)).isTrue();
        UserClientService users = ShopAuthDemoClientMain.asynchronousUsers(
                connection, "demo-client", TIMEOUT, deferred);
        LoginResult expected = loginResult();
        AtomicBoolean sendRanOnEdt = new AtomicBoolean();
        when(connection.<LoginResult>send(eq("USER_LOGIN"), any(LoginCommand.class), eq(TIMEOUT)))
                .thenAnswer(invocation -> {
                    sendRanOnEdt.set(javax.swing.SwingUtilities.isEventDispatchThread());
                    LoginCommand command = invocation.getArgument(1);
                    assertThat(command.password()).containsExactly(
                            'D', 'e', 'm', 'o', 'P', 'a', 's', 's', 'w', 'o', 'r', 'd', '7');
                    return CompletableFuture.completedFuture(ResponseBody.success(expected));
                });
        char[] password = "DemoPassword7".toCharArray();

        CompletableFuture<LoginResult> response =
                edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt(
                        (Callable<CompletableFuture<LoginResult>>) () ->
                                users.login("DEMO_BUYER", password));

        assertThat(password).containsOnly('\0');
        assertThat(response).isNotDone();
        assertThat(scheduled.get()).isNotNull();
        scheduled.get().run();
        assertThat(response.join()).isEqualTo(expected);
        assertThat(sendRanOnEdt).isFalse();
        verify(connection).setSessionToken("opaque-session");
    }

    @Test
    void authenticatedDemoShellKeepsTheShopCardHostAndVisibleIdentity() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        LoginResult login = loginResult();
        MainFrame main = edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt(
                () -> ShopAuthDemoClientMain.authenticatedMain(login.user()));
        ShopClientPort client = mock(ShopClientPort.class);
        when(client.home(any())).thenReturn(new CompletableFuture<>());

        edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt(() ->
                ShopUiInstaller.install(main, client, new DefaultShopUiKit(), () -> { }));
        JButton shop = edu.seu.vcampus.client.shop.ShopSwingTestSupport.component(
                main.navigation(), "shop.navigation", JButton.class);
        edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt(
                (Runnable) () -> shop.doClick());

        assertThat(main.content().getLayout()).isInstanceOf(CardLayout.class);
        assertThat(edu.seu.vcampus.client.shop.ShopSwingTestSupport.component(
                main.header(), "shop-demo.identity", JLabel.class).getText())
                .isEqualTo("当前用户：DEMO_BUYER（STUDENT）");
        edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt(main::dispose);
    }

    @Test
    void loginCartCheckoutAndPaymentPersistExactlyOnce() throws Exception {
        Path database = temp.resolve("shop-auth-e2e.accdb");
        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());
        InventoryState initial = inventoryState(database);

        try (ShopAuthDemoRuntime runtime = ShopAuthDemoRuntime.start(database, 0);
                ClientConnection connection = new ClientConnection(
                        "127.0.0.1", runtime.localPort())) {
            connection.connect(TIMEOUT);
            UserClientService users = new UserClientService(
                    connection, "e2e-client", TIMEOUT);
            LoginResult login = users.login(
                    "DEMO_BUYER", "DemoPassword7".toCharArray()).join();
            ShopClientService shop = new ShopClientService(connection, TIMEOUT);

            CartView cart = shop.addToCart(
                    new AddCartItemCommand("demo-pen-black", 2)).join();
            CheckoutResult checkout = shop.checkout(new CheckoutCommand(
                    cart.items().stream()
                            .map(item -> new CheckoutItem(
                                    item.cartItemId(), item.displayedUnitPrice()))
                            .toList(),
                    false)).join();

            Message paymentRequest = new Message(
                    PAYMENT_REQUEST_ID,
                    MessageType.REQUEST,
                    "SHOP_SIMULATE_PAYMENT",
                    login.sessionToken(),
                    new SimulatePaymentCommand(
                            checkout.paymentId(),
                            PaymentChannel.ALIPAY,
                            PaymentAttemptStatus.SUCCEEDED),
                    1L);
            try (RawProtocolExchange exchange = new RawProtocolExchange(
                    "127.0.0.1", runtime.localPort(), TIMEOUT)) {
                PaymentView firstPayment = requirePayment(exchange.send(paymentRequest));

                assertThat(firstPayment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
                DatabaseState afterFirst = assertDatabaseInvariant(
                        database, checkout, firstPayment, initial);

                PaymentView replayedPayment = requirePayment(exchange.send(paymentRequest));

                assertThat(replayedPayment).isEqualTo(firstPayment);
                assertThat(readDatabaseState(database, checkout.paymentId()))
                        .isEqualTo(afterFirst);
            }
        }

        assertThat(demoClientEntryPointExists())
                .as("authenticated Shop demo client entry point")
                .isTrue();
    }

    private static PaymentView requirePayment(Message response) {
        assertThat(response.requestId()).isEqualTo(PAYMENT_REQUEST_ID);
        assertThat(response.type()).isEqualTo(MessageType.RESPONSE);
        assertThat(response.command()).isEqualTo("SHOP_SIMULATE_PAYMENT");
        assertThat(response.body()).isInstanceOf(ResponseBody.class);
        ResponseBody<?> body = (ResponseBody<?>) response.body();
        assertThat(body.success()).isTrue();
        assertThat(body.code()).isEqualTo("SUCCESS");
        assertThat(body.data()).isInstanceOf(PaymentView.class);
        return (PaymentView) body.data();
    }

    private static DatabaseState assertDatabaseInvariant(
            Path database,
            CheckoutResult checkout,
            PaymentView payment,
            InventoryState initial) throws Exception {
        assertThat(payment.paymentId()).isEqualTo(checkout.paymentId());
        DatabaseState state = readDatabaseState(database, checkout.paymentId());
        assertThat(state.cartItemCount()).isZero();
        assertThat(state.paymentAttemptCount()).isEqualTo(1);
        assertThat(state.paymentStatus()).isEqualTo("SUCCEEDED");
        assertThat(state.reservationStatus()).isEqualTo("CONSUMED");
        assertThat(state.stockQuantity()).isEqualTo(initial.stockQuantity() - 2);
        assertThat(state.reservedQuantity()).isZero();
        assertThat(state.salesCount()).isEqualTo(initial.salesCount() + 2);
        return state;
    }

    private static InventoryState inventoryState(Path database) throws Exception {
        try (Connection connection = open(database)) {
            long stock = scalarLong(connection,
                    "SELECT stockQuantity FROM tblProductSku WHERE skuId = ?",
                    "demo-pen-black");
            long sales = scalarLong(connection,
                    "SELECT salesCount FROM tblProduct WHERE productId = ?",
                    "demo-pen");
            assertThat(stock).isEqualTo(10);
            assertThat(sales).isZero();
            return new InventoryState(stock, sales);
        }
    }

    private static DatabaseState readDatabaseState(
            Path database, String paymentId) throws Exception {
        try (Connection connection = open(database)) {
            return new DatabaseState(
                    scalarLong(connection,
                            "SELECT COUNT(*) FROM tblCartItem i "
                                    + "INNER JOIN tblCart c ON i.cartId = c.cartId "
                                    + "WHERE c.userId = ?",
                            "demo-buyer"),
                    scalarLong(connection,
                            "SELECT COUNT(*) FROM tblPaymentAttempt WHERE paymentId = ?",
                            paymentId),
                    scalarString(connection,
                            "SELECT paymentStatus FROM tblPayment WHERE paymentId = ?",
                            paymentId),
                    scalarString(connection,
                            "SELECT reservationStatus FROM tblInventoryReservation "
                                    + "WHERE paymentId = ? AND skuId = 'demo-pen-black'",
                            paymentId),
                    scalarLong(connection,
                            "SELECT stockQuantity FROM tblProductSku WHERE skuId = ?",
                            "demo-pen-black"),
                    scalarLong(connection,
                            "SELECT reservedQuantity FROM tblProductSku WHERE skuId = ?",
                            "demo-pen-black"),
                    scalarLong(connection,
                            "SELECT salesCount FROM tblProduct WHERE productId = ?",
                            "demo-pen"));
        }
    }

    private static Connection open(Path database) throws Exception {
        return DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true");
    }

    private static long scalarLong(
            Connection connection, String sql, String parameter) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private static String scalarString(
            Connection connection, String sql, String parameter) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static boolean demoClientEntryPointExists() {
        try {
            Class.forName("edu.seu.vcampus.client.shop.demo.ShopAuthDemoClientMain");
            return true;
        } catch (ClassNotFoundException error) {
            return false;
        }
    }

    private static LoginResult loginResult() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 30, 0, 0);
        UserView user = new UserView(
                "demo-buyer", "DEMO_BUYER",
                edu.seu.vcampus.common.user.UserRole.STUDENT,
                edu.seu.vcampus.common.user.AccountStatus.ACTIVE,
                false, now, 0, now, now);
        return new LoginResult("opaque-session", user, Set.of(), false);
    }

    private static Path schemaDir() {
        return projectDirectory("schema");
    }

    private static Path seedDir() {
        return projectDirectory("seed");
    }

    private static Path projectDirectory(String name) {
        Path fromModule = Path.of("..", "vcampus-database", name);
        return Files.isDirectory(fromModule) ? fromModule : Path.of("vcampus-database", name);
    }

    private record InventoryState(long stockQuantity, long salesCount) { }

    private record DatabaseState(
            long cartItemCount,
            long paymentAttemptCount,
            String paymentStatus,
            String reservationStatus,
            long stockQuantity,
            long reservedQuantity,
            long salesCount) { }

    private static final class RawProtocolExchange implements AutoCloseable {
        private final Socket socket;
        private final ObjectOutputStream output;
        private final ObjectInputStream input;

        private RawProtocolExchange(
                String host, int port, Duration timeout) throws Exception {
            socket = new Socket();
            socket.connect(
                    new java.net.InetSocketAddress(host, port),
                    Math.toIntExact(timeout.toMillis()));
            socket.setSoTimeout(Math.toIntExact(timeout.toMillis()));
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());
        }

        private Message send(Message request) throws Exception {
            output.writeObject(request);
            output.flush();
            output.reset();
            return (Message) input.readObject();
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
