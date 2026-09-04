package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.CourseOutcome;
import edu.seu.vcampus.common.course.ImportCourseOutcomesCommand;
import edu.seu.vcampus.common.course.RetakeCommand;
import edu.seu.vcampus.common.course.RetakeEligibility;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.CourseRuleException;
import edu.seu.vcampus.server.course.domain.DuplicateEnrollmentException;
import edu.seu.vcampus.server.course.domain.EnrollmentClosedException;
import edu.seu.vcampus.server.course.domain.OfferingFullException;
import edu.seu.vcampus.server.course.domain.OfferingHasEnrollmentsException;
import edu.seu.vcampus.server.course.domain.OutcomeImportInvalidException;
import edu.seu.vcampus.server.course.domain.RetakeNotEligibleException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.domain.SelectionPhasePolicy;
import edu.seu.vcampus.server.course.domain.CourseAlreadyPassedException;
import edu.seu.vcampus.server.course.domain.RetakeRequiredException;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.CourseAttempt;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.course.repository.Schedule;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.paging.PageResult;

/** Concurrency-safe implementation of course enrollment application rules. */
public final class CourseServiceImpl implements CourseService, CourseQueryPort {
    private final CourseAuthorizationGateway authorization;
    private final CourseStudentGateway students;
    private final CourseRepository repository;
    private final ResourceLockManager locks;
    private final TransactionManager transactions;
    private final TermWindowPolicy windows;
    private final ScheduleConflictPolicy conflicts;
    private final Clock clock;
    private final EnrollmentAdjustmentService adjustments;
    private final SelectionPhaseService selectionPhases;
    private final SelectionPhasePolicy phasePolicy;

