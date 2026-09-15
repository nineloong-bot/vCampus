package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.OfferingHasEnrollmentsException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.course.repository.Schedule;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Connection;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Offering management and availability queries for the course-service segments. */
abstract class CourseServiceImplOfferings extends CourseServiceImplCatalog {

    /**
     * Creates the offering segment.
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
    protected CourseServiceImplOfferings(CourseAuthorizationGateway authorization,
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

    @Override public OfferingView createOffering(CreateOfferingCommand command){Objects.requireNonNull(command);return locks.withLocks(List.of(new ResourceKey("OFFERING_CREATE",command.termId()+":"+command.courseId()+":"+command.className())),()->{authorization.requireUserRole(command.teacherUserId(),"TEACHER");return transactions.inTransaction(c->{repository.requireTerm(c,command.termId());var course=repository.requireCourse(c,command.courseId());var value=new Offering(null,command.termId(),command.courseId(),command.teacherUserId(),command.className(),command.capacity(),0,command.offeringStatus(),0,null,null);var saved=repository.insertOffering(c,value,toSchedules(null,command.schedules()));repository.saveRetakeCapacity(c,saved.offeringId(),command.retakeCapacity());return toView(saved,repository.findSchedules(c,saved.offeringId()),course,repository.findRetakeQuota(c,saved.offeringId()));});});}
    @Override public OfferingView updateOffering(UpdateOfferingCommand command){Objects.requireNonNull(command);return locks.withLocks(List.of(new ResourceKey("OFFERING",command.offeringId())),()->{authorization.requireUserRole(command.teacherUserId(),"TEACHER");return transactions.inTransaction(c->{var old=repository.requireOffering(c,command.offeringId());repository.requireTerm(c,command.termId());var course=repository.requireCourse(c,command.courseId());var schedules=toSchedules(command.offeringId(),command.schedules());if(repository.existsEnrollmentForOffering(c,old.offeringId())&&structuralOfferingChange(c,old,command,schedules))throw new OfferingHasEnrollmentsException();if(command.capacity()<old.enrolledCount())throw new IllegalArgumentException("capacity below enrolled count");var quota=repository.findRetakeQuota(c,old.offeringId());if(command.retakeCapacity()<quota.enrolledCount())throw new IllegalArgumentException("retake capacity below enrolled count");var value=new Offering(old.offeringId(),command.termId(),command.courseId(),command.teacherUserId(),command.className(),command.capacity(),old.enrolledCount(),command.offeringStatus(),old.rowVersion(),old.createdAt(),old.updatedAt());var saved=repository.updateOffering(c,value,command.expectedVersion(),schedules);var updatedQuota=repository.saveRetakeCapacity(c,saved.offeringId(),command.retakeCapacity());return toView(saved,repository.findSchedules(c,saved.offeringId()),course,updatedQuota);});});}
    @Override public EnrollmentView adminEnrollStudent(AdminEnrollStudentCommand command){return adminEnrollments.enroll(command);}
    @Override public PageResult<OfferingSummary> searchOfferings(OfferingSearchQuery query){Objects.requireNonNull(query);DayOfWeek day=query.dayOfWeek()==null||query.dayOfWeek().isBlank()?null:DayOfWeek.valueOf(query.dayOfWeek().toUpperCase());return transactions.inTransaction(c->{var page=repository.searchOfferings(c,new edu.seu.vcampus.server.course.repository.OfferingSearchCriteria(query.termId(),query.keyword(),day,Boolean.TRUE.equals(query.availableOnly()),query.page(),query.pageSize()));List<OfferingSummary> items=new ArrayList<>();for(var o:page.items()){var course=repository.requireCourse(c,o.courseId());var quota=repository.findRetakeQuota(c,o.offeringId());items.add(new OfferingSummary(o.offeringId(),o.termId(),o.courseId(),course.courseCode(),course.courseName(),o.teacherUserId(),o.className(),o.capacity(),o.enrolledCount(),quota.capacity(),quota.enrolledCount(),o.offeringStatus(),o.rowVersion(),toScheduleItems(repository.findSchedules(c,o.offeringId()),o,course)));}return new PageResult<>(items,query.page(),query.pageSize(),page.total());});}

    boolean isFull(Connection connection, Offering offering, boolean retake) {
        if (!retake) return offering.enrolledCount() >= offering.capacity();
        var quota = repository.findRetakeQuota(connection, offering.offeringId());
        return quota.enrolledCount() >= quota.capacity();
    }

    OfferingSummary summary(Connection c,Offering o,edu.seu.vcampus.server.course.repository.Course course){var quota=repository.findRetakeQuota(c,o.offeringId());return new OfferingSummary(o.offeringId(),o.termId(),o.courseId(),course.courseCode(),course.courseName(),o.teacherUserId(),o.className(),o.capacity(),o.enrolledCount(),quota.capacity(),quota.enrolledCount(),o.offeringStatus(),o.rowVersion(),toScheduleItems(repository.findSchedules(c,o.offeringId()),o,course));}
    boolean hasConflict(Connection c,List<Enrollment> active,Offering candidate){for(var enrollment:active){for(var selected:repository.findSchedules(c,enrollment.offeringId()))for(var target:repository.findSchedules(c,candidate.offeringId()))if(conflicts.conflicts(selected,target))return true;}return false;}

    private static List<Schedule> toSchedules(String offeringId,List<CreateOfferingCommand.ScheduleInput> values){return values.stream().map(s->new Schedule(null,offeringId,DayOfWeek.valueOf(s.dayOfWeek().toUpperCase()),s.startPeriod(),s.endPeriod(),s.startWeek(),s.endWeek(),s.classroom())).toList();}
    private boolean structuralOfferingChange(Connection connection, Offering old,
                                             UpdateOfferingCommand command, List<Schedule> schedules) {
        if (!old.termId().equals(command.termId()) || !old.courseId().equals(command.courseId())) return true;
        List<ScheduleShape> before = scheduleShapes(repository.findSchedules(connection, old.offeringId()));
        List<ScheduleShape> after = scheduleShapes(schedules);
        return !before.equals(after);
    }
    private static List<ScheduleShape> scheduleShapes(List<Schedule> schedules) {
        return schedules.stream().map(ScheduleShape::from)
                .sorted(Comparator.comparing(ScheduleShape::dayOfWeek)
                        .thenComparingInt(ScheduleShape::startPeriod)
                        .thenComparingInt(ScheduleShape::endPeriod)
                        .thenComparingInt(ScheduleShape::startWeek)
                        .thenComparingInt(ScheduleShape::endWeek)
                        .thenComparing(ScheduleShape::classroom))
                .toList();
    }
    /** Provides schedule shape behavior. */
    private record ScheduleShape(DayOfWeek dayOfWeek, int startPeriod, int endPeriod,
                                 int startWeek, int endWeek, String classroom) {
        private static ScheduleShape from(Schedule schedule) {
            return new ScheduleShape(schedule.dayOfWeek(), schedule.startPeriod(), schedule.endPeriod(),
                    schedule.startWeek(), schedule.endWeek(), schedule.classroom());
        }
    }
    static List<ScheduleItem> toScheduleItems(List<Schedule> values,Offering o,edu.seu.vcampus.server.course.repository.Course c){return values.stream().map(s->new ScheduleItem(s.scheduleId(),o.offeringId(),c.courseCode(),c.courseName(),o.className(),o.teacherUserId(),s.dayOfWeek().name(),s.startPeriod(),s.endPeriod(),s.startWeek(),s.endWeek(),s.classroom())).toList();}
    static OfferingView toView(Offering o,List<Schedule> s,edu.seu.vcampus.server.course.repository.Course course,edu.seu.vcampus.server.course.repository.RetakeQuota quota){return new OfferingView(o.offeringId(),o.termId(),o.courseId(),o.teacherUserId(),o.className(),o.capacity(),o.enrolledCount(),quota.capacity(),quota.enrolledCount(),o.offeringStatus(),o.rowVersion(),o.createdAt(),o.updatedAt(),toScheduleItems(s,o,course));}
}
