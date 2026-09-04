# Student Record Validation, Manual Creation, and Withdrawal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add authoritative student-profile validation with field tooltips, hierarchical manual student creation for administrators, teacher history privacy, and reversible pending profile applications without changing automatic admission.

**Architecture:** Put deterministic field rules in a UI-independent common validator consumed by both Swing and server services. Add a separate administrator-only manual-creation command and transactional coordinator path that reuses account provisioning but never touches automatic number sequences. Implement withdrawal as an optimistic, locked `PENDING -> DRAFT` transition on the existing application row so its edited snapshot survives.

**Tech Stack:** Java 21, Swing, CompletableFuture, socket command DTOs, Microsoft Access/UCanAccess, JUnit 5, AssertJ, Maven.

**Spec:** `docs/superpowers/specs/2026-09-03-student-record-validation-manual-entry-withdrawal-design.md`

## Global Constraints

- Preserve the existing uncommitted PDF export changes in `MyStudentProfilePanel.java`.
- Keep `STUDENT_CREATE` and both automatic numbering sequences behaviorally and structurally unchanged.
- `STUDENT_CREATE_MANUAL` is strict-ADMIN only and initializes email/phone to null.
- Manual creation is atomic and idempotent; campus card, student number, and normalized identity-document number are unique.
- Enrollment date must be at least `birthDate.plusYears(18)` and no later than today.
- Height range is 100–280 cm and weight range is 20–300 kg.
- Teacher detail never requests or renders change history.
- Withdrawal preserves the existing application snapshot and serializes against approve/reject with `STUDENT:<studentId>`.
- Use `apply_patch` for source edits and stage only files belonging to the current task.

---

### Task 1: Shared Validation Contract

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/CreateStudentManualCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentFieldError.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentProfileValidator.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/student/StudentProfileValidatorTest.java`

**Interfaces:**
- Produces: `record CreateStudentManualCommand(String campusCardNumber, String studentNumber, String studentName, String gender, StudentType studentType, String idDocumentType, String idDocumentNumber, LocalDate birthDate, LocalDate enrollmentDate, String classId)`.
- Produces: `record StudentFieldError(String field, String message)`.
- Produces: `StudentProfileValidator.validateManual(CreateStudentManualCommand, LocalDate)` and `validatePersonal(StudentPersonalProfile, LocalDate enrollmentDate, LocalDate today)`, returning errors in visual field order.
- Produces normalization helpers for uppercase student/document numbers without database access.

- [ ] **Step 1: Write failing validator tests**

Add literal, table-driven cases for valid values and failures: invalid/duplicate-shaped campus card, student number, name characters, mainland ID checksum and birth-date mismatch, passport/HK/other patterns, future dates, exactly-18 boundary, one day under 18, member dates, email, phone, height 99/100/280/281, weight 19/20/300/301, and control characters. Assert the first returned `field` and Chinese message.

- [ ] **Step 2: Verify RED**

Run:

```bash
mvn -pl vcampus-common -Dtest=StudentProfileValidatorTest test
```

Expected: compilation failure because the validator contract does not exist.

- [ ] **Step 3: Implement deterministic validation**

Use immutable regex patterns, `LocalDate` comparisons, the GB 11643 weighted checksum for resident identity cards, and explicit maximum lengths matching `020_student.sql`. Keep uniqueness and organization lookup out of this class.

- [ ] **Step 4: Verify GREEN**

Run the Task 1 command and confirm all boundary cases pass.

- [ ] **Step 5: Commit**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student/CreateStudentManualCommand.java vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentFieldError.java vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentProfileValidator.java vcampus-common/src/test/java/edu/seu/vcampus/common/student/StudentProfileValidatorTest.java
git commit -m "feat(student): add shared profile validation"
```

