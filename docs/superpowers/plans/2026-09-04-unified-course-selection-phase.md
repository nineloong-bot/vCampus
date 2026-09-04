# Unified Course Selection Phase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace student teaching-class, adjustment, and retake pages with one course-grouped selection page controlled by a manually opened, administrator-named selection phase.

**Architecture:** Persist manual selection phases independently from academic terms, enforce one globally open phase on the server, and expose student-specific course groups whose teaching-class options already carry conflict and eligibility states. Keep the existing teacher offering search, but give students and administrators dedicated protocol, service, and Swing components.

**Tech Stack:** Java 21, Maven multi-module build, Swing, JDBC/UCanAccess, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-04-unified-course-selection-phase-design.md`

## Global Constraints

- Student navigation labels are exactly `选课`, `我的选课`, `我的课表`; teacher navigation keeps `教学班查询`.
- Selection phases are manual only; no start/end time automation remains.
- At most one `OPEN` selection phase exists across the system, and its term must be `ACTIVE`.
- Student courses are paginated by distinct course, then expanded into teaching-class options.
- There is no atomic change workflow: switching class is always cancel first, then select again.
- `PASSED`/`FAILED` outcomes determine retake state; the system still stores no numeric grade.
- Client action states are advisory; every write repeats authorization, phase, student, duplicate, capacity, outcome, and schedule checks.
- Existing user-owned untracked files must remain untouched.

---

## File Structure

- `vcampus-common/.../course/*Selection*.java`: serializable phase commands/views and course-grouped student query contracts.
- `vcampus-server/.../repository/SelectionPhase.java`: persisted phase domain row.
- `vcampus-server/.../repository/AccessSelectionPhaseRepository.java`: phase-only JDBC statements and row mapping.
- `vcampus-server/.../domain/SelectionPhasePolicy.java`: one authority for phase/open-term checks used by every mutation.
- `vcampus-server/.../service/SelectionPhaseService.java`: administrator phase lifecycle.
- `vcampus-server/.../service/StudentCourseSelectionService.java`: student context and course/teaching-class action calculation.
- `vcampus-client/.../ui/StudentCourseSelectionPanel.java`: page orchestration, filtering, paging, refresh, and mutations.
- `vcampus-client/.../ui/CourseSelectionCard.java`: one expandable course row and its teaching-class choices.
- `vcampus-client/.../ui/SelectionPhaseManagementPanel.java`: administrator list and lifecycle actions.
- `vcampus-client/.../ui/SelectionPhaseEditorDialog.java`: draft phase creation/title editing.

---

### Task 1: Define the manual-phase and grouped-course protocol

**Files:**

- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CreateSelectionPhaseCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/UpdateSelectionPhaseCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/ChangeSelectionPhaseStatusCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/SelectionPhaseView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/StudentSelectionContextView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CourseSelectionQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/TeachingClassOptionView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CourseSelectionView.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/course/CourseSelectionDtoTest.java`
- Modify test: `vcampus-common/src/test/java/edu/seu/vcampus/common/protocol/MessageSerializationTest.java`

**Interfaces:**

- Produces `SelectionPhaseView(String phaseId, String termId, String phaseType, String displayTitle, String phaseStatus, long rowVersion, Instant createdAt, Instant updatedAt)`.
- Produces `StudentSelectionContextView(String termId, String termName, String termStatus, String phaseId, String phaseType, String displayTitle, Instant serverTime, boolean studentEligible, String ineligibleReason)`; phase fields are nullable when no phase is open.
- Produces `CourseSelectionQuery(String termId, String keyword, String weekday, int page, int pageSize)`.
- Produces `TeachingClassOptionView(OfferingSummary offering, String actionType, String actionReason)`.
- Produces `CourseSelectionView(String courseId, String courseCode, String courseName, String courseAction, String courseReason, String activeEnrollmentId, Long activeEnrollmentVersion, String activeOfferingId, List<TeachingClassOptionView> teachingClasses)`; the three active-enrollment fields are all null when the course is not selected.

- [ ] **Step 1: Write failing DTO validation and serialization tests**

```java
@Test void selectionPhaseContractsValidateAndRoundTrip() throws Exception {
    var create = new CreateSelectionPhaseCommand("term-1", "ENROLLMENT", "2026-2027秋季学期选课");
    assertThat(roundTrip(create)).isEqualTo(create);
    assertThatThrownBy(() -> new CreateSelectionPhaseCommand("term-1", "AUTO", "选课"))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ChangeSelectionPhaseStatusCommand("p1", "DRAFT", 0))
            .isInstanceOf(IllegalArgumentException.class);
}

```

- [ ] **Step 2: Run the common-module tests and verify RED**

Run: `mvn -pl vcampus-common -Dtest=CourseSelectionDtoTest,MessageSerializationTest test`

Expected: compilation fails because the new contracts do not exist.

- [ ] **Step 3: Implement the records and validation**

```java
public record CreateSelectionPhaseCommand(String termId, String phaseType, String displayTitle)
        implements Serializable {
    public CreateSelectionPhaseCommand {
        CourseValidation.text("termId", Objects.requireNonNull(termId), 36);
        CourseValidation.text("displayTitle", Objects.requireNonNull(displayTitle), 64);
        if (!Set.of("ENROLLMENT", "ADJUSTMENT").contains(phaseType)) {
            throw new IllegalArgumentException("invalid phase type");
        }
    }
}

public record ChangeSelectionPhaseStatusCommand(String phaseId, String targetStatus,
                                                 long expectedVersion) implements Serializable {
    public ChangeSelectionPhaseStatusCommand {
        CourseValidation.text("phaseId", Objects.requireNonNull(phaseId), 36);
        if (!Set.of("OPEN", "CLOSED").contains(targetStatus) || expectedVersion < 0) {
            throw new IllegalArgumentException("invalid phase status change");
        }
    }
}
```

Implement `UpdateSelectionPhaseCommand` with a nonblank phase id/title and nonnegative version; `SelectionPhaseView` with the exact type/status sets; `StudentSelectionContextView` with required term identity and nullable all-or-none phase fields; `CourseSelectionQuery` with nonnegative page, page size `1..100`, and weekday `MONDAY..SUNDAY` or null; `TeachingClassOptionView` with actions `ENROLL/RETAKE/LATE_ADD/SELECTED/UNAVAILABLE`; and `CourseSelectionView` with actions `SELECT_COURSE/CANCEL_SELECTION/DISABLED`, defensive `List.copyOf`, and all-or-none active-enrollment fields.

- [ ] **Step 4: Run common tests and verify GREEN**

Run: `mvn -pl vcampus-common test`

Expected: all common tests pass with no serialization or validation warnings.

- [ ] **Step 5: Commit**

```bash
git add vcampus-common
git commit -m "feat(course): define manual selection phase contracts"
```

---

### Task 2: Persist manual selection phases

**Files:**

- Modify: `vcampus-database/schema/030_course.sql`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/SelectionPhase.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessSelectionPhaseRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/CourseRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessCourseRepository.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/repository/CourseRepositoryTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/composition/CourseSchemaInitializerTest.java`

**Interfaces:**

- Produces repository methods `insertSelectionPhase`, `requireSelectionPhase`, `findSelectionPhases`, `findOpenSelectionPhase`, and `updateSelectionPhase`.
- Produces `SelectionPhase` with the same field order as `SelectionPhaseView`.

- [ ] **Step 1: Add failing schema and repository lifecycle tests**

```java
@Test void storesAndOptimisticallyUpdatesSelectionPhase() {
    SelectionPhase draft = transactions.inTransaction(c -> repository.insertSelectionPhase(c,
            new SelectionPhase(null, term.termId(), "ENROLLMENT", "秋季学期选课",
                    "DRAFT", 0, null, null))));
    assertThat(repositoryView().findSelectionPhases(connection)).containsExactly(draft);
    SelectionPhase open = transactions.inTransaction(c -> repository.updateSelectionPhase(c,
            withStatus(draft, "OPEN"), draft.rowVersion()));
    assertThat(open.rowVersion()).isEqualTo(1);
    assertThat(repositoryView().findOpenSelectionPhase(connection)).contains(open);
    assertThatThrownBy(() -> repository.updateSelectionPhase(connection,
            withStatus(open, "CLOSED"), 0)).hasMessageContaining("concurrent");
}
```

Assert schema metadata contains all `tblCourseSelectionPhase` columns and its foreign key to `tblTerm`.

- [ ] **Step 2: Run repository tests and verify RED**

Run: `mvn -pl vcampus-server -am -Dtest=CourseRepositoryTest,CourseSchemaInitializerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation fails because `SelectionPhase` and its repository methods are absent.

- [ ] **Step 3: Add the phase table to the schema**

```sql
CREATE TABLE tblCourseSelectionPhase (
    phaseId VARCHAR(36) NOT NULL,
    termId VARCHAR(36) NOT NULL,
    phaseType VARCHAR(16) NOT NULL,
    displayTitle VARCHAR(64) NOT NULL,
    phaseStatus VARCHAR(16) NOT NULL,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT pk_tblCourseSelectionPhase PRIMARY KEY (phaseId),
    CONSTRAINT fk_tblCourseSelectionPhase_term FOREIGN KEY (termId) REFERENCES tblTerm (termId)
);

CREATE INDEX idx_tblCourseSelectionPhase_termId ON tblCourseSelectionPhase (termId);
CREATE INDEX idx_tblCourseSelectionPhase_status ON tblCourseSelectionPhase (phaseStatus);
```

- [ ] **Step 4: Implement focused phase JDBC persistence**

```java
Optional<SelectionPhase> findOpenSelectionPhase(Connection c) {
    try (PreparedStatement s = c.prepareStatement(
            "SELECT * FROM tblCourseSelectionPhase WHERE phaseStatus='OPEN' ORDER BY updatedAt DESC")) {
        try (ResultSet r = s.executeQuery()) {
            return r.next() ? Optional.of(map(r)) : Optional.empty();
        }
    } catch (SQLException e) {
        throw CourseJdbc.failure("find open selection phase", e);
    }
}
```

`updateSelectionPhase` must update title/status, increment `rowVersion`, match `phaseId AND rowVersion`, and raise `CourseJdbc.stale` when exactly one row is not updated.

- [ ] **Step 5: Run repository and schema tests and verify GREEN**

Run: `mvn -pl vcampus-server -am -Dtest=CourseRepositoryTest,CourseSchemaInitializerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: both test classes pass.

- [ ] **Step 6: Commit**

```bash
git add vcampus-database/schema/030_course.sql vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository vcampus-server/src/test/java/edu/seu/vcampus/server/course
git commit -m "feat(course): persist manual selection phases"
```

---

### Task 3: Implement administrator phase lifecycle and authoritative policy

**Files:**

- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/SelectionPhaseAlreadyOpenException.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/SelectionPhaseInvalidStateException.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/TermNotActiveException.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/SelectionPhasePolicy.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/SelectionPhaseService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/composition/CourseComposition.java`
- Create test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/SelectionPhaseServiceTest.java`
- Create test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/ConcurrentSelectionPhaseTest.java`
- Create test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/domain/SelectionPhasePolicyTest.java`

**Interfaces:**

- Produces service operations `List<SelectionPhaseView> listSelectionPhases()`, `SelectionPhaseView createSelectionPhase(CreateSelectionPhaseCommand)`, `SelectionPhaseView updateSelectionPhase(UpdateSelectionPhaseCommand)`, and `SelectionPhaseView changeSelectionPhaseStatus(ChangeSelectionPhaseStatusCommand)`.
- Produces policy methods `Optional<SelectionPhase> current(Connection)`, `SelectionPhase requireEnrollmentOpen(Connection, String termId)`, `SelectionPhase requireAdjustmentOpen(Connection, String termId)`, and `SelectionPhase requireDropOpen(Connection, String termId)`.

- [ ] **Step 1: Write failing transition, mutual-exclusion, and policy tests**

```java
@Test void opensDraftOnlyWhenItsTermIsActive() {
    SelectionPhaseView draft = service.createSelectionPhase(
            new CreateSelectionPhaseCommand(activeTermId, "ENROLLMENT", "秋季学期选课"));
    SelectionPhaseView open = service.changeSelectionPhaseStatus(
            new ChangeSelectionPhaseStatusCommand(draft.phaseId(), "OPEN", draft.rowVersion()));
    assertThat(open.phaseStatus()).isEqualTo("OPEN");
    assertThatThrownBy(() -> service.changeSelectionPhaseStatus(
            new ChangeSelectionPhaseStatusCommand(open.phaseId(), "OPEN", open.rowVersion())))
            .hasMessageContaining("COURSE_SELECTION_PHASE_INVALID_STATE");
}

@Test void concurrentOpenAllowsExactlyOneWinner() {
    List<Future<SelectionPhaseView>> attempts = List.of(
            pool.submit(() -> open(first)), pool.submit(() -> open(second)));
    assertThat(attempts.stream().filter(this::completedSuccessfully)).hasSize(1);
    assertThat(repositoryOpenPhases()).hasSize(1);
}
```

Policy tests must assert `ENROLLMENT` permits enroll/retake/drop, `ADJUSTMENT` permits late-add/drop, the wrong phase rejects, and a closed term rejects even if an `OPEN` phase row exists.

- [ ] **Step 2: Run new service/domain tests and verify RED**

Run: `mvn -pl vcampus-server -am -Dtest=SelectionPhaseServiceTest,ConcurrentSelectionPhaseTest,SelectionPhasePolicyTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation fails because the lifecycle service and policy do not exist.

- [ ] **Step 3: Implement state transitions under the global phase lock**

```java
SelectionPhaseView changeStatus(ChangeSelectionPhaseStatusCommand command) {
    return locks.withLocks(List.of(new ResourceKey("COURSE_SELECTION_PHASE", "GLOBAL")), () ->
            transactions.inTransaction(c -> {
                SelectionPhase current = repository.requireSelectionPhase(c, command.phaseId());
                if ("OPEN".equals(command.targetStatus())) {
                    if (!"DRAFT".equals(current.phaseStatus())) throw new SelectionPhaseInvalidStateException();
                    if (!"ACTIVE".equals(repository.requireTerm(c, current.termId()).termStatus())) throw new TermNotActiveException();
                    if (repository.findOpenSelectionPhase(c).isPresent()) throw new SelectionPhaseAlreadyOpenException();
                } else if (!"OPEN".equals(current.phaseStatus())) {
                    throw new SelectionPhaseInvalidStateException();
                }
                return toView(repository.updateSelectionPhase(c,
                        withStatus(current, command.targetStatus()), command.expectedVersion()));
            }));
}
```

Creation always writes `DRAFT`; title editing accepts only `DRAFT`; `CLOSED` can never reopen or edit.

- [ ] **Step 4: Implement `SelectionPhasePolicy` and add composition wiring**

```java
public SelectionPhase requireEnrollmentOpen(Connection c, String termId) {
    return require(c, termId, "ENROLLMENT", EnrollmentClosedException::new);
}

public SelectionPhase requireDropOpen(Connection c, String termId) {
    SelectionPhase phase = requireCurrentActiveTerm(c, termId);
    if (!Set.of("ENROLLMENT", "ADJUSTMENT").contains(phase.phaseType())) {
        throw new DropClosedException();
    }
    return phase;
}
```

Create one `SelectionPhasePolicy` in `CourseComposition` and pass it to `CourseServiceImpl`; keep the existing `TermWindowPolicy` wired temporarily so the build stays green until Task 5 migrates every write in one step. Preserve the old package-private `CourseServiceImpl(..., TermWindowPolicy, ScheduleConflictPolicy, Clock)` constructor as a delegating compatibility overload that creates `new SelectionPhasePolicy(repository)`; this prevents unrelated existing test fixtures from breaking before Task 5 updates them.

- [ ] **Step 5: Run lifecycle, policy, and course service tests and verify GREEN**

Run: `mvn -pl vcampus-server -am -Dtest=SelectionPhaseServiceTest,ConcurrentSelectionPhaseTest,SelectionPhasePolicyTest,CourseManagementServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all named tests pass.

- [ ] **Step 6: Commit**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/course vcampus-server/src/test/java/edu/seu/vcampus/server/course
git commit -m "feat(course): control enrollment with manual phases"
```

---

### Task 4: Compute student context and course-grouped option states

**Files:**

- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/StudentCourseSelectionService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/CourseRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessAuditRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessCourseRepository.java`
- Create test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/StudentCourseSelectionServiceTest.java`

**Interfaces:**

- Produces `StudentSelectionContextView getStudentSelectionContext(String sessionToken)`.
- Produces `PageResult<CourseSelectionView> searchStudentCourses(String sessionToken, CourseSelectionQuery query)`.
- Adds `boolean existsPassedAttempt(Connection, String studentId, String courseId)` to `CourseRepository`.

- [ ] **Step 1: Write failing grouping and action-priority tests**

```java
@Test void groupsOfferingsByCourseAndMarksConflictsPerTeachingClass() {
    PageResult<CourseSelectionView> page = service.searchStudentCourses("student-token",
            new CourseSelectionQuery(termId, "", null, 0, 20));
    assertThat(page.items()).extracting(CourseSelectionView::courseCode)
            .containsExactly("CS201", "MATH101");
    CourseSelectionView math = byCode(page, "MATH101");
    assertThat(math.teachingClasses()).hasSize(2)
            .extracting(TeachingClassOptionView::actionReason)
            .containsExactly(null, "时间冲突");
    assertThat(math.courseAction()).isEqualTo("SELECT_COURSE");
}

@Test void selectedCourseOffersCancelAndDisablesSiblingClasses() {
    CourseSelectionView selected = byCode(search(), "CS201");
    assertThat(selected.courseAction()).isEqualTo("CANCEL_SELECTION");
    assertThat(selected.activeEnrollmentId()).isEqualTo("enrollment-1");
    assertThat(selected.teachingClasses()).extracting(TeachingClassOptionView::actionType)
            .containsExactlyInAnyOrder("SELECTED", "UNAVAILABLE");
    assertThat(selected.teachingClasses()).filteredOn(o -> "UNAVAILABLE".equals(o.actionType()))
            .extracting(TeachingClassOptionView::actionReason).containsOnly("已选择相同课程");
}
```

Add separate tests for inactive student, no open phase, full offering, inactive offering, `PASSED`, `FAILED` without `PASSED`, adjustment `LATE_ADD`, keyword/day filtering, and pagination counting courses rather than offerings.

- [ ] **Step 2: Run the student selection tests and verify RED**

Run: `mvn -pl vcampus-server -am -Dtest=StudentCourseSelectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation fails because the student selection service methods are absent.

- [ ] **Step 3: Implement context without rejecting ineligible students**

```java
StudentSelectionContextView context(String token) {
    CourseSessionIdentity identity = requireStudent(token);
    StudentEnrollmentEligibility eligibility = students.getEnrollmentEligibility(identity.userId());
    return transactions.inTransaction(c -> {
        Term term = currentTerm(c);
        SelectionPhase phase = policy.current(c).filter(p -> p.termId().equals(term.termId())).orElse(null);
        boolean eligible = eligibility != null && "ACTIVE".equals(eligibility.status());
        return new StudentSelectionContextView(term.termId(), term.termName(), term.termStatus(),
                phase == null ? null : phase.phaseId(), phase == null ? null : phase.phaseType(),
                phase == null ? null : phase.displayTitle(), clock.instant(), eligible,
                eligible ? null : "学籍状态不允许选课");
    });
}
```

Unlike writes, this read path returns an ineligible context so the UI can remain visible and explain why actions are disabled.

- [ ] **Step 4: Implement deterministic option classification and course pagination**

Use this precedence for `UNAVAILABLE`: no matching open phase, inactive term, ineligible student, course has any `PASSED`, same course selected, offering not `OPEN`, full capacity, schedule conflict. Otherwise return `RETAKE` for `FAILED` without `PASSED`, `ENROLL` in `ENROLLMENT`, or `LATE_ADD` in `ADJUSTMENT`.

```java
private TeachingClassOptionView option(OfferingSummary view, Evaluation e) {
    if (e.blockReason() != null) return new TeachingClassOptionView(view, "UNAVAILABLE", e.blockReason());
    String action = e.failedBefore() ? "RETAKE"
            : "ADJUSTMENT".equals(e.phaseType()) ? "LATE_ADD" : "ENROLL";
    return new TeachingClassOptionView(view, action, null);
}
```

Load all offerings for the requested term, apply keyword/day filters, group by `courseId`, sort courses by `courseCode`, sort teaching classes by `className`, then slice the grouped course list using `page * pageSize`. `total` is the distinct-course count.

- [ ] **Step 5: Run query and existing schedule tests and verify GREEN**

Run: `mvn -pl vcampus-server -am -Dtest=StudentCourseSelectionServiceTest,ScheduleConflictPolicyTest,CourseQueryPortTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all named tests pass.

- [ ] **Step 6: Commit**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/course vcampus-server/src/test/java/edu/seu/vcampus/server/course
git commit -m "feat(course): group student choices by course"
```

---

### Task 5: Move every mutation to the manual phase policy

**Files:**

- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/EnrollmentAdjustmentService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/AdjustmentEnrollmentRules.java`
- Delete: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/TermWindowPolicy.java`
- Delete test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/domain/TermWindowPolicyTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/CourseEnrollmentServiceTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/RetakeServiceTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/EnrollmentAdjustmentTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/AdjustmentRuleCoverageTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/AdjustmentFailureAuditTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/ConcurrentAdjustmentTest.java`

**Interfaces:**

- Consumes `SelectionPhasePolicy` from Task 3.
- Keeps mutation signatures unchanged during this compatibility step; obsolete atomic-change APIs are removed across all modules in Task 9.

- [ ] **Step 1: Rewrite mutation tests to arrange explicit open phase records**

```java
@Test void normalEnrollRequiresEnrollmentPhase() {
    openPhase(termId, "ADJUSTMENT");
    assertThatThrownBy(() -> service.enroll("student-token", new EnrollCommand(offeringId)))
            .hasMessageContaining("COURSE_ENROLLMENT_NOT_OPEN");
}

```

Rewrite every fixture to create an explicit `OPEN` phase instead of placing the fixed clock inside a term window. Keep existing atomic-change tests temporarily so this task changes only phase authority.

- [ ] **Step 2: Run mutation tests and verify RED**

Run: `mvn -pl vcampus-server -am -Dtest=CourseEnrollmentServiceTest,RetakeServiceTest,EnrollmentAdjustmentTest,AdjustmentRuleCoverageTest,AdjustmentFailureAuditTest,ConcurrentAdjustmentTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: failures show mutation code still reads time windows rather than the open phase.

- [ ] **Step 3: Replace every time-window call with phase checks**

```java
policy.requireEnrollmentOpen(connection, offering.termId()); // normal and retake
policy.requireAdjustmentOpen(connection, target.termId());   // late add
policy.requireDropOpen(connection, offering.termId());        // cancel selection
```

Run these checks inside the same transaction as the final duplicate/capacity/conflict revalidation. Keep the existing student and offering locks.

- [ ] **Step 4: Remove the old window policy after all writes migrate**

Migrate the temporary atomic-change path to `SelectionPhasePolicy.requireAdjustmentOpen` as well, so it no longer needs `TermWindowPolicy`. Remove all remaining `TermWindowPolicy` imports, constructor parameters, composition wiring, and tests, then delete the class.

- [ ] **Step 5: Run all server course tests and verify GREEN**

Run: `mvn -pl vcampus-server -am test`

Expected: all server course tests pass.

- [ ] **Step 6: Commit**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/course vcampus-server/src/test/java/edu/seu/vcampus/server/course
git commit -m "refactor(course): enforce phases across selection writes"
```

---

### Task 6: Publish phase and grouped-course operations over the client/server boundary

**Files:**

- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/handler/CourseHandlers.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/service/CourseClientService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiGateway.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseClientGateway.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/handler/CourseHandlersTest.java`
- Modify test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/service/CourseClientServiceTest.java`
- Modify test: `vcampus-client/src/test/java/edu/seu/vcampus/client/integration/LoginCourseSocketIntegrationTest.java`

**Interfaces:**

- Publishes the six message types defined in the spec.
- Adds matching asynchronous methods to `CourseClientService`, `CourseUiGateway`, and `CourseClientGateway`.
- Keeps obsolete methods and registrations temporarily so the full reactor remains green; Task 9 deletes them after the student UI no longer references them.

- [ ] **Step 1: Add failing handler authorization and client decoding tests**

```java
@Test void studentGroupedSearchUsesAuthenticatedSession() {
    ResponseBody<?> response = dispatch("COURSE_STUDENT_COURSE_SEARCH", studentToken,
            new CourseSelectionQuery(termId, "数学", "MONDAY", 0, 20));
    assertThat(response.success()).isTrue();
    verify(service).searchStudentCourses(eq(studentToken), any(CourseSelectionQuery.class));
}

@Test void teacherCannotManageSelectionPhase() {
    ResponseBody<?> response = dispatch("COURSE_SELECTION_PHASE_CREATE", teacherToken,
            new CreateSelectionPhaseCommand(termId, "ENROLLMENT", "秋季选课"));
    assertThat(response.code()).isEqualTo("COMMON_FORBIDDEN");
}
```

Add client tests asserting the returned object types and message names for context, grouped search, list/create/update/change-status.

- [ ] **Step 2: Run boundary tests and verify RED**

Run: `mvn -pl vcampus-client,vcampus-server -am -Dtest=CourseHandlersTest,CourseClientServiceTest,LoginCourseSocketIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: tests fail because handlers and client methods are absent.

- [ ] **Step 3: Register the new handlers and error messages**

```java
r.register("COURSE_STUDENT_SELECTION_CONTEXT", read(EmptyRequest.class, Set.of("STUDENT"),
        (m, b) -> service.getStudentSelectionContext(m.sessionToken())));
r.register("COURSE_STUDENT_COURSE_SEARCH", read(CourseSelectionQuery.class, Set.of("STUDENT"),
        (m, b) -> service.searchStudentCourses(m.sessionToken(), b)));
r.register("COURSE_SELECTION_PHASE_CHANGE_STATUS", write(ChangeSelectionPhaseStatusCommand.class,
        Set.of("ADMIN"), (m, b) -> service.changeSelectionPhaseStatus(b)));
```

Register the other lifecycle handlers explicitly:

```java
r.register("COURSE_SELECTION_PHASE_LIST", read(EmptyRequest.class, Set.of("ADMIN"),
        (m, b) -> (Serializable) new ArrayList<>(service.listSelectionPhases())));
r.register("COURSE_SELECTION_PHASE_CREATE", write(CreateSelectionPhaseCommand.class, Set.of("ADMIN"),
        (m, b) -> service.createSelectionPhase(b)));
r.register("COURSE_SELECTION_PHASE_UPDATE", write(UpdateSelectionPhaseCommand.class, Set.of("ADMIN"),
        (m, b) -> service.updateSelectionPhase(b)));
```

Map `COURSE_SELECTION_PHASE_ALREADY_OPEN` to “已有开放阶段，请先关闭当前阶段”, `COURSE_SELECTION_PHASE_INVALID_STATE` to “阶段状态已变化，请刷新后重试”, and `COURSE_TERM_NOT_ACTIVE` to “仅进行中的学期可以开放选课阶段”. Leave obsolete handlers in place during this additive boundary task.

- [ ] **Step 4: Add client and UI gateway methods**

```java
public CompletableFuture<StudentSelectionContextView> getStudentSelectionContext() {
    return call("COURSE_STUDENT_SELECTION_CONTEXT", EmptyRequest.INSTANCE, READ,
            StudentSelectionContextView.class);
}

public CompletableFuture<PageResult<CourseSelectionView>> searchStudentCourses(CourseSelectionQuery query) {
    return callPage("COURSE_STUDENT_COURSE_SEARCH", query, READ, CourseSelectionView.class);
}
```

Add typed phase list/create/update/status methods and delegate them through `CourseClientGateway`. Update `CourseUiGateway.preview()` with deterministic phase, course-group, and mutation results.

- [ ] **Step 5: Run boundary tests and verify GREEN**

Run: `mvn -pl vcampus-client,vcampus-server -am -Dtest=CourseHandlersTest,CourseClientServiceTest,LoginCourseSocketIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all named tests pass.

- [ ] **Step 6: Commit**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/course/handler vcampus-client/src/main/java/edu/seu/vcampus/client/course vcampus-server/src/test vcampus-client/src/test
git commit -m "feat(course): expose selection phase workflows"
```

---

### Task 7: Remove selection windows from terms across all layers

**Files:**

- Modify: `vcampus-database/schema/030_course.sql`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CreateTermCommand.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/UpdateTermCommand.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/TermView.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/Term.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessCatalogRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/TermEditorDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/TermManagementPanel.java`
- Modify: every term constructor fixture reported by `rg -l 'new (Term|TermView|CreateTermCommand|UpdateTermCommand)\(' vcampus-common/src vcampus-server/src vcampus-client/src`
- Modify test: `vcampus-common/src/test/java/edu/seu/vcampus/common/course/CourseManagementDtoValidationTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/repository/CourseRepositoryTest.java`
- Modify test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`

**Interfaces:**

- Changes `CreateTermCommand` to `(String termCode, String termName, LocalDate startDate, LocalDate endDate, String termStatus)`.
- Changes `UpdateTermCommand` to `(String termId, String termCode, String termName, LocalDate startDate, LocalDate endDate, String termStatus, long expectedVersion)`.
- Changes `TermView` and repository `Term` to omit all four selection-window instants.

- [ ] **Step 1: Write failing contract, schema, and UI absence tests**

```java
@Test void termIsOnlyAcademicIdentityDatesAndStatus() {
    CreateTermCommand command = new CreateTermCommand("2026-1", "秋季学期",
            LocalDate.parse("2026-09-01"), LocalDate.parse("2027-01-15"), "ACTIVE");
    assertThat(command.termStatus()).isEqualTo("ACTIVE");
}

@Test void termEditorHasNoPhaseWindowControls() {
    TermEditorDialog dialog = onEdt(() -> new TermEditorDialog(owner, gateway, term, () -> {}));
    assertThat(componentNames(dialog)).doesNotContain(
            "选课开始", "选课结束", "退改补开始", "退改补结束");
}
```

Extend schema metadata assertions so all four legacy term-window columns are absent.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am -Dtest=CourseManagementDtoValidationTest,CourseRepositoryTest,CourseUiTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: constructor compilation and UI/schema assertions fail because the window fields still exist.

- [ ] **Step 3: Remove window fields from schema, records, JDBC, and mapping**

```java
public record Term(String termId, String termCode, String termName,
                   LocalDate startDate, LocalDate endDate, String termStatus,
                   long rowVersion, Instant createdAt, Instant updatedAt) { }
```

Rewrite `INSERT`, `UPDATE`, and result mapping in `AccessCatalogRepository` with the reduced column order. Update `CourseServiceImpl.createTerm`, `updateTerm`, and `toView` to the new constructors.

- [ ] **Step 4: Remove window controls and update every fixture constructor**

Delete the four spinners, date parsing, validation, and command arguments from `TermEditorDialog`. Change the term table columns to `学期代码`, `学期名称`, `学期日期`, `状态`, `版本`, and render `startDate + " 至 " + endDate`. Update every constructor match from the file-search command in the Files section; do not add dummy instants.

- [ ] **Step 5: Run the full reactor and verify GREEN**

Run: `mvn test`

Expected: all modules compile and all tests pass with no term-window fields.

- [ ] **Step 6: Commit**

```bash
git add vcampus-database/schema/030_course.sql vcampus-common vcampus-server vcampus-client
git commit -m "refactor(course): separate terms from selection phases"
```

---

### Task 8: Build administrator phase management

**Files:**

- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/SelectionPhaseManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/SelectionPhaseEditorDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseWorkspacePanel.java`
- Modify test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`
- Modify test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/AuthenticatedCourseShellTest.java`

**Interfaces:**

- Consumes phase lifecycle gateway methods from Task 6.
- Produces administrator tabs `学期管理`, `选课阶段`, `课程目录`, `教学班管理`, `修读结果导入`, `选退记录`.

- [ ] **Step 1: Add failing administrator UI tests**

```java
@Test void administratorCanCreateOpenAndCloseNamedPhase() throws Exception {
    SelectionPhaseManagementPanel panel = onEdt(() -> new SelectionPhaseManagementPanel(gateway));
    click(panel, "新建阶段");
    setText(openDialog(), "阶段展示标题", "2026-2027秋季学期选课");
    choose(openDialog(), "阶段类型", "正常选课");
    click(openDialog(), "保存草稿");
    selectRow(panel, "2026-2027秋季学期选课");
    click(panel, "开放阶段");
    assertThat(gateway.changedStatus()).extracting(ChangeSelectionPhaseStatusCommand::targetStatus)
            .containsExactly("OPEN");
}

```

- [ ] **Step 2: Run administrator UI tests and verify RED**

Run: `mvn -pl vcampus-client -am -Dtest=CourseUiTest,AuthenticatedCourseShellTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: failures show the new administrator tab and phase lifecycle controls are missing.

- [ ] **Step 3: Implement the phase list and editor**

The list columns are `学期`, `阶段类型`, `展示标题`, `状态`, `版本`, `更新时间`. Enable actions strictly by selected state: edit/open for `DRAFT`, close for `OPEN`, none for `CLOSED`.

```java
private void updateActions() {
    SelectionPhaseView selected = selectedPhase();
    edit.setEnabled(selected != null && "DRAFT".equals(selected.phaseStatus()));
    open.setEnabled(selected != null && "DRAFT".equals(selected.phaseStatus()));
    close.setEnabled(selected != null && "OPEN".equals(selected.phaseStatus()));
}
```

Show a confirmation dialog before close. On concurrent/open-conflict failures, retain rows and show “已有开放阶段，请先关闭后重试” or “阶段已被其他管理员修改，请刷新后重试”.

- [ ] **Step 4: Add the administrator tab and phase-specific guidance**

Change the term page description to `维护学期基础信息与状态；选课开放请前往“选课阶段”。` Add `选课阶段` immediately after `学期管理` in `CourseWorkspacePanel`.

- [ ] **Step 5: Run administrator UI tests and verify GREEN**

Run: `mvn -pl vcampus-client -am -Dtest=CourseUiTest,AuthenticatedCourseShellTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all administrator and shell assertions pass.

- [ ] **Step 6: Commit**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui vcampus-client/src/test/java/edu/seu/vcampus/client
git commit -m "feat(course-ui): manage manual selection phases"
```

---

### Task 9: Build the expandable student course selection page and remove obsolete flows

**Files:**

- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/StudentCourseSelectionPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseSelectionCard.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/AbstractCoursePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseWorkspacePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/theme/UiTypography.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/MyEnrollmentPanel.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/AdjustmentPanel.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/RetakePanel.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingDetailDialog.java`
- Delete: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/TermPhaseView.java`
- Delete: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/ChangeOfferingCommand.java`
- Delete: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/ChangeTargetInvalidException.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/EnrollmentAdjustmentService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/handler/CourseHandlers.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/service/CourseClientService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiGateway.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseClientGateway.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/EnrollmentAdjustmentTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/AdjustmentRuleCoverageTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/AdjustmentFailureAuditTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/ConcurrentAdjustmentTest.java`
- Modify test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/handler/CourseHandlersTest.java`
- Modify test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/service/CourseClientServiceTest.java`
- Modify test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`
- Modify test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/AuthenticatedCourseShellTest.java`

**Interfaces:**

- `StudentCourseSelectionPanel(CourseUiGateway gateway, DropConfirmation confirmation, Runnable onMutation)` owns loading, filters, course paging, and refresh.
- `CourseSelectionCard(CourseSelectionView course, Consumer<SelectionRequest> onSelect, Consumer<DropCommand> onCancel)` owns one course row, expansion, radio selection, local button state, and accessibility names.
- `AbstractCoursePanel.setHeading(String breadcrumbTitle, String title, String description, Font titleFont)` updates the dynamic title safely on the EDT.

- [ ] **Step 1: Write failing navigation, grouping, expansion, button, and disabled-state tests**

```java
@Test void studentUsesOnlyThreeCourseTabs() {
    CourseWorkspacePanel panel = new CourseWorkspacePanel(gateway, UserRole.STUDENT);
    assertThat(tabTitles(panel)).containsExactly("选课", "我的选课", "我的课表");
}

@Test void courseCardExpandsClassesAndOwnsItsActionButton() {
    StudentCourseSelectionPanel panel = new StudentCourseSelectionPanel(gateway, confirmDrop(), () -> {});
    assertThat(buttons(panel)).noneMatch(b -> "选择教学班".equals(b.getText()));
    click(panel, "高等数学");
    assertThat(labels(panel)).contains("Demo-01", "Demo-02", "时间冲突");
    assertThat(button(panel, "高等数学", "选择课程").isEnabled()).isFalse();
    chooseTeachingClass(panel, "Demo-01");
    assertThat(button(panel, "高等数学", "选择课程").isEnabled()).isTrue();
}

@Test void selectedCourseShowsCancelAndSiblingClassIsDisabled() {
    CourseSelectionCard card = card(selectedCourse());
    assertThat(findButton(card, "取消选课")).isEnabled();
    expand(card);
    assertThat(option(card, "Demo-02").isEnabled()).isFalse();
    assertThat(labels(card)).contains("已选择相同课程");
}
```

Add tests for configured heading title, fallback `选课`, `DISPLAY` font size, `RETAKE`, `LATE_ADD`, all-options-disabled course, successful refresh after select/cancel, retained data on network failure, and accessible names.

- [ ] **Step 2: Run student UI tests and verify RED**

Run: `mvn -pl vcampus-client -am -Dtest=CourseUiTest,AuthenticatedCourseShellTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: failures show five old tabs, the old offering table, and one global top-right selection button.

- [ ] **Step 3: Make headings dynamic and implement focused course cards**

Use `UiTypography.DISPLAY` (26 pt) for the administrator-provided title. A card header contains expand control, course code/name, summary/status, and its own action button. The expanded body uses a `ButtonGroup` of teaching-class radio controls; an `UNAVAILABLE` option is disabled and includes its reason in visible text and accessible description.

```java
void applySelection(TeachingClassOptionView option) {
    selected = option;
    action.setText("RETAKE".equals(option.actionType()) ? "重修选课" : "选择课程");
    action.setEnabled(Set.of("ENROLL", "RETAKE", "LATE_ADD").contains(option.actionType()));
}
```

- [ ] **Step 4: Implement page loading and mutations**

Load context and page together. Build one card per `CourseSelectionView`; there is no page-level select button. Dispatch `EnrollCommand`, `RetakeCommand`, or `LateAddCommand` from the selected option action. Dispatch `DropCommand(activeEnrollmentId, activeEnrollmentVersion)` from `CANCEL_SELECTION` after confirmation.

```java
private CompletableFuture<?> select(TeachingClassOptionView option) {
    return switch (option.actionType()) {
        case "ENROLL" -> gateway.enroll(new EnrollCommand(option.offering().offeringId()));
        case "RETAKE" -> gateway.enrollRetake(new RetakeCommand(option.offering().offeringId()));
        case "LATE_ADD" -> gateway.lateAdd(new LateAddCommand(option.offering().offeringId()));
        default -> CompletableFuture.failedFuture(new IllegalStateException("unselectable option"));
    };
}
```

After success call `refresh()` and `onMutation.run()` so “我的选课”和“我的课表” reload. Keep the previous cards visible during recoverable network errors.

- [ ] **Step 5: Replace student tabs and delete old pages**

Instantiate `StudentCourseSelectionPanel` only for students; keep `OfferingSearchPanel` for teachers. Update dirty-tab indexes to `{0,1,2}` and change `MyEnrollmentPanel` empty guidance to `可前往“选课”选择课程`. Delete standalone adjustment/retake/change UI after references are removed.

- [ ] **Step 6: Remove obsolete protocol, service, handler, and gateway paths**

Remove `getTermPhase`, `checkRetake`, and atomic-change methods from all interfaces and implementations. Remove handler registrations `COURSE_GET_TERM_PHASE`, `COURSE_RETAKE_CHECK`, and `COURSE_ADJUSTMENT_CHANGE`; direct grouped search already supplies retake status. Delete `TermPhaseView`, `ChangeOfferingCommand`, `ChangeTargetInvalidException`, and server change-only rule code. Delete atomic-change test cases and retain `ADD`/`DROP` coverage; keep old persisted `CHANGE` audit values readable.

- [ ] **Step 7: Run all client UI tests and verify GREEN**

Run: `mvn test`

Expected: the full reactor passes without obsolete-protocol compilation errors or EDT exceptions.

- [ ] **Step 8: Commit**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client vcampus-client/src/test/java/edu/seu/vcampus/client
git commit -m "feat(course-ui): unify student course selection"
```

---

### Task 10: Update demo data, integration coverage, screenshots, and distribution

**Files:**

- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/demo/CourseDemoServerMain.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/demo/CourseDemoFrame.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/demo/CourseDemoFrameTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/demo/CourseDemoNetworkTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/demo/CourseDemoServerMainTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiScreenshotGenerator.java`
- Modify: `vcampus-distribution/config/server-with-data.properties`
- Modify: `vcampus-distribution/README.md`
- Modify: `docs/course-user-management-demo-and-test-guide.md`
- Regenerate: `docs/ui-review/course/*.png`

**Interfaces:**

- Demo startup creates one active term and one explicitly `OPEN` phase from `demo.phase=ENROLLMENT|ADJUSTMENT`.
- Screenshot generator produces normal selection, adjustment, no-open-phase, and administrator phase-management artifacts at standard, `1024x680`, and 150% scale.

- [ ] **Step 1: Write failing end-to-end phase/title and refresh tests**

```java
@Test void administratorOpenPhaseChangesStudentPageTitleOverSocket() {
    SelectionPhaseView draft = admin.createSelectionPhase(
            new CreateSelectionPhaseCommand(termId, "ADJUSTMENT", "2026-2027秋季学期退改补选课")).join();
    admin.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(
            draft.phaseId(), "OPEN", draft.rowVersion())).join();
    StudentSelectionContextView context = student.getStudentSelectionContext().join();
    assertThat(context.displayTitle()).isEqualTo("2026-2027秋季学期退改补选课");
    assertThat(student.searchStudentCourses(new CourseSelectionQuery(termId, "", null, 0, 20)).join()
            .items()).allSatisfy(course -> assertThat(course.teachingClasses())
                    .allSatisfy(option -> assertThat(option.actionType())
                            .isIn("LATE_ADD", "SELECTED", "UNAVAILABLE")));
}
```

- [ ] **Step 2: Run demo/integration tests and verify RED**

Run: `mvn -pl vcampus-client,vcampus-server -am -Dtest=CourseDemoFrameTest,CourseDemoNetworkTest,CourseDemoServerMainTest,LoginCourseSocketIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: fixture/startup failures show that demo terms still depend on timestamps and no phase is seeded.

- [ ] **Step 3: Seed explicit phases and update documentation/configuration**

Replace generated time-window manipulation with phase lifecycle calls:

```java
SelectionPhaseView draft = service.createSelectionPhase(new CreateSelectionPhaseCommand(
        term.termId(), requestedPhase.toUpperCase(Locale.ROOT),
        "ADJUSTMENT".equalsIgnoreCase(requestedPhase)
                ? "2026-2027秋季学期退改补选课" : "2026-2027秋季学期选课"));
service.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(
        draft.phaseId(), "OPEN", draft.rowVersion()));
```

Document administrator close/open steps and the student course-expand/select/cancel workflow. Do not document atomic change.

- [ ] **Step 4: Run demo/integration tests and verify GREEN**

Run: `mvn -pl vcampus-client,vcampus-server -am -Dtest=CourseDemoFrameTest,CourseDemoNetworkTest,CourseDemoServerMainTest,LoginCourseSocketIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all named tests pass.

- [ ] **Step 5: Generate and visually inspect screenshots**

Run: `mvn -pl vcampus-client -am -Dtest=CourseUiScreenshotGenerator -Dsurefire.failIfNoSpecifiedTests=false test`

Inspect every changed PNG in `docs/ui-review/course/`: configured title uses 26 pt font; each course has its own button; expanded classes do not clip teacher/time/location/status; disabled conflicts are readable; no standalone adjustment/retake tab appears.

- [ ] **Step 6: Run full verification and package the distribution**

Run: `mvn clean verify`

Expected: reactor summary reports `SUCCESS` for `vcampus-common`, `vcampus-server`, and `vcampus-client`, with no test failures.

Run: `mvn package -DskipTests`

Expected: shaded server/client jars and distribution resources are regenerated successfully.

- [ ] **Step 7: Verify removed concepts and clean diff**

Run: `rg -n 'TermPhaseView|TermWindowPolicy|ChangeOfferingCommand|COURSE_ADJUSTMENT_CHANGE|COURSE_GET_TERM_PHASE|选课开始|退改补开始' vcampus-common/src vcampus-server/src vcampus-client/src vcampus-database/schema/030_course.sql`

Expected: no production-source or schema matches. Test names and historical design documents outside those paths may still mention the old model.

Run: `git diff --check && git status --short`

Expected: no whitespace errors; only intended implementation, generated screenshot, documentation, and distribution changes are present, plus any pre-existing user-owned untracked file.

- [ ] **Step 8: Commit**

```bash
git add vcampus-server vcampus-client vcampus-distribution docs/course-user-management-demo-and-test-guide.md docs/ui-review/course
git commit -m "test(course): verify unified manual-phase selection flow"
```
