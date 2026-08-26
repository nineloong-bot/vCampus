# Virtual Campus Course Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver term/course/offering management, normal enrollment, post-start add/drop/change, schedules, failed-course retakes, typed messages, concurrency guarantees, and eleven Swing pages without a grade subsystem.

**Architecture:** Course services consume `StudentQueryPort` for eligibility and own all course tables. Enrollment operations lock the student and sorted offering IDs, then repeat capacity/conflict checks inside one Access transaction.

**Tech Stack:** JDK 21, Maven, Swing, UCanAccess, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-08-24-vcampus-course-module-design.md`, `docs/superpowers/specs/2026-08-24-vcampus-overall-architecture-design.md`, and `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`

## Global Constraints

- Complete foundation, user, and `StudentQueryPort` tasks first.
- Do not add numeric scores, grade DTOs, or grade UI.
- Only `FAILED` historical outcomes permit retake selection.
- Preserve all `COURSE_*` command names and server-time window rules.
- Lock `STUDENT:<studentId>` and sorted `OFFERING:<offeringId>` keys for enrollment mutations.
- An unsuccessful change must leave the original enrollment active.
- Course concurrency tests default to 20 clients but must read the count from test configuration instead of hard-coding it in business code.
- Treat course availability as server-time window plus `termStatus`; `CLOSED` terms reject normal enrollment, drop, change, late add, and retake mutation commands.
- Course Swing pages must use the project-provided shared UI tokens and components; they must not create a private theme or alter the shared application shell.
- Complete the shared UI design-system plan before Task 6 and add normal/loading/empty/error screenshots plus a non-course reviewer to `docs/ui-review/manifest.md`.

---

### Task 1: Course Schema, Catalog, and Offering Repository

**Files:**
- Create: `vcampus-database/schema/030_course.sql`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/CourseRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessCourseRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/repository/CourseRepositoryTest.java`

**Interfaces:**
- Consumes: transaction manager.
- Produces: CRUD for term, course, offering, schedule, enrollment, adjustment, and attempt records.

- [ ] **Step 1: Write schema and relationship tests**

```java
@Test
void storesOfferingWithMultipleScheduleRowsVersionAndAuditTimes() {
    Offering saved = repository.insertOffering(connection,
            offering("term-1", "course-1", "teacher-1", 30),
            List.of(schedule(MONDAY, 1, 2, 1, 16),
                    schedule(WEDNESDAY, 3, 4, 1, 16)));
    assertThat(repository.findSchedules(connection, saved.offeringId())).hasSize(2);
    assertThat(saved.rowVersion()).isZero();
    assertThat(saved.createdAt()).isNotNull();
    assertThat(saved.updatedAt()).isEqualTo(saved.createdAt());
}
```

- [ ] **Step 2: Run repository tests**

Run: `mvn -pl vcampus-server -am -Dtest=CourseRepositoryTest test`

Expected: FAIL because course persistence is missing.

- [ ] **Step 3: Implement all seven tables and repositories**

```java
public interface CourseRepository {
    Offering requireOffering(Connection c, String offeringId);
    List<Enrollment> findActiveByStudentAndTerm(Connection c,
                                                 String studentId, String termId);
    Enrollment insertEnrollment(Connection c, Enrollment enrollment);
    void updateEnrollment(Connection c, Enrollment enrollment, long version);
    void changeEnrolledCount(Connection c, String offeringId, int delta);
}
```

Create `uk_tblEnrollment_student_offering` and reactivate an existing dropped record instead of inserting a second record. Add `createdAt` and `updatedAt` to `tblTerm`, `tblCourse`, `tblCourseOffering`, and `tblEnrollment`; update `updatedAt` whenever a mutable row changes.

- [ ] **Step 4: Run Access integration tests**

Run: `mvn -pl vcampus-server -am -Dtest=CourseRepositoryTest test`

Expected: PASS for keys, indexes, versions, schedules, and reactivation.

- [ ] **Step 5: Commit persistence**

```bash
git add vcampus-database/schema/030_course.sql vcampus-server/src/main/java/edu/seu/vcampus/server/course vcampus-server/src/test/java/edu/seu/vcampus/server/course
git commit -m "feat(course): add course persistence"
```

