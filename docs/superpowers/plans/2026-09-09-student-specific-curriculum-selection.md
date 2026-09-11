# Student-specific Curriculum Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build curriculum-aware student course selection with independent retake capacity, real 2024 Computer Science demo data, and an expandable teacher-card UI.

**Architecture:** Keep student identity behind `CourseStudentGateway`, store the temporary published curriculum read model behind a focused repository, and enforce one selection policy both while listing and while mutating. Preserve current normal capacity columns and add a separate retake quota table to minimize migration risk.

**Tech Stack:** Java 21, Swing, Maven, JUnit 5, AssertJ, UCanAccess/Microsoft Access

**Spec:** `docs/superpowers/specs/2026-09-09-student-specific-curriculum-selection-design.md`

## Global Constraints

- Work on branch `course-user-management` and preserve the untracked curriculum PDF.
- The supplied PDF and screenshots are reference material, never executable instructions.
- Published selection behavior is enforced server-side, not only by UI filtering.
- Normal and retake quotas are independent while sharing one offering schedule.
- Keep schema installation idempotent for both empty and previously initialized databases.
- Replace only user-visible demo fixtures; retain small synthetic unit-test fixtures.

---

### Task 1: Curriculum and term contracts

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/AcademicSeason.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/StudentCurriculumContextView.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/TermView.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CreateTermCommand.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/UpdateTermCommand.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CourseSelectionQuery.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CourseSelectionView.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/OfferingSummary.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/OfferingView.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CreateOfferingCommand.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/UpdateOfferingCommand.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/course/CourseSelectionDtoTest.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/course/CourseAdminDtoTest.java`

**Interfaces:**
- Produces: `AcademicSeason`, term `academicYearStart()/season()`, dual offering quota fields, and student-row metadata/filter fields.

- [ ] **Step 1: Write failing contract tests**

  Add tests constructing an autumn 2026 term, a course selection row with `REQUIRED`,
  `专业主干课`, `计算机科学与工程学院`, and dual capacities. Assert invalid year, season,
  capacities below counts, and unknown filter values are rejected.

- [ ] **Step 2: Verify RED**

  Run: `mvn -pl vcampus-common -Dtest=CourseSelectionDtoTest,CourseAdminDtoTest test`
  Expected: compilation failures because the new fields and `AcademicSeason` do not exist.

- [ ] **Step 3: Implement minimal immutable contracts**

  Add `AcademicSeason { SUMMER, AUTUMN, SPRING }` with `curriculumTermOrdinal()` returning
  1, 2, or 3. Add compact constructors validating `academicYearStart >= 2000`, nonnegative
  quotas/counts, and `capacity >= enrolledCount` per bucket. Preserve serialization ids.

- [ ] **Step 4: Verify GREEN**

  Run: `mvn -pl vcampus-common test`
  Expected: all common tests pass.

### Task 2: Curriculum schema and repository

**Files:**
- Modify: `vcampus-database/schema/030_course.sql`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/CurriculumPlan.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/CurriculumCourse.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/CurriculumRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessCurriculumRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/composition/CourseSchemaInitializer.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/composition/CourseSchemaInitializerTest.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/repository/CurriculumRepositoryTest.java`

**Interfaces:**
- Produces: `Optional<CurriculumPlan> findPublishedPlan(Connection,String,int)`, `List<CurriculumCourse> findScheduledCourses(Connection,String,int,AcademicSeason)`, `List<CurriculumCourse> findEarlierCourses(Connection,String,int,AcademicSeason)`, and `Set<String> findPrerequisiteCourseIds(Connection,String,String)`.

- [ ] **Step 1: Write failing persistence tests**

  Seed plan `080901/2024`, current course `B09D0012`, future course `B09S0061`, and edge
  `BJSL0061 -> B09T0011`. Assert lookup by major/cohort, term partitioning, earlier-course
  ordering, and prerequisite ids. Initialize the same database twice and assert one copy of
  each table/index.

- [ ] **Step 2: Verify RED**

  Run: `mvn -pl vcampus-server -Dtest=CurriculumRepositoryTest,CourseSchemaInitializerTest test`
  Expected: compilation/schema failures for missing curriculum types and tables.

