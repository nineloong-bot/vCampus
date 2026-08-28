package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.*;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseManagementServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private ConnectionProvider connections;
    private TransactionManager transactions;
    private CourseRepository repository;
    private CourseService service;

    @BeforeEach void createRealService() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        try (Connection c = DriverManager.getConnection(url)) {
            for (String statement : Files.readString(Path.of("..", "vcampus-database", "schema", "030_course.sql")).split(";")) {
                if (!statement.isBlank()) c.createStatement().execute(statement);
            }
        }
        connections = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(connections);
        repository = new AccessCourseRepository();
        CourseAuthorizationGateway authorization = new CourseAuthorizationGateway() {
            @Override public CourseSessionIdentity requireSession(String token) { return new CourseSessionIdentity("student-user", "STUDENT"); }
            @Override public void requireUserRole(String userId, String role) {
                if (!"teacher-1".equals(userId) || !"TEACHER".equals(role)) throw new IllegalArgumentException("teacher");
            }
        };
        service = new CourseServiceImpl(authorization, ignored -> new StudentEnrollmentEligibility("student-1", "ACTIVE"),
                repository, new StripedResourceLockManager(), transactions, new TermWindowPolicy(),
                new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void persistsListsAndOptimisticallyUpdatesTermsAndCatalog() {
        TermView term = service.createTerm(termCommand());
        CourseView course = service.createCourse(courseCommand("CS101", "程序设计"));

        assertThat(service.listTerms()).extracting(TermView::termId).containsExactly(term.termId());
        assertThat(service.searchCatalog(new CourseCatalogQuery("程序", true, 0, 20)).items())
                .extracting(CourseView::courseId).containsExactly(course.courseId());

        TermView updated = service.updateTerm(new UpdateTermCommand(term.termId(), term.termCode(), "秋季学期",
                term.startDate(), term.endDate(), term.enrollmentStartAt(), term.enrollmentEndAt(),
                term.adjustmentStartAt(), term.adjustmentEndAt(), "ACTIVE", term.rowVersion()));
        assertThat(updated.termName()).isEqualTo("秋季学期");
        assertThat(service.listTerms().getFirst().rowVersion()).isEqualTo(1);
        assertThatThrownBy(() -> service.updateTerm(new UpdateTermCommand(term.termId(), term.termCode(), "过期写入",
                term.startDate(), term.endDate(), term.enrollmentStartAt(), term.enrollmentEndAt(),
                term.adjustmentStartAt(), term.adjustmentEndAt(), "ACTIVE", term.rowVersion())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void offeringAggregateUsesRealCourseLabelsAndRollsBackInvalidReferences() {
        TermView term = service.createTerm(termCommand());
        CourseView course = service.createCourse(courseCommand("CS101", "程序设计"));
        var schedule = new CreateOfferingCommand.ScheduleInput("MONDAY", 1, 2, 1, 16, "教一-101");

        OfferingView created = service.createOffering(new CreateOfferingCommand(term.termId(), course.courseId(),
                "teacher-1", "01班", 30, "OPEN", List.of(schedule)));
        assertThat(created.schedules()).singleElement().satisfies(item -> {
            assertThat(item.courseCode()).isEqualTo("CS101");
            assertThat(item.courseName()).isEqualTo("程序设计");
        });

        assertThatThrownBy(() -> service.updateOffering(new UpdateOfferingCommand(created.offeringId(), term.termId(),
                "missing-course", "teacher-1", "失败更新", 30, "OPEN", created.rowVersion(), List.of(schedule))))
                .isInstanceOf(IllegalStateException.class);
        Offering persisted = transactions.inTransaction(c -> repository.requireOffering(c, created.offeringId()));
        assertThat(persisted.className()).isEqualTo("01班");
        assertThat(persisted.rowVersion()).isZero();
    }

    @Test void exposesServerPhaseAndFilteredPagedAdjustmentAudit() {
        TermView term = service.createTerm(termCommand());
        CourseView course = service.createCourse(courseCommand("CS101", "程序设计"));
        OfferingView offering = service.createOffering(new CreateOfferingCommand(term.termId(), course.courseId(),
                "teacher-1", "01班", 30, "OPEN", List.of()));
        transactions.inTransaction(c -> repository.insertAdjustment(c, new EnrollmentAdjustment(null, "student-1",
                "ADD", null, offering.offeringId(), "FAILED", "COURSE_OFFERING_FULL", NOW)));

        TermPhaseView phase = service.getTermPhase(term.termId());
        assertThat(phase.serverTime()).isEqualTo(NOW);
        assertThat(phase.phase()).isEqualTo("ENROLLMENT");
        assertThat(service.searchAdjustmentAudits(new AdjustmentAuditQuery("student-1", term.termId(), "ADD", "FAILED", 0, 20)).items())
                .singleElement().satisfies(row -> assertThat(row.failureCode()).isEqualTo("COURSE_OFFERING_FULL"));
        assertThat(service.searchAdjustmentAudits(new AdjustmentAuditQuery("other", null, null, null, 0, 20)).total()).isZero();
    }

    private static CreateTermCommand termCommand() {
        return new CreateTermCommand("2026-1", "秋季", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15),
                NOW.minusSeconds(60), NOW.plusSeconds(60), NOW.plusSeconds(120), NOW.plusSeconds(240), "PLANNED");
    }

    private static CreateCourseCommand courseCommand(String code, String name) {
        return new CreateCourseCommand(code, name, BigDecimal.valueOf(3), 48, "课程说明", true);
    }
}
