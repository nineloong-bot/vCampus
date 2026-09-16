# Batch Major Transfer Immediate Effect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-application delayed execution with an atomic target-college batch finalization that updates student records, balances classes, continues student numbers, and drops courses outside the new curriculum.

**Architecture:** A focused batch finalizer owns validation, allocation, numbering, and one Access transaction. Course cleanup is exposed through a narrow `MajorTransferEnrollmentPort` implemented by the course module against canonical training-plan tables. The college client invokes one batch command and has no individual finalization or execution action.

**Tech Stack:** Java 21, Maven, Swing, Microsoft Access/UCanAccess, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-16-batch-major-transfer-effective-design.md`

## Global Constraints

- Use JDK 21 and Maven from the repository root.
- Microsoft Access through UCanAccess remains the only database.
- Training plans remain canonical in `tblTrainingPlan`, `tblTrainingPlanCourse`, and `tblTrainingPlanPrerequisite`.
- Cross-module cleanup goes through `MajorTransferEnrollmentPort`; transfer code must not access course repositories.
- New Java files stay below 200 physical lines and public APIs have JavaDoc.
- Add a failing test before every production behavior.
- Preserve unrelated dirty-worktree changes.

---

### Task 1: Batch protocol and repository primitives

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/FinalizeMajorTransferBatchCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferBatchReadinessView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferBatchFinalizationResult.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferBatchStatus.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepositoryTest.java`

**Interfaces:**
- Produces `FinalizeMajorTransferBatchCommand(String batchId, long expectedVersion)`.
- Produces readiness counts: assessed, rejected, cancelled, unresolved, ready, reason, and batch version.
- Produces finalization result: effective student count, dropped enrollment count, and batch status.
- Produces repository operations to list a batch deterministically, count students in target classes, and optimistically update batch status.

- [ ] **Step 1: Write failing tests** for deterministic application ordering, zero-inclusive class counts, immutable protocol validation, and `CLOSED -> EFFECTIVE` optimistic locking.
- [ ] **Step 2: Run RED:**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement records, add `EFFECTIVE` to the batch enum, and add parameterized repository methods.** Use this guarded update:

```sql
UPDATE tblMajorTransferBatch SET batchStatus=?,rowVersion=rowVersion+1,updatedAt=?
WHERE batchId=? AND batchStatus=? AND rowVersion=?
```

- [ ] **Step 4: Run the Task 1 command and verify GREEN.**
- [ ] **Step 5: Commit:**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/repository vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/repository
git commit -m "feat: add transfer batch finalization protocol"
```

### Task 2: Course reconciliation port

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferEnrollmentPort.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/integration/AccessMajorTransferEnrollmentAdapter.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/CourseRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessCourseRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessEnrollmentRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/integration/AccessMajorTransferEnrollmentAdapterTest.java`

**Interfaces:**
- Produces `Reconciliation reconcile(Connection, String studentId, String targetMajorCode, int cohortYear, String operatorUserId, Instant occurredAt)`.
- `Reconciliation` exposes `int droppedEnrollments()`.

- [ ] **Step 1: Write a failing integration test** with one retained plan course, one removed normal course, and one removed retake. Assert statuses, normal/retake counters, two `MAJOR_TRANSFER_AUTO_DROP` audits, and result count.
- [ ] **Step 2: Run RED:**

```bash
mvn -pl vcampus-server -am -Dtest=AccessMajorTransferEnrollmentAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement the adapter** using `AccessCurriculumRepository` and `AccessCourseRepository`. Retain a current enrollment when its course code appears anywhere in semesters 1–8 of the published target plan. Use the supplied connection and never open or commit a nested transaction.
- [ ] **Step 4: Add missing-plan and caller-rollback tests, then run GREEN.**
- [ ] **Step 5: Commit:**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/course vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferEnrollmentPort.java vcampus-server/src/test/java/edu/seu/vcampus/server/course/integration
git commit -m "feat: reconcile enrollments after major transfer"
```

### Task 3: Balanced class assignment planner

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferBatchPlanner.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferBatchPlannerTest.java`

**Interfaces:**
- Consumes assessed applications, options, students, active target classes, and current class counts.
- Produces immutable assignments with application, student, option, target class, target major, and number-sequence key.

- [ ] **Step 1: Write failing tests** for uneven starting counts `28,30,30`, deterministic ties, grouping by major/cohort, missing matching-year classes, and mixed target departments.
- [ ] **Step 2: Run RED:**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferBatchPlannerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement a pure planner.** Sort applications by ID; choose the smallest projected class size, then `classNumber`, then class ID. Emit sequence keys as `STUDENT_NUMBER:<majorCode>:<yy>:<classNumber>`.
- [ ] **Step 4: Run GREEN and commit:**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferBatchPlanner.java vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferBatchPlannerTest.java
git commit -m "feat: plan balanced transfer class assignments"
```

