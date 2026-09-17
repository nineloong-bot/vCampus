# College-Scoped Major Transfer Finalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support independent final review, rollback, and one-time effectuation for every target college inside one school-wide major-transfer batch.

**Architecture:** Persist a college lifecycle row per `(batchId, targetDepartmentId)` and a prepared transfer snapshot per accepted application. Keep application states unchanged, scope every operation by the authenticated college, preflight and persist assignments during review, and atomically apply only the prepared college during effectuation.

**Tech Stack:** Java 21, Maven, Swing, Microsoft Access/UCanAccess, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-17-college-major-transfer-finalization-design.md`

## Global Constraints

- Build and test with JDK 21 from the repository root.
- Microsoft Access through UCanAccess is the only supported database.
- `vcampus-database/schema` and `vcampus-database/seed` are the schema source of truth.
- New Java files must remain below 200 physical lines and public APIs require useful JavaDoc.
- Write a failing automated test before each production behavior change.
- College scope comes from the authenticated administrator, never from a trusted client field.
- Writes retain request deduplication, stable lock order, optimistic versions, and transaction-time rule checks.

---

### Task 1: Persist college lifecycle and prepared transfers

**Files:**
- Modify: `vcampus-database/schema/025_major_transfer.sql`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepositoryTest.java`

**Interfaces:**
- Produces: `CollegeBatchRow`, `PreparedTransferRow`, `findOrCreateCollegeBatch`, `findCollegeBatch`, `updateCollegeBatchStatus`, `replacePreparedTransfers`, `listPreparedTransfers`, and `deletePreparedTransfers`.
- Consumes: existing `BatchRow`, `OptionRow`, and UCanAccess transaction conventions.

- [x] **Step 1: Write failing repository tests**

Add tests proving two colleges can have independent lifecycle rows in one batch, lifecycle updates require matching status/version, and prepared rows can be replaced and read by college.

```java
@Test void collegeBatchStatesAreIndependent() {
    repository.findOrCreateCollegeBatch(connection, "batch-1", "dept-1", NOW);
    repository.findOrCreateCollegeBatch(connection, "batch-1", "dept-2", NOW);
    assertThat(repository.updateCollegeBatchStatus(connection, "batch-1", "dept-1",
            PROCESSING, REVIEWED, 0, "admin-1", NOW)).isOne();
    assertThat(repository.findCollegeBatch(connection, "batch-1", "dept-2"))
            .get().extracting(CollegeBatchRow::status).isEqualTo(PROCESSING);
}
```

- [x] **Step 2: Run the repository test and verify RED**

Run: `mvn -q -pl vcampus-server -am -Dtest=MajorTransferRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation fails because the lifecycle and preparation repository APIs do not exist.

- [x] **Step 3: Add schema tables and focused repository methods**

Create `tblMajorTransferBatchCollege` with a unique `(batchId,targetDepartmentId)` index and `tblMajorTransferPreparedTransfer` with a unique `applicationId` index. Add immutable repository row records and parameterized CRUD methods; do not expose SQL outside the repository.

```java
public record CollegeBatchRow(String batchId, String targetDepartmentId,
        MajorTransferCollegeStatus status, long rowVersion,
        String reviewedBy, Instant reviewedAt, String effectiveBy,
        Instant effectiveAt, Instant createdAt, Instant updatedAt) { }

public record PreparedTransferRow(String applicationId, String batchId,
        String targetDepartmentId, String targetMajorId, String targetClassId,
        int targetCohortYear, long studentVersion, long applicationVersion,
        Instant preparedAt) { }
```

- [x] **Step 4: Run repository tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add vcampus-database/schema/025_major_transfer.sql \
  vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepository.java \
  vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepositoryTest.java
git commit -m "feat(student): persist college transfer lifecycle"
```