    /** Creates an enrollment service from course-owned infrastructure and gateway boundaries. */
    public CourseServiceImpl(CourseAuthorizationGateway authorization,
                             CourseStudentGateway students,
                             CourseRepository repository,
                             ResourceLockManager locks,
                             TransactionManager transactions,
                             TermWindowPolicy windows,
                             ScheduleConflictPolicy conflicts,
                             Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.students = Objects.requireNonNull(students, "students");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.windows = Objects.requireNonNull(windows, "windows");
        this.conflicts = Objects.requireNonNull(conflicts, "conflicts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.phasePolicy = new SelectionPhasePolicy(repository);
        this.adjustments = new EnrollmentAdjustmentService(authorization, students, repository, locks,
                transactions, phasePolicy, conflicts, clock);
        this.selectionPhases = new SelectionPhaseService(repository, locks, transactions);
    }
    @Override public List<TermView> listTerms(){return transactions.inTransaction(c->repository.findTerms(c).stream().map(CourseServiceImpl::toView).toList());}
    @Override public TermView getCurrentTerm(){return transactions.inTransaction(c->toView(currentTerm(c)));}
    @Override public TermView createTerm(CreateTermCommand x){return locks.withLocks(List.of(new ResourceKey("TERM_CODE",x.termCode())),()->transactions.inTransaction(c->toView(repository.insertTerm(c,new edu.seu.vcampus.server.course.repository.Term(null,x.termCode(),x.termName(),x.startDate(),x.endDate(),x.enrollmentStartAt(),x.enrollmentEndAt(),x.adjustmentStartAt(),x.adjustmentEndAt(),x.termStatus(),0,null,null)))));}
    @Override public TermView updateTerm(UpdateTermCommand x){return locks.withLocks(List.of(new ResourceKey("TERM",x.termId())),()->transactions.inTransaction(c->{var old=repository.requireTerm(c,x.termId());return toView(repository.updateTerm(c,new edu.seu.vcampus.server.course.repository.Term(x.termId(),x.termCode(),x.termName(),x.startDate(),x.endDate(),x.enrollmentStartAt(),x.enrollmentEndAt(),x.adjustmentStartAt(),x.adjustmentEndAt(),x.termStatus(),old.rowVersion(),old.createdAt(),old.updatedAt()),x.expectedVersion()));}));}
    @Override public List<SelectionPhaseView> listSelectionPhases(){return selectionPhases.list();}
    @Override public SelectionPhaseView createSelectionPhase(CreateSelectionPhaseCommand x){return selectionPhases.create(x);}
    @Override public SelectionPhaseView updateSelectionPhase(UpdateSelectionPhaseCommand x){return selectionPhases.update(x);}
    @Override public SelectionPhaseView changeSelectionPhaseStatus(ChangeSelectionPhaseStatusCommand x){return selectionPhases.changeStatus(x);}
    @Override public PageResult<CourseView> searchCatalog(CourseCatalogQuery q){return transactions.inTransaction(c->{var all=repository.findCourses(c).stream().filter(x->q.keyword()==null||x.courseCode().contains(q.keyword())||x.courseName().contains(q.keyword())).filter(x->!Boolean.TRUE.equals(q.activeOnly())||x.active()).map(CourseServiceImpl::toView).toList();int from=Math.min(all.size(),q.page()*q.pageSize());return new PageResult<>(all.subList(from,Math.min(all.size(),from+q.pageSize())),q.page(),q.pageSize(),all.size());});}
    @Override public PageResult<AdjustmentAuditView> searchAdjustmentAudits(AdjustmentAuditQuery q){Objects.requireNonNull(q,"query");return transactions.inTransaction(c->{var filtered=repository.findAdjustments(c).stream().filter(x->blank(q.studentId())||x.studentId().equals(q.studentId())).filter(x->blank(q.adjustmentType())||x.adjustmentType().equals(q.adjustmentType())).filter(x->blank(q.operationResult())||x.operationResult().equals(q.operationResult())).filter(x->blank(q.termId())||adjustmentBelongsToTerm(c,x,q.termId())).map(CourseServiceImpl::toView).toList();int from=Math.min(filtered.size(),Math.multiplyExact(q.page(),q.pageSize()));return new PageResult<>(filtered.subList(from,Math.min(filtered.size(),from+q.pageSize())),q.page(),q.pageSize(),filtered.size());});}
    @Override public TermPhaseView getTermPhase(String id){return transactions.inTransaction(c->{var t=repository.requireTerm(c,id);var now=clock.instant();String p="CLOSED";if(!"CLOSED".equals(t.termStatus())){if(!now.isBefore(t.enrollmentStartAt())&&now.isBefore(t.enrollmentEndAt()))p="ENROLLMENT";else if(!now.isBefore(t.adjustmentStartAt())&&now.isBefore(t.adjustmentEndAt()))p="ADJUSTMENT";else p="READ_ONLY";}return new TermPhaseView(id,t.termStatus(),p,now,t.enrollmentStartAt(),t.enrollmentEndAt(),t.adjustmentStartAt(),t.adjustmentEndAt());});}
    private static TermView toView(edu.seu.vcampus.server.course.repository.Term t){return new TermView(t.termId(),t.termCode(),t.termName(),t.startDate(),t.endDate(),t.enrollmentStartAt(),t.enrollmentEndAt(),t.adjustmentStartAt(),t.adjustmentEndAt(),t.termStatus(),t.rowVersion(),t.createdAt(),t.updatedAt());}

    @Override public CourseView createCourse(CreateCourseCommand command){Objects.requireNonNull(command);return locks.withLocks(List.of(new ResourceKey("COURSE_CODE",command.courseCode())),()->transactions.inTransaction(c->toView(repository.insertCourse(c,new edu.seu.vcampus.server.course.repository.Course(null,command.courseCode(),command.courseName(),command.credit(),command.totalHours(),command.description(),command.active(),0,null,null)))));}
    @Override public CourseView updateCourse(UpdateCourseCommand command){Objects.requireNonNull(command);return locks.withLocks(List.of(new ResourceKey("COURSE",command.courseId())),()->transactions.inTransaction(c->{var old=repository.requireCourse(c,command.courseId());return toView(repository.updateCourse(c,new edu.seu.vcampus.server.course.repository.Course(old.courseId(),command.courseCode(),command.courseName(),command.credit(),command.totalHours(),command.description(),command.active(),old.rowVersion(),old.createdAt(),old.updatedAt()),command.expectedVersion()));}));}
    @Override public OfferingView createOffering(CreateOfferingCommand command){Objects.requireNonNull(command);return locks.withLocks(List.of(new ResourceKey("OFFERING_CREATE",command.termId()+":"+command.courseId()+":"+command.className())),()->{authorization.requireUserRole(command.teacherUserId(),"TEACHER");return transactions.inTransaction(c->{repository.requireTerm(c,command.termId());var course=repository.requireCourse(c,command.courseId());var value=new Offering(null,command.termId(),command.courseId(),command.teacherUserId(),command.className(),command.capacity(),0,command.offeringStatus(),0,null,null);var saved=repository.insertOffering(c,value,toSchedules(null,command.schedules()));return toView(saved,repository.findSchedules(c,saved.offeringId()),course);});});}
    @Override public OfferingView updateOffering(UpdateOfferingCommand command){Objects.requireNonNull(command);return locks.withLocks(List.of(new ResourceKey("OFFERING",command.offeringId())),()->{authorization.requireUserRole(command.teacherUserId(),"TEACHER");return transactions.inTransaction(c->{var old=repository.requireOffering(c,command.offeringId());repository.requireTerm(c,command.termId());var course=repository.requireCourse(c,command.courseId());var schedules=toSchedules(command.offeringId(),command.schedules());if(repository.existsEnrollmentForOffering(c,old.offeringId())&&structuralOfferingChange(c,old,command,schedules))throw new OfferingHasEnrollmentsException();if(command.capacity()<old.enrolledCount())throw new IllegalArgumentException("capacity below enrolled count");var value=new Offering(old.offeringId(),command.termId(),command.courseId(),command.teacherUserId(),command.className(),command.capacity(),old.enrolledCount(),command.offeringStatus(),old.rowVersion(),old.createdAt(),old.updatedAt());var saved=repository.updateOffering(c,value,command.expectedVersion(),schedules);return toView(saved,repository.findSchedules(c,saved.offeringId()),course);});});}
    @Override public PageResult<OfferingSummary> searchOfferings(OfferingSearchQuery query){Objects.requireNonNull(query);DayOfWeek day=query.dayOfWeek()==null||query.dayOfWeek().isBlank()?null:DayOfWeek.valueOf(query.dayOfWeek().toUpperCase());return transactions.inTransaction(c->{var page=repository.searchOfferings(c,new edu.seu.vcampus.server.course.repository.OfferingSearchCriteria(query.termId(),query.keyword(),day,Boolean.TRUE.equals(query.availableOnly()),query.page(),query.pageSize()));List<OfferingSummary> items=new ArrayList<>();for(var o:page.items()){var course=repository.requireCourse(c,o.courseId());items.add(new OfferingSummary(o.offeringId(),o.termId(),o.courseId(),course.courseCode(),course.courseName(),o.teacherUserId(),o.className(),o.capacity(),o.enrolledCount(),o.offeringStatus(),o.rowVersion(),toScheduleItems(repository.findSchedules(c,o.offeringId()),o,course)));}return new PageResult<>(items,query.page(),query.pageSize(),page.total());});}
    @Override public StudentSelectionContextView getStudentSelectionContext(String token){CourseSessionIdentity identity=requireStudentSession(token);StudentEnrollmentEligibility eligibility=students.getEnrollmentEligibility(identity.userId());return transactions.inTransaction(c->{var term=currentTerm(c);var phase=phasePolicy.current(c).filter(p->p.termId().equals(term.termId())).orElse(null);boolean eligible=eligibility!=null&&"ACTIVE".equals(eligibility.status());return new StudentSelectionContextView(term.termId(),term.termName(),term.termStatus(),phase==null?null:phase.phaseId(),phase==null?null:phase.phaseType(),phase==null?null:phase.displayTitle(),phase==null?null:phase.phaseStatus(),clock.instant(),eligible,eligible?null:"学籍状态不允许选课");});}
    @Override public PageResult<CourseSelectionView> searchStudentCourses(String token,CourseSelectionQuery query){Objects.requireNonNull(query);CourseSessionIdentity identity=requireStudentSession(token);StudentEnrollmentEligibility eligibility=students.getEnrollmentEligibility(identity.userId());String studentId=eligibility==null?null:eligibility.studentId();return transactions.inTransaction(c->{var term=repository.requireTerm(c,query.termId());var phase=phasePolicy.current(c).filter(p->p.termId().equals(term.termId())).orElse(null);if(phase==null||!"ACTIVE".equals(term.termStatus()))return new PageResult<>(List.of(),query.page(),query.pageSize(),0);boolean preview="PREVIEW".equals(phase.phaseStatus());boolean eligible=eligibility!=null&&"ACTIVE".equals(eligibility.status());List<Enrollment> active=studentId==null?List.of():repository.findActiveByStudentAndTerm(c,studentId,term.termId());java.util.Map<String,Enrollment> selectedByCourse=new java.util.HashMap<>();for(var e:active){var o=repository.requireOffering(c,e.offeringId());selectedByCourse.put(o.courseId(),e);}String keyword=query.keyword().toLowerCase(java.util.Locale.ROOT);DayOfWeek day=query.weekday()==null?null:DayOfWeek.valueOf(query.weekday());java.util.Map<String,List<Offering>> grouped=new java.util.TreeMap<>();for(var o:repository.findOfferingsByTerm(c,term.termId())){var course=repository.requireCourse(c,o.courseId());if(!keyword.isBlank()&&!course.courseCode().toLowerCase(java.util.Locale.ROOT).contains(keyword)&&!course.courseName().toLowerCase(java.util.Locale.ROOT).contains(keyword))continue;if(day!=null&&repository.findSchedules(c,o.offeringId()).stream().noneMatch(s->s.dayOfWeek()==day))continue;grouped.computeIfAbsent(course.courseCode()+"\u0000"+course.courseId(),ignored->new ArrayList<>()).add(o);}List<CourseSelectionView> rows=new ArrayList<>();for(var entry:grouped.entrySet()){List<Offering> offerings=entry.getValue().stream().sorted(Comparator.comparing(Offering::className)).toList();var first=offerings.getFirst();var course=repository.requireCourse(c,first.courseId());var selected=selectedByCourse.get(course.courseId());List<TeachingClassOptionView> options=new ArrayList<>();for(var o:offerings){String action;String reason=null;if(preview){action="UNAVAILABLE";reason="预选课阶段，仅可查看";}else if(selected!=null){if(selected.offeringId().equals(o.offeringId()))action="SELECTED";else{action="UNAVAILABLE";reason="已选择相同课程";}}else if(!eligible){action="UNAVAILABLE";reason="学籍状态不允许选课";}else if(repository.existsPassedAttempt(c,studentId,course.courseId())){action="UNAVAILABLE";reason="该课程已通过，无需重修";}else if(!"OPEN".equals(o.offeringStatus())){action="UNAVAILABLE";reason="教学班未开放";}else if(o.enrolledCount()>=o.capacity()){action="UNAVAILABLE";reason="教学班容量已满";}else if(hasConflict(c,active,o)){action="UNAVAILABLE";reason="时间冲突";}else if("ENROLLMENT".equals(phase.phaseType())){action=repository.existsFailedAttempt(c,studentId,course.courseId())?"RETAKE":"ENROLL";}else{action="LATE_ADD";}options.add(new TeachingClassOptionView(summary(c,o,course),action,reason));}boolean mutationOpen=!preview&&eligible;String courseAction;String courseReason=null;if(selected!=null&&mutationOpen)courseAction="CANCEL_SELECTION";else if(selected!=null){courseAction="DISABLED";courseReason=preview?"预选课阶段，仅可查看":"学籍状态不允许选课";}else if(options.stream().anyMatch(o->java.util.Set.of("ENROLL","RETAKE","LATE_ADD").contains(o.actionType())))courseAction="SELECT_COURSE";else{courseAction="DISABLED";courseReason=options.getFirst().actionReason();}rows.add(new CourseSelectionView(course.courseId(),course.courseCode(),course.courseName(),courseAction,courseReason,selected==null?null:selected.enrollmentId(),selected==null?null:selected.rowVersion(),selected==null?null:selected.offeringId(),options));}int from=Math.min(rows.size(),Math.multiplyExact(query.page(),query.pageSize()));return new PageResult<>(rows.subList(from,Math.min(rows.size(),from+query.pageSize())),query.page(),query.pageSize(),rows.size());});}
    private OfferingSummary summary(Connection c,Offering o,edu.seu.vcampus.server.course.repository.Course course){return new OfferingSummary(o.offeringId(),o.termId(),o.courseId(),course.courseCode(),course.courseName(),o.teacherUserId(),o.className(),o.capacity(),o.enrolledCount(),o.offeringStatus(),o.rowVersion(),toScheduleItems(repository.findSchedules(c,o.offeringId()),o,course));}
    private boolean hasConflict(Connection c,List<Enrollment> active,Offering candidate){for(var enrollment:active){for(var selected:repository.findSchedules(c,enrollment.offeringId()))for(var target:repository.findSchedules(c,candidate.offeringId()))if(conflicts.conflicts(selected,target))return true;}return false;}
    @Override public List<EnrollmentView> getCurrentEnrollments(String token){var identity=requireStudentSession(token);var initial=requireEligible(students.getEnrollmentEligibility(identity.userId()));return locks.withLocks(List.of(new ResourceKey("STUDENT",initial.studentId())),()->{var current=revalidateStudent(token,identity,initial);return transactions.inTransaction(c->{String termId=currentTerm(c).termId();return repository.findByStudentAndTerm(c,current.studentId(),termId).stream().map(CourseServiceImpl::toView).toList();});});}
    @Override public List<ScheduleItem> getCurrentSchedule(String token){var identity=authorization.requireSession(token);if(identity==null)throw new CourseForbiddenException();if("STUDENT".equals(identity.role())){StudentEnrollmentEligibility initial=requireEligible(students.getEnrollmentEligibility(identity.userId()));return locks.withLocks(List.of(new ResourceKey("STUDENT",initial.studentId())),()->{var currentIdentity=authorization.requireSession(token);if(currentIdentity==null||!identity.userId().equals(currentIdentity.userId())||!identity.role().equals(currentIdentity.role()))throw new CourseForbiddenException();var current=requireEligible(students.getEnrollmentEligibility(currentIdentity.userId()));if(!initial.studentId().equals(current.studentId()))throw new StudentIneligibleException();return scheduleForStudent(current.studentId());});}if("TEACHER".equals(identity.role()))return transactions.inTransaction(c->{String termId=currentTerm(c).termId();return scheduleItems(c,repository.findOfferingsByTeacher(c,identity.userId()).stream().filter(o->termId.equals(o.termId())).toList());});throw new CourseForbiddenException();}

    private List<ScheduleItem> scheduleForStudent(String studentId){return transactions.inTransaction(c->{String termId=currentTerm(c).termId();return scheduleItems(c,repository.findActiveByStudentAndTerm(c,studentId,termId).stream().map(e->repository.requireOffering(c,e.offeringId())).toList());});}
    @Override public boolean hasActiveEnrollment(String studentId){if(studentId==null||studentId.isBlank())throw new IllegalArgumentException("studentId");return transactions.inTransaction(c->!repository.findActiveByStudentAndTerm(c,studentId,currentTerm(c).termId()).isEmpty());}
    @Override public List<CourseSummary> findCoursesByStudent(String studentId){if(studentId==null||studentId.isBlank())throw new IllegalArgumentException("studentId");return transactions.inTransaction(c->repository.findActiveByStudentAndTerm(c,studentId,currentTerm(c).termId()).stream().map(e->repository.requireOffering(c,e.offeringId())).map(o->repository.requireCourse(c,o.courseId())).map(course->new CourseSummary(course.courseId(),course.courseCode(),course.courseName())).distinct().toList());}
    private List<ScheduleItem> scheduleItems(Connection c,List<Offering> offerings){List<ScheduleItem> result=new ArrayList<>();for(var o:offerings){var course=repository.requireCourse(c,o.courseId());result.addAll(toScheduleItems(repository.findSchedules(c,o.offeringId()),o,course));}return List.copyOf(result);}

    private edu.seu.vcampus.server.course.repository.Term currentTerm(Connection connection) {
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
    private record ScheduleShape(DayOfWeek dayOfWeek, int startPeriod, int endPeriod,
                                 int startWeek, int endWeek, String classroom) {
        private static ScheduleShape from(Schedule schedule) {
            return new ScheduleShape(schedule.dayOfWeek(), schedule.startPeriod(), schedule.endPeriod(),
                    schedule.startWeek(), schedule.endWeek(), schedule.classroom());
        }
    }
    private static List<ScheduleItem> toScheduleItems(List<Schedule> values,Offering o,edu.seu.vcampus.server.course.repository.Course c){return values.stream().map(s->new ScheduleItem(s.scheduleId(),o.offeringId(),c.courseCode(),c.courseName(),o.className(),o.teacherUserId(),s.dayOfWeek().name(),s.startPeriod(),s.endPeriod(),s.startWeek(),s.endWeek(),s.classroom())).toList();}
    private static CourseView toView(edu.seu.vcampus.server.course.repository.Course c){return new CourseView(c.courseId(),c.courseCode(),c.courseName(),c.credit(),c.totalHours(),c.description(),c.active(),c.rowVersion(),c.createdAt(),c.updatedAt());}
    private static OfferingView toView(Offering o,List<Schedule> s,edu.seu.vcampus.server.course.repository.Course course){return new OfferingView(o.offeringId(),o.termId(),o.courseId(),o.teacherUserId(),o.className(),o.capacity(),o.enrolledCount(),o.offeringStatus(),o.rowVersion(),o.createdAt(),o.updatedAt(),toScheduleItems(s,o,course));}
    private static AdjustmentAuditView toView(edu.seu.vcampus.server.course.repository.EnrollmentAdjustment x){return new AdjustmentAuditView(x.adjustmentId(),x.studentId(),x.adjustmentType(),x.sourceOfferingId(),x.targetOfferingId(),x.operationResult(),x.failureCode(),x.operatedAt());}
    private static boolean blank(String value){return value==null||value.isBlank();}
    private boolean adjustmentBelongsToTerm(Connection c,edu.seu.vcampus.server.course.repository.EnrollmentAdjustment x,String termId){String offeringId=x.targetOfferingId()!=null?x.targetOfferingId():x.sourceOfferingId();return offeringId!=null&&repository.requireOffering(c,offeringId).termId().equals(termId);}

    /** Uses the declared student-then-offering lock order and repeats mutable validation. */
    @Override
    public EnrollmentView enroll(String sessionToken, EnrollCommand command) {
        Objects.requireNonNull(command, "command");
        CourseSessionIdentity identity = requireStudentSession(sessionToken);
        StudentEnrollmentEligibility initial = requireEligible(
                students.getEnrollmentEligibility(identity.userId()));
        List<ResourceKey> orderedKeys = List.of(
                new ResourceKey("STUDENT", initial.studentId()),
                new ResourceKey("OFFERING", command.offeringId()));
        return locks.withLocks(orderedKeys, () -> {
            CourseSessionIdentity currentIdentity = requireStudentSession(sessionToken);
            if (!identity.userId().equals(currentIdentity.userId())) {
                throw new CourseForbiddenException();
            }
            StudentEnrollmentEligibility current = requireEligible(
                    students.getEnrollmentEligibility(currentIdentity.userId()));
            if (!initial.studentId().equals(current.studentId())) {
                throw new StudentIneligibleException();
            }
            Instant operationTime = clock.instant();
            return transactions.inTransaction(connection ->
                    enrollLocked(connection, current.studentId(), command.offeringId(), operationTime));
        });
    }

    @Override
    public EnrollmentView addDuringAdjustment(String sessionToken, LateAddCommand command) {
        return adjustments.add(sessionToken, command);
    }

    @Override
    public void drop(String sessionToken, DropCommand command) {
        adjustments.drop(sessionToken, command);
    }

    @Deprecated
    @Override
    public void dropDuringAdjustment(String sessionToken, DropCommand command) {
        drop(sessionToken, command);
    }

    @Override
    public EnrollmentView changeDuringAdjustment(String sessionToken, ChangeOfferingCommand command) {
        return adjustments.change(sessionToken, command);
    }

    @Override
    public RetakeEligibility checkRetakeEligibility(String sessionToken, String courseId) {
        if (courseId == null || courseId.isBlank()) throw new IllegalArgumentException("courseId");
        CourseSessionIdentity identity = requireStudentSession(sessionToken);
        StudentEnrollmentEligibility initial = requireEligible(
                students.getEnrollmentEligibility(identity.userId()));
        return locks.withLocks(List.of(new ResourceKey("STUDENT", initial.studentId())), () -> {
            StudentEnrollmentEligibility current = revalidateStudent(sessionToken, identity, initial);
            return transactions.inTransaction(connection -> {
                List<String> failedIds = repository.findAttempts(connection, current.studentId(), courseId)
                        .stream()
                        .filter(attempt -> CourseOutcome.FAILED.name().equals(attempt.outcome()))
                        .map(CourseAttempt::attemptId)
                        .toList();
                boolean eligible = !failedIds.isEmpty()
                        && !repository.existsPassedAttempt(connection, current.studentId(), courseId);
                return new RetakeEligibility(courseId, eligible, failedIds,
                        eligible ? null : RetakeNotEligibleException.CODE);
            });
        });
    }

    @Override
    public EnrollmentView enrollRetake(String sessionToken, RetakeCommand command) {
        Objects.requireNonNull(command, "command");
        CourseSessionIdentity identity = requireStudentSession(sessionToken);
        StudentEnrollmentEligibility initial = requireEligible(
                students.getEnrollmentEligibility(identity.userId()));
        List<ResourceKey> orderedKeys = List.of(
                new ResourceKey("STUDENT", initial.studentId()),
                new ResourceKey("OFFERING", command.offeringId()));
        return locks.withLocks(orderedKeys, () -> {
            StudentEnrollmentEligibility current = revalidateStudent(sessionToken, identity, initial);
            Instant operationTime = clock.instant();
            return transactions.inTransaction(connection ->
                    enrollLocked(connection, current.studentId(), command.offeringId(),
                            operationTime, "RETAKE", true));
        });
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

    private EnrollmentView enrollLocked(Connection connection, String studentId, String offeringId,
                                        Instant operationTime) {
        return enrollLocked(connection, studentId, offeringId, operationTime, "NORMAL", false);
    }

    private EnrollmentView enrollLocked(Connection connection, String studentId, String offeringId,
                                        Instant operationTime, String enrollmentType,
                                        boolean requireFailedAttempt) {
        Offering offering = repository.requireOffering(connection, offeringId);
        boolean passed = repository.existsPassedAttempt(connection, studentId, offering.courseId());
        boolean failed = repository.existsFailedAttempt(connection, studentId, offering.courseId());
        if (passed) throw new CourseAlreadyPassedException();
        if (requireFailedAttempt && !failed) throw new RetakeNotEligibleException();
        if (!requireFailedAttempt && failed) throw new RetakeRequiredException();
        if (!"OPEN".equals(offering.offeringStatus())) {
            throw new EnrollmentClosedException();
        }
        phasePolicy.requireEnrollmentOpen(connection, offering.termId());
        List<Enrollment> active = repository.findActiveByStudentAndTerm(
                connection, studentId, offering.termId());
        requireNoDuplicate(connection, active, offering);
        requireNoScheduleConflict(connection, active, offering);
        if (offering.enrolledCount() >= offering.capacity()) {
            throw new OfferingFullException();
        }
        Enrollment saved = repository.insertEnrollment(connection, new Enrollment(
                UUID.randomUUID().toString(), offeringId, studentId, enrollmentType, "ACTIVE",
                operationTime, null, 0, null, null));
        repository.changeEnrolledCount(connection, offeringId, 1);
        return toView(saved);
    }

    private void requireNoDuplicate(Connection connection, List<Enrollment> active, Offering target) {
        for (Enrollment enrollment : active) {
            Offering selected = repository.requireOffering(connection, enrollment.offeringId());
            if (target.courseId().equals(selected.courseId())) {
                throw new DuplicateEnrollmentException();
            }
        }
    }

    private void requireNoScheduleConflict(Connection connection, List<Enrollment> active,
                                           Offering target) {
        List<Schedule> targetSchedules = repository.findSchedules(connection, target.offeringId());
        for (Enrollment enrollment : active) {
            for (Schedule selected : repository.findSchedules(connection, enrollment.offeringId())) {
                for (Schedule candidate : targetSchedules) {
                    if (conflicts.conflicts(selected, candidate)) {
                        throw new ScheduleConflictException();
                    }
                }
            }
        }
    }

    private CourseSessionIdentity requireStudentSession(String sessionToken) {
        CourseSessionIdentity identity = authorization.requireSession(sessionToken);
        if (identity == null || !"STUDENT".equals(identity.role())) {
            throw new CourseForbiddenException();
        }
        return identity;
    }

    private StudentEnrollmentEligibility revalidateStudent(String sessionToken,
                                                            CourseSessionIdentity identity,
                                                            StudentEnrollmentEligibility initial) {
        CourseSessionIdentity currentIdentity = requireStudentSession(sessionToken);
        if (!identity.userId().equals(currentIdentity.userId())) throw new CourseForbiddenException();
        StudentEnrollmentEligibility current = requireEligible(
                students.getEnrollmentEligibility(currentIdentity.userId()));
        if (!initial.studentId().equals(current.studentId())) throw new StudentIneligibleException();
        return current;
    }

    private static void requireSameImport(CourseAttempt existing, CourseAttempt incoming) {
        if (!existing.studentId().equals(incoming.studentId())
                || !existing.courseId().equals(incoming.courseId())
                || !existing.termId().equals(incoming.termId())
                || !existing.outcome().equals(incoming.outcome())) {
            throw new OutcomeImportInvalidException();
        }
    }

    private static StudentEnrollmentEligibility requireEligible(
            StudentEnrollmentEligibility eligibility) {
        if (eligibility == null || !"ACTIVE".equals(eligibility.status())) {
            throw new StudentIneligibleException();
        }
        return eligibility;
    }

    private static EnrollmentView toView(Enrollment enrollment) {
        return new EnrollmentView(enrollment.enrollmentId(), enrollment.offeringId(),
                enrollment.studentId(), enrollment.enrollmentType(), enrollment.enrollmentStatus(),
                enrollment.enrolledAt(), enrollment.droppedAt(), enrollment.rowVersion());
    }
}
