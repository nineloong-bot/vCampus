package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.CreateTermCommand;
import edu.seu.vcampus.common.course.CreateSelectionPhaseCommand;
import edu.seu.vcampus.common.course.ChangeSelectionPhaseStatusCommand;
import edu.seu.vcampus.common.course.SelectionPhaseView;
import edu.seu.vcampus.common.course.TermPhaseView;
import edu.seu.vcampus.common.course.TermView;
import edu.seu.vcampus.common.course.UpdateSelectionPhaseCommand;
import edu.seu.vcampus.common.course.UpdateTermCommand;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Term and selection-phase management for the course-service segments. */
abstract class CourseServiceImplTerms extends CourseServiceImplBase {

    /**
     * Creates the term-management segment.
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
    protected CourseServiceImplTerms(CourseAuthorizationGateway authorization,
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

    @Override public List<TermView> listTerms(){return transactions.inTransaction(c->repository.findTerms(c).stream().map(CourseServiceImpl::toView).toList());}
    @Override public TermView getCurrentTerm(){return transactions.inTransaction(c->toView(currentTerm(c)));}
    @Override public TermView createTerm(CreateTermCommand x){return locks.withLocks(List.of(new ResourceKey("TERM_CODE",x.termCode())),()->transactions.inTransaction(c->toView(repository.insertTerm(c,new edu.seu.vcampus.server.course.repository.Term(null,x.termCode(),x.termName(),x.startDate(),x.endDate(),x.academicYearStart(),x.season(),x.enrollmentStartAt(),x.enrollmentEndAt(),x.adjustmentStartAt(),x.adjustmentEndAt(),x.termStatus(),0,null,null)))));}
    @Override public TermView updateTerm(UpdateTermCommand x){return locks.withLocks(List.of(new ResourceKey("TERM",x.termId())),()->transactions.inTransaction(c->{var old=repository.requireTerm(c,x.termId());return toView(repository.updateTerm(c,new edu.seu.vcampus.server.course.repository.Term(x.termId(),x.termCode(),x.termName(),x.startDate(),x.endDate(),x.academicYearStart(),x.season(),x.enrollmentStartAt(),x.enrollmentEndAt(),x.adjustmentStartAt(),x.adjustmentEndAt(),x.termStatus(),old.rowVersion(),old.createdAt(),old.updatedAt()),x.expectedVersion()));}));}
    @Override public List<SelectionPhaseView> listSelectionPhases(){return selectionPhases.list();}
    @Override public SelectionPhaseView createSelectionPhase(CreateSelectionPhaseCommand x){return selectionPhases.create(x);}
    @Override public SelectionPhaseView updateSelectionPhase(UpdateSelectionPhaseCommand x){return selectionPhases.update(x);}
    @Override public SelectionPhaseView changeSelectionPhaseStatus(ChangeSelectionPhaseStatusCommand x){return selectionPhases.changeStatus(x);}
    @Override public TermPhaseView getTermPhase(String id){return transactions.inTransaction(c->{var t=repository.requireTerm(c,id);var now=clock.instant();String p="CLOSED";if(!"CLOSED".equals(t.termStatus())){if(!now.isBefore(t.enrollmentStartAt())&&now.isBefore(t.enrollmentEndAt()))p="ENROLLMENT";else if(!now.isBefore(t.adjustmentStartAt())&&now.isBefore(t.adjustmentEndAt()))p="ADJUSTMENT";else p="READ_ONLY";}return new TermPhaseView(id,t.termStatus(),p,now,t.enrollmentStartAt(),t.enrollmentEndAt(),t.adjustmentStartAt(),t.adjustmentEndAt());});}
    static TermView toView(edu.seu.vcampus.server.course.repository.Term t){return new TermView(t.termId(),t.termCode(),t.termName(),t.startDate(),t.endDate(),t.academicYearStart(),t.season(),t.enrollmentStartAt(),t.enrollmentEndAt(),t.adjustmentStartAt(),t.adjustmentEndAt(),t.termStatus(),t.rowVersion(),t.createdAt(),t.updatedAt());}

    edu.seu.vcampus.server.course.repository.Term currentTerm(Connection connection) {
        List<edu.seu.vcampus.server.course.repository.Term> terms = repository.findTerms(connection);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        return terms.stream().filter(term -> "ACTIVE".equals(term.termStatus())).findFirst()
                .or(() -> terms.stream().filter(term -> !"CLOSED".equals(term.termStatus()))
                        .filter(term -> isOperationalNow(term, now)
                                || (!today.isBefore(term.startDate()) && !today.isAfter(term.endDate())))
                        .findFirst())
                .or(() -> terms.stream().filter(term -> "PLANNED".equals(term.termStatus())).findFirst())
                .orElseThrow(() -> new IllegalStateException("No current course term is configured"));
    }

    private static boolean isOperationalNow(edu.seu.vcampus.server.course.repository.Term term, Instant now) {
        return (!now.isBefore(term.enrollmentStartAt()) && now.isBefore(term.enrollmentEndAt()))
                || (!now.isBefore(term.adjustmentStartAt()) && now.isBefore(term.adjustmentEndAt()));
    }
}
