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
    private static final String DEMO_PASSWORD = "DemoPassword7";
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
            System.out.println("账号：DEMO_STUDENT / DEMO_TEACHER / DEMO_ADMIN"
                    + "（详见 docs/course-user-management-demo-and-test-guide.md）");
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
        seedUser(connections, "demo-student", "DEMO_STUDENT", DEMO_PASSWORD,
                "STUDENT", false, clock.instant());
        seedUser(connections, "demo-teacher", "DEMO_TEACHER", DEMO_PASSWORD,
                "TEACHER", false, clock.instant());
        seedUser(connections, "demo-admin", "DEMO_ADMIN", DEMO_PASSWORD,
                "ADMIN", true, clock.instant());
        seedDemoStudent(connections);
        seedCourses(runtime.course().service(), connections, clock);
        return runtime;
    }

    private static void seedDemoStudent(ConnectionProvider connections) throws Exception {
        try (var connection = connections.open(); var query = connection.prepareStatement(
                "SELECT 1 FROM tblStudent WHERE studentId=?")) {
            query.setString(1, "demo-student");
            try (var rows = query.executeQuery()) {
                if (rows.next()) return;
            }
        }
        try (var connection = connections.open(); var insert = connection.prepareStatement("""
                INSERT INTO tblStudent
                    (studentId, userId, studentNumber, studentType, studentName, gender,
                     email, phone, classId, enrollmentDate, studentStatus, rowVersion,
                     createdAt, updatedAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, "demo-student");
            insert.setString(2, "demo-student");
            insert.setString(3, "09999999");
            insert.setString(4, "UNDERGRADUATE");
            insert.setString(5, "课程演示学生");
            insert.setString(6, "未知");
            insert.setString(7, "demo.student@seu.edu.cn");
            insert.setString(8, null);
            insert.setString(9, "00000000-0000-0000-0000-000000000103");
            Timestamp now = Timestamp.from(Instant.now());
            insert.setTimestamp(10, now);
            insert.setString(11, "ACTIVE");
            insert.setLong(12, 0);
            insert.setTimestamp(13, now);
            insert.setTimestamp(14, now);
            insert.executeUpdate();
        }
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

    private static void seedCourses(CourseService courses, ConnectionProvider connections,
                                    Clock clock) throws Exception {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        var terms = courses.listTerms();
        var term = terms.isEmpty()
                ? courses.createTerm(new CreateTermCommand("DEMO-TERM", "登录选课集成演示学期",
                        today.minusMonths(1), today.plusMonths(5),
                        now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)),
                        now.plus(Duration.ofDays(31)), now.plus(Duration.ofDays(60)), "ACTIVE"))
                : terms.getFirst();
        var mathematics = ensureCourse(courses, "DEMO-MATH101", "高等数学（集成演示）",
                new BigDecimal("5.0"), 80, "已选教学班用于验证正常阶段立即退选");
        var programming = ensureCourse(courses, "DEMO-CS201", "Java 程序设计（集成演示）",
                new BigDecimal("4.0"), 64, "未选教学班用于验证选课后立即退选");
        String enrolledOffering = ensureOffering(courses, term.termId(), mathematics.courseId(),
                "DEMO-MATH101", "Demo-Math-A", "MONDAY", 1, 2, "教一-101");
        ensureOffering(courses, term.termId(), mathematics.courseId(),
                "DEMO-MATH101", "Demo-Math-B", "TUESDAY", 3, 4, "教一-203");
        ensureOffering(courses, term.termId(), programming.courseId(),
                "DEMO-CS201", "Demo-CS-A", "WEDNESDAY", 5, 6, "计算中心-305");
        seedActiveEnrollment(connections, enrolledOffering, now);
    }

    private static edu.seu.vcampus.common.course.CourseView ensureCourse(
            CourseService courses, String code, String name, BigDecimal credit, int hours,
            String description) {
        return courses.searchCatalog(new CourseCatalogQuery(code, null, 0, 20)).items().stream()
                .filter(item -> code.equals(item.courseCode())).findFirst()
                .orElseGet(() -> courses.createCourse(new CreateCourseCommand(
                        code, name, credit, hours, description, true)));
    }

    private static String ensureOffering(CourseService courses, String termId, String courseId,
                                         String courseCode, String className, String day,
                                         int startPeriod, int endPeriod, String classroom) {
        var existing = courses.searchOfferings(new OfferingSearchQuery(
                termId, courseCode, null, false, 0, 20)).items().stream()
                .filter(item -> className.equals(item.className())).findFirst();
        if (existing.isPresent()) return existing.get().offeringId();
        return courses.createOffering(new CreateOfferingCommand(termId, courseId,
                "demo-teacher", className, 40, "OPEN",
                List.of(new CreateOfferingCommand.ScheduleInput(
                        day, startPeriod, endPeriod, 1, 16, classroom)))).offeringId();
    }

    private static void seedActiveEnrollment(ConnectionProvider connections, String offeringId,
                                             Instant now) throws Exception {
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            try (var query = connection.prepareStatement(
                    "SELECT 1 FROM tblEnrollment WHERE studentId=? AND offeringId=?")) {
                query.setString(1, "demo-student");
                query.setString(2, offeringId);
                try (var rows = query.executeQuery()) {
                    if (rows.next()) {
                        connection.rollback();
                        return;
                    }
                }
            }
            Timestamp timestamp = Timestamp.from(now.minus(Duration.ofHours(2)));
            try (var insert = connection.prepareStatement("""
                    INSERT INTO tblEnrollment
                        (enrollmentId, offeringId, studentId, enrollmentType, enrollmentStatus,
                         enrolledAt, droppedAt, rowVersion, createdAt, updatedAt)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, "demo-enrollment-active");
                insert.setString(2, offeringId);
                insert.setString(3, "demo-student");
                insert.setString(4, "NORMAL");
                insert.setString(5, "ACTIVE");
                insert.setTimestamp(6, timestamp);
                insert.setTimestamp(7, null);
                insert.setLong(8, 0);
                insert.setTimestamp(9, timestamp);
                insert.setTimestamp(10, timestamp);
                insert.executeUpdate();
            }
            try (var update = connection.prepareStatement("""
                    UPDATE tblCourseOffering
                    SET enrolledCount=enrolledCount+1, rowVersion=rowVersion+1, updatedAt=?
                    WHERE offeringId=?
                    """)) {
                update.setTimestamp(1, timestamp);
                update.setString(2, offeringId);
                if (update.executeUpdate() != 1) {
                    throw new IllegalStateException("Demo offering missing: " + offeringId);
                }
            }
            connection.commit();
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
