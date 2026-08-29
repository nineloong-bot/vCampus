package edu.seu.vcampus.client.shop.demo;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.shop.service.ShopClientService;
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
import edu.seu.vcampus.server.shop.demo.ShopAuthDemoDatabase;
import edu.seu.vcampus.server.shop.demo.ShopAuthDemoRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ShopAuthEndToEndTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String PAYMENT_REQUEST_ID = "shop-auth-e2e-payment-request";

    @TempDir
    Path temp;

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
