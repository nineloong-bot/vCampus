# Major Transfer Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a realistic undergraduate major-transfer workflow from configurable application batches through staged review, assessment, publication, and atomic effective-date enrollment change.

**Architecture:** Add a separate `majortransfer` contract and server subdomain under the existing student module instead of extending `tblStudentProfileApplication`. Reuse existing socket routing, session authorization, request deduplication, resource locks, Access transactions, organization repositories, and `tblStudentChange`; expose a student workspace and an administrator workflow through dedicated Swing tabs.

**Tech Stack:** Java 21, Swing, CompletableFuture, Java serialization socket commands, Microsoft Access/UCanAccess, JUnit 5, AssertJ, Mockito, Maven.

**Spec:** `docs/superpowers/specs/2026-09-08-major-transfer-workflow-design.md`

## Global Constraints

- Only `UNDERGRADUATE` students participate in the first version.
- One student may select exactly one target major in each batch; no adjustment between majors.
- Ordinary profile applications remain unchanged and cannot update department, major, or class.
- Only `SUBMITTED` applications without a source-college decision can be withdrawn by the student.
- Final approval enters `PENDING_EFFECTIVE`; formal enrollment changes only through the effective-date execution command.
- Academic warning, misconduct, and special-admission eligibility are explicit administrator attestations until authoritative source modules exist.
- Score weights total 100; cutoff ties are all proposed and may exceed the nominal quota.
- Each application has at most three PDF/JPEG/PNG attachments of at most 5 MiB each.
- All writes are deduplicated, version checked, serialized by resource locks, and audited.
- Preserve every existing uncommitted file; edit only listed files and use `apply_patch` for hand-written changes.
- Replace corresponding artifacts under `vCampus-release` only after all tests and release smoke checks pass.

---

### Task 1: Shared Transfer Contract and State Machine

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferStatus.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferBatchStatus.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferApplicationType.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferReviewStage.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferDecision.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferStateMachine.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferStateMachineTest.java`

**Interfaces:**
- Produces: statuses `DRAFT`, `SUBMITTED`, `SOURCE_APPROVED`, `QUALIFIED`, `ASSESSED`, `PROPOSED`, `PENDING_EFFECTIVE`, `EFFECTIVE`, `REJECTED`, `CANCELLED`, and `EXECUTION_FAILED`.
- Produces: `MajorTransferStateMachine.requireTransition(MajorTransferStatus from, MajorTransferStatus to)`.
- Produces: `MajorTransferStateMachine.studentMayEdit`, `studentMaySubmit`, `studentMayWithdraw`, and `adminMayCancel`.

- [ ] **Step 1: Write the failing state-transition tests**

```java
@Test void finalApprovalDoesNotBecomeEffectiveImmediately() {
    assertThatCode(() -> MajorTransferStateMachine.requireTransition(
            MajorTransferStatus.PROPOSED, MajorTransferStatus.PENDING_EFFECTIVE))
            .doesNotThrowAnyException();
    assertThatThrownBy(() -> MajorTransferStateMachine.requireTransition(
            MajorTransferStatus.PROPOSED, MajorTransferStatus.EFFECTIVE))
            .isInstanceOf(IllegalStateException.class);
}