### Task 2: Define precise college workflow protocol

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferCollegeStatus.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferCollegeReadinessView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferBatchReviewResult.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferBatchEffectResult.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferBatchRollbackResult.java`
- Modify: `FinalizeMajorTransferBatchCommand.java`, `EffectiveMajorTransferBatchCommand.java`, `RollbackMajorTransferBatchCommand.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferReviewStage.java`
- Remove after call sites migrate: `MajorTransferBatchReadinessView.java`, `MajorTransferBatchFinalizationResult.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferCollegeProtocolTest.java`

**Interfaces:**
- Produces: `MajorTransferCollegeStatus { PROCESSING, REVIEWED, EFFECTIVE }` and three semantically distinct result records.
- Commands consume `batchId` and `expectedCollegeVersion` only; department identity remains server-resolved.

- [x] **Step 1: Write protocol validation tests**

```java
@Test void readinessRepresentsReviewedZeroAdmissionCollege() {
    var view = new MajorTransferCollegeReadinessView("batch", "dept", REVIEWED,
            0, 0, 4, 1, 0, false, true, true, null, 3);
    assertThat(view.pendingEffective()).isZero();
    assertThat(view.canEffect()).isTrue();
}
```

- [x] **Step 2: Run the protocol test and verify RED**

Run: `mvn -q -pl vcampus-common -Dtest=MajorTransferCollegeProtocolTest test`

Expected: compilation fails because the new protocol types do not exist.

- [x] **Step 3: Implement immutable protocol records**

Validate nonblank identifiers, nonnegative counts and versions, and nonnull status. Rename command component `expectedVersion` to `expectedCollegeVersion`, and add `FINAL_APPROVAL_ROLLBACK` as a distinct immutable audit stage so rollback never deletes the original approval. Keep each new file under 200 lines with JavaDoc.

- [x] **Step 4: Run common tests and verify GREEN**

Run: `mvn -q -pl vcampus-common test`. Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer \
  vcampus-common/src/test/java/edu/seu/vcampus/common/student/majortransfer
git commit -m "refactor(student): define college transfer workflow protocol"
```

### Task 3: Enforce irreversible school-wide batch lifecycle

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferBatchStatus.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferStudentWorkflowTest.java`

**Interfaces:**
- Consumes: existing `saveBatch` and `SaveMajorTransferBatchCommand`.
- Produces: server-side transition validation allowing only `DRAFT -> DRAFT|OPEN` and `OPEN -> OPEN|CLOSED`; `CLOSED` is immutable.

- [x] **Step 1: Write failing lifecycle tests**

Add tests that reopening `CLOSED`, changing dates on `CLOSED`, and saving `EFFECTIVE` are rejected with `TRANSFER_BATCH_CLOSED` or `TRANSFER_STATE_INVALID`.

- [x] **Step 2: Run the workflow test and verify RED**

Run: `mvn -q -pl vcampus-server -am -Dtest=MajorTransferStudentWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: at least the reopen test fails because `saveBatch` currently accepts arbitrary target status.

- [x] **Step 3: Implement the batch transition guard**

Add a focused private validator or package-private `MajorTransferBatchStateMachine`. Reject `EFFECTIVE` on all new writes and preserve read compatibility until regenerated data removes it.

- [x] **Step 4: Run workflow tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferBatchStatus.java \
  vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java \
  vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferStudentWorkflowTest.java
