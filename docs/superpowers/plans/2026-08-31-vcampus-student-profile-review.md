# vCampus Student Profile Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete student profile draft, submission, administrator review, approved-data PDF export, ServerMain integration, and release-package workflow.

**Architecture:** Keep approved values in `tblStudent`, keep one open editable snapshot per student in `tblStudentProfileApplication`, and move snapshots into the formal row only inside an administrator approval transaction. Add typed socket commands around a focused `StudentProfileService`, then replace the current profile page and add an administrator review tab without disturbing existing admission, search, and organization flows.

**Tech Stack:** Java 21 records and enums, Swing, Maven, JUnit 5, AssertJ, UCanAccess/Microsoft Access, Apache PDFBox 3, embedded OFL CJK font, Poppler/pdfplumber for PDF QA.

**Spec:** `docs/superpowers/specs/2026-08-31-vcampus-student-profile-review-design.md`

## Global Constraints

- Preserve all existing uncommitted user changes and never overwrite unrelated files.
- Students may edit personal attributes/contact data and only `attendanceMode` in academic data.
- `attendanceMode` values are exactly `DAY_STUDENT`, `RESIDENT`, `LODGING`, and `OTHER`.
- A student has at most one `DRAFT` or `PENDING` application; `PENDING` is immutable.
- PDF data always comes from approved `tblStudent` values.
- All student self-service targets come from the authenticated session, never a client-supplied student id.
- Release artifacts replace only corresponding files under `vCampus-release`.

---

### Task 1: Profile Contracts and Access Schema

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/AttendanceMode.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentPersonalProfile.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentAcademicProfile.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentProfileData.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentProfileApplicationStatus.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentProfileApplicationView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentProfileWorkspace.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/SaveStudentPersonalDraftCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/SaveStudentAttendanceDraftCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/SubmitStudentProfileCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentProfileReviewQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/ReviewStudentProfileCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/PdfDocument.java`
- Modify: `vcampus-database/schema/020_student.sql`
- Modify: `vcampus-database/seed/020_test_accounts.sql`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/student/StudentProfileContractTest.java`

**Interfaces:**
- Produces: `StudentProfileData(StudentView core, StudentPersonalProfile personal, StudentAcademicProfile academic)` and the command/response records consumed by all later tasks.
- Produces: `AttendanceMode.fromDisplayName(String)` and `displayName()` for the UI and persistence layer.

- [ ] **Step 1: Write the failing contract test**

```java
@Test void attendanceModesUseTheFourApprovedLabels() {
    assertThat(Arrays.stream(AttendanceMode.values()).map(AttendanceMode::displayName))
            .containsExactly("走读", "住校", "借宿", "其他");
    assertThat(AttendanceMode.fromDisplayName("住校")).isEqualTo(AttendanceMode.RESIDENT);
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn -pl vcampus-common -Dtest=StudentProfileContractTest test`
Expected: compilation failure because `AttendanceMode` and profile contracts do not exist.

- [ ] **Step 3: Add exact serializable records and schema columns**

```java
public enum AttendanceMode {
    DAY_STUDENT("走读"), RESIDENT("住校"), LODGING("借宿"), OTHER("其他");
    private final String displayName;
    AttendanceMode(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
    public static AttendanceMode fromDisplayName(String value) {
        return Arrays.stream(values()).filter(mode -> mode.displayName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知就读方式"));
    }
}
```

Add named columns for every personal field in the reference table, the supplemental academic fields, and a `tblStudentProfileApplication` row containing the editable snapshot plus status/review/version timestamps. Seed the demo student with complete Chinese values and `RESIDENT`.

- [ ] **Step 4: Run contract and serialization tests and verify GREEN**

Run: `mvn -pl vcampus-common test`
Expected: all common tests pass.

- [ ] **Step 5: Commit only Task 1 files**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student vcampus-common/src/test/java/edu/seu/vcampus/common/student vcampus-database/schema/020_student.sql vcampus-database/seed/020_test_accounts.sql
git commit -m "feat(student): define profile review contracts"
```

### Task 2: Profile Repository and Approval Transactions

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentProfileApplicationRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentProfileService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentProfileServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentChangeRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/StudentProfileReviewServiceTest.java`

