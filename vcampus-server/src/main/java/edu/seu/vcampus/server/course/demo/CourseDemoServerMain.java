package edu.seu.vcampus.server.course.demo;

import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.course.CreateCourseCommand;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.CreateTermCommand;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.TermView;
import edu.seu.vcampus.common.course.UpdateTermCommand;
import edu.seu.vcampus.common.course.SelectionPhaseView;
import edu.seu.vcampus.common.course.CreateSelectionPhaseCommand;
import edu.seu.vcampus.common.course.ChangeSelectionPhaseStatusCommand;
import edu.seu.vcampus.common.course.AcademicSeason;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.composition.CourseComposition;
import edu.seu.vcampus.server.course.composition.CourseSchemaInitializer;
import edu.seu.vcampus.server.course.service.CourseAuthorizationGateway;
import edu.seu.vcampus.server.course.service.CourseService;
import edu.seu.vcampus.server.course.service.CourseSessionIdentity;
import edu.seu.vcampus.server.course.service.CourseStudentGateway;
import edu.seu.vcampus.server.course.service.StudentEnrollmentEligibility;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.routing.MessageRouter;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Standalone real-socket course demo; it never replaces the application's production bootstrap. */
public final class CourseDemoServerMain {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private CourseDemoServerMain() { }

    public static void main(String[] args) {
        Path config = Path.of(args.length == 0 ? "config/course-demo.properties" : args[0])
                .toAbsolutePath().normalize();
        try {
            Properties properties = load(config);
            Path root = config.getParent() == null ? Path.of(".").toAbsolutePath() : config.getParent().getParent();
            Path database = root.resolve(required(properties, "database.path")).normalize();
            Path schema = root.resolve(required(properties, "schema.path")).normalize();
            int port = Integer.parseInt(required(properties, "server.port"));
            DemoRuntime runtime = prepare(database, schema, properties.getProperty("demo.phase", "ENROLLMENT"));
            SocketServer server = new SocketServer(port, 8, 100, runtime.router());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(server), "course-demo-shutdown"));
            System.out.println("课程 Demo 服务已启动：http/socket 端口 " + server.localPort());
            System.out.println("学生令牌 student-demo-1 / student-demo-2；管理员令牌 admin-demo");
            System.out.println("当前阶段 " + properties.getProperty("demo.phase", "ENROLLMENT").toUpperCase());
            server.serve();
        } catch (Exception failure) {
            System.err.println("课程 Demo 服务启动失败：" + failure.getMessage());
            failure.printStackTrace(System.err);
            System.exit(2);
        }
    }

    public static DemoRuntime prepare(Path databasePath, Path schemaPath, String phase) throws Exception {
        Files.createDirectories(databasePath.toAbsolutePath().normalize().getParent());
        String url = "jdbc:ucanaccess://" + databasePath.toAbsolutePath().normalize()
                + ";newDatabaseVersion=V2010";
        ConnectionProvider connections = () -> DriverManager.getConnection(url);
        installCommonSchema(connections, schemaPath.resolveSibling("001_common.sql"));
        new CourseSchemaInitializer(schemaPath).initialize(connections);

        Map<String, CourseSessionIdentity> sessions = Map.of(
                "student-demo-1", new CourseSessionIdentity("student-user-1", "STUDENT"),
                "student-demo-2", new CourseSessionIdentity("student-user-2", "STUDENT"),
                "admin-demo", new CourseSessionIdentity("admin-user", "ADMIN"));
        CourseAuthorizationGateway authorization = new CourseAuthorizationGateway() {
            @Override public CourseSessionIdentity requireSession(String token) { return sessions.get(token); }
            @Override public void requireUserRole(String userId, String role) {
                if (!"teacher-user".equals(userId) || !"TEACHER".equals(role)) {
                    throw new edu.seu.vcampus.server.course.domain.CourseForbiddenException();
                }
            }
        };
        CourseStudentGateway students = CourseStudentGateway.of(userId -> switch (userId) {
                    case "student-user-1" -> new StudentEnrollmentEligibility(
                            "student-demo-1", "ACTIVE", CourseDemoDataset.MAJOR, CourseDemoDataset.COHORT);
                    case "student-user-2" -> new StudentEnrollmentEligibility(
                            "student-demo-2", "ACTIVE", CourseDemoDataset.MAJOR, CourseDemoDataset.COHORT);
                    default -> null;
                }, studentId -> "student-demo-1".equals(studentId) || "student-demo-2".equals(studentId));
        MessageRouter router = new MessageRouter(Map.of());
        CourseComposition courses = CourseComposition.create(connections, authorization, students,
                Clock.systemUTC(), new StripedResourceLockManager());
        courses.register(router);
        seed(connections, courses.service(), phase);
        return new DemoRuntime(router, courses.service(), connections);
    }

    private static void installCommonSchema(ConnectionProvider connections, Path commonSchema) throws Exception {
        try (var connection = connections.open()) {
            boolean installed = false;
            try (ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
                while (tables.next()) {
                    if ("tblRequestDedup".equalsIgnoreCase(tables.getString("TABLE_NAME"))) { installed = true; break; }
                }
            }
            if (installed) return;
            for (String sql : Files.readString(commonSchema).split(";")) {
                if (sql.isBlank()) continue;
                try (Statement statement = connection.createStatement()) { statement.execute(sql); }
            }
        }
    }

    private static void seed(ConnectionProvider connections, CourseService service, String requestedPhase) {
        Instant now = Instant.now();
        boolean adjustment = "ADJUSTMENT".equalsIgnoreCase(requestedPhase);
        Instant enrollmentStart = adjustment ? now.minus(Duration.ofDays(4)) : now.minus(Duration.ofDays(1));
        Instant enrollmentEnd = adjustment ? now.minus(Duration.ofDays(3)) : now.plus(Duration.ofDays(1));
        Instant adjustmentStart = adjustment ? now.minus(Duration.ofDays(1)) : now.plus(Duration.ofDays(2));
        Instant adjustmentEnd = adjustment ? now.plus(Duration.ofDays(1)) : now.plus(Duration.ofDays(4));
        LocalDate today = LocalDate.now(CAMPUS_ZONE);
        List<TermView> terms = service.listTerms();
        TermView term;
        if (terms.isEmpty()) {
            term = service.createTerm(new CreateTermCommand("DEMO-TERM", "课程模块演示学期",
                    today.minusMonths(1), today.plusMonths(5), today.getYear(),
                    AcademicSeason.fromStartMonth(today.getMonthValue()), enrollmentStart, enrollmentEnd,
                    adjustmentStart, adjustmentEnd, "ACTIVE"));
        } else {
            TermView old = terms.getFirst();
            term = service.updateTerm(new UpdateTermCommand(old.termId(), old.termCode(), old.termName(),
                    today.minusMonths(1), today.plusMonths(5), today.getYear(),
                    AcademicSeason.fromStartMonth(today.getMonthValue()), enrollmentStart, enrollmentEnd,
                    adjustmentStart, adjustmentEnd, "ACTIVE", old.rowVersion()));
        }
        if (service.listSelectionPhases().stream().noneMatch(p -> "OPEN".equals(p.phaseStatus()))) {
            String phaseType = adjustment ? "ADJUSTMENT" : "ENROLLMENT";
            String displayTitle = term.termName() + (adjustment ? "退改补选课" : "选课");
            SelectionPhaseView draft = service.createSelectionPhase(
                    new CreateSelectionPhaseCommand(term.termId(), phaseType, displayTitle));
            service.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(
                    draft.phaseId(), "OPEN", draft.rowVersion()));
        }

        CourseDemoDataset.install(connections, service, term);
    }

    private static Properties load(Path path) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少配置项 " + key);
        return value.trim();
    }

    private static void shutdown(SocketServer server) {
        try { server.stopAccepting(); server.awaitRequests(Duration.ofSeconds(10)); server.close(); }
        catch (Exception ignored) { }
    }

    public record DemoRuntime(MessageRouter router, CourseService service, ConnectionProvider connections) { }
}