### Task 2: Student Editor Validation and Tooltips

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/PersonalProfileEditPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/PersonalProfileEditDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/MyStudentProfilePanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/StudentProfileUiTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/PersonalProfileValidationUiTest.java`

**Interfaces:**
- Consumes: `StudentProfileValidator.validatePersonal(...)` and `StudentFieldError.field()`.
- Produces: `PersonalProfileEditDialog` receives the formal enrollment date and blocks invalid drafts before `STUDENT_PROFILE_SAVE_PERSONAL_DRAFT`.
- Produces: controlled combo boxes for political status, ethnicity, marital status, document type, household type, overseas status, health status, and blood type; legacy values remain representable.

- [ ] **Step 1: Write failing Swing tests**

Assert every editable text/combo/check control has nonblank tooltip text; invalid email, ID checksum, under-18 birth date, height 281, and checked league-member without date produce the expected error, focus the named control, preserve input, and queue no network command. Assert valid input queues one complete save command.

- [ ] **Step 2: Verify RED outside the macOS sandbox**

```bash
mvn -pl vcampus-client -am -Dtest=StudentProfileUiTest,PersonalProfileValidationUiTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing tooltips/combos and invalid values currently reach the request client.

- [ ] **Step 3: Implement editor controls and error mapping**

Add a small field metadata table containing key, label, tooltip, and controlled options. Parse the panel value, run the shared validator with the formal enrollment date, map the first error key to its component, request focus, and show its message. When a membership checkbox is cleared, clear the related date before building the DTO. Pass `workspace.formalProfile().core().enrollmentDate()` from `MyStudentProfilePanel` while preserving the existing `exportPdf()` implementation.

- [ ] **Step 4: Verify GREEN**

Run the Task 2 command and ensure UI tests pass without EDT exceptions.

- [ ] **Step 5: Commit**

Stage only the validator-related hunks plus tests; do not accidentally discard or overwrite the pre-existing PDF export hunk.

### Task 3: Transactional Manual Student Creation

**Files:**
- Modify: `vcampus-database/schema/020_student.sql`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentAdmissionService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentAdmissionCoordinator.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/handler/StudentHandlers.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/ManualStudentCreationTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/handler/StudentHandlersTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/ServerMainSessionConfigurationTest.java`

**Interfaces:**
- Consumes: `CreateStudentManualCommand` and `StudentProfileValidator`.
- Produces: `StudentAdmissionService.createManual(CreateStudentManualCommand, RequestContext)` returning `StudentAdmissionResult`.
- Produces: socket command `STUDENT_CREATE_MANUAL` registered in `ServerMain`, strict-ADMIN authorized, and write-deduplicated.

- [ ] **Step 1: Write failing service and handler tests**

Create a real Access fixture with one active hierarchy. Assert manual creation writes one user/student/change/dedup row, persists identity type/number/birth date, stores null email/phone, sets ACTIVE, returns `mustChangePassword=true`, and leaves automatic sequence values unchanged. Add rollback injection tests and duplicate campus-card/student-number/document-number tests. Assert STUDENT and TEACHER principals are forbidden even if granted `STUDENT_WRITE`.

- [ ] **Step 2: Verify RED**

```bash
mvn -pl vcampus-server -am -Dtest=ManualStudentCreationTest,StudentHandlersTest,ServerMainSessionConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing service method and route.

- [ ] **Step 3: Implement the minimal manual path**

Normalize command strings, run shared validation, load and verify active class/major/department, acquire fixed-order login/student-number locks, then use the existing caller-owned transaction to provision the `12345678` account, insert student core plus identity profile, add `ADMISSION`, and store the dedup result. Convert unique-index failures into the three stable error codes from the spec. Add `uk_tblStudent_idDocumentNumber`.

- [ ] **Step 4: Prove automatic admission is unchanged**

Run:

```bash
mvn -pl vcampus-server -am -Dtest=ManualStudentCreationTest,StudentAdmissionCoordinatorTest,StudentAdmissionConcurrencyTest,StudentHandlersTest,ServerMainSessionConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Confirm automatic number sequence assertions retain their previous literal values.

- [ ] **Step 5: Commit**

```bash
git add vcampus-database/schema/020_student.sql vcampus-server/src vcampus-server/src/test
git commit -m "feat(student): add atomic manual student creation"
```

### Task 4: Organization Buttons and Manual Creation Dialog

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/service/StudentClientService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/OrganizationManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/ManualStudentCreationDialog.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/OrganizationManagementPanelTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/ManualStudentCreationDialogTest.java`

**Interfaces:**
- Produces: `StudentClientService.createManual(CreateStudentManualCommand)` using `STUDENT_CREATE_MANUAL`.
- Produces component names `student.org.add-student` and `student.manual.{card,number,name,gender,type,idType,idNumber,birthDate,enrollmentDate,submit,error}`.

- [ ] **Step 1: Write failing hierarchy-state tests**

For root/no selection, department, major, and class selection, assert all four buttons are visible and exactly the required button is enabled. Assert disconnect/loading disables all four. Assert class selection opens a dialog bound to the exact class path.

- [ ] **Step 2: Write failing manual-dialog tests**

Assert email/phone components do not exist, every editable control has a tooltip, invalid values do not queue a request and focus the first field, a valid form sends one literal `CreateStudentManualCommand`, success shows the submitted card/number plus `12345678`, and failure preserves inputs.

- [ ] **Step 3: Verify RED outside the sandbox**

```bash
mvn -pl vcampus-client -am -Dtest=OrganizationManagementPanelTest,ManualStudentCreationDialogTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: add buttons are hidden by current selection logic and manual dialog is absent.

- [ ] **Step 4: Implement the four-button state machine and dialog**

Keep buttons visible, centralize enablement in one method based on connection/loading/selected node type, and style disabled state through the existing Swing theme. Construct the manual dialog from immutable department/major/class views, render the path read-only, validate before sending, prevent double submission, discard stale async responses after disposal, and restore the exact selected class after successful close.

- [ ] **Step 5: Verify GREEN and commit**

Run Task 4 tests, then stage only the listed client files and tests.

### Task 5: Teacher Change-History Privacy

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentDetailPanel.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/StudentDetailPanelTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/AdminStudentProfilePanelTest.java`

**Interfaces:**
- Consumes: existing `canEdit`/admin-mode constructor flag.
- Produces: teacher mode contains no `student.detail.changes` component and emits no `STUDENT_GET_CHANGES`; admin mode retains both.

- [ ] **Step 1: Write the failing privacy test**

Create teacher-mode panel, record every command, and assert the component tree and command queue both exclude change history while limited profile still renders.

- [ ] **Step 2: Verify RED**

```bash
mvn -pl vcampus-client -am -Dtest=StudentDetailPanelTest,AdminStudentProfilePanelTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Build history UI and call `listChanges` only in admin mode**

Do not merely hide an already-populated table; omit construction/request entirely for teachers.

- [ ] **Step 4: Verify GREEN and commit**

Run Task 5 tests and stage only the panel/tests.

### Task 6: Server-Side Pending Application Withdrawal

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/WithdrawStudentProfileCommand.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentProfileApplicationRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentProfileService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentProfileServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/handler/StudentHandlers.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/StudentProfileReviewServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/handler/StudentProfileHandlersTest.java`

**Interfaces:**
- Produces: `record WithdrawStudentProfileCommand(long expectedApplicationVersion)`.
- Produces: `StudentProfileService.withdraw(String userId, WithdrawStudentProfileCommand)` returning `StudentProfileWorkspace`.
- Produces: student-self-only command `STUDENT_PROFILE_WITHDRAW`.

- [ ] **Step 1: Write failing workflow tests**

Submit a draft, withdraw it, and assert the same application ID/personal snapshot/attendance mode remain, status becomes DRAFT, submitted time clears, and version increments once. Add stale-version, no-pending, processed-application, cross-student target absence, idempotent retry, and withdraw-vs-approve serialized outcome tests.

- [ ] **Step 2: Verify RED**