**Interfaces:**
- Consumes: Task 1 DTOs and `ResourceLockManager`, `TransactionManager`, `StudentRepository`.
- Produces: `getWorkspace(userId)`, `savePersonalDraft(userId, command)`, `saveAttendanceDraft(userId, command)`, `submit(userId, command)`, `listPending(query)`, `getApplication(applicationId)`, `approve(applicationId, reviewerUserId)`, and `reject(applicationId, reason, reviewerUserId)`.

- [ ] **Step 1: Write failing service tests for draft isolation and review**

```java
@Test void draftDoesNotChangeFormalProfileUntilApproval() {
    StudentProfileWorkspace draft = service.saveAttendanceDraft("user-1",
            new SaveStudentAttendanceDraftCommand(AttendanceMode.DAY_STUDENT, 0));
    assertThat(draft.formalProfile().academic().attendanceMode()).isEqualTo(AttendanceMode.RESIDENT);
    assertThat(draft.application().attendanceMode()).isEqualTo(AttendanceMode.DAY_STUDENT);
    service.submit("user-1", new SubmitStudentProfileCommand(draft.application().applicationVersion()));
    service.approve(draft.application().applicationId(), "admin-1");
    assertThat(service.getWorkspace("user-1").formalProfile().academic().attendanceMode())
            .isEqualTo(AttendanceMode.DAY_STUDENT);
}
```

Also add separate tests for overwriting one draft, pending immutability, rejection reason, stale formal version rollback, and one `PROFILE_CHANGE` history row.

- [ ] **Step 2: Run the service test and verify RED**

Run: `mvn -pl vcampus-server -am -Dtest=StudentProfileReviewServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: compilation failure because the profile repository and service are missing.

- [ ] **Step 3: Implement repository mapping and transactional service**

```java
return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), () ->
        transactions.inTransaction(connection -> {
            StudentProfileApplicationView pending = applications.requirePending(connection, applicationId);
            StudentProfileData formal = students.getProfile(connection, pending.studentId());
            if (formal.core().rowVersion() != pending.baseStudentVersion()) {
                throw new ConcurrentModificationException("Student profile version changed");
            }
            students.applyApprovedProfile(connection, pending, Instant.now());
            applications.markApproved(connection, applicationId, reviewerUserId, Instant.now());
            changes.insertProfileChange(connection, pending, reviewerUserId, Instant.now());
            return applications.requireById(connection, applicationId);
        }));
```

Use one open-row lookup inside the student lock for both draft saves. Reject blank review comments server-side. Compare editable snapshots before submit and reject a no-change draft.

- [ ] **Step 4: Run repository/service tests and verify GREEN**

Run: `mvn -pl vcampus-server -am -Dtest=StudentProfileReviewServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: all profile service tests pass.

- [ ] **Step 5: Commit only Task 2 files**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/StudentProfileReviewServiceTest.java
git commit -m "feat(student): add profile review transactions"
```

### Task 3: Socket Handlers and ServerMain Wiring

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/handler/StudentHandlers.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/handler/StudentProfileHandlersTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/ServerMainStudentWiringTest.java`

**Interfaces:**
- Consumes: `StudentProfileService` from Task 2 and existing `StudentAuthorizationPort`.
- Produces: nine profile commands registered on `MessageRouter` with self-targeting and `STUDENT_WRITE` review authorization.

- [ ] **Step 1: Write failing authorization and registration tests**

```java
@Test void studentWorkspaceUsesAuthenticatedUserId() {
    ResponseBody<?> response = route("STUDENT_PROFILE_GET_WORKSPACE", EmptyRequest.INSTANCE,
            principal("student-user", "STUDENT"));
    assertThat(response.success()).isTrue();
    verify(profiles).getWorkspace("student-user");
}
```

Assert teachers cannot list/approve requests, students cannot choose another id, and `ServerMain` constructs handlers with the same transaction/lock/runtime dependencies as the existing student service.

- [ ] **Step 2: Run focused handler tests and verify RED**

