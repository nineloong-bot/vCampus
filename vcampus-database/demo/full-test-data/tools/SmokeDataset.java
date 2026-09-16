import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.shop.catalog.CatalogDtos.Page;
import edu.seu.vcampus.common.shop.catalog.CatalogDtos.Query;
import edu.seu.vcampus.server.bootstrap.ApplicationRuntime;
import edu.seu.vcampus.server.network.SocketServer;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Starts the real server against a copy and checks representative read paths. */
class SmokeDataset {
    private static <T> T result(CompletableFuture<T> future) throws Exception {
        return future.get(30, TimeUnit.SECONDS);
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException(description);
        System.out.println("PASS " + description);
    }

    public static void main(String[] args) throws Exception {
        var provider = (edu.seu.vcampus.server.persistence.ConnectionProvider) () ->
                DriverManager.getConnection("jdbc:ucanaccess://" + args[0]
                        + ";immediatelyReleaseResources=true");
        var runtime = ApplicationRuntime.create(provider, Path.of(args[1]), Clock.systemUTC());
        require(sequence(args[0]) == 40, "startup preserves campus-card sequence");
        try (var server = new SocketServer(0, 4, 20, runtime.router())) {
            var executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> { server.serve(); return null; });
            try {
                smokeAdministrator(server.localPort());
                smokeTeacher(server.localPort());
                smokeStudent(server.localPort());
            } finally {
                server.close();
                executor.shutdownNow();
                executor.awaitTermination(10, TimeUnit.SECONDS);
            }
        }
        try (var connection = provider.open(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE tblNumberSequence SET currentValue=41 "
                    + "WHERE sequenceKey='CAMPUS_CARD_GLOBAL'");
        }
        ApplicationRuntime.create(provider, Path.of(args[1]), Clock.systemUTC());
        require(sequence(args[0]) == 41, "restart preserves advanced campus-card sequence");
        System.out.println("SMOKE PASSED");
    }

    private static void smokeAdministrator(int port) throws Exception {
        try (var connection = connect(port)) {
            var users = users(connection, "release-admin");
            require(result(users.login("ADMIN", "123456".toCharArray())).user()
                    .loginId().equals("ADMIN"), "administrator login");
            require(result(new CourseClientService(connection).listTerms()).size() == 1,
                    "single course term");
            var books = new LibraryClientService(connection, Duration.ofSeconds(30));
            require(result(books.searchBooks(new BookSearchQuery("", null, false, 1, 20)))
                    .total() == 12, "library catalog");
            var catalog = result(connection.<Page>send("SHOP2_CATALOG_LIST",
                    new Query("", null, "SALES_DESC", 1, 20), Duration.ofSeconds(30)));
            require(catalog.success() && catalog.data().total() > 0, "shop catalog");
            result(users.logout());
        }
    }

    private static void smokeTeacher(int port) throws Exception {
        try (var connection = connect(port)) {
            var users = users(connection, "release-teacher");
            require(result(users.login("T001", "123456".toCharArray())).user()
                    .loginId().equals("T001"), "teacher login");
            result(users.logout());
        }
    }

    private static void smokeStudent(int port) throws Exception {
        try (var connection = connect(port)) {
            var users = users(connection, "release-student");
            var login = result(users.login("213240001", "123456".toCharArray()));
            require(login.mustChangePassword(), "student initial password change");
            result(users.logout());
        }
    }

    private static ClientConnection connect(int port) throws Exception {
        var connection = new ClientConnection("127.0.0.1", port);
        connection.connect(Duration.ofSeconds(10));
        return connection;
    }

    private static UserClientService users(ClientConnection connection, String instance) {
        return new UserClientService(connection, instance, Duration.ofSeconds(30));
    }

    private static int sequence(String database) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + database);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT currentValue FROM tblNumberSequence "
                     + "WHERE sequenceKey='CAMPUS_CARD_GLOBAL'")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