@Test void studentCanWithdrawOnlyBeforeSourceReview() {
    assertThat(MajorTransferStateMachine.studentMayWithdraw(MajorTransferStatus.SUBMITTED)).isTrue();
    assertThat(MajorTransferStateMachine.studentMayWithdraw(MajorTransferStatus.SOURCE_APPROVED)).isFalse();
}
```

- [ ] **Step 2: Run the contract test and confirm RED**

```bash
mvn -pl vcampus-common -Dtest=MajorTransferStateMachineTest test
```

Expected: compilation fails because the transfer contract does not exist.

- [ ] **Step 3: Implement the immutable enums and transition table**

```java
private static final Map<MajorTransferStatus, Set<MajorTransferStatus>> ALLOWED = Map.ofEntries(
    entry(DRAFT, Set.of(SUBMITTED)),
    entry(SUBMITTED, Set.of(DRAFT, SOURCE_APPROVED, REJECTED)),
    entry(SOURCE_APPROVED, Set.of(QUALIFIED, REJECTED, CANCELLED)),
    entry(QUALIFIED, Set.of(ASSESSED, REJECTED, CANCELLED)),
    entry(ASSESSED, Set.of(PROPOSED, REJECTED, CANCELLED)),
    entry(PROPOSED, Set.of(PENDING_EFFECTIVE, REJECTED, CANCELLED)),
    entry(PENDING_EFFECTIVE, Set.of(EFFECTIVE, EXECUTION_FAILED, CANCELLED)),
    entry(EXECUTION_FAILED, Set.of(EFFECTIVE, CANCELLED))
);
```

- [ ] **Step 4: Run the Task 1 test and confirm GREEN**

- [ ] **Step 5: Commit the contract**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer vcampus-common/src/test/java/edu/seu/vcampus/common/student/majortransfer
git commit -m "feat(student): add major transfer state contract"
```

### Task 2: Transfer Schema and Repository Persistence

**Files:**
- Create: `vcampus-database/schema/025_major_transfer.sql`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepository.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepositoryTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/support/StudentAccessTestDatabase.java`

**Interfaces:**
- Produces tables `tblMajorTransferBatch`, `tblMajorTransferOption`, `tblMajorTransferApplication`, `tblMajorTransferAttachment`, `tblMajorTransferReview`, and `tblMajorTransferExecution`.
- Produces nested persistence records `MajorTransferRepository.ApplicationRow`, `BatchRow`, `OptionRow`, `ReviewRow`, `AttachmentRow`, and `ExecutionRow` so persistence does not depend on wire views introduced in Task 3.
- Produces optimistic repository methods `updateApplicationStatus(Connection, String, MajorTransferStatus, MajorTransferStatus, long, Instant)` and query methods scoped by student, batch, option, and status.
- Produces a unique `(batchId, studentId)` application index and unique `(applicationId, reviewStage)` review index.

- [ ] **Step 1: Write failing Access repository tests**

```java
@Test void preservesSubmissionSnapshotAndRejectsSecondApplicationInBatch() {
    String id = repository.insertDraft(connection, draftFor("batch-1", "student-1", "major-2"));
    MajorTransferRepository.ApplicationRow row = repository.findApplication(connection, id).orElseThrow();
    assertThat(row.fromMajorId())
            .isEqualTo("major-1");
    assertThatThrownBy(() -> repository.insertDraft(
            connection, draftFor("batch-1", "student-1", "major-3")))
            .isInstanceOf(RuntimeException.class);
}

@Test void staleStatusUpdateChangesNoRows() {
    int changed = repository.updateApplicationStatus(connection, "application-1",
            MajorTransferStatus.DRAFT, MajorTransferStatus.SUBMITTED, 99, Instant.now());
    assertThat(changed).isZero();
}
```

- [ ] **Step 2: Run the repository test and confirm RED**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Create the normalized Access schema and JDBC mappings**

Use `VARCHAR(36)` identifiers, `MEMO` for requirements/reasons, `LONGBINARY` for attachment content, `DOUBLE` for scores, `LONG` for quotas/weights/versions, `YESNO` for attestations, and `DATETIME` for every lifecycle timestamp. Store source department, major, class, student number, grade, and student row version on the application row so later organization edits cannot rewrite application history.

- [ ] **Step 4: Verify inserts, filters, indexes, attachment round trips, and optimistic updates**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: Commit schema and persistence**

```bash
git add vcampus-database/schema/025_major_transfer.sql vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/repository vcampus-server/src/test/java/edu/seu/vcampus/server/student
git commit -m "feat(student): persist major transfer workflow"
```

### Task 3: Batch, Option, and Student Workspace Contracts

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferBatchView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferOptionView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferEligibilityItem.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferApplicationView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferWorkspace.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferApplicationQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/SaveMajorTransferBatchCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/SaveMajorTransferOptionCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/SaveMajorTransferDraftCommand.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferContractTest.java`