git commit -m "fix(student): make closed transfer batches irreversible"
```

### Task 4: Implement college-scoped review preparation, rollback, and effectuation

**Files:**
- Modify: `MajorTransferBatchFinalizer.java`
- Modify: `MajorTransferBatchReadinessEvaluator.java`
- Modify: `MajorTransferService.java`
- Modify: `MajorTransferServiceImpl.java`
- Modify: `MajorTransferRepository.java`
- Create if needed to stay below 200 lines: `MajorTransferBatchPreparer.java`
- Test: `MajorTransferStudentWorkflowTest.java`
- Test: `MajorTransferCollegeQueryTest.java`

**Interfaces:**
- `getBatchReadiness(String batchId, String trustedDepartmentId)` returns `MajorTransferCollegeReadinessView`.
- `finalizeBatch(...)` returns `MajorTransferBatchReviewResult`.
- `rollbackBatch(...)` returns `MajorTransferBatchRollbackResult`.
- `effectiveBatch(...)` returns `MajorTransferBatchEffectResult`.

- [x] **Step 1: Write failing multi-college and preparation tests**

Create one batch with options/applications for `dept-1` and `dept-2`. Assert reviewing `dept-1` changes only its applications, writes prepared assignments, leaves student enrollment untouched, and does not block `dept-2`.

Add RED tests for missing target class, missing curriculum, stale student version, unresolved applications, zero-admission review, rollback, repeated effect, and effect after preparation becomes stale.

- [x] **Step 2: Run focused service tests and verify RED**

Run: `mvn -q -pl vcampus-server -am -Dtest=MajorTransferStudentWorkflowTest,MajorTransferCollegeQueryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: failures show current batch-wide queries and missing prepared snapshots.

- [x] **Step 3: Scope readiness and locking by target college**

Replace all finalizer/readiness calls to `listApplicationsByBatch` with repository queries joining the option and filtering `o.targetDepartmentId=?`. Lock `TRANSFER_BATCH`, `TRANSFER_BATCH_COLLEGE`, then sorted application and student keys for that college only.

- [x] **Step 4: Implement review preparation**

Move planning and all prerequisite validation before application status changes. Persist one prepared row per accepted application, transition applications to `PENDING_EFFECTIVE`, append `FINAL_APPROVAL`, and update college `PROCESSING -> REVIEWED` in one transaction. Permit an empty prepared set when every formal application is rejected or cancelled.

- [x] **Step 5: Implement non-destructive rollback**

Require `REVIEWED`, restore only the authenticated college's pending applications, delete prepared snapshots, append a new immutable rollback review/audit event, and update `REVIEWED -> PROCESSING`. Check every update count.

- [x] **Step 6: Implement prepared effectuation**

Require `REVIEWED`; compare every prepared snapshot against current application, student, class, major, curriculum, and college version. Apply prepared target classes, allocate student numbers, reconcile enrollment, update applications, and transition the college to `EFFECTIVE` atomically. Never update the school-wide batch to `EFFECTIVE`.

- [x] **Step 7: Block options and student applications after college effectuation**

In `saveOption`, `saveDraft`, and `submit`, load or initialize the target college row and reject `EFFECTIVE` with `TRANSFER_COLLEGE_ALREADY_EFFECTIVE` inside the same transaction.

- [x] **Step 8: Run focused service tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [x] **Step 9: Commit**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer \
  vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer
git commit -m "feat(student): finalize transfers independently by college"
```

### Task 5: Update handlers and Swing workflow

**Files:**
- Modify: `MajorTransferApplicationHandlers.java`
- Modify: `MajorTransferHandlers.java`
- Modify: `MajorTransferCollegeAuthorizationService.java`
- Modify: `StudentClientService.java`
- Modify: `MajorTransferCollegeBatchFinalizer.java`
- Modify: `MajorTransferCollegeProcessingPanel.java`
- Test: `MajorTransferRoleAuthorizationTest.java`
- Test: `MajorTransferRoleUiTest.java`
- Test: `MajorTransferCollegeWorkspaceTest.java`

**Interfaces:**
- Produces client methods `reviewTransferBatch`, `rollbackTransferBatch`, and `effectiveTransferBatch` with distinct response types.
- Authorization accepts a batch when it has at least one active option owned by the authenticated college; it does not require every option in the batch to belong to that college.

- [x] **Step 1: Write failing authorization and UI tests**

Assert that two target colleges can call readiness/review for the same batch, unrelated colleges are forbidden, the command catalog includes review/rollback/effect, and button visibility follows `PROCESSING`, `REVIEWED`, and `EFFECTIVE` exactly.

- [x] **Step 2: Run handler and UI tests and verify RED**

Run: `mvn -q -pl vcampus-server,vcampus-client -am -Dtest=MajorTransferRoleAuthorizationTest,MajorTransferRoleUiTest,MajorTransferCollegeWorkspaceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: failures show all-options authorization and the missing rollback client/UI path.

