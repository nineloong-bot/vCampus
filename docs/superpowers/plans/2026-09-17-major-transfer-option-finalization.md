# Major Transfer Option Finalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make final review, rollback, and effectuation independent per transfer option, disable empty-option finalization, and preserve exactly one student application per batch while exposing valid same-college and cross-college choices.

**Architecture:** Replace the college lifecycle boundary with an option lifecycle row keyed by `optionId`. Resolve the caller's college only for authorization, then perform readiness, ranking, prepared-transfer handling, rollback, and effectuation strictly against that option. The college UI selects an owned option before loading readiness; the student UI still offers multiple candidates but persists one application row per `(batchId, studentId)`.

**Tech Stack:** Java 21, Swing, Maven, JUnit 5, AssertJ, Microsoft Access via UCanAccess.

**Spec:** `docs/superpowers/specs/2026-09-17-major-transfer-option-finalization-design.md`

## Global Constraints

- Build and run with JDK 21 from the repository root.
- Microsoft Access through UCanAccess remains the only production database.
- Treat `vcampus-database/schema` and `vcampus-database/seed` as schema source of truth.
- New Java files must not exceed 200 physical lines; public APIs require JavaDoc.
- Add a failing automated test before each production behavior change.
- Do not replace the user's current `vcampus-distribution/data/vCampus.accdb`; rebuild and validate only in a temporary path until separately approved.
- One student may have only one application row per batch; changing a draft replaces its `optionId`.

---

### Task 1: Define option-scoped protocol contracts

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferOptionFinalizationStatus.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferOptionReadinessView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/FinalizeMajorTransferOptionCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/EffectiveMajorTransferOptionCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/RollbackMajorTransferOptionCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferOptionReviewResult.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferOptionEffectResult.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferOptionRollbackResult.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferOptionFinalizationContractTest.java`

**Interfaces:**
- Produces: commands `(String optionId, long expectedOptionVersion)`.
- Produces: readiness containing `optionId`, `batchId`, target department/major identity and name, counts, booleans, reason, and `optionVersion`.
- Produces: result records containing option identity, affected counts, status, and new version.

- [ ] **Step 1: Write failing constructor-contract tests**

```java
assertThatThrownBy(() -> new FinalizeMajorTransferOptionCommand("", 0))
        .isInstanceOf(IllegalArgumentException.class);
assertThat(new FinalizeMajorTransferOptionCommand("option-ai", 3).optionId())
        .isEqualTo("option-ai");
```

- [ ] **Step 2: Run the common contract test and verify RED**

Run: `mvn -pl vcampus-common -Dtest=MajorTransferOptionFinalizationContractTest test`
Expected: compilation fails because option-scoped contracts do not exist.

- [ ] **Step 3: Add immutable records and enum with validation and JavaDoc**

```java
public record FinalizeMajorTransferOptionCommand(String optionId, long expectedOptionVersion)
        implements Serializable {
    public FinalizeMajorTransferOptionCommand {
        if (optionId == null || optionId.isBlank()) throw new IllegalArgumentException("optionId");
        if (expectedOptionVersion < 0) throw new IllegalArgumentException("expectedOptionVersion");
    }
}
```

- [ ] **Step 4: Run the common contract test and verify GREEN**

Run: `mvn -pl vcampus-common -Dtest=MajorTransferOptionFinalizationContractTest test`
Expected: all contract tests pass.

- [ ] **Step 5: Commit the protocol slice**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer \
  vcampus-common/src/test/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferOptionFinalizationContractTest.java
git commit -m "feat(transfer): add option finalization contracts"
```

### Task 2: Persist option lifecycle and option-scoped prepared data

**Files:**
- Modify: `vcampus-database/schema/025_major_transfer.sql`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ApplicationSchemaInitializer.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferOptionFinalizationRepository.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepositoryTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/ApplicationSchemaInitializerTest.java`

**Interfaces:**
- Produces: `findOrCreate(Connection, OptionRow, Instant)` returning `OptionFinalizationRow`.
- Produces: `updateStatus(Connection, optionId, from, to, expectedVersion, operator, now)`.
- Produces: `replacePrepared(Connection, optionId, rows)`, `listPrepared(Connection, optionId)`, and `deletePrepared(Connection, optionId)`.

- [ ] **Step 1: Add failing schema and repository tests**

```java
assertThat(tableNames(connection)).contains("TBLMAJORTRANSFEROPTIONFINALIZATION");
repository.replacePrepared(connection, "option-ai", List.of(aiPrepared));
repository.replacePrepared(connection, "option-se", List.of(sePrepared));
repository.deletePrepared(connection, "option-ai");
assertThat(repository.listPrepared(connection, "option-se")).hasSize(1);
```

- [ ] **Step 2: Run focused server tests and verify RED**

Run: `mvn -pl vcampus-server -am -Dtest=ApplicationSchemaInitializerTest,MajorTransferRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: missing table/repository API failures.

- [ ] **Step 3: Add schema and focused repository**