Run: `mvn -pl vcampus-server -am -Dtest=StudentProfileHandlersTest,ServerMainStudentWiringTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: missing constructor/command registration failures.

- [ ] **Step 3: Register commands and wire the service**

```java
router.register("STUDENT_PROFILE_GET_WORKSPACE", typed(EmptyRequest.class,
        (message, body) -> success(profiles.getWorkspace(principal(message).userId()))));
router.register("STUDENT_PROFILE_APPROVE", typed(ReviewStudentProfileCommand.class,
        (message, body) -> adminReview(message,
                () -> profiles.approve(body.applicationId(), principal(message).userId()))));
```

Keep current user and student handler wiring, add `StudentProfileApplicationRepository` and `StudentProfileServiceImpl`, and pass them into `StudentHandlers` without replacing unrelated user-owned edits.

- [ ] **Step 4: Run all server handler tests and verify GREEN**

Run: `mvn -pl vcampus-server -am -Dtest='*HandlersTest,*WiringTest' -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: all selected tests pass.

- [ ] **Step 5: Commit Task 3 files**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/handler/StudentHandlers.java vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java vcampus-server/src/test/java/edu/seu/vcampus/server/student/handler/StudentProfileHandlersTest.java vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/ServerMainStudentWiringTest.java
git commit -m "feat(server): expose student profile review commands"
```

### Task 4: Approved-Data PDF Service

**Files:**
- Modify: `pom.xml`
- Modify: `vcampus-server/pom.xml`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/pdf/StudentProfilePdfService.java`
- Create: `vcampus-server/src/main/resources/fonts/NotoSansCJKsc-Regular.otf`
- Create: `vcampus-server/src/main/resources/fonts/OFL.txt`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/pdf/StudentProfilePdfServiceTest.java`

**Interfaces:**
- Consumes: approved `StudentProfileData` only.
- Produces: `PdfDocument generate(StudentProfileData profile, Instant generatedAt)` with a safe suggested filename and PDF bytes.

- [ ] **Step 1: Write a failing PDF content test**

```java
@Test void pdfContainsApprovedChineseProfileAndNeverDraftValues() throws Exception {
    PdfDocument pdf = service.generate(formalProfile("住校", "测试学生"), FIXED_TIME);
    try (PDDocument document = Loader.loadPDF(pdf.content())) {
        String text = new PDFTextStripper().getText(document);
        assertThat(text).contains("学生基本信息表", "测试学生", "住校", "数据以系统正式档案为准");
        assertThat(text).doesNotContain("走读");
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn -pl vcampus-server -am -Dtest=StudentProfilePdfServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: compilation failure because the PDF service is missing.

- [ ] **Step 3: Add PDFBox, OFL font, and A4 table renderer**

```java
public PdfDocument generate(StudentProfileData profile, Instant generatedAt) {
    try (PDDocument document = new PDDocument()) {
        PDFont font = PDType0Font.load(document,
                requireResource("/fonts/NotoSansCJKsc-Regular.otf"), true);
        renderTitleAndMetadata(document, font, profile, generatedAt);
        renderPersonalTable(document, font, profile.personal());
        renderAcademicTable(document, font, profile.academic());
        renderPageFooters(document, font);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output);
        return new PdfDocument(safeFilename(profile), output.toByteArray());
    } catch (IOException error) {
        throw new IllegalStateException("无法生成学籍信息 PDF", error);
    }
}
```

Download the official OFL font from its primary upstream source, retain its license file, embed it in the shaded server JAR, and paginate before a row would cross the footer boundary.

- [ ] **Step 4: Run PDF tests and render a fixture**

Run: `mvn -pl vcampus-server -am -Dtest=StudentProfilePdfServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: text assertions pass and the fixture writes a valid PDF under `target/pdf-qa/`.

Run: `pdftoppm -png vcampus-server/target/pdf-qa/student-profile.pdf /tmp/vcampus-student-profile`
Expected: page PNG files render without Poppler errors.

- [ ] **Step 5: Commit Task 4 files**

```bash
git add pom.xml vcampus-server/pom.xml vcampus-server/src/main/java/edu/seu/vcampus/server/student/pdf vcampus-server/src/main/resources/fonts vcampus-server/src/test/java/edu/seu/vcampus/server/student/pdf
git commit -m "feat(student): export approved profile PDF"
```

### Task 5: Typed Client Facade and Student Profile UI

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/service/StudentClientService.java`
- Replace: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/MyStudentProfilePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentProfileSectionPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/PersonalProfileEditDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/AttendanceModeEditDialog.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/StudentProfileUiTest.java`

**Interfaces:**
- Consumes: nine profile socket commands and Task 1 DTOs.
- Produces: table sections with title-adjacent edit buttons, draft state rendering, PDF save chooser, and bottom export/submit actions.

- [ ] **Step 1: Replace profile UI tests with failing behavior tests**

```java
@Test void academicEditorOnlyEnablesAttendanceMode() {
    MyStudentProfilePanel panel = loadedProfilePanel();
    click(findButton(panel, "student.profile.academic.edit"));
    JComboBox<?> mode = findCombo(panel, "student.profile.attendanceMode.input");
    assertThat(items(mode)).containsExactly("走读", "住校", "借宿", "其他");
    assertThat(findAllEditableTextComponents(panel)).isEmpty();
}
```

Add tests for edit buttons beside headings, personal save-to-draft, pending lockout, rejected reason, export on the left, submit on the right, and disconnected write disabling.

- [ ] **Step 2: Run focused UI tests and verify RED**

Run: `mvn -pl vcampus-client -am -Dtest=StudentProfileUiTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: missing components and command methods fail.

- [ ] **Step 3: Implement facade and refined table UI**

```java
public CompletableFuture<ResponseBody<StudentProfileWorkspace>> getProfileWorkspace() {
    return sendAsync("STUDENT_PROFILE_GET_WORKSPACE", EmptyRequest.INSTANCE);
}
public CompletableFuture<ResponseBody<StudentProfileWorkspace>> saveAttendanceDraft(
        SaveStudentAttendanceDraftCommand command) {
    return sendAsync("STUDENT_PROFILE_SAVE_ATTENDANCE_DRAFT", command);
}
```

Use the established theme constants, subtle teal section rules, compact bordered label/value cells, responsive column count, and exact component names required by tests. Use `JFileChooser` and `Files.write` only after a successful PDF response.

- [ ] **Step 4: Run all student client tests and verify GREEN**

Run: `mvn -pl vcampus-client -am -Dtest='Student*Test,*Profile*Test' -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: all selected student UI/service tests pass.

- [ ] **Step 5: Commit Task 5 files**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/student vcampus-client/src/test/java/edu/seu/vcampus/client/student/StudentProfileUiTest.java
git commit -m "feat(student-ui): add staged profile editing"
```

### Task 6: Administrator Review UI

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentProfileReviewPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentProfileReviewDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentModulePageFactory.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/StudentProfileReviewPanelTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/StudentModulePageFactoryTest.java`

**Interfaces:**
- Consumes: client review list/get/approve/reject methods from Task 5.
- Produces: administrator-only `资料审核` tab and formal-to-requested diff UI.

- [ ] **Step 1: Write failing administrator UI tests**

```java
@Test void adminHasReviewTabAndTeacherDoesNot() {
    assertThat(tabTitles(factoryFor(UserRole.ADMIN))).contains("资料审核");
    assertThat(tabTitles(factoryFor(UserRole.TEACHER))).doesNotContain("资料审核");
}
```

Add tests that changed fields are highlighted, approve requires confirmation, reject disables confirmation for blank reason, and successful review refreshes the pending list.

- [ ] **Step 2: Run focused review UI tests and verify RED**

Run: `mvn -pl vcampus-client -am -Dtest=StudentProfileReviewPanelTest,StudentModulePageFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: missing review panel/tab failures.

- [ ] **Step 3: Implement pending list and diff dialog**

```java
if (admin) {
    tabs.addTab("组织管理", new OrganizationManagementPanel(students, connection));
    tabs.addTab("资料审核", new StudentProfileReviewPanel(students, connection));
}
```

Render each changed field as `正式值  →  申请值`, use an accessible highlight color plus text marker, and keep review actions disabled during asynchronous requests.

- [ ] **Step 4: Run all client tests and verify GREEN**

Run: `mvn -pl vcampus-client -am test`
Expected: all client tests pass.

- [ ] **Step 5: Commit Task 6 files**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui vcampus-client/src/test/java/edu/seu/vcampus/client/student
git commit -m "feat(student-ui): add administrator profile review"
```

### Task 7: End-to-End Verification and Release Replacement

**Files:**
- Modify: `vCampus-release/使用说明.md`
- Replace: `vCampus-release/lib/vCampusClient.jar`
- Replace: `vCampus-release/lib/vCampusServer.jar`
- Replace: `vCampus-release/data/vCampus.accdb`
- Replace if changed: `vCampus-release/config/*`, `vCampus-release/scripts/*`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/network/StudentProfileSocketIntegrationTest.java`

**Interfaces:**
- Consumes: complete client/server/profile/PDF implementation.
- Produces: a runnable release directory with seeded administrator/student accounts and the entire review workflow.

- [ ] **Step 1: Write a failing socket workflow test**

```java
@Test void studentDraftRequiresAdministratorApprovalAcrossSocketBoundary() throws Exception {
    StudentProfileWorkspace before = student.getProfileWorkspace().join().data();
    student.saveAttendanceDraft(new SaveStudentAttendanceDraftCommand(AttendanceMode.DAY_STUDENT,
            before.application() == null ? 0 : before.application().applicationVersion())).join();
    student.submitProfile(new SubmitStudentProfileCommand(1)).join();
    assertThat(student.getProfileWorkspace().join().data().formalProfile().academic().attendanceMode())
            .isEqualTo(AttendanceMode.RESIDENT);
    administrator.approveProfile(pendingApplicationId()).join();
    assertThat(student.getProfileWorkspace().join().data().formalProfile().academic().attendanceMode())
            .isEqualTo(AttendanceMode.DAY_STUDENT);
}
```

- [ ] **Step 2: Run the socket integration test and verify RED, then complete missing boundary wiring**

Run: `mvn -pl vcampus-client -am -Dtest=StudentProfileSocketIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected before final wiring: command or state assertion failure. Add only the missing transport/runtime integration necessary for the test.

- [ ] **Step 3: Run complete verification**

Run: `mvn clean verify`
Expected: reactor success with zero test failures.

Run: `mvn package -DskipTests`
Expected: shaded client/server JARs are written to `vcampus-distribution/lib`.

- [ ] **Step 4: Rebuild the release database and replace matching artifacts**

```bash
java -cp vcampus-distribution/lib/vCampusServer.jar edu.seu.vcampus.server.bootstrap.DatabaseInitializer vcampus-database/schema vcampus-database/seed /tmp/vCampus.accdb
cp vcampus-distribution/lib/vCampusClient.jar vCampus-release/lib/vCampusClient.jar
cp vcampus-distribution/lib/vCampusServer.jar vCampus-release/lib/vCampusServer.jar
cp /tmp/vCampus.accdb vCampus-release/data/vCampus.accdb
```

Update the usage guide with the profile draft/review/PDF flow and keep existing account credentials accurate.

- [ ] **Step 5: Smoke-test release JARs and inspect PDF PNGs**

Run server with an isolated copy of the release database, execute login/profile/export/review through the integration harness, then render the exported PDF with `pdftoppm`. Inspect every PNG for missing Chinese glyphs, clipped rows, overlaps, blank pages, incorrect footers, and unreadable text.

- [ ] **Step 6: Commit source, tests, guide, and permitted release metadata**

```bash
git add vcampus-client vcampus-common vcampus-server vcampus-database vCampus-release/使用说明.md docs/superpowers/plans/2026-08-31-vcampus-student-profile-review.md
git commit -m "feat: complete student profile approval workflow"
```

Do not attempt to add ignored binary release artifacts to Git; verify their checksums and report their absolute paths instead.