**Interfaces:**
- `MajorTransferWorkspace` returns the active batch, available target options, ordered eligibility items, current formal organization snapshot, and the student's application.
- `SaveMajorTransferDraftCommand` contains `batchId`, `optionId`, `applicationType`, `reason`, and `expectedVersion`; student identity and source organization never come from the client.
- `SaveMajorTransferOptionCommand` contains grade CSV, quotas, written/interview pass scores, weight percentages, difficulty quota exemption, requirements, active flag, and expected version.

- [ ] **Step 1: Write serialization and constructor-invariant tests**

```java
@Test void optionRejectsWeightsThatDoNotTotalOneHundred() {
    assertThatThrownBy(() -> optionCommand(60, 30))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("100");
}

@Test void workspaceRoundTripsThroughJavaSerialization() throws Exception {
    assertThat(roundTrip(workspaceFixture())).isEqualTo(workspaceFixture());
}
```

- [ ] **Step 2: Run the common contract test and confirm RED**

```bash
mvn -pl vcampus-common -Dtest=MajorTransferContractTest test
```

- [ ] **Step 3: Implement serializable records with defensive copies**

Copy every returned list with `List.copyOf`, reject blank IDs/titles, reject negative quotas, require chronological batch timestamps, require exactly 100 total weight, cap reason at 2000 characters, and require a nonblank reason before submission rather than before an empty draft is first created.

- [ ] **Step 4: Run the Task 3 test and confirm GREEN**

- [ ] **Step 5: Commit the wire contract**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer vcampus-common/src/test/java/edu/seu/vcampus/common/student/majortransfer
git commit -m "feat(student): define major transfer wire contract"
```

### Task 4: Student Draft, Eligibility, Submit, Withdraw, and Attachment Service

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/UploadMajorTransferAttachmentCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/DeleteMajorTransferAttachmentCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/SubmitMajorTransferCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/WithdrawMajorTransferCommand.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferStudentWorkflowTest.java`

**Interfaces:**
- Produces `getStudentWorkspace(String userId)`, `saveDraft(String userId, SaveMajorTransferDraftCommand)`, `uploadAttachment`, `deleteAttachment`, `submit`, and `withdraw`.
- Produces stable failures `TRANSFER_BATCH_CLOSED`, `TRANSFER_INELIGIBLE`, `TRANSFER_DUPLICATE_APPLICATION`, `TRANSFER_INVALID_TARGET`, `TRANSFER_ATTACHMENT_INVALID`, `TRANSFER_STATE_INVALID`, and `COMMON_CONCURRENT_MODIFICATION`.

- [ ] **Step 1: Write failing student workflow tests**

```java
@Test void submitUsesServerSideIdentityAndSourceSnapshot() {
    MajorTransferApplicationView submitted = service.submit("student-user-1",
            new SubmitMajorTransferCommand("application-1", 1));
    assertThat(submitted.status()).isEqualTo(MajorTransferStatus.SUBMITTED);
    assertThat(submitted.studentId()).isEqualTo("student-1");
    assertThat(submitted.fromMajorId()).isEqualTo("major-1");
}

@Test void withdrawalPreservesDraftAndAttachments() {
    MajorTransferApplicationView reopened = service.withdraw("student-user-1",
            new WithdrawMajorTransferCommand("application-1", 2));
    assertThat(reopened.status()).isEqualTo(MajorTransferStatus.DRAFT);
    assertThat(reopened.reason()).isEqualTo("希望学习计算机专业");
    assertThat(reopened.attachments()).hasSize(1);
}
```

