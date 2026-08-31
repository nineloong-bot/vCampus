package edu.seu.vcampus.server.bootstrap.demo;

import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.course.CreateCourseCommand;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.CreateTermCommand;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.server.bootstrap.ApplicationRuntime;
import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.course.service.CourseService;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/** Isolated authenticated three-role demo backed by the production application runtime. */
public final class IntegratedDemoServerMain {
    private static final String DEMO_DATABASE_FILENAME = "course-user-demo.accdb";
    private static final int PASSWORD_ITERATIONS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private IntegratedDemoServerMain() {
    }

    /** Starts the isolated demo with an optional path to its server properties. */
    public static void main(String[] args) {
        Path configFile = Path.of(args.length == 0
                ? "config/server-with-data.properties" : args[0])
                .toAbsolutePath().normalize();
        Path configDirectory = configFile.getParent();
        Path baseDirectory = configDirectory == null ? Path.of(".").toAbsolutePath()
                : configDirectory.getParent();
        if (baseDirectory == null) baseDirectory = Path.of(".").toAbsolutePath();
        SocketServer server = null;
        try {
            ServerConfig config = ServerConfig.load(configFile, baseDirectory);
            ApplicationRuntime runtime = prepare(config.databasePath(),
                    config.databaseResourceRoot(), Clock.systemUTC(),
                    Duration.ofMinutes(config.sessionTimeoutMinutes()));
            server = new SocketServer(config.port(), config.workerThreads(),
                    config.maxConnections(), runtime.router());
            SocketServer running = server;
            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> shutdown(running), "integrated-demo-shutdown"));
            System.out.println("带数据服务端已启动，端口 " + server.localPort());
            System.out.println("账号：ADMIN / 213000001 / TEACHER_DEMO（详见 docs/course-runtime-integration.md）");
            server.serve();
        } catch (Exception failure) {
            if (server != null) shutdown(server);
            System.err.println("带数据服务端启动失败：" + failure.getMessage());
            System.exit(2);
        }
    }

    /** Creates and idempotently seeds an isolated demo database for tests and local use. */
    public static ApplicationRuntime prepare(Path databasePath, Path databaseResourceRoot,
            Clock clock) throws Exception {
        return prepare(databasePath, databaseResourceRoot, clock, Duration.ofMinutes(30));
    }

    private static ApplicationRuntime prepare(Path databasePath, Path databaseResourceRoot,
            Clock clock, Duration sessionTimeout) throws Exception {
        Path database = databasePath.toAbsolutePath().normalize();
        if (database.getFileName() == null
                || !DEMO_DATABASE_FILENAME.equals(database.getFileName().toString())) {
            throw new IllegalArgumentException(
                    "Demo database must be named " + DEMO_DATABASE_FILENAME);
        }
        Path parent = database.getParent();
        if (parent != null) Files.createDirectories(parent);
        String url = "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010";
        ConnectionProvider connections = () -> DriverManager.getConnection(url);
        ApplicationRuntime runtime = ApplicationRuntime.create(connections,
                databaseResourceRoot, clock, sessionTimeout);
        seedUser(connections, "213000001", "213000001", "Student1234",
                "STUDENT", false, clock.instant());
        seedUser(connections, "teacher-demo-001", "TEACHER_DEMO", "Teacher1234",
                "TEACHER", false, clock.instant());
        seedCourses(runtime.course().service(), clock);
        return runtime;
    }

    private static void seedUser(ConnectionProvider connections, String userId, String loginId,
            String password, String role, boolean mustChangePassword, Instant now) throws Exception {
        try (var connection = connections.open(); var query = connection.prepareStatement(
                "SELECT 1 FROM tblUser WHERE userId = ? OR loginId = ?")) {
            query.setString(1, userId);
            query.setString(2, loginId);
            try (var rows = query.executeQuery()) {
                if (rows.next()) return;
            }
        }
        char[] value = password.toCharArray();
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String hash;
        try {
            PBEKeySpec specification = new PBEKeySpec(value, salt, PASSWORD_ITERATIONS, 256);
            try {
                hash = Base64.getEncoder().encodeToString(
                        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                                .generateSecret(specification).getEncoded());
            } finally {
                specification.clearPassword();
            }
        } finally {
            Arrays.fill(value, '\0');
        }
        LocalDateTime timestamp = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        try (var connection = connections.open(); var insert = connection.prepareStatement("""
                INSERT INTO tblUser
                    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
                     roleCode, accountStatus, mustChangePassword, failedLoginCount,
                     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, userId);
            insert.setString(2, loginId);
            insert.setString(3, hash);
            insert.setString(4, Base64.getEncoder().encodeToString(salt));
            insert.setInt(5, PASSWORD_ITERATIONS);
            insert.setString(6, role);
            insert.setString(7, "ACTIVE");
            insert.setBoolean(8, mustChangePassword);
            insert.setInt(9, 0);
            insert.setTimestamp(10, null);
            insert.setTimestamp(11, null);
            insert.setInt(12, 0);
            insert.setTimestamp(13, Timestamp.valueOf(timestamp));
            insert.setTimestamp(14, Timestamp.valueOf(timestamp));
            insert.executeUpdate();
        }
    }

    private static void seedCourses(CourseService courses, Clock clock) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        var terms = courses.listTerms();
        var term = terms.isEmpty()
                ? courses.createTerm(new CreateTermCommand("DEMO-TERM", "登录选课集成演示学期",
                        today.minusMonths(1), today.plusMonths(5),
                        now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)),
                        now.plus(Duration.ofDays(31)), now.plus(Duration.ofDays(60)), "ACTIVE"))
                : terms.getFirst();
        var catalog = courses.searchCatalog(new CourseCatalogQuery("MATH101", null, 0, 20)).items();
        var course = catalog.stream().filter(item -> "MATH101".equals(item.courseCode()))
                .findFirst().orElseGet(() -> courses.createCourse(new CreateCourseCommand(
                        "MATH101", "高等数学（带数据演示）", new BigDecimal("5.0"), 80,
                        "用于验证登录后的真实查询、选课和课表", true)));
        var offerings = courses.searchOfferings(new OfferingSearchQuery(
                term.termId(), "MATH101", null, false, 0, 20)).items();
        if (offerings.isEmpty()) {
            courses.createOffering(new CreateOfferingCommand(term.termId(), course.courseId(),
                    "teacher-demo-001", "Demo-01", 40, "OPEN",
                    List.of(new CreateOfferingCommand.ScheduleInput(
                            "MONDAY", 1, 2, 1, 16, "Demo-101"))));
        }
    }

    private static void shutdown(SocketServer server) {
        try {
            server.stopAccepting();
            server.awaitRequests(Duration.ofSeconds(10));
            server.close();
        } catch (Exception ignored) {
        }
    }
}