- [x] **Step 3: Implement target-college batch authorization**

Change `requireTargetApprovalForBatch` to require existence of at least one active option whose `targetDepartmentId` equals the server-resolved college. Retain service-layer transaction-time scope checks.

- [x] **Step 4: Wire distinct commands and result types**

Update Handler registrations, `ADMIN_COMMANDS`, service client methods, and response generics. Preserve `StudentWriteExecutor` request deduplication for all three writes.

- [x] **Step 5: Add rollback and state-driven controls**

Add a named `major-transfer.rollback-batch` button. Render readiness counts including `pendingEffective`; enable only actions explicitly permitted by `canReview`, `canRollback`, and `canEffect`; show the terminal message for `EFFECTIVE`.

- [x] **Step 6: Run handler and UI tests and verify GREEN**

Run the command from Step 2. Expected: PASS. If Mockito attachment is blocked by the sandbox, rerun with the repository's approved JDK agent configuration or record the environmental limitation without treating it as a product failure.

- [x] **Step 7: Commit**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/handler \
  vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/security \
  vcampus-client/src/main/java/edu/seu/vcampus/client/student \
  vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/handler \
  vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer
git commit -m "feat(client): expose college transfer review controls"
```

### Task 6: Rebuild and verify the canonical Access dataset

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/major_transfer.py`
- Modify: `vcampus-database/demo/full-test-data/tools/test_generation.py`
- Modify: `vcampus-database/demo/full-test-data/tools/ValidateDataset.java`
- Modify generated dataset snapshots and user documentation affected by the new tables
- Replace after validation: `vcampus-distribution/data/vCampus.accdb`

**Interfaces:**
- Produces deterministic college lifecycle rows for every generated batch/target-college pair.
- Existing open demo batch produces a `PROCESSING` college row and no prepared transfers.

- [x] **Step 1: Write failing generator assertions**

Extend `test_generation.py` to require one college lifecycle row for the demo batch, matching option departments, `PROCESSING` status, version zero, and no prepared rows.

- [x] **Step 2: Run generator tests and verify RED**

Run: `python3 -m unittest vcampus-database/demo/full-test-data/tools/test_generation.py`

Expected: failure because generated rows omit the new tables.

- [x] **Step 3: Generate lifecycle rows and update validators**

Update `major_transfer.py`, table ordering, count snapshots, SQL output, scenario manifest, validator queries, and user-facing workflow text. Do not hand-edit generated SQL or counts independently of the generator.

- [x] **Step 4: Run generator and database validators**

Use the documented full-test-data build command to generate a temporary `.accdb`; run `ValidateDataset` and smoke checks against that temporary path. Expected: all schema, count, relationship, and workflow assertions pass.

- [x] **Step 5: Replace the release database only after validation**

Copy the validated temporary database to `vcampus-distribution/data/vCampus.accdb` using the repository's rebuild procedure, preserving any documented backup requirement.

- [x] **Step 6: Run complete verification**

Run: `mvn test`

Expected: PASS under JDK 21. Socket tests require loopback binding permission; report any sandbox-only denial separately. Then run `git diff --check` and inspect `git status --short` to exclude targets, logs, locks, backups, caches, and secrets.

- [x] **Step 7: Commit**

```bash
git add vcampus-database vcampus-distribution/data/vCampus.accdb vcampus-distribution/docs
git commit -m "data: rebuild college-scoped transfer dataset"
```
