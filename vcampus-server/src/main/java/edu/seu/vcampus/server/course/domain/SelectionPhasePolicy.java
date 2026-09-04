package edu.seu.vcampus.server.course.domain;

import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.SelectionPhase;

import java.sql.Connection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Authoritative manual-phase policy for every student course mutation. */
public final class SelectionPhasePolicy {
    private final CourseRepository repository;

    public SelectionPhasePolicy(CourseRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Optional<SelectionPhase> current(Connection connection) {
        Optional<SelectionPhase> current = repository.findOpenSelectionPhase(connection);
        current.ifPresent(this::requireKnownOpenPhase);
        return current;
    }

    public SelectionPhase requireEnrollmentOpen(Connection connection, String termId) {
        SelectionPhase phase = requireCurrentActiveTerm(connection, termId, new EnrollmentClosedException());
        if (!"OPEN".equals(phase.phaseStatus()) || !"ENROLLMENT".equals(phase.phaseType())) throw new EnrollmentClosedException();
        return phase;
    }

    public SelectionPhase requireAdjustmentOpen(Connection connection, String termId) {
        SelectionPhase phase = requireCurrentActiveTerm(connection, termId, new AdjustmentClosedException());
        if (!"OPEN".equals(phase.phaseStatus()) || !"ADJUSTMENT".equals(phase.phaseType())) throw new AdjustmentClosedException();
        return phase;
    }

    public SelectionPhase requireDropOpen(Connection connection, String termId) {
        SelectionPhase phase = requireCurrentActiveTerm(connection, termId, new DropClosedException());
        if (!"OPEN".equals(phase.phaseStatus())) throw new DropClosedException();
        return phase;
    }

    private SelectionPhase requireCurrentActiveTerm(Connection connection, String termId,
                                                    CourseRuleException closed) {
        SelectionPhase phase = repository.findOpenSelectionPhase(connection).orElseThrow(() -> closed);
        requireKnownOpenPhase(phase);
        if (!termId.equals(phase.termId())
                || !"ACTIVE".equals(repository.requireTerm(connection, termId).termStatus())) {
            throw closed;
        }
        return phase;
    }

    private void requireKnownOpenPhase(SelectionPhase phase) {
        if (!Set.of("PREVIEW", "OPEN").contains(phase.phaseStatus())
                || !Set.of("ENROLLMENT", "ADJUSTMENT").contains(phase.phaseType())) {
            throw new IllegalStateException("Invalid open selection phase: " + phase.phaseId());
        }
    }
}