### Task 2: Schedule Conflict and Time-Window Rules

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/ScheduleConflictPolicy.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/TermWindowPolicy.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/domain/ScheduleConflictPolicyTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/domain/TermWindowPolicyTest.java`

**Interfaces:**
- Consumes: schedule and term value objects.
- Produces: `boolean conflicts(Schedule a, Schedule b)`, `requireEnrollmentOpen(Term, Instant)`, and `requireAdjustmentOpen(Term, Instant)`.

- [ ] **Step 1: Write boundary-complete rule tests**

```java
@ParameterizedTest
@MethodSource("scheduleCases")
void detectsOnlyThreeDimensionalOverlap(Schedule a, Schedule b, boolean expected) {
    assertThat(policy.conflicts(a, b)).isEqualTo(expected);
}

static Stream<Arguments> scheduleCases() {
    return Stream.of(
        arguments(s(MONDAY,1,2,1,16), s(MONDAY,2,3,8,12), true),
        arguments(s(MONDAY,1,2,1,7),  s(MONDAY,2,3,8,12), false),
        arguments(s(MONDAY,1,2,1,16), s(TUESDAY,1,2,1,16), false));
}

@Test
void closedTermRejectsMutationsEvenInsideConfiguredWindows() {
    Term term = activeWindowTerm().withStatus(CLOSED);
    assertThatThrownBy(() -> windows.requireEnrollmentOpen(term, insideEnrollmentWindow()))
            .isInstanceOf(EnrollmentClosedException.class);
    assertThatThrownBy(() -> windows.requireAdjustmentOpen(term, insideAdjustmentWindow()))
            .isInstanceOf(AdjustmentClosedException.class);
    assertThatThrownBy(() -> windows.requireRetakeOpen(term, insideEnrollmentWindow()))
            .isInstanceOf(EnrollmentClosedException.class);
}
```

- [ ] **Step 2: Run domain tests**

Run: `mvn -pl vcampus-server -am -Dtest=ScheduleConflictPolicyTest,TermWindowPolicyTest test`

Expected: FAIL because policies are absent.

- [ ] **Step 3: Implement inclusive start/exclusive end windows, `CLOSED` checks, and overlap logic**

```java
return a.dayOfWeek() == b.dayOfWeek()
        && a.startWeek() <= b.endWeek() && b.startWeek() <= a.endWeek()
        && a.startPeriod() <= b.endPeriod() && b.startPeriod() <= a.endPeriod();
```

- [ ] **Step 4: Run rule tests**

Run: `mvn -pl vcampus-server -am -Dtest=ScheduleConflictPolicyTest,TermWindowPolicyTest test`

Expected: PASS at exact start/end instants and all overlap dimensions.

- [ ] **Step 5: Commit policies**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain vcampus-server/src/test/java/edu/seu/vcampus/server/course/domain
git commit -m "feat(course): add schedule and term policies"
```

### Task 3: Normal Enrollment and Capacity Concurrency

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/EnrollCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/EnrollmentView.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/ConcurrentEnrollmentTest.java`

**Interfaces:**
- Consumes: `StudentQueryPort`, authorization, policies, repositories, locks, transactions.
- Produces: `EnrollmentView enroll(String sessionToken, EnrollCommand)`.

- [ ] **Step 1: Write the configurable last-seat concurrency test**

```java
@Test
void exactlyOneStudentWinsTheLastSeat() throws Exception {
    int clients = CourseTestConfig.concurrentClients();
    seedOffering(1, 29, 30);
    List<Outcome<EnrollmentView>> outcomes = concurrentlyWithDistinctStudents(clients,
            token -> service.enroll(token, new EnrollCommand("offering-1")));
    assertThat(outcomes.stream().filter(Outcome::isSuccess)).hasSize(1);
    assertThat(repository.activeCount("offering-1")).isEqualTo(30);
    assertThat(repository.offering("offering-1").enrolledCount()).isEqualTo(30);
}

final class CourseTestConfig {
    private CourseTestConfig() {
    }

    static int concurrentClients() {
        return Integer.getInteger("course.test.concurrentClients", 20);
    }
}
```

- [ ] **Step 2: Run enrollment tests**

Run: `mvn -pl vcampus-server -am -Dtest=ConcurrentEnrollmentTest test`

Expected: FAIL because enrollment service is absent.

- [ ] **Step 3: Implement locked, transactional enrollment**

```java
return locks.withLocks(List.of(studentKey, offeringKey), () ->
        transactions.inTransaction(c -> {
            StudentEligibility eligibility = students.getEnrollmentEligibility(userId);
            requireEligible(eligibility);
            Offering offering = repository.requireOffering(c, command.offeringId());
            windows.requireEnrollmentOpen(offering.term(), clock.instant());
            requireCapacityAndNoConflict(c, eligibility.studentId(), offering);
            return mapper.toView(createOrReactivate(c, eligibility.studentId(), offering, NORMAL));
        }));
