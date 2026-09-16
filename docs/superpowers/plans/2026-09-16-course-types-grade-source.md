# Course Types and Grade Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align course selection types and pass/retake decisions with training-plan and grade data owned by the student module.

**Architecture:** The common selection DTOs use the student module’s canonical three course-type codes. A new student read port is adapted to a narrow course-owned academic-record gateway at application composition, and the manual course-outcome import stack is removed.

**Tech Stack:** Java 21, Swing, Maven, Microsoft Access/UCanAccess, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-16-course-types-grade-source-design.md`

## Global Constraints

- Microsoft Access remains the only database.
- Training plans and `tblStudentGrade` are the source of truth.
- Cross-module access uses ports, never another module’s repository.
- New Java files stay below 200 physical lines and public APIs have JavaDoc.
- Tests are written and observed failing before production changes.

---

### Task 1: Canonical course types

**Files:**
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CourseSelectionQuery.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CourseSelectionView.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/StudentCourseSelectionPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/StudentCourseRowPanel.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/course/CourseSelectionDtoTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`

**Interfaces:**
- Consumes: `CourseType.REQUIRED`, `CourseType.ELECTIVE`, `CourseType.CROSS_DISCIPLINARY`.
- Produces: query and response fields using exactly those enum names.

- [ ] Add failing DTO tests accepting `CROSS_DISCIPLINARY`, rejecting `RESTRICTED`, and failing UI tests expecting “必修/选修/跨学科”.
- [ ] Run the focused common and client tests and confirm failures mention the old `RESTRICTED`/“限选/任选” mapping.
- [ ] Replace allowed-value sets and Swing mappings with the canonical values and labels.
- [ ] Re-run the focused tests and confirm they pass.

### Task 2: Student academic-record port

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentAcademicRecordQueryPort.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseAcademicRecordGateway.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentGradeRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentGradeServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/UnifiedModuleRegistry.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ApplicationRuntime.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/composition/CourseComposition.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/CurriculumSelectionPolicy.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/RetakeServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/StudentGradeServiceImplTest.java`

**Interfaces:**
- Produces: `StudentAcademicRecordQueryPort.results(studentId, courseId)` returning immutable record ids and `GradeResult` values.
- Produces: `CourseAcademicRecordGateway.results(studentId, courseId)` returning course-owned `PASSED`/`FAILED` projections.

- [ ] Add failing repository/service tests for grade lookup by canonical `courseId` and course-service tests whose gateway reports pass/fail without `tblCourseAttempt` rows.
- [ ] Run focused server tests and confirm failure because the ports and lookup do not exist.
- [ ] Implement the two ports, repository join query, production composition adapter, and replace all selection-policy and retake checks with the gateway.
- [ ] Re-run the focused tests and confirm pass, prerequisite, already-passed, and retake behavior all pass.

### Task 3: Remove manual outcome import

**Files:**
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OutcomeImportPanel.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OutcomeImportEditorPanel.java`
- Delete: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/ImportCourseOutcomesCommand.java`
- Delete: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CourseOutcome.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/service/CourseClientService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseClientGateway.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiGateway.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/demo/CourseDemoDataset.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/handler/CourseHandlersTest.java`

**Interfaces:**
- Removes: `importOutcomes`, `importCourseOutcomes`, `COURSE_IMPORT_OUTCOMES`, and the import command DTO.
- Preserves: grade recording only through student-module `GRADE_RECORD` and `GRADE_BATCH_RECORD`.

- [ ] Replace outcome-import tests with a failing navigation/protocol test asserting no import action exists.
- [ ] Run focused tests and confirm the old page or callable API causes failure.
- [ ] Remove the page, client/service methods, DTOs, demo import, and obsolete server implementation while leaving legacy schema tables untouched.
- [ ] Run focused tests plus `git grep` checks proving no production import symbol remains.

### Task 4: Integrated verification and delivery

**Files:**
- Modify only files required by failures found in the checks above.

**Interfaces:**
- Consumes: canonical types and academic-record ports from Tasks 1–3.
- Produces: one coherent selection flow backed by student grades.

- [ ] Run Maven focused tests for course DTO, course UI, curriculum selection, retake, student grade, composition, and handlers.
- [ ] Run `git diff --check`, Java line-count checks, and inspect the exact diff.
- [ ] Commit the implementation without local configuration, generated database, JAR, target, or log files.