Add cases for closed windows, postgraduate users, inactive/same target major, excluded grades, previous successful transfer, second batch application, missing difficulty evidence, stale versions, three-file limit, 5 MiB boundary, forged MIME type, and concurrent submit/withdraw.

- [ ] **Step 2: Run the workflow test and confirm RED**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferStudentWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement authoritative validation and locked transitions**

Resolve the student from `userId`, acquire `ResourceKey.student(studentId)`, read current student and organization data inside one transaction, calculate ordered eligibility items, and only then persist. Detect `%PDF-`, JPEG `FF D8 FF`, and PNG `89 50 4E 47 0D 0A 1A 0A` signatures before storing attachment bytes.

- [ ] **Step 4: Run Task 4 plus existing profile tests**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferStudentWorkflowTest,StudentProfileReviewServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: Commit student workflow service**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer
git commit -m "feat(student): add major transfer application workflow"
```

### Task 5: Administrator Configuration, Review, and Attestation Workflow

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferReviewView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/ReviewMajorTransferSourceCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/ReviewMajorTransferQualificationCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/CancelMajorTransferCommand.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferReviewWorkflowTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferConfigurationTest.java`

**Interfaces:**
- Produces `saveBatch`, `saveOption`, `listBatches`, `listOptions`, `reviewSource`, `reviewQualification`, `cancel`, `listApplications`, and `getApplicationDetail`.
- Every review appends one immutable `MajorTransferReviewView`; it never overwrites an earlier review row.

- [ ] **Step 1: Write failing review tests**

```java
@Test void sourceApprovalRequiresAllThreeAttestations() {
    var command = new ReviewMajorTransferSourceCommand("application-1", APPROVE,
            true, false, true, "存在未核销处分", 1);
    assertThatThrownBy(() -> service.reviewSource("admin-1", command))
            .hasMessageContaining("三项资格");
}

@Test void repeatedStageReviewIsRejectedAndOriginalAuditRemains() {
    service.reviewQualification("admin-1", qualificationApproval("application-1", 2));
    assertThatThrownBy(() -> service.reviewQualification(
            "admin-2", qualificationApproval("application-1", 3)))
            .isInstanceOf(IllegalStateException.class);
    assertThat(repository.listReviews(connection, "application-1")).hasSize(2);
}

@Test void refusesToOpenOverlappingApplicationBatches() {
    service.saveBatch("admin-1", openBatch("2026-03-17T00:00:00Z", "2026-03-20T23:59:59Z"));
    assertThatThrownBy(() -> service.saveBatch("admin-1",
            openBatch("2026-03-20T00:00:00Z", "2026-03-25T23:59:59Z")))
            .hasMessageContaining("报名时间重叠");
}
```

- [ ] **Step 2: Run the review test and confirm RED**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferReviewWorkflowTest,MajorTransferConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement configuration validation, strict stage ordering, and rejection reasons**

Allow only one `OPEN` batch whose application time overlaps a given instant. Validate batch chronology and option weights/quotas before writing. An approval requires the current expected stage and version. A rejection requires a nonblank comment, appends a stage review, and moves to `REJECTED`. Cancellation requires a nonblank reason and is allowed only after `SOURCE_APPROVED` and before `EFFECTIVE`.

- [ ] **Step 4: Run Task 5 tests and confirm every review includes reviewer and timestamp**

- [ ] **Step 5: Commit staged review workflow**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer
git commit -m "feat(student): add staged major transfer review"
```

### Task 6: Assessment, Ranking, and Proposed Admission

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/RecordMajorTransferScoreCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/GenerateMajorTransferProposalCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferRankingView.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferRankingService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferRankingServiceTest.java`

**Interfaces:**
- Produces `recordScore(String adminUserId, RecordMajorTransferScoreCommand)` and `generateProposal(String adminUserId, GenerateMajorTransferProposalCommand)`.
- `finalScore = writtenScore * writtenWeightPct / 100 + interviewScore * interviewWeightPct / 100`, rounded to two decimal places with `RoundingMode.HALF_UP`.