```bash
mvn -pl vcampus-server -am -Dtest=StudentProfileReviewServiceTest,StudentProfileHandlersTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement optimistic withdrawal**

Add repository SQL equivalent to:

```sql
UPDATE tblStudentProfileApplication
SET applicationStatus='DRAFT', submittedAt=NULL,
    applicationVersion=applicationVersion+1, updatedAt=?
WHERE applicationId=? AND applicationStatus='PENDING' AND applicationVersion=?
```

Resolve student ID exclusively from session user ID, hold `STUDENT:<id>`, require exactly one updated row, return current formal profile plus reopened draft, and register the command through the existing deduplicating write wrapper.

- [ ] **Step 4: Verify GREEN and commit**

Run Task 6 tests and stage only withdrawal files/tests.

### Task 7: Student Withdrawal UI

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/service/StudentClientService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/MyStudentProfilePanel.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/StudentClientServiceTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/StudentProfileUiTest.java`

**Interfaces:**
- Produces: `StudentClientService.withdrawProfile(WithdrawStudentProfileCommand)`.
- Produces: the existing `student.profile.submit` button changes label/action between submit and withdraw based on application status.

- [ ] **Step 1: Write failing client/UI tests**

Assert PENDING renders `撤回申请`; canceling confirmation sends nothing; confirming sends expected version; success renders the preserved draft and re-enables edit; failure keeps PENDING. Click both edit links while pending and assert a user prompt appears but no editor opens. Assert DRAFT still submits normally.

- [ ] **Step 2: Verify RED outside the sandbox**

```bash
mvn -pl vcampus-client -am -Dtest=StudentClientServiceTest,StudentProfileUiTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement one state-dependent action button**

Render text/style/action from current application status; confirm withdrawal, disable controls while in flight, send expected version, and replace workspace only with the successful response. In edit handlers, detect PENDING before checking the enabled state and show the withdraw-first prompt. Preserve the existing PDF chooser/filter/extension behavior exactly.

- [ ] **Step 4: Verify GREEN and commit**

Run Task 7 tests; stage the full `MyStudentProfilePanel.java` only after confirming its pre-existing PDF export behavior remains in the diff and tests.

### Task 8: Integration, Visual QA, and Release

**Files:**
- Modify: `vcampus-database/seed/020_test_accounts.sql` or `021_more_students.sql` only if existing seeded identity data violates the finalized validator.
- Modify: `vCampus-release/`
- Replace: `vCampus-release.zip`
- Modify: `vCampus-release/使用说明.md`

**Interfaces:**
- Consumes all prior tasks.
- Produces tested shaded client/server JARs and a clean seeded Access database.

- [ ] **Step 1: Run focused integration tests**

Run server route/profile/manual tests and all student Swing tests. Fix only evidenced regressions.

- [ ] **Step 2: Run full verification**

```bash
mvn test
mvn -DskipTests clean package
git diff --check
```

Record module test counts and exit codes.

- [ ] **Step 3: Perform visual QA**

Render at least organization management with each selected depth, manual-student dialog at minimum supported size, invalid-field feedback/tooltip state, and pending/withdrawn student profile. Inspect alignment, clipping, disabled contrast, focus order, and role-specific visibility.

- [ ] **Step 4: Rebuild release artifacts**

Copy the latest distribution JARs/config/scripts, regenerate `vCampus-release/data/vCampus.accdb` with `DatabaseInitializer`, update usage notes, and create a clean zip without smoke-test logs or `.DS_Store`.

- [ ] **Step 5: Run release smoke test**

On a temporary port, verify administrator manual creation, student first-password behavior, student draft submission/withdraw/re-edit, teacher detail without history request, and administrator full detail. Stop the server, regenerate the clean release database, compare release/distribution JAR bytes, and run `unzip -tq vCampus-release.zip`.

- [ ] **Step 6: Commit release-source changes if requested**

Do not commit generated ignored/untracked release binaries unless the repository policy or user explicitly requests it.