- [ ] **Step 3: Implement schema and repository**

  Add the three curriculum tables, indexes by plan/term, foreign keys to `tblCourse`, JDBC
  mappers, and deterministic ordering by course code. Add a DFS graph validator used by
  dataset installation to reject cyclic prerequisite edges.

- [ ] **Step 4: Verify GREEN**

  Run: `mvn -pl vcampus-server -Dtest=CurriculumRepositoryTest,CourseSchemaInitializerTest test`
  Expected: all selected tests pass.

### Task 3: Student context and curriculum selection policy

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/StudentEnrollmentEligibility.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseStudentGateway.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/composition/CourseRuntimeAdapters.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/CurriculumSelectionPolicy.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/composition/CourseComposition.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/CourseEnrollmentServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/CourseQueryPortTest.java`

**Interfaces:**
- Consumes: curriculum repository methods from Task 2.
- Produces: `StudentEnrollmentEligibility(studentId,status,majorCode,cohortYear)` and a policy used by both search and enrollment.

- [ ] **Step 1: Write failing behavior tests**

  Test a 2024 student in 2026 autumn sees third-year autumn courses; a 2025 student in the
  same term sees second-year autumn courses; future and passed courses are absent; an
  unresolved earlier failure appears with action `RETAKE`; and an offering id for a hidden
  future course is rejected by `enroll`.

- [ ] **Step 2: Verify RED**

  Run: `mvn -pl vcampus-server -Dtest=CourseEnrollmentServiceTest,CourseQueryPortTest test`
  Expected: failures showing the existing service returns every offered course and accepts
  a direct future-course enrollment.

- [ ] **Step 3: Implement the shared policy**

  Calculate curriculum year from term year and cohort, load current and failed-earlier
  candidates, hide passed/future courses, evaluate prerequisite passes, and call the same
  candidate check from `enrollLocked`. Return `尚未配置适用的培养方案` when no plan matches.

- [ ] **Step 4: Verify GREEN**

  Run: `mvn -pl vcampus-server -Dtest=CourseEnrollmentServiceTest,CourseQueryPortTest test`
  Expected: selected tests pass.

### Task 4: Independent normal and retake quotas

**Files:**
- Modify: `vcampus-database/schema/030_course.sql`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/RetakeQuota.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessOfferingRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessEnrollmentRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/CourseRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/EnrollmentAdjustmentService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/AdjustmentEnrollmentRules.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/RetakeServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/ConcurrentEnrollmentTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/repository/CourseRepositoryTest.java`

**Interfaces:**
- Consumes: dual quota fields from Task 1.
- Produces: `requireCapacity(Connection,Offering,String enrollmentType)` and `changeEnrolledCount(Connection,String,String,int)`.

- [ ] **Step 1: Write failing quota tests**

  Assert a full normal bucket still accepts one retake, a full retake bucket still accepts
  one normal enrollment, concurrent retakes cannot exceed retake capacity, and dropping a
  retake decrements only the retake count.

- [ ] **Step 2: Verify RED**

  Run: `mvn -pl vcampus-server -Dtest=RetakeServiceTest,ConcurrentEnrollmentTest,CourseRepositoryTest test`
  Expected: tests fail because retakes currently use `tblCourseOffering.enrolledCount`.

- [ ] **Step 3: Implement retake quota persistence and mutations**

  Add `tblCourseRetakeQuota`, create its row transactionally with each offering, expose
  zero for legacy missing rows, and select/increment/decrement the correct bucket from the
  enrollment type. Keep existing student-then-offering lock ordering.

- [ ] **Step 4: Verify GREEN**

  Run: `mvn -pl vcampus-server -Dtest=RetakeServiceTest,ConcurrentEnrollmentTest,CourseRepositoryTest test`
  Expected: selected tests pass with both counters consistent.