- [ ] **Step 1: Write failing scoring and cutoff tests**

```java
@Test void calculatesConfiguredWeightedScore() {
    assertThat(ranking.finalScore(new BigDecimal("80"), new BigDecimal("90"), 60, 40))
            .isEqualByComparingTo("84.00");
}

@Test void includesEveryApplicantTiedAtQuotaBoundary() {
    List<String> proposed = ranking.selectProposed(
            List.of(scored("s1", 95), scored("s2", 90), scored("s3", 90), scored("s4", 80)), 2);
    assertThat(proposed).containsExactly("s1", "s2", "s3");
}
```

Add cases for missing scores, zero-weight components, below-pass scores, difficulty quota exemption, stale option version, repeated proposal generation, and concurrent generation for one option.

- [ ] **Step 2: Run the ranking test and confirm RED**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferRankingServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement deterministic ranking under `TRANSFER_OPTION:<optionId>`**

Sort by final score descending and student number ascending for stable display, but use only the final-score value to expand the quota boundary. In one transaction mark selected applications `PROPOSED`, mark other eligible assessed applications `REJECTED`, and append immutable review records describing the generated cutoff.

- [ ] **Step 4: Run Task 6 tests twice to prove deterministic output**

- [ ] **Step 5: Commit assessment and ranking**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer
git commit -m "feat(student): rank major transfer applicants"
```

### Task 7: Final Approval and Atomic Effective-Date Execution

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/FinalizeMajorTransferCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/ExecuteMajorTransferCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferExecutionView.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferExecutionTest.java`

**Interfaces:**
- Produces `finalizeProposal(String adminUserId, FinalizeMajorTransferCommand)` and `execute(String adminUserId, ExecuteMajorTransferCommand)`.
- Produces `StudentRepository.updateOrganization(Connection, studentId, classId, expectedVersion, updatedAt)`; department and major are derived from the class hierarchy.

- [ ] **Step 1: Write failing finalization and rollback tests**

```java
@Test void finalApprovalWaitsForEffectiveExecution() {
    MajorTransferApplicationView approved = service.finalizeProposal("admin-1", finalApproval());
    assertThat(approved.status()).isEqualTo(MajorTransferStatus.PENDING_EFFECTIVE);
    assertThat(students.findById(connection, "student-1").orElseThrow().majorId())
            .isEqualTo("source-major");
}

@Test void executionAtomicallyUpdatesStudentAndWritesChangeHistory() {
    service.execute("admin-1", executeInto("target-class", effectiveDate, 4));
    assertThat(student("student-1").classId()).isEqualTo("target-class");
    assertThat(changes("student-1")).anyMatch(c -> c.changeType().equals("MAJOR_TRANSFER"));
    assertThat(application("application-1").status()).isEqualTo(MajorTransferStatus.EFFECTIVE);
}
```

Add tests for execution before effective date, class outside target major, inactive class, changed student version, injected failure after student update, repeated request ID, previous successful transfer, and retry from `EXECUTION_FAILED`.

- [ ] **Step 2: Run the execution test and confirm RED**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferExecutionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement fixed-order locking and one transaction**

Acquire the application lock followed by the student lock, validate the target class hierarchy and date, update the student, append `MAJOR_TRANSFER` with before/after organization names and IDs, insert an execution row with course-recognition status `PENDING`, and change the application to `EFFECTIVE`. On failure, roll back formal data and record `EXECUTION_FAILED` in a separate transaction with a safe message.

- [ ] **Step 4: Run execution, existing enrollment, and change-history tests**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferExecutionTest,StudentProfileUpdateTest,StudentChangeHistoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: Commit effective enrollment transfer**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer vcampus-server/src/main/java/edu/seu/vcampus/server/student vcampus-server/src/test/java/edu/seu/vcampus/server/student
git commit -m "feat(student): execute approved major transfers"
```

### Task 8: Socket Commands, Authorization, and ServerMain Wiring

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferHandlers.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferHandlersTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/ServerMainSessionConfigurationTest.java`

