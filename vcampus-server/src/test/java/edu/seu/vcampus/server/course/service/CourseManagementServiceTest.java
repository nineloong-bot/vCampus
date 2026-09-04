package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.domain.CourseConcurrentModificationException;
import edu.seu.vcampus.server.course.domain.CourseRuleException;
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
import java.util.ArrayList;
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
    private List<String> studentGatewayUserIds;

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
            @Override public CourseSessionIdentity requireSession(String token) {
                return switch (token) {
                    case "admin" -> new CourseSessionIdentity("admin-user", "ADMIN");
                    case "teacher-token" -> new CourseSessionIdentity("teacher-1", "TEACHER");
                    default -> new CourseSessionIdentity("student-user", "STUDENT");
                };
            }
            @Override public void requireUserRole(String userId, String role) {
                if (!"teacher-1".equals(userId) || !"TEACHER".equals(role)) throw new IllegalArgumentException("teacher");
            }
        };
        studentGatewayUserIds = new ArrayList<>();
        service = new CourseServiceImpl(authorization, userId -> {
                    studentGatewayUserIds.add(userId);
                    return switch (userId) {
                        case "student-user" -> new StudentEnrollmentEligibility("student-1", "ACTIVE");
                        case "admin-user" -> new StudentEnrollmentEligibility("admin-student", "ACTIVE");
                        default -> throw new IllegalArgumentException("unknown user");
                    };
                },
                repository, new StripedResourceLockManager(), transactions, new TermWindowPolicy(),
                new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void scheduleAndEnrollmentsStayBoundToStudentWhileAdminSelfServiceIsRejected() {
        TermView term = activateEnrollmentPhase(service.createTerm(termCommand()));
        CourseView studentCourse = service.createCourse(courseCommand("CS101", "程序设计"));
        OfferingView studentOffering = service.createOffering(new CreateOfferingCommand(term.termId(), studentCourse.courseId(),
                "teacher-1", "学生班", 30, "OPEN", List.of(new CreateOfferingCommand.ScheduleInput("MONDAY", 1, 2, 1, 16, "教一-101"))));
        EnrollmentView studentEnrollment = service.enroll("student", new EnrollCommand(studentOffering.offeringId()));
        studentGatewayUserIds.clear();

        assertThat(service.getCurrentSchedule("student")).singleElement().satisfies(item -> {
            assertThat(item.offeringId()).isEqualTo(studentOffering.offeringId());
            assertThat(item.courseCode()).isEqualTo("CS101");
            assertThat(item.courseName()).isEqualTo("程序设计");
        });
        assertThatThrownBy(() -> service.getCurrentSchedule("admin"))
                .isInstanceOf(edu.seu.vcampus.server.course.domain.CourseForbiddenException.class);
        assertThat(service.getCurrentEnrollments("student")).extracting(EnrollmentView::enrollmentId)
                .containsExactly(studentEnrollment.enrollmentId());
        assertThatThrownBy(() -> service.getCurrentEnrollments("admin"))
                .isInstanceOf(edu.seu.vcampus.server.course.domain.CourseForbiddenException.class);
        assertThat(studentGatewayUserIds).containsExactly(
                "student-user", "student-user", "student-user", "student-user");
    }

    @Test void currentEnrollmentsRetainDroppedHistoryWhileActiveViewsExcludeIt() {
        TermView term = activateEnrollmentPhase(service.createTerm(termCommand()));
        CourseView course = service.createCourse(courseCommand("CS101", "程序设计"));
        OfferingView offering = service.createOffering(new CreateOfferingCommand(
                term.termId(), course.courseId(), "teacher-1", "学生班", 30, "OPEN",
                List.of(new CreateOfferingCommand.ScheduleInput(
                        "MONDAY", 1, 2, 1, 16, "教一-101"))));
        EnrollmentView enrolled = service.enroll("student", new EnrollCommand(offering.offeringId()));

        service.drop("student", new DropCommand(enrolled.enrollmentId(), enrolled.rowVersion()));

        assertThat(service.getCurrentEnrollments("student"))
                .filteredOn(row -> row.enrollmentId().equals(enrolled.enrollmentId()))
                .extracting(EnrollmentView::enrollmentStatus).containsExactly("DROPPED");
        assertThat(service.getCurrentSchedule("student")).isEmpty();
        assertThat(((CourseQueryPort) service).hasActiveEnrollment("student-1")).isFalse();
    }

    @Test void currentViewsExcludeClosedTermEnrollmentsAndTeachingAssignments() {
        TermView current = activateEnrollmentPhase(service.createTerm(termCommand()));
        TermView closed = service.createTerm(new CreateTermCommand(
                "2025-2", "已结束学期", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 15),
                NOW.minusSeconds(7200), NOW.minusSeconds(7100), NOW.minusSeconds(7000),
                NOW.minusSeconds(6900), "CLOSED"));
        CourseView course = service.createCourse(courseCommand("CS101", "程序设计"));
        OfferingView currentOffering = service.createOffering(new CreateOfferingCommand(
                current.termId(), course.courseId(), "teacher-1", "当前班", 30, "OPEN",
                List.of(new CreateOfferingCommand.ScheduleInput("MONDAY", 1, 2, 1, 16, "当前教室"))));
        OfferingView historicalOffering = service.createOffering(new CreateOfferingCommand(
                closed.termId(), course.courseId(), "teacher-1", "历史班", 30, "OPEN",
                List.of(new CreateOfferingCommand.ScheduleInput("TUESDAY", 3, 4, 1, 16, "历史教室"))));
        EnrollmentView currentEnrollment = service.enroll("student", new EnrollCommand(currentOffering.offeringId()));
        transactions.inTransaction(connection -> {
            repository.insertEnrollment(connection, new Enrollment(null, historicalOffering.offeringId(),
                    "student-1", "NORMAL", "ACTIVE", NOW.minusSeconds(3600), null, 0, null, null));
            repository.insertEnrollment(connection, new Enrollment(null, historicalOffering.offeringId(),
                    "historical-only", "NORMAL", "ACTIVE", NOW.minusSeconds(3600), null, 0, null, null));
            return null;
        });

        assertThat(service.getCurrentEnrollments("student")).extracting(EnrollmentView::enrollmentId)
                .containsExactly(currentEnrollment.enrollmentId());
        assertThat(service.getCurrentSchedule("student")).extracting(ScheduleItem::offeringId)
                .containsExactly(currentOffering.offeringId());
        assertThat(service.getCurrentSchedule("teacher-token")).extracting(ScheduleItem::offeringId)
                .containsExactly(currentOffering.offeringId());
        CourseQueryPort queryPort = (CourseQueryPort) service;
        assertThat(queryPort.hasActiveEnrollment("historical-only")).isFalse();
        assertThat(queryPort.findCoursesByStudent("historical-only")).isEmpty();
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
                .isInstanceOf(CourseConcurrentModificationException.class)
                .extracting("code").isEqualTo("COMMON_CONCURRENT_MODIFICATION");
    }

    @Test void createsEditsOpensAndClosesManualSelectionPhase() {
        TermView term = service.createTerm(termCommand());
        TermView active = service.updateTerm(new UpdateTermCommand(term.termId(), term.termCode(), term.termName(),
                term.startDate(), term.endDate(), term.enrollmentStartAt(), term.enrollmentEndAt(),
                term.adjustmentStartAt(), term.adjustmentEndAt(), "ACTIVE", term.rowVersion()));
        SelectionPhaseView draft = service.createSelectionPhase(new CreateSelectionPhaseCommand(
                active.termId(), "ENROLLMENT", "秋季选课"));

        SelectionPhaseView edited = service.updateSelectionPhase(new UpdateSelectionPhaseCommand(
                draft.phaseId(), "2026-2027秋季学期选课", draft.rowVersion()));
        SelectionPhaseView open = service.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(
                edited.phaseId(), "OPEN", edited.rowVersion()));
        SelectionPhaseView closed = service.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(
                open.phaseId(), "CLOSED", open.rowVersion()));

        assertThat(service.listSelectionPhases()).extracting(SelectionPhaseView::displayTitle)
                .containsExactly("2026-2027秋季学期选课");
        assertThat(closed.phaseStatus()).isEqualTo("CLOSED");
        assertThatThrownBy(() -> service.updateSelectionPhase(new UpdateSelectionPhaseCommand(
                closed.phaseId(), "不能修改", closed.rowVersion())))
                .isInstanceOf(CourseRuleException.class)
                .extracting("code").isEqualTo("COURSE_SELECTION_PHASE_INVALID_STATE");
    }

    @Test void refusesOpeningSecondPhaseOrPhaseForInactiveTerm() {
        TermView inactive = service.createTerm(termCommand());
        SelectionPhaseView inactiveDraft = service.createSelectionPhase(new CreateSelectionPhaseCommand(
                inactive.termId(), "ENROLLMENT", "未开放"));
        assertThatThrownBy(() -> service.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(
                inactiveDraft.phaseId(), "OPEN", inactiveDraft.rowVersion())))
                .isInstanceOf(CourseRuleException.class)
                .extracting("code").isEqualTo("COURSE_TERM_NOT_ACTIVE");

        TermView active = service.updateTerm(new UpdateTermCommand(inactive.termId(), inactive.termCode(),
                inactive.termName(), inactive.startDate(), inactive.endDate(), inactive.enrollmentStartAt(),
                inactive.enrollmentEndAt(), inactive.adjustmentStartAt(), inactive.adjustmentEndAt(),
                "ACTIVE", inactive.rowVersion()));
        SelectionPhaseView first = service.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(
                inactiveDraft.phaseId(), "OPEN", inactiveDraft.rowVersion()));
        SelectionPhaseView second = service.createSelectionPhase(new CreateSelectionPhaseCommand(
                active.termId(), "ADJUSTMENT", "退改补"));

        assertThat(first.phaseStatus()).isEqualTo("OPEN");
        assertThatThrownBy(() -> service.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(
                second.phaseId(), "OPEN", second.rowVersion())))
                .isInstanceOf(CourseRuleException.class)
                .extracting("code").isEqualTo("COURSE_SELECTION_PHASE_ALREADY_OPEN");
    }

    @Test void studentCourseSearchGroupsTeachingClassesUnderOneCourse() {
        TermView term = service.createTerm(termCommand());
        service.updateTerm(new UpdateTermCommand(term.termId(), term.termCode(), term.termName(),
                term.startDate(), term.endDate(), term.enrollmentStartAt(), term.enrollmentEndAt(),
                term.adjustmentStartAt(), term.adjustmentEndAt(), "ACTIVE", term.rowVersion()));
        CourseView course = service.createCourse(courseCommand("CS101", "程序设计"));
        service.createOffering(new CreateOfferingCommand(term.termId(), course.courseId(), "teacher-1", "01班",
                30, "OPEN", List.of(new CreateOfferingCommand.ScheduleInput("MONDAY", 1, 2, 1, 16, "A101"))));
        service.createOffering(new CreateOfferingCommand(term.termId(), course.courseId(), "teacher-1", "02班",
                30, "OPEN", List.of(new CreateOfferingCommand.ScheduleInput("TUESDAY", 3, 4, 1, 16, "A102"))));
        SelectionPhaseView draft = service.createSelectionPhase(new CreateSelectionPhaseCommand(
                term.termId(), "ENROLLMENT", "秋季选课"));
        service.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(
                draft.phaseId(), "OPEN", draft.rowVersion()));

        PageResult<CourseSelectionView> page = service.searchStudentCourses("student",
                new CourseSelectionQuery(term.termId(), "程序", null, 0, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(row -> {
            assertThat(row.courseAction()).isEqualTo("SELECT_COURSE");
            assertThat(row.teachingClasses()).hasSize(2)
                    .allSatisfy(option -> assertThat(option.actionType()).isEqualTo("ENROLL"));
        });
        assertThat(service.getStudentSelectionContext("student").displayTitle()).isEqualTo("秋季选课");
    }

    @Test void updatesCourseAndOfferingWithDatabaseReadbackAndVersion() {
        TermView term=service.createTerm(termCommand()); CourseView course=service.createCourse(courseCommand("CS101","程序设计"));
        CourseView changed=service.updateCourse(new UpdateCourseCommand(course.courseId(),"CS102","高级程序设计",BigDecimal.valueOf(4),64,"更新说明",false,0));
        var persistedCourse=transactions.inTransaction(c->repository.requireCourse(c,course.courseId()));
        assertThat(changed.rowVersion()).isEqualTo(1); assertThat(persistedCourse).extracting(Course::courseCode,Course::courseName,Course::credit,Course::totalHours,Course::description,Course::active,Course::rowVersion).containsExactly("CS102","高级程序设计",new BigDecimal("4.0"),64,"更新说明",false,1L);
        var input=new CreateOfferingCommand.ScheduleInput("MONDAY",1,2,1,16,"教一-101"); OfferingView offering=service.createOffering(new CreateOfferingCommand(term.termId(),course.courseId(),"teacher-1","01班",30,"OPEN",List.of(input)));
        var changedSchedule=new CreateOfferingCommand.ScheduleInput("TUESDAY",3,4,2,15,"教二-202"); OfferingView updated=service.updateOffering(new UpdateOfferingCommand(offering.offeringId(),term.termId(),course.courseId(),"teacher-1","02班",40,"CLOSED",0,List.of(changedSchedule)));
        var persisted=transactions.inTransaction(c->repository.requireOffering(c,offering.offeringId()));
        assertThat(updated.rowVersion()).isEqualTo(1); assertThat(persisted).extracting(Offering::className,Offering::capacity,Offering::enrolledCount,Offering::offeringStatus,Offering::rowVersion).containsExactly("02班",40,0,"CLOSED",1L);
        List<Schedule> persistedSchedules=transactions.inTransaction(c->repository.findSchedules(c,offering.offeringId())); assertThat(persistedSchedules).singleElement().extracting(Schedule::dayOfWeek,Schedule::classroom).containsExactly(DayOfWeek.TUESDAY,"教二-202");
    }

    @Test void rejectsNonTeacherAndMissingTermWithoutRowsOrPartialUpdates(){
        TermView term=service.createTerm(termCommand()); CourseView course=service.createCourse(courseCommand("CS101","程序设计")); var input=new CreateOfferingCommand.ScheduleInput("MONDAY",1,2,1,16,"教室");
        assertThatThrownBy(()->service.createOffering(new CreateOfferingCommand(term.termId(),course.courseId(),"not-teacher","坏班",10,"OPEN",List.of(input)))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->service.createOffering(new CreateOfferingCommand("missing",course.courseId(),"teacher-1","坏班",10,"OPEN",List.of(input)))).isInstanceOf(IllegalStateException.class);
        List<Offering> rejected=transactions.inTransaction(c->repository.findOfferingsByTerm(c,term.termId())); assertThat(rejected).isEmpty();
        OfferingView good=service.createOffering(new CreateOfferingCommand(term.termId(),course.courseId(),"teacher-1","好班",10,"OPEN",List.of(input)));
        assertThatThrownBy(()->service.updateOffering(new UpdateOfferingCommand(good.offeringId(),"missing",course.courseId(),"teacher-1","坏更新",20,"CLOSED",0,List.of(input)))).isInstanceOf(IllegalStateException.class);
        Offering unchanged=transactions.inTransaction(c->repository.requireOffering(c,good.offeringId())); assertThat(unchanged).extracting(Offering::className,Offering::rowVersion).containsExactly("好班",0L);
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

    @Test void enrolledOfferingCannotMoveAcrossTermsCoursesOrSchedules() {
        TermView originalTerm = activateEnrollmentPhase(service.createTerm(termCommand()));
        TermView otherTerm = service.createTerm(new CreateTermCommand(
                "2026-2", "春季", LocalDate.of(2027, 2, 20), LocalDate.of(2027, 7, 1),
                NOW.minusSeconds(60), NOW.plusSeconds(60), NOW.plusSeconds(120),
                NOW.plusSeconds(240), "PLANNED"));
        CourseView originalCourse = service.createCourse(courseCommand("CS101", "程序设计"));
        CourseView otherCourse = service.createCourse(courseCommand("CS102", "离散数学"));
        var monday = new CreateOfferingCommand.ScheduleInput("MONDAY", 1, 2, 1, 16, "教一-101");
        var tuesday = new CreateOfferingCommand.ScheduleInput("TUESDAY", 3, 4, 1, 16, "教二-202");
        OfferingView offering = service.createOffering(new CreateOfferingCommand(
                originalTerm.termId(), originalCourse.courseId(), "teacher-1", "01班", 30, "OPEN", List.of(monday)));
        service.enroll("student", new EnrollCommand(offering.offeringId()));

        List<UpdateOfferingCommand> invalidChanges = List.of(
                new UpdateOfferingCommand(offering.offeringId(), otherTerm.termId(), originalCourse.courseId(),
                        "teacher-1", "01班", 30, "OPEN", 1, List.of(monday)),
                new UpdateOfferingCommand(offering.offeringId(), originalTerm.termId(), otherCourse.courseId(),
                        "teacher-1", "01班", 30, "OPEN", 1, List.of(monday)),
                new UpdateOfferingCommand(offering.offeringId(), originalTerm.termId(), originalCourse.courseId(),
                        "teacher-1", "01班", 30, "OPEN", 1, List.of(tuesday)));

        for (UpdateOfferingCommand invalid : invalidChanges) {
            assertThatThrownBy(() -> service.updateOffering(invalid))
                    .isInstanceOf(CourseRuleException.class)
                    .extracting("code").isEqualTo("COURSE_OFFERING_HAS_ENROLLMENTS");
        }
        Offering persisted = transactions.inTransaction(c -> repository.requireOffering(c, offering.offeringId()));
        assertThat(persisted.termId()).isEqualTo(originalTerm.termId());
        assertThat(persisted.courseId()).isEqualTo(originalCourse.courseId());
        List<Schedule> persistedSchedules = transactions.inTransaction(
                c -> repository.findSchedules(c, offering.offeringId()));
        assertThat(persistedSchedules)
                .singleElement().satisfies(row -> assertThat(row.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY));
    }

    @Test void droppedEnrollmentHistoryStillFreezesOfferingMeaning() {
        TermView term = activateEnrollmentPhase(service.createTerm(termCommand()));
        CourseView course = service.createCourse(courseCommand("CS101", "程序设计"));
        CourseView otherCourse = service.createCourse(courseCommand("CS102", "离散数学"));
        var monday = new CreateOfferingCommand.ScheduleInput("MONDAY", 1, 2, 1, 16, "教一-101");
        OfferingView offering = service.createOffering(new CreateOfferingCommand(
                term.termId(), course.courseId(), "teacher-1", "01班", 30, "OPEN", List.of(monday)));
        EnrollmentView enrollment = service.enroll("student", new EnrollCommand(offering.offeringId()));
        transactions.inTransaction(connection -> {
            Enrollment stored = repository.requireEnrollment(connection, enrollment.enrollmentId());
            repository.updateEnrollment(connection, new Enrollment(
                    stored.enrollmentId(), stored.offeringId(), stored.studentId(), stored.enrollmentType(),
                    "DROPPED", stored.enrolledAt(), NOW, stored.rowVersion(), stored.createdAt(), stored.updatedAt()),
                    stored.rowVersion());
            repository.changeEnrolledCount(connection, offering.offeringId(), -1);
            return null;
        });

        assertThatThrownBy(() -> service.updateOffering(new UpdateOfferingCommand(
                offering.offeringId(), term.termId(), otherCourse.courseId(), "teacher-1", "01班",
                30, "OPEN", 2, List.of(monday))))
                .isInstanceOf(CourseRuleException.class)
                .extracting("code").isEqualTo("COURSE_OFFERING_HAS_ENROLLMENTS");
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

    private TermView activateEnrollmentPhase(TermView term) {
        TermView active = service.updateTerm(new UpdateTermCommand(term.termId(), term.termCode(), term.termName(),
                term.startDate(), term.endDate(), term.enrollmentStartAt(), term.enrollmentEndAt(),
                term.adjustmentStartAt(), term.adjustmentEndAt(), "ACTIVE", term.rowVersion()));
        SelectionPhaseView draft = service.createSelectionPhase(new CreateSelectionPhaseCommand(
                active.termId(), "ENROLLMENT", active.termName() + "选课"));
        service.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(
                draft.phaseId(), "OPEN", draft.rowVersion()));
        return active;
    }

    private static CreateCourseCommand courseCommand(String code, String name) {
        return new CreateCourseCommand(code, name, BigDecimal.valueOf(3), 48, "课程说明", true);
    }
}