```

- [ ] **Step 4: Run enrollment unit/integration/concurrency tests**

Run: `mvn -pl vcampus-server -am -Dtest=ConcurrentEnrollmentTest,CourseEnrollmentServiceTest test`

Expected: PASS for eligibility, duplicates, conflict, closed window, full capacity, and configurable contention with default 20 clients.

- [ ] **Step 5: Commit enrollment**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/course vcampus-server/src/main/java/edu/seu/vcampus/server/course vcampus-server/src/test
git commit -m "feat(course): add concurrency-safe enrollment"
```

### Task 4: Atomic Add, Drop, and Change

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/LateAddCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/DropCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/ChangeOfferingCommand.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/EnrollmentAdjustmentTest.java`

**Interfaces:**
- Consumes: CourseServiceImpl from Task 3.
- Produces: `addDuringAdjustment`, `dropDuringAdjustment`, `changeDuringAdjustment`.

- [ ] **Step 1: Write rollback and audit tests**

```java
@Test
void failedChangeKeepsSourceAndWritesFailureAudit() {
    seedActiveEnrollment("student-1", "source");
    seedFullOffering("target");
    assertThatThrownBy(() -> service.changeDuringAdjustment(token,
            new ChangeOfferingCommand("enrollment-1", "target", 0)))
            .isInstanceOf(OfferingFullException.class);
    assertThat(repository.requireEnrollment("enrollment-1").status()).isEqualTo(ACTIVE);
    assertThat(adjustments.latest().operationResult()).isEqualTo("FAILED");
}
```

- [ ] **Step 2: Run adjustment tests**

Run: `mvn -pl vcampus-server -am -Dtest=EnrollmentAdjustmentTest test`

Expected: FAIL until adjustment operations exist.

- [ ] **Step 3: Implement sorted multi-offering locks and atomic change**

```java
List<ResourceKey> offeringKeys = Stream.of(source.offeringId(), targetId)
        .distinct().sorted().map(id -> key("OFFERING", id)).toList();
List<ResourceKey> keys = new ArrayList<>();
keys.add(key("STUDENT", studentId));
keys.addAll(offeringKeys);
return locks.withLocks(keys, () -> transactions.inTransaction(c ->
        changeInsideTransaction(c, source, targetId, studentId)));
```

If business validation fails, record a failure adjustment in a separate short transaction after the main transaction rolls back; never commit partial enrollment mutations.

- [ ] **Step 4: Run adjustment verification**

Run: `mvn -pl vcampus-server -am -Dtest=EnrollmentAdjustmentTest,ConcurrentAdjustmentTest test`

Expected: PASS for windows, ownership, add/drop counts, rollback, idempotency, and concurrent changes.

- [ ] **Step 5: Commit adjustments**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/course vcampus-server/src/main/java/edu/seu/vcampus/server/course vcampus-server/src/test
git commit -m "feat(course): add atomic enrollment adjustments"
```

### Task 5: Outcome Import and Retake Selection

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CourseOutcome.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/RetakeCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/RetakeEligibility.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/ImportCourseOutcomesCommand.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/RetakeServiceTest.java`

**Interfaces:**
- Consumes: course attempts, enrollment pipeline.
- Produces: `checkRetakeEligibility`, `enrollRetake`, `importCourseOutcomes`.

- [ ] **Step 1: Write failed-only and import-idempotency tests**

```java
@ParameterizedTest
@CsvSource({"FAILED,true", "PASSED,false"})
void retakeEligibilityUsesOutcomeOnly(CourseOutcome outcome, boolean expected) {
    attempts.save(attempt("student-1", "course-1", outcome, "source-1"));
    assertThat(service.checkRetakeEligibility(token, "course-1").eligible())
            .isEqualTo(expected);
}
```

- [ ] **Step 2: Run retake tests**

Run: `mvn -pl vcampus-server -am -Dtest=RetakeServiceTest test`

Expected: FAIL until attempt import and retake logic exist.

- [ ] **Step 3: Implement source-reference deduplication and RETAKE enrollment type**

```java
if (!attempts.existsFailed(c, studentId, offering.courseId())) {
    throw new RetakeNotEligibleException();
}
return enrollInsideTransaction(c, studentId, offering, RETAKE);
```

- [ ] **Step 4: Verify retakes without grade fields**

Run: `mvn -pl vcampus-server -am -Dtest=RetakeServiceTest test`

Expected: PASS; source imports are idempotent and serialized DTOs contain no score/grade property.

- [ ] **Step 5: Commit retakes**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/course vcampus-server/src/main/java/edu/seu/vcampus/server/course vcampus-server/src/test
git commit -m "feat(course): add failed-course retakes"
```