```sql
CREATE TABLE tblMajorTransferOptionFinalization (
    optionId VARCHAR(36) PRIMARY KEY,
    finalizationStatus VARCHAR(16) NOT NULL,
    rowVersion LONG NOT NULL,
    reviewedBy VARCHAR(36), reviewedAt DATETIME,
    effectiveBy VARCHAR(36), effectiveAt DATETIME,
    createdAt DATETIME NOT NULL, updatedAt DATETIME NOT NULL
);
```

Prepared-row queries must join `tblMajorTransferPreparedTransfer p` to
`tblMajorTransferApplication a` on `applicationId` and filter `a.optionId=?`.

- [ ] **Step 4: Implement idempotent existing-database initialization**

Add the new table to the normal schema installer. `findOrCreate` derives an initial status from existing option applications: `EFFECTIVE` wins over `PENDING_EFFECTIVE`, otherwise `PROCESSING`.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run: `mvn -pl vcampus-server -am -Dtest=ApplicationSchemaInitializerTest,MajorTransferRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: all focused tests pass.

- [ ] **Step 6: Commit the persistence slice**

```bash
git add vcampus-database/schema/025_major_transfer.sql \
  vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ApplicationSchemaInitializer.java \
  vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferOptionFinalizationRepository.java \
  vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer
git commit -m "feat(transfer): persist option finalization state"
```

### Task 3: Make readiness and finalization option-scoped

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferOptionReadinessEvaluator.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferOptionFinalizer.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferStudentWorkflowTest.java`

**Interfaces:**
- Consumes: Task 1 protocol records and Task 2 repository.
- Produces: `getOptionReadiness(optionId, trustedDepartmentId)`.
- Produces: `finalizeOption`, `rollbackOption`, and `effectiveOption` service methods.

- [ ] **Step 1: Add failing workflow tests for independent options**

```java
var ai = service.finalizeOption("cs-admin",
        new FinalizeMajorTransferOptionCommand("option-ai", 0), "dept-cse");
assertThat(applicationFor("option-ai").status()).isEqualTo(PENDING_EFFECTIVE);
assertThat(applicationFor("option-se").status()).isEqualTo(ASSESSED);
```

Add separate tests asserting zero applications and zero `ASSESSED` applications return
`TRANSFER_OPTION_NO_APPLICATIONS` and `TRANSFER_OPTION_NO_ASSESSED_APPLICATIONS`.

- [ ] **Step 2: Run the workflow test and verify RED**

Run: `mvn -pl vcampus-server -am -Dtest=MajorTransferStudentWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: option-scoped service methods are missing.

- [ ] **Step 3: Implement readiness with explicit empty guards**

```java
if (applications.isEmpty()) reason = "该专业暂无转入申请";
else if (unresolved > 0) reason = "还有 " + unresolved + " 份申请未处理完毕";
else if (assessed == 0 && status == PROCESSING) reason = "该专业没有待终审申请";
```

- [ ] **Step 4: Implement option-only finalization, rollback, and effectuation**

Lock `TRANSFER_BATCH`, `TRANSFER_OPTION_FINALIZATION:<optionId>`, sorted application IDs, sorted student IDs, then run all authorization and state checks inside the same transaction. Pass a one-entry option map to ranking/planning and touch only prepared rows joined to the selected `optionId`.

- [ ] **Step 5: Run the workflow test and verify GREEN**

Run: `mvn -pl vcampus-server -am -Dtest=MajorTransferStudentWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: option independence, empty guards, rollback, and effectuation tests pass.

- [ ] **Step 6: Commit the service slice**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service \
  vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferStudentWorkflowTest.java
git commit -m "feat(transfer): finalize applications per option"
```

### Task 4: Enforce option authorization at handlers

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/security/MajorTransferCollegeAuthorizationService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferApplicationHandlers.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/security/MajorTransferCollegeAuthorizationServiceTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferRoleAuthorizationTest.java`

**Interfaces:**
- Consumes: `requireTargetApprovalForOption(userId, optionId)`.
- Produces commands `MAJOR_TRANSFER_GET_OPTION_READINESS`, `MAJOR_TRANSFER_FINALIZE_OPTION`, `MAJOR_TRANSFER_EFFECTIVE_OPTION`, and `MAJOR_TRANSFER_ROLLBACK_OPTION`.

- [ ] **Step 1: Add failing handler tests**

Verify a math administrator receives forbidden for `option-ai`, while the CS administrator reaches `finalizeOption` with trusted department `dept-cse`.

- [ ] **Step 2: Run focused authorization tests and verify RED**

Run: `mvn -pl vcampus-server -am -Dtest=MajorTransferCollegeAuthorizationServiceTest,MajorTransferRoleAuthorizationTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: new routes/method calls are absent.

- [ ] **Step 3: Register option routes and remove batch finalization routes**

Each route first calls `requireTargetApprovalForOption`, then passes the trusted department into the option-scoped service method. Never accept a department ID from the request body.

- [ ] **Step 4: Run focused authorization tests and verify GREEN**

Run the same Maven command; expected all tests pass.

- [ ] **Step 5: Commit the authorization slice**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer \
  vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer
git commit -m "fix(transfer): authorize final review by option"
```