**Interfaces:**
- Student commands: `MAJOR_TRANSFER_GET_WORKSPACE`, `MAJOR_TRANSFER_SAVE_DRAFT`, `MAJOR_TRANSFER_UPLOAD_ATTACHMENT`, `MAJOR_TRANSFER_DELETE_ATTACHMENT`, `MAJOR_TRANSFER_SUBMIT`, `MAJOR_TRANSFER_WITHDRAW`.
- Administrator commands: `MAJOR_TRANSFER_SAVE_BATCH`, `MAJOR_TRANSFER_SAVE_OPTION`, `MAJOR_TRANSFER_LIST_APPLICATIONS`, `MAJOR_TRANSFER_GET_APPLICATION`, `MAJOR_TRANSFER_REVIEW_SOURCE`, `MAJOR_TRANSFER_REVIEW_QUALIFICATION`, `MAJOR_TRANSFER_RECORD_SCORE`, `MAJOR_TRANSFER_GENERATE_PROPOSAL`, `MAJOR_TRANSFER_FINALIZE`, `MAJOR_TRANSFER_EXECUTE`, `MAJOR_TRANSFER_CANCEL`.

- [ ] **Step 1: Write failing routing and authorization tests**

```java
@Test void studentCannotReadAnotherStudentsTransfer() {
    ResponseBody<?> response = routeAsStudent("MAJOR_TRANSFER_GET_APPLICATION",
            new EntityIdRequest("application-of-student-2"));
    assertThat(response.code()).isEqualTo("COMMON_FORBIDDEN");
}

@Test void teacherHasNoTransferCommands() {
    assertThat(routeAsTeacher("MAJOR_TRANSFER_LIST_APPLICATIONS", adminQuery()).code())
            .isEqualTo("COMMON_FORBIDDEN");
}
```

Assert every write passes through `StudentWriteExecutor`, every admin mutation requires the `ADMIN` role, and every student mutation resolves identity from the session.

- [ ] **Step 2: Run handler and bootstrap tests and confirm RED**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferHandlersTest,ServerMainSessionConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Register the dedicated handler in `ServerMain`**

Construct one repository and service graph using the existing `TransactionManager`, `StripedResourceLockManager`, `StudentRepository`, `AccessOrganizationRepository`, `StudentChangeRepository`, session-backed principal resolver, and `DeduplicatingStudentWriteExecutor`. Register it beside `StudentHandlers`, not inside the ordinary profile handler list.

- [ ] **Step 4: Run all server student-handler tests**

```bash
mvn -pl vcampus-server -am -Dtest='*Student*Handler*Test,*MajorTransfer*Test,ServerMainSessionConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: Commit transport integration**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/handler vcampus-server/src/test/java/edu/seu/vcampus/server
git commit -m "feat(student): expose major transfer commands"
```

### Task 9: Student Transfer Client and UI

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/service/MajorTransferClientService.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MyMajorTransferPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferDraftDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferTimelinePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentModulePageFactory.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/MajorTransferStudentUiTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/StudentModulePageFactoryTest.java`

**Interfaces:**
- Produces async typed methods matching every student command and never blocks the EDT.
- Produces component names `major-transfer.student.eligibility`, `.target-department`, `.target-major`, `.application-type`, `.reason`, `.attachments`, `.save`, `.submit`, `.withdraw`, `.timeline`, and `.error`.

- [ ] **Step 1: Write failing Swing behavior tests**

```java
@Test void ineligibleStudentSeesEveryReasonAndCannotOpenEditor() {
    panel.render(workspaceWithFailedEligibility("报名时间未开始", "仅面向本科生"));
    assertThat(textOf("major-transfer.student.eligibility"))
            .contains("报名时间未开始", "仅面向本科生");
    assertThat(button("major-transfer.student.save").isEnabled()).isFalse();
}

