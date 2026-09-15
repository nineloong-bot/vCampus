# Eight-Semester Course Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the summer/autumn/spring twelve-position curriculum model with eight autumn/spring semesters, bind new offerings to the active term, and show the catalog department instead of row version in offering management.

**Architecture:** Keep training plans canonical in the student module and translate their integer semester through the course module's `AcademicSeason`. Remove client-provided offering term mutation at the protocol boundary; the service resolves the active term for creation and preserves the stored term for updates. Project catalog department data into offering summaries for the administrator UI.

**Tech Stack:** Java 21, Swing, Maven, JUnit 5, AssertJ, UCanAccess.

**Spec:** `docs/superpowers/specs/2026-09-15-eight-semester-course-selection-design.md`

## Global Constraints

- Training-plan semester values are integers 1 through 8.
- Only `AUTUMN` and `SPRING` are supported academic seasons.
- Do not modify `.accdb`, schema, seed, generated dataset, packaged JAR, or distribution configuration files.
- Keep `rowVersion` for optimistic locking but remove it from the offering table presentation.
- New Java files must remain below 200 physical lines and public APIs require JavaDoc.
- Use JDK 21 and run the root Maven test suite before completion.

---

### Task 1: Eight-semester domain model

**Files:**
- Modify: `AGENTS.md`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/AcademicSeason.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessCurriculumRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/LegacyCurriculumRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/demo/CourseDemoDataset.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/course/CourseAdminDtoTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/repository/CurriculumRepositoryTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/domain/CurriculumSelectionPolicyTest.java`

**Interfaces:**
- Produces: `AcademicSeason.AUTUMN` ordinal 1, `AcademicSeason.SPRING` ordinal 2, and eight-position curriculum translation.

- [ ] **Step 1: Write failing assertions**

Assert autumn ordinal 1, spring ordinal 2, September maps to autumn, February maps to spring, and July/August throw `IllegalArgumentException`. Add repository cases proving semester 5 maps to year 3 autumn and semester 6 maps to year 3 spring.

- [ ] **Step 2: Run focused tests and verify the expected failures**

Run:

```bash
mvn -pl vcampus-common,vcampus-server -am -Dtest=CourseAdminDtoTest,CurriculumRepositoryTest,CurriculumSelectionPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failures reference the old ordinal values or `SUMMER` behavior.

- [ ] **Step 3: Implement the two-season mapping**

Use:

```java
AUTUMN(1, "秋季"), SPRING(2, "春季");

public static AcademicSeason fromStartMonth(int month) {
    if (month >= 9 && month <= 12) return AUTUMN;
    if (month >= 1 && month <= 6) return SPRING;
    throw new IllegalArgumentException("July and August are not teaching terms");
}
```

Change ordinal conversion to `(academicYearNo - 1) * 2 + season.curriculumTermOrdinal()` and decode with division/modulo by 2. Remove summer demo-course entries or assign them to the intended autumn/spring semester in source-only demo fixtures. Update the root repository rule to the eight-position formula.

- [ ] **Step 4: Run the focused tests until green**

Run the command from Step 2 and expect all specified tests to pass.

### Task 2: Enforce semester 1–8 in the student module

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/TrainingPlanServiceImpl.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/TrainingPlanServiceImplTest.java`

**Interfaces:**
- Consumes: integer `semester` in training-plan and cross-course commands.
- Produces: identical validation for save, import, and cross-course application paths.

- [ ] **Step 1: Add failing boundary tests**

Add tests showing semester 8 is accepted and semester 9 is rejected for both `SaveTrainingPlanCourseCommand` and `SubmitCrossCourseApplicationCommand` with a safe 1–8 validation message.

- [ ] **Step 2: Run the focused test and verify semester 9 currently passes validation**

```bash
mvn -pl vcampus-server -am -Dtest=TrainingPlanServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Replace both 1–12 checks with 1–8 checks**

