package edu.seu.vcampus.server.shop.demo;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.shop.adapter.FoundationShopUserAdapter;
import edu.seu.vcampus.server.shop.handler.BuyerShopHandlers;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.payment.SimulatedPaymentService;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.service.CartService;
import edu.seu.vcampus.server.shop.service.CheckoutService;
import edu.seu.vcampus.server.shop.service.ShopService;
import edu.seu.vcampus.server.user.handler.UserHandlers;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.service.PasswordHasher;
import edu.seu.vcampus.server.user.service.UserService;
import edu.seu.vcampus.server.user.service.UserServiceImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Running socket composition for authenticated buyer Shop requests. */
public final class ShopAuthDemoRuntime implements AutoCloseable {
    private static final int WORKER_COUNT = 4;
    private static final int QUEUE_CAPACITY = 100;

    private final SocketServer server;
    private final Thread serverThread;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile IOException servingFailure;

    private ShopAuthDemoRuntime(SocketServer server) {
        this.server = server;
        serverThread = new Thread(this::serve, "shop-auth-demo-server");
    }

    /** Starts against an already initialized database without replacing its contents. */
    public static ShopAuthDemoRuntime start(Path database, int port) throws IOException {
        Path target = Objects.requireNonNull(database, "database")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Initialized demo database is required: " + target);
        }
        String url = "jdbc:ucanaccess://" + target + ";immediatelyReleaseResources=true";
        ConnectionProvider connections = () -> DriverManager.getConnection(url);
        TransactionManager transactions = new TransactionManager(connections);
        StripedResourceLockManager locks = new StripedResourceLockManager();
        Clock clock = Clock.systemUTC();
        SessionRegistry sessions = new SessionRegistry(clock);
        UserService userService = new UserServiceImpl(transactions, locks,
                new AccessUserRepository(), new AccessAuditRepository(), new PasswordHasher(),
                sessions, clock);
        AuthorizationService authorization = new AuthorizationService(sessions);
        FoundationShopUserAdapter shopUsers = new FoundationShopUserAdapter(authorization);
        AccessShopRepository repository = new AccessShopRepository();
        ShopService shopService = new ShopService(repository, transactions);
        CartService cartService = new CartService(
                repository, shopUsers, transactions, locks, clock);
        CheckoutService checkoutService = new CheckoutService(
                repository, shopUsers, transactions, locks, clock);
        SimulatedPaymentService paymentService = new SimulatedPaymentService(
                shopUsers, transactions, locks, clock);
        MessageRouter router = new MessageRouter(Map.of(
                "PING", (request, context) -> ResponseBody.success(EmptyResponse.INSTANCE)));
        new UserHandlers(router, userService, authorization);
        new BuyerShopHandlers(router, shopUsers,
                new RequestDeduplicator(transactions, locks), shopService, cartService,
                checkoutService, paymentService, new ShopBusinessLogger());

        ShopAuthDemoRuntime runtime = new ShopAuthDemoRuntime(
                new SocketServer(port, WORKER_COUNT, QUEUE_CAPACITY, router));
        runtime.serverThread.start();
        return runtime;
    }

    /** Returns the actual bound port, including the OS-assigned port requested with zero. */
    public int localPort() {
        return server.localPort();
    }

    void await() throws IOException, InterruptedException {
        stopped.await();
        if (servingFailure != null) {
            throw servingFailure;
        }
    }

    private void serve() {
        try {
            server.serve();
        } catch (IOException error) {
            if (!closed.get()) {
                servingFailure = error;
            }
        } finally {
            stopped.countDown();
        }
    }

    /** Stops accepting requests, releases workers, and joins the serving thread. */
    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IOException closeFailure = null;
        try {
            server.close();
        } catch (IOException error) {
            closeFailure = error;
        }
        try {
            if (!stopped.await(5, TimeUnit.SECONDS)) {
                serverThread.interrupt();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            IOException interrupted = new IOException("Interrupted while stopping demo runtime", error);
            if (closeFailure == null) {
                closeFailure = interrupted;
            } else {
                closeFailure.addSuppressed(interrupted);
            }
        }
        if (closeFailure != null) {
            throw closeFailure;
        }
    }
}