@Test void submittedApplicationOffersWithdrawAndPreservesDraftAfterConfirmation() {
    panel.render(submittedWorkspace());
    assertThat(button("major-transfer.student.withdraw").isVisible()).isTrue();
    clickAndConfirm("major-transfer.student.withdraw");
    assertThat(lastCommand()).isEqualTo(new WithdrawMajorTransferCommand("application-1", 2));
}
```

Add tests for cascading active options, one-target selection, missing reason, difficulty attachment requirement, upload limits, stale async response suppression, disabled controls while disconnected, and timeline rendering.

- [ ] **Step 2: Run client UI tests and confirm RED**

```bash
mvn -pl vcampus-client -am -Dtest=MajorTransferStudentUiTest,StudentModulePageFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Build the student page with the established design system**

Change the student module root to a tabbed page containing existing `MyStudentProfilePanel` as “学籍档案” and `MyMajorTransferPanel` as “转专业申请”. Use existing `UiColors`, `UiTypography`, `UiSpacing`, and named components; preserve the current profile panel and PDF actions unchanged.

- [ ] **Step 4: Run UI tests on the EDT and inspect a visual-QA screenshot**

```bash
mvn -pl vcampus-client -am -Dtest=MajorTransferStudentUiTest,StudentModulePageFactoryTest,EdtSafetyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: Commit student transfer UI**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentModulePageFactory.java vcampus-client/src/test/java/edu/seu/vcampus/client/student
git commit -m "feat(student): add student major transfer page"
```

### Task 10: Administrator Transfer Management UI

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferAdminPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferBatchPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferReviewPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferAssessmentPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferExecutionPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentModulePageFactory.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/MajorTransferAdminUiTest.java`

**Interfaces:**
- Adds administrator tab “转专业管理” only for `ADMIN`.
- Produces four inner views: “批次与专业”, “申请审核”, “考核录取”, and “生效办理”.
- Teacher module continues to contain only “学生查询”.

- [ ] **Step 1: Write failing administrator UI tests**

```java
@Test void reviewActionsFollowCurrentStage() {
    panel.render(applicationAt(MajorTransferStatus.SUBMITTED));
    assertThat(button("major-transfer.admin.source-review").isEnabled()).isTrue();
    assertThat(button("major-transfer.admin.qualification-review").isEnabled()).isFalse();
    assertThat(button("major-transfer.admin.execute").isEnabled()).isFalse();
}

@Test void effectiveExecutionRequiresTargetClassAndEffectiveDate() {
    panel.render(pendingEffectiveApplicationWithoutClass());
    assertThat(button("major-transfer.admin.execute").isEnabled()).isFalse();
    select("major-transfer.admin.target-class", "软件工程2601");
    clock.advanceTo(effectiveDate);
    assertThat(button("major-transfer.admin.execute").isEnabled()).isTrue();
}
```

Add tests for batch date validation, option weight validation, qualification attestations, score entry, cutoff-tie preview, rejection comment requirement, admin-only tab visibility, disconnect handling, and stale response suppression.

- [ ] **Step 2: Run administrator UI tests and confirm RED**

```bash
mvn -pl vcampus-client -am -Dtest=MajorTransferAdminUiTest,StudentModulePageFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement the four focused administrator views**

Keep editing dialogs modal, make list/detail calls asynchronous, show source and target organization side by side, display immutable review history, preview the exact proposed list and cutoff before confirmation, and require a target-major class before execution. Do not expose the tab or send transfer queries for teachers.

- [ ] **Step 4: Run administrator, teacher privacy, and visual-QA tests**

```bash
mvn -pl vcampus-client -am -Dtest=MajorTransferAdminUiTest,StudentModulePageFactoryTest,StudentDetailPanelTest,EdtSafetyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: Commit administrator UI**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentModulePageFactory.java vcampus-client/src/test/java/edu/seu/vcampus/client/student
git commit -m "feat(student): add major transfer administration"
```

