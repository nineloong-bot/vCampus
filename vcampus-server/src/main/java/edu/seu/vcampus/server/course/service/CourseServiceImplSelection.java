package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Connection;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Student-facing course selection, schedule and enrollment views. */
abstract class CourseServiceImplSelection extends CourseServiceImplOfferings {

    /**
     * Creates the student-selection segment.
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
    protected CourseServiceImplSelection(CourseAuthorizationGateway authorization,
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

    @Override public StudentSelectionContextView getStudentSelectionContext(String token){CourseSessionIdentity identity=requireStudentSession(token);StudentEnrollmentEligibility eligibility=students.getEnrollmentEligibility(identity.userId());return transactions.inTransaction(c->{var term=currentTerm(c);var phase=phasePolicy.current(c).filter(p->p.termId().equals(term.termId())).orElse(null);boolean eligible=eligibility!=null&&"ACTIVE".equals(eligibility.status());return new StudentSelectionContextView(term.termId(),term.termName(),term.termStatus(),phase==null?null:phase.phaseId(),phase==null?null:phase.phaseType(),phase==null?null:phase.displayTitle(),phase==null?null:phase.phaseStatus(),clock.instant(),eligible,eligible?null:"学籍状态不允许选课");});}
    @Override public PageResult<CourseSelectionView> searchStudentCourses(String token,CourseSelectionQuery query){Objects.requireNonNull(query);CourseSessionIdentity identity=requireStudentSession(token);StudentEnrollmentEligibility eligibility=students.getEnrollmentEligibility(identity.userId());String studentId=eligibility==null?null:eligibility.studentId();return transactions.inTransaction(c->{var term=repository.requireTerm(c,query.termId());var candidates=curriculumPolicy==null?null:curriculumPolicy.resolve(c,eligibility,term);var phase=phasePolicy.current(c).filter(p->p.termId().equals(term.termId())).orElse(null);if(phase==null||!"ACTIVE".equals(term.termStatus()))return new PageResult<>(List.of(),query.page(),query.pageSize(),0);boolean preview="PREVIEW".equals(phase.phaseStatus());boolean eligible=eligibility!=null&&"ACTIVE".equals(eligibility.status());List<Enrollment> active=studentId==null?List.of():repository.findActiveByStudentAndTerm(c,studentId,term.termId());java.util.Map<String,Enrollment> selectedByCourse=new java.util.HashMap<>();for(var e:active){var o=repository.requireOffering(c,e.offeringId());selectedByCourse.put(o.courseId(),e);}String keyword=query.keyword().toLowerCase(java.util.Locale.ROOT);DayOfWeek day=query.weekday()==null?null:DayOfWeek.valueOf(query.weekday());java.util.Map<String,List<Offering>> grouped=new java.util.TreeMap<>();for(var o:repository.findOfferingsByTerm(c,term.termId())){var course=repository.requireCourse(c,o.courseId());if(candidates!=null&&!candidates.allows(course.courseId()))continue;var metadata=candidates==null?null:candidates.metadata(course.courseId());if(!blank(query.courseNature())&&(metadata==null||!query.courseNature().equals(metadata.courseNature())))continue;if(!blank(query.courseCategory())&&(metadata==null||!query.courseCategory().equals(metadata.courseCategory())))continue;if(!keyword.isBlank()&&!course.courseCode().toLowerCase(java.util.Locale.ROOT).contains(keyword)&&!course.courseName().toLowerCase(java.util.Locale.ROOT).contains(keyword))continue;if(day!=null&&repository.findSchedules(c,o.offeringId()).stream().noneMatch(s->s.dayOfWeek()==day))continue;if(query.conflict()!=null&&query.conflict()!=hasConflict(c,active,o))continue;grouped.computeIfAbsent(course.courseCode()+"\u0000"+course.courseId(),ignored->new ArrayList<>()).add(o);}List<CourseSelectionView> rows=new ArrayList<>();for(var entry:grouped.entrySet()){List<Offering> offerings=entry.getValue().stream().sorted(Comparator.comparing(Offering::className)).toList();var first=offerings.getFirst();var course=repository.requireCourse(c,first.courseId());var metadata=candidates==null?null:candidates.metadata(course.courseId());boolean retake=candidates!=null?candidates.isRetake(course.courseId()):repository.existsFailedAttempt(c,studentId,course.courseId());var selected=selectedByCourse.get(course.courseId());List<TeachingClassOptionView> options=new ArrayList<>();for(var o:offerings){String action;String reason=null;if(preview){action="UNAVAILABLE";reason="预选课阶段，仅可查看";}else if(selected!=null){if(selected.offeringId().equals(o.offeringId()))action="SELECTED";else{action="UNAVAILABLE";reason="已选择相同课程";}}else if(!eligible){action="UNAVAILABLE";reason="学籍状态不允许选课";}else if(repository.existsPassedAttempt(c,studentId,course.courseId())){action="UNAVAILABLE";reason="该课程已通过，无需重修";}else if(!"OPEN".equals(o.offeringStatus())){action="UNAVAILABLE";reason="教学班未开放";}else if(isFull(c,o,retake)){action="UNAVAILABLE";reason="教学班容量已满";}else if(hasConflict(c,active,o)){action="UNAVAILABLE";reason="时间冲突";}else if("ENROLLMENT".equals(phase.phaseType())){action=retake?"RETAKE":"ENROLL";}else{action="LATE_ADD";}options.add(new TeachingClassOptionView(summary(c,o,course),action,reason));}boolean mutationOpen=!preview&&eligible;String courseAction;String courseReason=null;if(selected!=null&&mutationOpen)courseAction="CANCEL_SELECTION";else if(selected!=null){courseAction="DISABLED";courseReason=preview?"预选课阶段，仅可查看":"学籍状态不允许选课";}else if(options.stream().anyMatch(o->java.util.Set.of("ENROLL","RETAKE","LATE_ADD").contains(o.actionType())))courseAction="SELECT_COURSE";else{courseAction="DISABLED";courseReason=options.getFirst().actionReason();}rows.add(new CourseSelectionView(course.courseId(),course.courseCode(),course.courseName(),course.credit(),metadata==null?"ELECTIVE":metadata.courseNature(),metadata==null?"其他课程":metadata.courseCategory(),metadata==null?"开课单位":metadata.offeringUnit(),retake,courseAction,courseReason,selected==null?null:selected.enrollmentId(),selected==null?null:selected.rowVersion(),selected==null?null:selected.offeringId(),options));}int from=Math.min(rows.size(),Math.multiplyExact(query.page(),query.pageSize()));return new PageResult<>(rows.subList(from,Math.min(rows.size(),from+query.pageSize())),query.page(),query.pageSize(),rows.size());});}
    @Override public List<EnrollmentView> getCurrentEnrollments(String token){var identity=requireStudentSession(token);var initial=requireEligible(students.getEnrollmentEligibility(identity.userId()));return locks.withLocks(List.of(new ResourceKey("STUDENT",initial.studentId())),()->{var current=revalidateStudent(token,identity,initial);return transactions.inTransaction(c->{String termId=currentTerm(c).termId();return repository.findByStudentAndTerm(c,current.studentId(),termId).stream().map(CourseServiceImpl::toView).toList();});});}
    @Override public List<ScheduleItem> getCurrentSchedule(String token){var identity=authorization.requireSession(token);if(identity==null)throw new CourseForbiddenException();if("STUDENT".equals(identity.role())){StudentEnrollmentEligibility initial=requireEligible(students.getEnrollmentEligibility(identity.userId()));return locks.withLocks(List.of(new ResourceKey("STUDENT",initial.studentId())),()->{var currentIdentity=authorization.requireSession(token);if(currentIdentity==null||!identity.userId().equals(currentIdentity.userId())||!identity.role().equals(currentIdentity.role()))throw new CourseForbiddenException();var current=requireEligible(students.getEnrollmentEligibility(currentIdentity.userId()));if(!initial.studentId().equals(current.studentId()))throw new StudentIneligibleException();return scheduleForStudent(current.studentId());});}if("TEACHER".equals(identity.role()))return transactions.inTransaction(c->{String termId=currentTerm(c).termId();return scheduleItems(c,repository.findOfferingsByTeacher(c,identity.userId()).stream().filter(o->termId.equals(o.termId())).toList());});throw new CourseForbiddenException();}
    @Override public boolean hasActiveEnrollment(String studentId){if(studentId==null||studentId.isBlank())throw new IllegalArgumentException("studentId");return transactions.inTransaction(c->!repository.findActiveByStudentAndTerm(c,studentId,currentTerm(c).termId()).isEmpty());}
    @Override public List<CourseSummary> findCoursesByStudent(String studentId){if(studentId==null||studentId.isBlank())throw new IllegalArgumentException("studentId");return transactions.inTransaction(c->repository.findActiveByStudentAndTerm(c,studentId,currentTerm(c).termId()).stream().map(e->repository.requireOffering(c,e.offeringId())).map(o->repository.requireCourse(c,o.courseId())).map(course->new CourseSummary(course.courseId(),course.courseCode(),course.courseName())).distinct().toList());}

    private List<ScheduleItem> scheduleForStudent(String studentId){return transactions.inTransaction(c->{String termId=currentTerm(c).termId();return scheduleItems(c,repository.findActiveByStudentAndTerm(c,studentId,termId).stream().map(e->repository.requireOffering(c,e.offeringId())).toList());});}
    private List<ScheduleItem> scheduleItems(Connection c,List<Offering> offerings){List<ScheduleItem> result=new ArrayList<>();for(var o:offerings){var course=repository.requireCourse(c,o.courseId());result.addAll(toScheduleItems(repository.findSchedules(c,o.offeringId()),o,course));}return List.copyOf(result);}
}