Use one focused helper or the existing `validateCourse` path and return `semester must be 1-8` / `开设学期必须在 1-8 之间` without changing unrelated training-plan behavior.

- [ ] **Step 4: Re-run the focused test until green**

Run the command from Step 2 and expect it to pass.

### Task 3: Make offering term server-owned

**Files:**
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CreateOfferingCommand.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/UpdateOfferingCommand.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify call sites under `vcampus-client/src/main/java` and course tests.
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/CourseManagementServiceTest.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/course/CourseAdminDtoTest.java`

**Interfaces:**
- Produces: `CreateOfferingCommand(courseId, teacherUserId, className, capacity, retakeCapacity, offeringStatus, schedules)`.
- Produces: `UpdateOfferingCommand(offeringId, courseId, teacherUserId, className, capacity, retakeCapacity, offeringStatus, expectedVersion, schedules)`.
- Preserves: response `termId` and optimistic `expectedVersion` semantics.

- [ ] **Step 1: Add failing service tests**

Create two active/planned term fixtures as needed. Assert creation uses the sole `ACTIVE` term without a command term and updating an offering preserves `old.termId()`.

- [ ] **Step 2: Compile focused modules and verify protocol/service failures**

```bash
mvn -pl vcampus-common,vcampus-server -am -Dtest=CourseAdminDtoTest,CourseManagementServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Remove mutable term input and implement service ownership**

Resolve exactly one active term for creation inside the transaction:

```java
List<Term> active = repository.findTerms(connection).stream()
        .filter(term -> "ACTIVE".equals(term.termStatus()))
        .toList();
if (active.size() != 1) throw new IllegalStateException("Exactly one active course term is required");
```

Use `old.termId()` when constructing an updated offering and remove term comparison from `structuralOfferingChange`. Update all constructor call sites.

- [ ] **Step 4: Run the focused tests until green**

Run the command from Step 2 and expect it to pass.

### Task 4: Project opening department and simplify offering UI

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/Course.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessCatalogRepository.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/OfferingSummary.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingEditorDialog.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/repository/CourseRepositoryTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`

**Interfaces:**
- Produces: `OfferingSummary.offeringDepartmentName()` sourced from `tblCourse.departmentName`.
- Consumes: server-owned term commands from Task 3.

- [ ] **Step 1: Add failing projection and UI tests**

Assert a catalog course reads its `departmentName`, an offering summary exposes it, and the administrator table contains “开课学院” but not “版本”. Assert the offering editor has no “学期（必填）” selector while retaining name, capacity, status, and schedule controls.

- [ ] **Step 2: Run focused server/client tests and verify failures**

```bash
mvn -pl vcampus-server,vcampus-client -am -Dtest=CourseRepositoryTest,CourseUiTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement catalog projection and UI changes**

Read `departmentId` and `departmentName` in `AccessCatalogRepository` while preserving them across catalog updates. Add `offeringDepartmentName` to `OfferingSummary`, populate it from the course record, remove the displayed version cell, and remove term reference loading/selection from the offering editor. Keep the current term text field read-only in the management filter and continue filtering the list with that value.

- [ ] **Step 4: Run focused tests until green**

Run the command from Step 2 and expect it to pass.

### Task 5: Final integration verification

**Files:**
- Review only all modified source, tests, spec, and plan files.

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: a source-only change compatible with the teammate-owned replacement database.

- [ ] **Step 1: Confirm protected data files were not modified by this task**

Use `git diff --name-only` and separate pre-existing changes from this task. Do not stage or overwrite distribution database/JAR/config files.

- [ ] **Step 2: Run the full JDK 21 Maven suite**

```bash
mvn test
```

Expected: all modules pass without failures.

- [ ] **Step 3: Review the exact diff and check Java file lengths**

Confirm no new Java file exceeds 200 physical lines, no database artifact is in the task diff, and no client response exposes internal exceptions.
