# Student Major-Transfer Data Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce first-year, age, and cross-college transfer rules; clean invalid Access applications; generate large valid transfer fixtures; and render all transfer states in Chinese in the Swing client.

**Architecture:** Keep the existing Java service, Access schema, and Python full-test-data generator. Add a small pure eligibility policy used by the service, a transactional Java cleaner plus read-only Access audit SQL, generator helpers that produce and assert a dedicated computer-to-mathematics scenario, and one Swing dictionary per transfer enum family behind a shared facade.

**Tech Stack:** JDK 21, Maven, Java 21 records/enums, JUnit 5 + AssertJ, Microsoft Access/UCanAccess, Python 3 `unittest`, Java Swing.

**Spec:** `docs/superpowers/specs/2026-09-15-student-major-transfer-data-quality-design.md`

## Global Constraints

- Use JDK 21 and Maven from the repository root for verification.
- Microsoft Access through UCanAccess is the only supported database.
- Treat `vcampus-database/schema` plus `vcampus-database/seed` as the schema source of truth.
- Preserve `SUMMER`, `AUTUMN`, and `SPRING` and the canonical training-plan tables.
- Never update, delete, or overwrite existing test accounts; never trust client organization snapshots.
- New Java files must stay within 200 physical lines and public APIs need useful JavaDoc.
- Write a failing automated test before each production feature or bug fix.
- Do not directly edit the existing `vcampus-distribution/data/vCampus.accdb`; rebuild in a temporary path before replacement.
- Do not commit `target`, logs, caches, database locks/backups, generated release artifacts, or secrets.

---

### Task 1: Pure transfer eligibility policy

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferEligibilityPolicy.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferEligibilityInput.java`
- Create: `vcampus-common/src/test/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferEligibilityPolicyTest.java`

**Interfaces:**
- Produces `MajorTransferEligibilityPolicy.check(MajorTransferEligibilityInput)` returning a result with `eligible`, `reasonCode`, and `message`.
- `MajorTransferEligibilityInput` carries `StudentType`, active/enrolled/on-campus flags, `LocalDate birthDate`, `LocalDate applicationStart`, `int enrollmentYear`, `String currentDepartmentId`, `String targetDepartmentId`, `String currentMajorId`, `String targetMajorId`.
- The result exposes stable codes `TRANSFER_INELIGIBLE` and `TRANSFER_INVALID_TARGET` without SQL or internal exception text.

- [ ] **Step 1: Add failing policy tests**

```java
@Test void rejectsSecondYearStudentEvenWhenOptionListsTheirGrade() {
    var result = policy.check(input(2025, LocalDate.of(2006, 5, 1), "dept-cs", "dept-math"));
    assertThat(result.eligible()).isFalse();
    assertThat(result.reasonCode()).isEqualTo("TRANSFER_INELIGIBLE");
}

@Test void acceptsAgeBoundariesAndRejectsSameDepartment() {
    assertThat(policy.check(input(2026, LocalDate.of(2009, 9, 1), "dept-cs", "dept-math")).eligible()).isTrue();
    assertThat(policy.check(input(2026, LocalDate.of(2006, 9, 1), "dept-cs", "dept-math")).eligible()).isTrue();
    var sameCollege = policy.check(input(2026, LocalDate.of(2008, 9, 1), "dept-cs", "dept-cs"));
    assertThat(sameCollege.eligible()).isFalse();
    assertThat(sameCollege.reasonCode()).isEqualTo("TRANSFER_INVALID_TARGET");
}
```

- [ ] **Step 2: Run the common policy test and confirm RED**

Run `mvn -pl vcampus-common -Dtest=MajorTransferEligibilityPolicyTest test`.
Expected: compilation fails because the new input/policy types do not exist.

- [ ] **Step 3: Implement the minimal pure policy**

Compute academic grade as `applicationStart.getYear() - (applicationStart.getMonthValue() < 9 ? 1 : 0) - enrollmentYear + 1`. Compute age with `Period.between(birthDate, applicationStart).getYears()`. Require undergraduate, active, enrolled, on-campus, grade `1`, age `17..20`, different nonblank department IDs, and different major IDs. Return the first stable failure in the order student status, grade/age, target relation.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same Maven command and confirm all policy cases pass.

- [ ] **Step 5: Commit the policy**

Run `git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer vcampus-common/src/test/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferEligibilityPolicyTest.java && git commit -m "feat(student): centralize major transfer eligibility"`.

### Task 2: Integrate the policy into every transfer write/read check

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferStudentWorkflowTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferHandlersTest.java`

**Interfaces:**
- Consumes `MajorTransferEligibilityPolicy` from Task 1.
- Keeps existing service method signatures and error codes.
- Student workspace eligibility and `saveDraft`/`submit` decisions are derived from the same policy; administrator mutations re-check target department as they already re-check authorization.