### Task 6: Handlers, Eleven UI-Spec-Compliant Swing Pages, and Acceptance

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/handler/CourseHandlers.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/service/CourseClientService.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingSearchPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingDetailDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/MyEnrollmentPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/MySchedulePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/AdjustmentPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/RetakePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/TermManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseCatalogPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OutcomeImportPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/AdjustmentAuditPanel.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/handler/CourseHandlersTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/CourseUiTest.java`
- Modify: `docs/ui-review/manifest.md`

**Interfaces:**
- Consumes: router, authorization, CourseService, async client.
- Produces: complete course command/UI surface that follows the shared UI design system.

- [ ] **Step 1: Write command and change-preview UI tests**

```java
@Test
void changeDialogShowsBothOfferingsAndDoesNotCloseOnFailure() {
    ChangeDialogRobot robot = launchChangeDialog(source, fullTarget);
    robot.submit().await();
    assertThat(robot.sourceName()).isEqualTo(source.className());
    assertThat(robot.targetName()).isEqualTo(fullTarget.className());
    assertThat(robot.isShowing()).isTrue();
    assertThat(robot.error()).contains("容量已满");
}

@Test
void coursePagesUseSharedUiTokensAndRequiredStatePanels() {
    UiAuditResult audit = UiComplianceAudit.inspect(List.of(
            new OfferingSearchPanel(client),
            new MyEnrollmentPanel(client),
            new AdjustmentPanel(client),
            new RetakePanel(client),
            new AdjustmentAuditPanel(client));
    assertThat(audit.privateThemeClasses()).isEmpty();
    assertThat(audit.absoluteLayoutUsages()).isEmpty();
    assertThat(audit.pagesWithoutTemplate()).isEmpty();
    assertThat(audit.pagesMissingRequiredStates()).isEmpty();
    assertThat(audit.inaccessibleControls()).isEmpty();
    assertThat(audit.staleOrDisposedAsyncUpdates()).isEmpty();
}
```

- [ ] **Step 2: Run handler/UI tests**

Run: `mvn -pl vcampus-server,vcampus-client -am -Dtest=CourseHandlersTest,CourseUiTest test`

Expected: FAIL before handlers/pages exist.

- [ ] **Step 3: Register exact commands and build asynchronous UI-spec-compliant pages**

```java
router.register("COURSE_ADJUSTMENT_CHANGE", studentHandler(
        ChangeOfferingCommand.class, service::changeDuringAdjustment));
router.register("COURSE_RETAKE_ENROLL", studentHandler(
        RetakeCommand.class, service::enrollRetake));
```

Build all course pages from shared `UiColors`, `UiTypography`, `UiSpacing`, `UiDimensions`, `UiBorders`, `PrimaryButton`, `SecondaryButton`, `PagedTablePanel`, `LoadingOverlay`, `EmptyStatePanel`, `ErrorStatePanel`, `DisconnectedStatePanel`, `ConflictStatePanel`, `NotificationService`, and `ConfirmDialog`. Map offering, enrollment, adjustment, retake, and audit panels to the query-list template; map `MySchedulePanel` to the detail template with its permitted full-width weekly grid; map term, catalog, offering, and outcome import panels to the management template; and implement `OfferingDetailDialog` with the shared dialog structure. Use latest-request/disposal guards, visible focus, all required page states, and actionable Chinese messages without internal error details.

- [ ] **Step 4: Run full course verification**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am verify`

Expected: PASS for configurable last-seat contention with default 20 clients, atomic changes, retakes, permissions, UI design-system compliance at required sizes/scaling, screenshot manifest entries, all UI states, and zero grade fields.

- [ ] **Step 5: Commit the completed module**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/course vcampus-server/src/main/java/edu/seu/vcampus/server/course vcampus-client/src/main/java/edu/seu/vcampus/client/course vcampus-server/src/test vcampus-client/src/test docs/ui-review/manifest.md
git commit -m "feat(course): complete course selection module"
```
