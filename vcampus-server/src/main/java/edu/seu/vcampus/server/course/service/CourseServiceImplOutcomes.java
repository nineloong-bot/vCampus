package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.ImportCourseOutcomesCommand;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.OutcomeImportInvalidException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.CourseAttempt;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Idempotent course outcome import for the course-service segments. */
abstract class CourseServiceImplOutcomes extends CourseServiceImplEnrollment {

    /**
     * Creates the outcome-import segment.
     * @param authorization the authorization
     * @param students the students
     * @param repository the repository
     * @param curricula the curricula
     * @param locks the locks
     * @param transactions the transactions
     * @param windows the windows
     * @param conflicts the conflicts
     * @param clock the clock
     */
    protected CourseServiceImplOutcomes(CourseAuthorizationGateway authorization,
                             CourseStudentGateway students,
                             CourseRepository repository,
                             edu.seu.vcampus.server.course.repository.CurriculumRepository curricula,
                             ResourceLockManager locks,
                             TransactionManager transactions,
                             TermWindowPolicy windows,
                             ScheduleConflictPolicy conflicts,
                             Clock clock) {
        super(authorization, students, repository, curricula, locks, transactions, windows, conflicts, clock);
    }

    @Override
    public void importCourseOutcomes(ImportCourseOutcomesCommand command) {
        if (command == null || command.outcomes() == null || command.outcomes().isEmpty()) {
            throw new OutcomeImportInvalidException();
        }
        try {
            if (command.outcomes().stream().anyMatch(entry -> !students.existsActiveStudent(entry.studentId()))) {
                throw new OutcomeImportInvalidException();
            }
        } catch (OutcomeImportInvalidException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new OutcomeImportInvalidException(error);
        }
        List<ResourceKey> sourceKeys = command.outcomes().stream()
                .map(entry -> new ResourceKey("COURSE_OUTCOME", entry.sourceReference()))
                .distinct()
                .sorted(Comparator.comparing(ResourceKey::resourceType)
                        .thenComparing(ResourceKey::resourceId))
                .toList();
        locks.withLocks(sourceKeys, () -> {
            try {
                transactions.inTransaction(connection -> {
                    Instant importedAt = clock.instant();
                    for (ImportCourseOutcomesCommand.OutcomeEntry entry : command.outcomes()) {
                        CourseAttempt incoming = new CourseAttempt(UUID.randomUUID().toString(),
                                entry.studentId(), entry.courseId(), entry.termId(),
                                entry.outcome().name(), entry.sourceReference(), importedAt);
                        var existing = repository.findAttemptBySourceReference(
                                connection, entry.sourceReference());
                        if (existing.isPresent()) {
                            requireSameImport(existing.orElseThrow(), incoming);
                        } else {
                            boolean inserted = repository.insertAttemptIfAbsent(connection, incoming);
                            if (!inserted) {
                                CourseAttempt concurrent = repository.findAttemptBySourceReference(
                                                connection, entry.sourceReference())
                                        .orElseThrow(OutcomeImportInvalidException::new);
                                requireSameImport(concurrent, incoming);
                            }
                        }
                    }
                    return null;
                });
            } catch (OutcomeImportInvalidException error) {
                throw error;
            } catch (RuntimeException error) {
                throw new OutcomeImportInvalidException(error);
            }
            return null;
        });
    }

    private static void requireSameImport(CourseAttempt existing, CourseAttempt incoming) {
        if (!existing.studentId().equals(incoming.studentId())
                || !existing.courseId().equals(incoming.courseId())
                || !existing.termId().equals(incoming.termId())
                || !existing.outcome().equals(incoming.outcome())) {
            throw new OutcomeImportInvalidException();
        }
    }
}