### Task 4: Atomic target-college batch finalizer

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferBatchFinalizer.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/security/MajorTransferCollegeAuthorizationService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/UnifiedModuleRegistry.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferBatchFinalizerTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferStudentWorkflowTest.java`

**Interfaces:**
- Produces `getBatchReadiness(String batchId, String trustedDepartmentId)` and `finalizeBatch(String adminUserId, FinalizeMajorTransferBatchCommand command, String trustedDepartmentId)`.
- Removes service-level per-application `finalizeApproval` and `execute` after callers migrate.

- [ ] **Step 1: Write a failing happy-path test.** One CS administrator finalizes a closed CS batch; all assessed students immediately receive balanced CS classes, continued target numbers, `EFFECTIVE` applications, audits, and reconciled enrollments despite future effective/publicity dates.
- [ ] **Step 2: Write failing atomicity tests** for unresolved formal applications, quota excess, mixed departments, wrong college, stale batch version, changed source class, missing curriculum, and duplicate submission. Assert no partial changes to students, sequences, courses, counters, applications, batch, or audits.
- [ ] **Step 3: Run RED:**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferBatchFinalizerTest,MajorTransferStudentWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 4: Implement the finalizer** with fixed lock order: batch, sorted applications, sorted students, sorted number sequences. Revalidate ownership and state inside one transaction; generate numbers through `AccessStudentNumberGenerator`; reconcile courses; write final and execution reviews; update applications; update batch last.
- [ ] **Step 5: Keep new files below 200 lines** by delegating pure allocation to the planner and retaining `MajorTransferServiceImpl` as façade.
- [ ] **Step 6: Run GREEN and all major-transfer server tests:**

```bash
mvn -pl vcampus-server -am -Dtest='*MajorTransfer*Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 7: Commit:**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/UnifiedModuleRegistry.java vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer
git commit -m "feat: finalize major transfers atomically by batch"
```

### Task 5: Handler and college batch UI migration

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferHandlers.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferApplicationHandlers.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferConfigurationHandlers.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/service/StudentClientService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeActions.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeProcessingPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferStatusText.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferExecutionPanel.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferRoleAuthorizationTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeProcessingPanelTest.java`

**Interfaces:**
- Adds `MAJOR_TRANSFER_GET_BATCH_READINESS` and `MAJOR_TRANSFER_FINALIZE_BATCH`.
- Removes `MAJOR_TRANSFER_FINALIZE` and `MAJOR_TRANSFER_EXECUTE`.

- [ ] **Step 1: Write failing authorization tests** proving only the active administrator of the batch target college reaches readiness/finalization with a trusted department ID.
- [ ] **Step 2: Write failing Swing tests** proving individual final/execute buttons are absent and one `major-transfer.finalize-batch` button displays counts, disables with the server reason, confirms once, submits once, and refreshes on success.
- [ ] **Step 3: Run RED:**

```bash
mvn -pl vcampus-client -am -Dtest=MajorTransferRoleAuthorizationTest,MajorTransferCollegeProcessingPanelTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 4: Implement handler/client migration** while preserving source review, qualification review, scoring, rejection, and cancellation.
- [ ] **Step 5: Run GREEN and commit:**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/handler vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/handler vcampus-client/src/main/java/edu/seu/vcampus/client/student vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/ui
git commit -m "feat: add college batch transfer finalization UI"
```

### Task 6: Fixture and full verification

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/major_transfer.py`
- Modify: `vcampus-database/demo/full-test-data/tools/test_generation.py`
- Modify: `vcampus-database/demo/full-test-data/tools/ValidateDataset.java`
- Modify: generated snapshots in `vcampus-database/demo/full-test-data/tools/`
- Modify: `vcampus-database/demo/full-test-data/docs/验证记录.txt`

**Interfaces:**
- Provides one closed, fully processed, single-target-college ready batch and one unresolved blocked batch.

- [ ] **Step 1: Write failing generator tests** for single-college ownership, ready-state completeness/quota, and an unresolved blocker fixture.
- [ ] **Step 2: Run RED:**

```bash
python3 -m unittest discover -s vcampus-database/demo/full-test-data/tools -p 'test_*.py' -v
```

- [ ] **Step 3: Update and regenerate fixtures:**

```bash
python3 vcampus-database/demo/full-test-data/tools/generate.py vcampus-database/demo/full-test-data/tools
```

- [ ] **Step 4: Build a new temporary Access database** with `BuildDataset.java`, run `ValidateDataset.java`, and require no mixed-college batch, no invalid ready-state application, and no active out-of-plan enrollment for effective transfers.
- [ ] **Step 5: Run final verification:**

```bash
mvn -pl vcampus-server,vcampus-client -am test
git diff --check
```

If sandbox restrictions block Mockito self-attach or loopback sockets, record those exact failures separately; focused tests and Access validation must still pass.

- [ ] **Step 6: Review status/diff and commit only feature files.** Do not include pre-existing distribution databases, JARs, config, shop work, or unrelated files.

```bash
git add vcampus-database/demo/full-test-data
git commit -m "test: cover atomic batch major transfer data"
```