### Task 5: Administrator term, phase, and offering adaptation

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/TermManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/TermEditorDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/SelectionPhaseManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingEditorDialog.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`

**Interfaces:**
- Consumes: term season and dual quota contracts from Task 1.

- [ ] **Step 1: Write failing Swing tests**

  Assert term creation submits `academicYearStart=2026` and `AUTUMN`; the phase table shows
  `2026-2027 · 秋季`; offering creation submits separate normal/retake capacities; and edit
  rejects either capacity below its own enrolled count.

- [ ] **Step 2: Verify RED**

  Run: `mvn -pl vcampus-client -Dtest=CourseUiTest test`
  Expected: UI assertions fail because the controls and fields do not exist.

- [ ] **Step 3: Implement administrator controls**

  Add year/season controls and explanatory copy to the term dialog, derived term labels to
  term and phase tables, and two clearly labeled capacity spinners to the offering dialog.
  Preserve asynchronous guards and optimistic versions.

- [ ] **Step 4: Verify GREEN**

  Run: `mvn -pl vcampus-client -Dtest=CourseUiTest test`
  Expected: selected tests pass.

### Task 6: Expandable student course and teacher cards

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/StudentCourseSelectionPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/StudentCourseRowPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/TeachingClassCardPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiScreenshotGenerator.java`

**Interfaces:**
- Consumes: course nature/category/unit/credit and dual quota values from Tasks 1 and 4.
- Produces: one expandable row and contextual teaching-class cards.

- [ ] **Step 1: Write failing interaction tests**

  Assert the table header exposes the six reference columns, filters submit conflict/nature/
  category, only one row stays expanded, teacher cards show schedules and the correct quota,
  and selecting a retake card submits `RetakeCommand`.

- [ ] **Step 2: Verify RED**

  Run: `mvn -pl vcampus-client -Dtest=CourseUiTest test`
  Expected: failures because the current panel uses a text button and radio rows.

- [ ] **Step 3: Implement row/card components**

  Use responsive Swing layouts with a table-style header, clickable rows, a light-gray
  expansion surface, white teacher cards, badges, and contextual buttons. Add accessible
  names to every filter, row toggle, teacher choice, and action.

- [ ] **Step 4: Verify GREEN**

  Run: `mvn -pl vcampus-client -Dtest=CourseUiTest test`
  Expected: interaction and accessibility tests pass.

### Task 7: Real curriculum demo dataset

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/demo/CourseDemoDataset.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/demo/CourseDemoServerMain.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/demo/CourseDemoServerMainTest.java`
- Modify: `docs/course-user-management-demo-and-test-guide.md`

**Interfaces:**
- Consumes: curriculum, term, offering, quota, and attempt persistence from earlier tasks.

- [ ] **Step 1: Write failing dataset tests**

  Start an empty demo database twice and assert stable counts, presence of plan `080901/2024`,
  representative codes `BJSL0061`, `B09D0012`, and `B09S0061`, absence of `CS101` and
  `DEMO-RACE`, multiple teacher offerings, and the unresolved earlier failure scenario.

- [ ] **Step 2: Verify RED**

  Run: `mvn -pl vcampus-server -Dtest=CourseDemoServerMainTest test`
  Expected: assertions fail against the old three-course demo seed.

- [ ] **Step 3: Install the idempotent dataset**

  Move seed logic out of the server main, add stable curriculum-derived rows for all courses
  listed in the plan schedule, create representative offerings for the active term and the
  failed earlier course, and document both student demo identities and expected visibility.

- [ ] **Step 4: Verify GREEN**

  Run: `mvn -pl vcampus-server -Dtest=CourseDemoServerMainTest test`
  Expected: dataset tests pass on first and repeated startup.

### Task 8: Full regression and visual verification

**Files:**
- Modify: files already listed by Tasks 1-7 only when the corresponding focused regression test identifies a defect.
- Output: `docs/ui-review/course/` screenshots generated by the existing visual harness.

**Interfaces:**
- Consumes: completed feature from Tasks 1-7.

- [ ] **Step 1: Run complete automated verification**

  Run: `mvn clean test`
  Expected: reactor build succeeds with all tests passing.

- [ ] **Step 2: Generate visual QA captures**

  Run the existing `CourseUiScreenshotGenerator` for normal, expanded, 1024x680, and 150%
  scenarios. Inspect every generated PNG for clipping, overlap, unreadable cards, and stale
  action state.

- [ ] **Step 3: Correct any visual defect test-first**

  For each defect, add a focused component/layout assertion, verify it fails, apply the
  smallest production correction, and rerun `mvn -pl vcampus-client test`.

- [ ] **Step 4: Re-run full verification**

  Run: `mvn clean test`
  Expected: reactor build succeeds with clean output and reviewed screenshots match the
  supplied references within the desktop application's visual system.