### Task 5: Preserve one application while exposing valid candidates

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferStudentWorkflowTest.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MyMajorTransferPanel.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/MajorTransferUiRegressionTest.java`

**Interfaces:**
- Produces available options where `active && receiveQuota > 0 && targetMajorId != currentMajorId`.
- Preserves the unique `(batchId, studentId)` application invariant.

- [ ] **Step 1: Add failing candidate and duplicate-application tests**

For a CS student, assert candidate names contain AI, Software Engineering, and Mathematics, but not the current CS major. Attempting a second create command in the same batch must return `TRANSFER_DUPLICATE_APPLICATION`; updating the existing draft must keep one row and replace its `optionId`.

- [ ] **Step 2: Run focused workflow/UI tests and verify RED where filtering is incomplete**

Run: `mvn -pl vcampus-client -am -Dtest=MajorTransferStudentWorkflowTest,MajorTransferUiRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Apply the quota filter and explicit closed-batch explanation**

Keep the combo single-select. When the active/history batch is closed, show `报名已截止，不能修改目标专业` and disable editing without removing cross-college candidates from the workspace payload.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the same Maven command; expected all relevant tests pass.

- [ ] **Step 5: Commit the student-choice slice**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java \
  vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferStudentWorkflowTest.java \
  vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MyMajorTransferPanel.java \
  vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/MajorTransferUiRegressionTest.java
git commit -m "fix(transfer): enforce one application across candidate options"
```

### Task 6: Add professional finalization controls to the college UI

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/service/StudentClientService.java`
- Replace: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeBatchFinalizer.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeProcessingPanel.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/MajorTransferCollegeWorkspaceTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/MajorTransferRoleUiTest.java`

**Interfaces:**
- Consumes: Task 1 option contracts and Task 4 routes.
- Produces: an owned-option combo and option-specific readiness/actions.

- [ ] **Step 1: Add failing UI tests**

Assert the toolbar contains `major-transfer.finalization-option`; selecting `option-ai` requests AI readiness. A response with reason `该专业暂无转入申请` leaves finalize/effect/rollback disabled. Switching to `option-se` cannot reuse AI's version.

- [ ] **Step 2: Run focused client tests and verify RED**

Run: `mvn -pl vcampus-client -am -Dtest=MajorTransferCollegeWorkspaceTest,MajorTransferRoleUiTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: selector and option service APIs are missing.

- [ ] **Step 3: Implement the option selector and option action controller**

Populate the selector from `listTransferOptions(batchId)` for the authenticated college. Clear versions on every selection change, load readiness by selected `optionId`, and label actions `终审所选专业`, `生效所选专业`, and `回退所选专业`.

- [ ] **Step 4: Run focused client tests and verify GREEN**

Run the same Maven command; expected all focused tests pass.

- [ ] **Step 5: Commit the client slice**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/student \
  vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer
git commit -m "feat(ui): select a major for transfer finalization"
```

### Task 7: Align generated data, remove legacy production use, and verify

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/major_transfer.py`
- Modify: `vcampus-database/demo/full-test-data/tools/test_generation.py`
- Modify: `vcampus-database/demo/full-test-data/tools/ValidateDataset.java`
- Modify: `vcampus-database/demo/full-test-data/tools/counts.json`
- Modify: `vcampus-database/demo/full-test-data/tools/counts.tsv`
- Regenerate: `vcampus-database/demo/full-test-data/tools/generated.sql`
- Delete after references are gone: old college-scoped command/result/readiness/status Java types and finalizer/repository classes.

**Interfaces:**
- Produces a fresh dataset containing one option-finalization row per seeded option.
- Leaves the user's current release `.accdb` untouched unless separately authorized.

- [ ] **Step 1: Add failing generator and validator expectations**

Expect `tblMajorTransferOptionFinalization` rows keyed by each seeded option and no generated `tblMajorTransferBatchCollege` rows.

- [ ] **Step 2: Run generator tests and verify RED**

Run: `python3 -m unittest vcampus-database/demo/full-test-data/tools/test_generation.py`
Expected: option-finalization dataset expectations fail.

- [ ] **Step 3: Update generator, validator, snapshots, and generated SQL**

Generate deterministic `PROCESSING` rows for seeded options and update exact table counts.

- [ ] **Step 4: Rebuild and validate a temporary Access database**

Use the repository's dataset build tooling with a `mktemp -d` output path, then run `ValidateDataset` against that temporary `.accdb`. Do not copy it over `vcampus-distribution/data/vCampus.accdb`.

- [ ] **Step 5: Run complete verification**

Run with JDK 21:

```bash
mvn test
mvn -DskipTests package
```

Expected: Maven reactor succeeds; if an unrelated pre-existing client UI test fails, record its exact test and keep the new focused suites green.

- [ ] **Step 6: Review exact diff and commit final alignment**

```bash
git diff --check
git status --short
git add vcampus-database vcampus-common vcampus-server vcampus-client docs
git commit -m "feat: finalize major transfers by target option"
```