- [ ] **Step 1: Add failing integration tests**

Seed a valid first-year student, then change the target option to the same department and assert `saveDraft` throws `MajorTransferException` with `TRANSFER_INVALID_TARGET`. Add a student with a 2025 class and assert `saveDraft` throws `TRANSFER_INELIGIBLE`; add a 16-year-old and a 21-year-old and assert the same code. Assert `getStudentWorkspace` marks the corresponding eligibility item false.

- [ ] **Step 2: Run the focused server tests and confirm RED**

Run `mvn -pl vcampus-server -am -Dtest=MajorTransferStudentWorkflowTest,MajorTransferHandlersTest -Dsurefire.failIfNoSpecifiedTests=false test` and verify the new assertions fail against the current service.

- [ ] **Step 3: Replace duplicated checks with policy calls**

Inside the existing student lock and transaction, load the class enrollment year and current major department through `AccessOrganizationRepository`, load the target major department from the option’s target major, and build `MajorTransferEligibilityInput` using `batch.applicationStart()` and the student birth date. Pass the result to the existing `error(code, message)` path. Use the same input for `checkEligibility`; retain existing successful-transfer and duplicate-application checks around the policy.

- [ ] **Step 4: Verify integration GREEN and no persistence bypass**

Run the focused server command again. Also assert the database application count is unchanged after each rejected write.

- [ ] **Step 5: Commit the service change**

Run `git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer && git commit -m "fix(student): enforce first-year cross-college transfer rules"`.

### Task 3: Access audit and transactional dirty-data cleaner

**Files:**
- Create: `vcampus-database/audit/major_transfer_invalid_applications.sql`
- Create: `vcampus-database/tools/MajorTransferDataCleaner.java`
- Create: `vcampus-database/tools/MajorTransferDataCleanerTest.java`
- Modify: `vcampus-database/demo/README.md`

**Interfaces:**
- Produces `MajorTransferDataCleaner <database-path> --audit|--clean` command behavior.
- Audit SQL returns one row per invalid application with a reason column.
- Cleaner deletes only invalid application dependency rows in order attachment, review, execution, application; it never issues SQL against `tblUser`.

- [ ] **Step 1: Add failing cleaner test**

Build a temporary Access database using the existing schema initializer, insert one invalid same-department application with one attachment/review/execution row and one test account, run clean mode, and assert all four transfer tables lose only that application’s rows while `SELECT COUNT(*) FROM tblUser WHERE loginId='TESTADMIN'` remains one.

- [ ] **Step 2: Run the focused cleaner test and confirm RED**

Run `mvn -pl vcampus-database -am -Dtest=MajorTransferDataCleanerTest -Dsurefire.failIfNoSpecifiedTests=false test` if the module is Maven-addressable; otherwise compile/run the tool test with the repository’s existing database-tool test harness. Expected: missing cleaner entry point or failing deletion assertions.

- [ ] **Step 3: Implement the audit query and cleaner transaction**

Use Access-compatible scalar subqueries/joins and calculate grade from batch start and class enrollment year. Materialize invalid application IDs before deletion. In clean mode use one connection, `setAutoCommit(false)`, prepared statements with `IN`-equivalent per-ID deletes, commit only after the post-clean invalid count is zero, and rollback on any exception. Print counts only; do not print paths, SQL text, stack traces, or credentials to client-facing output.

- [ ] **Step 4: Verify audit and clean behavior**

Run the focused test, then run audit and clean against a newly built temporary database. Confirm audit returns zero after clean, account count is unchanged, and a valid cross-college first-year application remains.

- [ ] **Step 5: Commit the cleaner**

Run `git add vcampus-database/audit/major_transfer_invalid_applications.sql vcampus-database/tools/MajorTransferDataCleaner.java vcampus-database/tools/MajorTransferDataCleanerTest.java vcampus-database/demo/README.md && git commit -m "feat(database): audit and clean invalid transfer applications"`.

