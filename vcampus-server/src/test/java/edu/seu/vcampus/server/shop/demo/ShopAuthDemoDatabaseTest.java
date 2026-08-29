package edu.seu.vcampus.server.shop.demo;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShopAuthDemoDatabaseTest {
    @TempDir
    Path temp;

    @Test
    void createsKnownBuyerAndTwoVisibleShops() throws Exception {
        Path database = temp.resolve("shop-auth.accdb");

        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());

        try (Connection connection = open(database)) {
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblUser WHERE loginId='DEMO_BUYER' "
                            + "AND accountStatus='ACTIVE' AND mustChangePassword=FALSE"))
                    .isEqualTo(1);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblShop WHERE shopStatus='ACTIVE'"))
                    .isEqualTo(2);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProductSku WHERE skuId='demo-pen-black' "
                            + "AND stockQuantity=10 AND isActive=TRUE"))
                    .isEqualTo(1);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProductSku WHERE skuId='demo-book-standard' "
                            + "AND stockQuantity=5 AND isActive=TRUE"))
                    .isEqualTo(1);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProductSku WHERE stockQuantity=1 AND isActive=TRUE"))
                    .isEqualTo(1);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProductSku WHERE isActive=FALSE"))
                    .isEqualTo(1);
        }
    }

    @Test
    void replacesPriorDemoStateWhenInitializedAgain() throws Exception {
        Path database = temp.resolve("repeatable.accdb");
        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());
        try (Connection connection = open(database);
                var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE tblProductSku SET stockQuantity=2 "
                    + "WHERE skuId='demo-pen-black'");
        }

        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());

        try (Connection connection = open(database)) {
            assertThat(count(connection, "SELECT COUNT(*) FROM tblUser WHERE userId='demo-buyer'"))
                    .isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblShop"))
                    .isEqualTo(2);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProductSku WHERE skuId='demo-pen-black' "
                            + "AND stockQuantity=10"))
                    .isEqualTo(1);
        }
    }

    @Test
    void runtimeUsesPreparedDatabaseAndServesLoginOnEphemeralPort() throws Exception {
        Path database = temp.resolve("runtime.accdb");
        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());
        try (Connection connection = open(database);
                var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE tblProductSku SET stockQuantity=7 "
                    + "WHERE skuId='demo-pen-black'");
        }

        try (ShopAuthDemoRuntime runtime = ShopAuthDemoRuntime.start(database, 0)) {
            assertThat(runtime.localPort()).isPositive();
            assertThat(login(runtime.localPort()).user().loginId()).isEqualTo("DEMO_BUYER");
        }

        try (Connection connection = open(database)) {
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProductSku WHERE skuId='demo-pen-black' "
                            + "AND stockQuantity=7"))
                    .isEqualTo(1);
        }
    }

    private static LoginResult login(int port) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5_000);
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            try (output; ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                Message request = new Message(UUID.randomUUID().toString(), MessageType.REQUEST,
                        "USER_LOGIN", null,
                        new LoginCommand("DEMO_BUYER", "DemoPassword7".toCharArray(), "demo-test"),
                        System.currentTimeMillis());
                output.writeObject(request);
                output.flush();
                Message response = (Message) input.readObject();
                assertThat(response.body()).isInstanceOf(ResponseBody.class);
                ResponseBody<?> body = (ResponseBody<?>) response.body();
                assertThat(body.success()).isTrue();
                assertThat(body.data()).isInstanceOf(LoginResult.class);
                return (LoginResult) body.data();
            }
        }
    }

    private static Connection open(Path database) throws Exception {
        return DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true");
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
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
}
