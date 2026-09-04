package edu.seu.vcampus.server.course.domain;

import edu.seu.vcampus.server.course.repository.AccessCourseRepository;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.SelectionPhase;
import edu.seu.vcampus.server.course.repository.Term;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelectionPhasePolicyTest {
    private Connection connection;
    private CourseRepository repository;
    private SelectionPhasePolicy policy;

    @BeforeEach void setUp() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        connection = DriverManager.getConnection("jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
        for (String sql : Files.readString(Path.of("..", "vcampus-database", "schema", "030_course.sql")).split(";")) {
            if (!sql.isBlank()) connection.createStatement().execute(sql);
        }
        repository = new AccessCourseRepository();
        repository.insertTerm(connection, new Term("term-1", "2026-1", "秋季", LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 1, 15), Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-10T00:00:00Z"),
                "ACTIVE", 0, null, null));
        policy = new SelectionPhasePolicy(repository);
    }

    @Test void enrollmentPhasePermitsEnrollmentAndDropButNotAdjustment() {
        open("ENROLLMENT");

        assertThat(policy.requireEnrollmentOpen(connection, "term-1").phaseType()).isEqualTo("ENROLLMENT");
        assertThat(policy.requireDropOpen(connection, "term-1").phaseType()).isEqualTo("ENROLLMENT");
        assertThatThrownBy(() -> policy.requireAdjustmentOpen(connection, "term-1"))
                .isInstanceOf(AdjustmentClosedException.class);
    }

    @Test void closedTermRejectsEvenWithOpenPhase() {
        open("ADJUSTMENT");
        Term active = repository.requireTerm(connection, "term-1");
        repository.updateTerm(connection, new Term(active.termId(), active.termCode(), active.termName(),
                active.startDate(), active.endDate(), active.enrollmentStartAt(), active.enrollmentEndAt(),
                active.adjustmentStartAt(), active.adjustmentEndAt(), "CLOSED", active.rowVersion(),
                active.createdAt(), active.updatedAt()), active.rowVersion());

        assertThatThrownBy(() -> policy.requireDropOpen(connection, "term-1"))
                .isInstanceOf(DropClosedException.class);
    }

    private void open(String type) {
        SelectionPhase draft = repository.insertSelectionPhase(connection, new SelectionPhase(
                null, "term-1", type, "阶段", "DRAFT", 0, null, null));
        repository.updateSelectionPhase(connection, new SelectionPhase(draft.phaseId(), draft.termId(),
                draft.phaseType(), draft.displayTitle(), "OPEN", draft.rowVersion(), draft.createdAt(),
                draft.updatedAt()), draft.rowVersion());
    }
}
