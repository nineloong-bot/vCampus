package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.session.SessionRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class ServerMainSessionConfigurationTest {
    @Test
    void productionRuntimeUsesConfiguredSessionTimeoutAndInitializesConfiguredDatabase() throws Exception {
        Path directory = Files.createTempDirectory("vcampus-server-main-");
        Path database = directory.resolve("runtime.accdb");
        ServerConfig config = new ServerConfig(8888, 10, 2, database,
                databaseRoot(), true, 7, 15, 24);

        Method factory = ServerMain.class.getDeclaredMethod("createRuntime", ServerConfig.class);
        factory.setAccessible(true);
        Object runtime = factory.invoke(null, config);
        Method authorizationAccessor = runtime.getClass().getDeclaredMethod("authorization");
        authorizationAccessor.setAccessible(true);
        AuthorizationService authorization =
                (AuthorizationService) authorizationAccessor.invoke(runtime);

        Field sessionsField = AuthorizationService.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        SessionRegistry sessions = (SessionRegistry) sessionsField.get(authorization);
        Field timeoutField = SessionRegistry.class.getDeclaredField("idleTimeout");
        timeoutField.setAccessible(true);

        assertThat(timeoutField.get(sessions)).isEqualTo(Duration.ofMinutes(7));
        assertThat(database).exists();
    }

    @Test
    void existingDatabaseProductionRuntimeUsesTheSafeUcanaccessUrl() throws Exception {
        Path directory = Files.createTempDirectory("vcampus-server-existing-");
        Path database = directory.resolve("runtime.accdb");
        ServerConfig creating = new ServerConfig(8888, 10, 2, database,
                databaseRoot(), true, 7, 15, 24);
        ServerMain.createRuntime(creating);
        ServerConfig existing = new ServerConfig(8888, 10, 2, database,
                databaseRoot(), false, 7, 15, 24);

        CapturingUcanaccessDriver capture = CapturingUcanaccessDriver.install();
        try {
            ServerMain.createRuntime(existing);
        } finally {
            capture.restore();
        }

        assertThat(capture.urls()).contains("jdbc:ucanaccess://" + database);
        assertThat(capture.urls()).noneMatch(url -> url.contains("immediatelyReleaseResources=true"));
    }

    private static Path databaseRoot() {
        Path root = Path.of("vcampus-database");
        return Files.exists(root) ? root : Path.of("..", "vcampus-database");
    }

    /** Captures the URL sent to the production driver while delegating every connection. */
    private static final class CapturingUcanaccessDriver implements Driver {
        private final Driver delegate;
        private final List<Driver> replaced;
        private final List<String> urls = new ArrayList<>();

        private CapturingUcanaccessDriver(Driver delegate, List<Driver> replaced) {
            this.delegate = delegate;
            this.replaced = replaced;
        }

        static CapturingUcanaccessDriver install() throws SQLException {
            List<Driver> drivers = Collections.list(DriverManager.getDrivers());
            List<Driver> ucanaccess = drivers.stream()
                    .filter(driver -> driver.getClass().getName().equals("net.ucanaccess.jdbc.UcanaccessDriver"))
                    .toList();
            if (ucanaccess.isEmpty()) throw new SQLException("UCanAccess driver is not registered");
            for (Driver driver : ucanaccess) DriverManager.deregisterDriver(driver);
            CapturingUcanaccessDriver capture = new CapturingUcanaccessDriver(ucanaccess.getFirst(), ucanaccess);
            DriverManager.registerDriver(capture);
            return capture;
        }

        void restore() throws SQLException {
            DriverManager.deregisterDriver(this);
            for (Driver driver : replaced) DriverManager.registerDriver(driver);
        }

        List<String> urls() { return List.copyOf(urls); }

        @Override public Connection connect(String url, Properties properties) throws SQLException {
            urls.add(url);
            return delegate.connect(url, properties);
        }

        @Override public boolean acceptsURL(String url) { return url.startsWith("jdbc:ucanaccess://"); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) throws SQLException {
            return delegate.getPropertyInfo(url, properties);
        }
        @Override public int getMajorVersion() { return delegate.getMajorVersion(); }
        @Override public int getMinorVersion() { return delegate.getMinorVersion(); }
        @Override public boolean jdbcCompliant() { return delegate.jdbcCompliant(); }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