### Task 4: Large valid Python transfer fixture

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/people.py`
- Modify: `vcampus-database/demo/full-test-data/tools/generate.py`
- Modify: `vcampus-database/demo/full-test-data/tools/test_generation.py`
- Create: `vcampus-database/demo/full-test-data/tools/major_transfer.py`

**Interfaces:**
- `people.generate(add, now, seed=..., base_students=..., transfer_students=...)` keeps existing defaults compatible and returns the existing account list.
- `major_transfer.generate(add, now, transfer_students)` creates a valid open batch, a mathematics option, and `SUBMITTED` computer-to-mathematics applications.
- Generator assertions expose `validate_transfer_fixture(rows, now)` for tests and fail before SQL is written.

- [ ] **Step 1: Add failing Python generator tests**

Assert the default fixture contains at least 200 dedicated transfer students, every dedicated student has a 2026 class and a birth date producing age 17–20 at batch start, every application has `fromDepartmentName == '计算机学院'`, target department `数学学院`, `fromGrade == '1'`, and no application has equal source/target department IDs. Assert the existing `TESTADMIN`, `USER_ADMIN_2`, and non-bulk account rows are not deleted or updated by generated SQL.

- [ ] **Step 2: Run Python tests and confirm RED**

Run `python3 -m unittest discover -s vcampus-database/demo/full-test-data/tools -p 'test_*.py' -v`; expected failure is missing transfer rows/API or insufficient dedicated count.

- [ ] **Step 3: Implement deterministic generation**

Create dedicated computer department/major/classes only when absent from the generated namespace, use a deterministic `random.Random(seed)`, choose birthdays from an explicit legal date range, derive IDs from a `bulk-transfer-*` prefix, and emit applications from the same records rather than duplicating literals. Add SQL for the batch and target option before applications. Keep the existing credential function and test-account seed untouched.

- [ ] **Step 4: Verify generator and generated SQL**

Run the Python suite, generate into a temporary directory, inspect `counts.json`, and run the existing Java `BuildDataset`/`ValidateDataset` tools against a temporary `.accdb`. Confirm all three seasons and canonical training-plan counts remain unchanged.

- [ ] **Step 5: Commit fixture generation**

Run `git add vcampus-database/demo/full-test-data/tools && git commit -m "test(data): generate large valid major transfer scenarios"`.

### Task 5: Centralize Chinese Swing state rendering

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferStatusText.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferBatchStatusRenderer.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MyMajorTransferPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeProcessingPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferFlowChartPanel.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferStatusTextTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/MajorTransferUiRegressionTest.java`

**Interfaces:**
- Produces `MajorTransferStatusText.status(MajorTransferStatus)`, `.batchStatus(MajorTransferBatchStatus)`, `.decision(MajorTransferDecision)`, and `.reviewStage(MajorTransferReviewStage)`.
- Every method returns a Chinese label for known values and a safe Chinese fallback for null/unknown values.

- [ ] **Step 1: Add failing dictionary and UI tests**

Assert representative mappings `SUBMITTED -> 待审核`, `PENDING_EFFECTIVE -> 待生效`, `APPROVE -> 已通过`, `REJECT -> 已驳回`, and `SOURCE_REVIEW -> 转出学院审核`. Add a regression fixture containing a `SUBMITTED` application and assert the rendered list/detail/timeline text contains `待审核` and not `SUBMITTED`.

- [ ] **Step 2: Run focused client tests and confirm RED**

Run `mvn -pl vcampus-client -am -Dtest=MajorTransferStatusTextTest,MajorTransferUiRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected failure is missing dictionary and the current raw enum text.

- [ ] **Step 3: Implement and route all visible labels through the dictionary**

Keep enum comparisons for behavior. Replace visible `.status()`, `.decision().name()`, `.reviewStage().name()`, and string concatenation in the listed panels with dictionary calls. Preserve existing flow-chart human labels and add Chinese labels for newly covered pending/effective states.

- [ ] **Step 4: Verify client GREEN**

Run the focused client command and inspect the UI regression assertions for list, detail, timeline, batch, and flow-chart surfaces.

- [ ] **Step 5: Commit client localization**

Run `git add vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer && git commit -m "fix(client): localize major transfer statuses"`.

### Task 6: Full verification and dataset alignment

**Files:**
- Modify only generated documentation/count snapshots if verification demonstrates they are stale: `vcampus-database/demo/full-test-data/tools/counts.json`, `vcampus-database/demo/full-test-data/tools/counts.tsv`, `vcampus-database/demo/full-test-data/tools/generated.sql`.

- [ ] **Step 1: Run repository-wide tests with JDK 21**

Run `java -version` and `mvn test` from `/Users/nineloong/课设`. Record the exact exit code and failures; do not claim success from a partial module run.

- [ ] **Step 2: Rebuild and validate the temporary Access dataset**

Run the existing schema initializer and generator into a new temporary database path, run `ValidateDataset`, `SmokeDataset`, the transfer audit SQL, and confirm preserved account rows and transfer counts.

- [ ] **Step 3: Review the exact diff**

Run `git diff --check`, `git status --short`, and `git diff --stat`. Confirm the pre-existing `vcampus-distribution/data/vCampus.accdb` modification remains untouched and no generated release database or build output was accidentally added.

- [ ] **Step 4: Commit only if repository permissions allow it**

Stage only source, tests, SQL, and intended documentation. If `.git/index.lock` remains unavailable, report the verified working-tree changes and the exact commit command for the user instead of changing permissions or using a destructive workaround.
