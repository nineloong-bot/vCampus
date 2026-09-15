package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.AdjustmentAuditQuery;
import edu.seu.vcampus.common.course.AdjustmentAuditView;
import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.course.CourseView;
import edu.seu.vcampus.common.course.CreateCourseCommand;
import edu.seu.vcampus.common.course.UpdateCourseCommand;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Connection;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Course catalog and adjustment audit queries for the course-service segments. */
abstract class CourseServiceImplCatalog extends CourseServiceImplTerms {

    /**
     * Creates the catalog segment.
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
    protected CourseServiceImplCatalog(CourseAuthorizationGateway authorization,
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

    @Override public PageResult<CourseView> searchCatalog(CourseCatalogQuery q){return transactions.inTransaction(c->{var all=repository.findCourses(c).stream().filter(x->q.keyword()==null||x.courseCode().contains(q.keyword())||x.courseName().contains(q.keyword())).filter(x->!Boolean.TRUE.equals(q.activeOnly())||x.active()).map(CourseServiceImpl::toView).toList();int from=Math.min(all.size(),q.page()*q.pageSize());return new PageResult<>(all.subList(from,Math.min(all.size(),from+q.pageSize())),q.page(),q.pageSize(),all.size());});}
    @Override public CourseView createCourse(CreateCourseCommand command){Objects.requireNonNull(command);return locks.withLocks(List.of(new ResourceKey("COURSE_CODE",command.courseCode())),()->transactions.inTransaction(c->toView(repository.insertCourse(c,new edu.seu.vcampus.server.course.repository.Course(null,command.courseCode(),command.courseName(),command.credit(),command.totalHours(),command.description(),command.active(),0,null,null)))));}
    @Override public CourseView updateCourse(UpdateCourseCommand command){Objects.requireNonNull(command);return locks.withLocks(List.of(new ResourceKey("COURSE",command.courseId())),()->transactions.inTransaction(c->{var old=repository.requireCourse(c,command.courseId());return toView(repository.updateCourse(c,new edu.seu.vcampus.server.course.repository.Course(old.courseId(),command.courseCode(),command.courseName(),command.credit(),command.totalHours(),command.description(),command.active(),old.rowVersion(),old.createdAt(),old.updatedAt()),command.expectedVersion()));}));}
    @Override public PageResult<AdjustmentAuditView> searchAdjustmentAudits(AdjustmentAuditQuery q){Objects.requireNonNull(q,"query");return transactions.inTransaction(c->{var filtered=repository.findAdjustments(c).stream().filter(x->blank(q.studentId())||x.studentId().equals(q.studentId())).filter(x->blank(q.adjustmentType())||x.adjustmentType().equals(q.adjustmentType())).filter(x->blank(q.operationResult())||x.operationResult().equals(q.operationResult())).filter(x->blank(q.termId())||adjustmentBelongsToTerm(c,x,q.termId())).map(CourseServiceImpl::toView).toList();int from=Math.min(filtered.size(),Math.multiplyExact(q.page(),q.pageSize()));return new PageResult<>(filtered.subList(from,Math.min(filtered.size(),from+q.pageSize())),q.page(),q.pageSize(),filtered.size());});}

    static CourseView toView(edu.seu.vcampus.server.course.repository.Course c){return new CourseView(c.courseId(),c.courseCode(),c.courseName(),c.credit(),c.totalHours(),c.description(),c.active(),c.rowVersion(),c.createdAt(),c.updatedAt());}
    static AdjustmentAuditView toView(edu.seu.vcampus.server.course.repository.EnrollmentAdjustment x){return new AdjustmentAuditView(x.adjustmentId(),x.studentId(),x.adjustmentType(),x.sourceOfferingId(),x.targetOfferingId(),x.operationResult(),x.failureCode(),x.operatedAt());}
    private boolean adjustmentBelongsToTerm(Connection c,edu.seu.vcampus.server.course.repository.EnrollmentAdjustment x,String termId){String offeringId=x.targetOfferingId()!=null?x.targetOfferingId():x.sourceOfferingId();return offeringId!=null&&repository.requireOffering(c,offeringId).termId().equals(termId);}
}
