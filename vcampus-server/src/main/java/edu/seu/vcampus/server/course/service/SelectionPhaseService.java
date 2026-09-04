package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.ChangeSelectionPhaseStatusCommand;
import edu.seu.vcampus.common.course.CreateSelectionPhaseCommand;
import edu.seu.vcampus.common.course.SelectionPhaseView;
import edu.seu.vcampus.common.course.UpdateSelectionPhaseCommand;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.SelectionPhaseAlreadyOpenException;
import edu.seu.vcampus.server.course.domain.SelectionPhaseInvalidStateException;
import edu.seu.vcampus.server.course.domain.TermNotActiveException;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.SelectionPhase;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.util.List;
import java.util.Objects;

/** Administrator lifecycle for immutable-history manual selection phases. */
final class SelectionPhaseService {
    private static final ResourceKey GLOBAL = new ResourceKey("COURSE_SELECTION_PHASE", "GLOBAL");
    private final CourseRepository repository;
    private final ResourceLockManager locks;
    private final TransactionManager transactions;

    SelectionPhaseService(CourseRepository repository, ResourceLockManager locks,
                          TransactionManager transactions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    List<SelectionPhaseView> list() {
        return transactions.inTransaction(connection -> repository.findSelectionPhases(connection)
                .stream().map(SelectionPhaseService::view).toList());
    }

    SelectionPhaseView create(CreateSelectionPhaseCommand command) {
        Objects.requireNonNull(command, "command");
        return locks.withLocks(List.of(GLOBAL), () -> transactions.inTransaction(connection -> {
            repository.requireTerm(connection, command.termId());
            return view(repository.insertSelectionPhase(connection, new SelectionPhase(null,
                    command.termId(), command.phaseType(), command.displayTitle(),
                    "DRAFT", 0, null, null)));
        }));
    }

    SelectionPhaseView update(UpdateSelectionPhaseCommand command) {
        Objects.requireNonNull(command, "command");
        return locks.withLocks(List.of(GLOBAL), () -> transactions.inTransaction(connection -> {
            SelectionPhase current = repository.requireSelectionPhase(connection, command.phaseId());
            if (!"DRAFT".equals(current.phaseStatus())) throw new SelectionPhaseInvalidStateException();
            return view(repository.updateSelectionPhase(connection, new SelectionPhase(
                    current.phaseId(), current.termId(), current.phaseType(), command.displayTitle(),
                    current.phaseStatus(), current.rowVersion(), current.createdAt(), current.updatedAt()),
                    command.expectedVersion()));
        }));
    }

    SelectionPhaseView changeStatus(ChangeSelectionPhaseStatusCommand command) {
        Objects.requireNonNull(command, "command");
        return locks.withLocks(List.of(GLOBAL), () -> transactions.inTransaction(connection -> {
            SelectionPhase current = repository.requireSelectionPhase(connection, command.phaseId());
            if ("OPEN".equals(command.targetStatus())) {
                if (!"DRAFT".equals(current.phaseStatus())) throw new SelectionPhaseInvalidStateException();
                if (!"ACTIVE".equals(repository.requireTerm(connection, current.termId()).termStatus())) {
                    throw new TermNotActiveException();
                }
                if (repository.findOpenSelectionPhase(connection).isPresent()) {
                    throw new SelectionPhaseAlreadyOpenException();
                }
            } else if (!"OPEN".equals(current.phaseStatus())) {
                throw new SelectionPhaseInvalidStateException();
            }
            return view(repository.updateSelectionPhase(connection, new SelectionPhase(
                    current.phaseId(), current.termId(), current.phaseType(), current.displayTitle(),
                    command.targetStatus(), current.rowVersion(), current.createdAt(), current.updatedAt()),
                    command.expectedVersion()));
        }));
    }

    private static SelectionPhaseView view(SelectionPhase phase) {
        return new SelectionPhaseView(phase.phaseId(), phase.termId(), phase.phaseType(),
                phase.displayTitle(), phase.phaseStatus(), phase.rowVersion(),
                phase.createdAt(), phase.updatedAt());
    }
}