### Task 11: Seeded Scenario, End-to-End Verification, and Release Packaging

**Files:**
- Create: `vcampus-database/seed/025_major_transfer_demo.sql`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/MajorTransferSocketIntegrationTest.java`
- Modify: `vCampus-release/使用说明.md`
- Replace: `vCampus-release/lib/vCampusClient.jar`
- Replace: `vCampus-release/lib/vCampusServer.jar`
- Replace: `vCampus-release/data/vCampus.accdb`
- Replace: `vCampus-release.zip`

**Interfaces:**
- Demo data contains one open batch, two target options, and distinct student applications in `DRAFT`, `SUBMITTED`, and `PENDING_EFFECTIVE`.
- Release remains runnable through the existing start scripts and existing account credentials.

- [ ] **Step 1: Write the failing socket-level scenario**

```java
@Test void completeTransferLifecycleAcrossRealSocket() {
    String applicationId = studentClient.saveDraft(validDraft()).data().application().applicationId();
    studentClient.submit(new SubmitMajorTransferCommand(applicationId, 1));
    adminClient.reviewSource(sourceApproval(applicationId, 2));
    adminClient.reviewQualification(qualificationApproval(applicationId, 3));
    adminClient.recordScore(scores(applicationId, 4));
    adminClient.generateProposal(new GenerateMajorTransferProposalCommand("option-1", 1));
    adminClient.finalizeProposal(finalApproval(applicationId, 6));
    adminClient.execute(execution(applicationId, "target-class", 7));
    assertThat(studentClient.getCurrent().data().majorId()).isEqualTo("target-major");
}
```

- [ ] **Step 2: Run the focused integration test and confirm RED, then add deterministic seed data**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferSocketIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Run all tests and build shaded JARs**

```bash
mvn clean verify
mvn -pl vcampus-server,vcampus-client -am package -DskipTests
```

Expected: all modules pass and `vcampus-distribution/lib/vCampusServer.jar` plus `vCampusClient.jar` are rebuilt.

- [ ] **Step 4: Rebuild and replace release artifacts**

```bash
java -cp vcampus-distribution/lib/vCampusServer.jar edu.seu.vcampus.server.bootstrap.DatabaseInitializer vcampus-database/schema vcampus-database/seed /tmp/vCampus-major-transfer.accdb
cp vcampus-distribution/lib/vCampusClient.jar vCampus-release/lib/vCampusClient.jar
cp vcampus-distribution/lib/vCampusServer.jar vCampus-release/lib/vCampusServer.jar
cp /tmp/vCampus-major-transfer.accdb vCampus-release/data/vCampus.accdb
```

Update the usage guide with transfer demo accounts, workflow stages, attachment limits, withdrawal boundary, effective-date behavior, and the fact that teacher accounts have no transfer-management access. Recreate the zip without logs, `.DS_Store`, or temporary databases.

- [ ] **Step 5: Smoke-test the release and verify the archive**

Start the packaged server against a disposable copy of the release database. Exercise student draft/submit/withdraw, administrator source and qualification reviews, scoring, proposal generation with a tie, final approval without immediate profile change, and effective execution with profile/change-history verification. Stop the server, restore the clean seeded database, then run:

```bash
cmp vcampus-distribution/lib/vCampusClient.jar vCampus-release/lib/vCampusClient.jar
cmp vcampus-distribution/lib/vCampusServer.jar vCampus-release/lib/vCampusServer.jar
unzip -tq vCampus-release.zip
```

- [ ] **Step 6: Commit source documentation and permitted release metadata**

```bash
git add vcampus-common vcampus-server vcampus-client vcampus-database docs/superpowers vCampus-release/使用说明.md
git commit -m "feat(student): deliver major transfer workflow"
```

Do not add ignored binary release artifacts unless repository policy or the user explicitly requires them to be committed; report their absolute paths and checksums in the completion handoff.
