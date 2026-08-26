# Virtual Campus Student Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver organization data, student profiles, enrollment status changes, privacy-aware searches, the `StudentQueryPort`, message handlers, and seven Swing pages.

**Architecture:** The module owns organization, student, number-sequence, and change-history tables. `StudentAdmissionCoordinator` allocates both numbers, provisions the login account through `UserAccountProvisioningPort`, writes the profile/audit/idempotency result, and commits them in one caller-owned transaction; `StudentQueryPort` exposes only minimal eligibility data to course selection.

**Tech Stack:** JDK 21, Maven, Swing, UCanAccess, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-08-24-vcampus-student-module-design.md`, `docs/superpowers/specs/2026-08-24-vcampus-overall-architecture-design.md`, and `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`

## Global Constraints

- Complete foundation and user plans first.
- Never read course tables or depend on a course package.
- Only `ACTIVE` students are eligible to select courses.
- Keep contact fields out of teacher-facing summaries.
- Admission lock order is always `NUMBER_SEQUENCE:CAMPUS_CARD_GLOBAL`, `NUMBER_SEQUENCE:STUDENT_NUMBER:<majorCode>:<YY>:<classNumber>`, then generated `LOGIN_ID:<campusCardNumber>`; profile writes use `STUDENT:<studentId>`.
- Campus-card numbers are `2T3YYNNNN` with one global non-resetting `0001–9999` sequence; student numbers are `PPPYYCSS` with per-major/year/class `01–99` sequences.
- Admission creates the campus card, student number, `ACTIVE/STUDENT` account, profile, admission audit, and idempotent result in one Access transaction; any failure rolls back every write and both sequence increments.
- `tblUser.loginId` is the campus-card authority and `tblStudent.studentNumber` is the student-number authority; never duplicate the campus-card number in `tblStudent`.
- Preserve the ten `STUDENT_*` commands defined by the spec.
- Complete the shared UI design-system plan before Task 5; as the default UI owner, the student-module lead owns tokens, shared components, and shell together.

---

### Task 1: Organization Schema and Repository

**Files:**
- Create: `vcampus-database/schema/020_student.sql`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/OrganizationRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/AccessOrganizationRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/repository/OrganizationRepositoryTest.java`

**Interfaces:**
- Consumes: `TransactionManager`.
- Produces: active department, major, and class lookup plus hierarchy validation.

- [ ] **Step 1: Write hierarchy and deactivation tests**

```java
@Test
void rejectsClassWhoseMajorDoesNotBelongToDepartment() {
    Department cs = repository.insertDepartment(department("CS"));
    Major law = repository.insertMajor(major("LAW", otherDepartmentId));
    assertThat(repository.classBelongsTo("class-1", law.majorId(), cs.departmentId()))
            .isFalse();
}
```

- [ ] **Step 2: Run organization tests**

Run: `mvn -pl vcampus-server -am -Dtest=OrganizationRepositoryTest test`

Expected: FAIL because schema/repository do not exist.

- [ ] **Step 3: Implement `tblDepartment`, `tblMajor`, and `tblClass`**

```java
public interface OrganizationRepository {
    Optional<Department> findDepartment(Connection c, String id);
    Optional<Major> findMajor(Connection c, String id);
    Optional<StudentClass> findClass(Connection c, String id);
    List<Major> listActiveMajors(Connection c, String departmentId);
    List<StudentClass> listActiveClasses(Connection c, String majorId);
}
```

- [ ] **Step 4: Run Access integration tests**

Run: `mvn -pl vcampus-server -am -Dtest=OrganizationRepositoryTest test`

Expected: PASS for hierarchy, uniqueness, active filtering, and versions.

- [ ] **Step 5: Commit organization persistence**

```bash
git add vcampus-database/schema/020_student.sql vcampus-server/src/main/java/edu/seu/vcampus/server/student vcampus-server/src/test/java/edu/seu/vcampus/server/student
git commit -m "feat(student): add organization persistence"
```

### Task 2: Number Sequences and Deterministic Generators

**Files:**
- Modify: `vcampus-database/schema/020_student.sql`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/numbering/CampusCardNumberGenerator.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/numbering/StudentNumberGenerator.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/numbering/AccessCampusCardNumberGenerator.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/numbering/AccessStudentNumberGenerator.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/NumberSequenceRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/numbering/CampusCardNumberGeneratorTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/numbering/StudentNumberGeneratorTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/numbering/NumberSequenceConcurrencyTest.java`

**Interfaces:**
- Consumes: an already-open `TransactionContext`, `tblNumberSequence`, and Task 1 organization metadata.
- Produces: `CampusCardNumberGenerator.next(TransactionContext, StudentType, int)` and `StudentNumberGenerator.next(TransactionContext, String majorCode, int enrollmentYear, int classNumber)`.

- [ ] **Step 1: Write all format, boundary, rollback, and concurrency tests**

```java
@ParameterizedTest
@CsvSource({"UNDERGRADUATE,2024,2478,213242478",
            "MASTER,2000,1,223000001", "DOCTORATE,2099,9999,233999999"})
void formatsCampusCard(StudentType type, int year, int sequence, String expected) {
    assertThat(formatter.campusCard(type, year, sequence)).isEqualTo(expected);
}

@Test
void allocatesUniqueClassNumbersAndRollsBackFailedIncrement() throws Exception {
    List<String> numbers = concurrently(20, () -> inTransaction(tx ->
            studentNumbers.next(tx, "09J", 2024, 1)));
    assertThat(numbers).doesNotHaveDuplicates().allMatch(v -> v.matches("09J24[1-9][0-9]{2}"));
    assertThatThrownBy(() -> inTransaction(tx -> {
        campusCards.next(tx, UNDERGRADUATE, 2024);
        throw new InjectedFailure();
    })).isInstanceOf(InjectedFailure.class);
    assertThat(sequenceValue("CAMPUS_CARD_GLOBAL")).isZero();
}
```

- [ ] **Step 2: Run numbering tests and confirm failure**

Run: `mvn -pl vcampus-server -am -Dtest=CampusCardNumberGeneratorTest,StudentNumberGeneratorTest,NumberSequenceConcurrencyTest test`

Expected: FAIL because sequence repositories and generators do not exist.

- [ ] **Step 3: Implement transactional sequence increments and exact formatting**

```java
public String next(TransactionContext tx, StudentType type, int year) {
    NumberSequence sequence = sequences.require(tx.connection(), "CAMPUS_CARD_GLOBAL");
    if (sequence.currentValue() >= 9999) throw new CampusCardSequenceExhaustedException();
    int next = sequence.currentValue() + 1;
    sequences.updateWithVersion(tx.connection(), sequence.withCurrentValue(next));
    return "2" + type.digit() + "3" + twoDigits(year % 100) + fourDigits(next);
}
```

The class sequence key is `STUDENT_NUMBER:<majorCode>:<YY>:<classNumber>`. Validate `majorCode` against `^[0-9A-Z]{3}$`, year 2000–2099, class number 1–9, and limits before updating with `rowVersion`; generators never cache an uncommitted value.

- [ ] **Step 4: Verify numbering behavior**

Run: `mvn -pl vcampus-server -am -Dtest=CampusCardNumberGeneratorTest,StudentNumberGeneratorTest,NumberSequenceConcurrencyTest test`

Expected: PASS for all documented examples, 2000/2099 boundaries, global non-reset behavior, per-class isolation, 20-thread uniqueness, row-version protection, rollback reuse, and both exhaustion errors.

- [ ] **Step 5: Commit numbering**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/{numbering,repository} vcampus-server/src/test/java/edu/seu/vcampus/server/student/numbering vcampus-database/schema/020_student.sql
git commit -m "feat(student): add transactional student numbering"
```

### Task 3: Atomic Student Admission Coordinator

**Files:**
- Modify: `vcampus-database/schema/020_student.sql`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/CreateStudentAdmissionCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentAdmissionResult.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentView.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentChangeRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentAdmissionService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentAdmissionCoordinator.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/StudentAdmissionCoordinatorTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/StudentAdmissionConcurrencyTest.java`

**Interfaces:**
- Consumes: `RequestContext.requestId`, authorization, organization repository, Task 2 generators, `UserAccountProvisioningPort`, transactions, resource locks, and request deduplication.
- Produces: `StudentAdmissionResult admit(CreateStudentAdmissionCommand, RequestContext)` and one active student/profile/account binding.

- [ ] **Step 1: Write idempotency, fault-injection, and 20-admission tests**

```java
@ParameterizedTest
@EnumSource(AdmissionFailurePoint.class)
void everyFailureRollsBackSequencesAccountProfileAuditsAndDedup(AdmissionFailurePoint point) {
    coordinator.failAt(point);
    assertThatThrownBy(() -> coordinator.admit(command(),
            request("8e7c1a21-9d44-4c82-978b-df34326a0341")))
            .isInstanceOf(InjectedAdmissionFailure.class);
    assertThat(snapshot()).isEqualTo(snapshotBeforeAdmission());
}

@Test
void replayReturnsOriginalResultWithoutAllocatingAgain() {
    String requestId = "8e7c1a21-9d44-4c82-978b-df34326a0341";
    StudentAdmissionResult first = coordinator.admit(command(), request(requestId));
    StudentAdmissionResult replay = coordinator.admit(command(), request(requestId));
    assertThat(replay).isEqualTo(first);
    assertThat(sequenceDelta()).containsExactly(1, 1);
}
```

- [ ] **Step 2: Run admission tests and confirm failure**

Run: `mvn -pl vcampus-server -am -Dtest=StudentAdmissionCoordinatorTest,StudentAdmissionConcurrencyTest test`

Expected: FAIL because the coordinator and admission DTOs are absent.

- [ ] **Step 3: Implement the exact admission transaction and lock order**

```java
AdmissionKeyData keyData = validator.preValidate(command);
return requestDeduplicator.replayCompleted(request.requestId()).orElseGet(() ->
        locks.withLocks(List.of(keyData.campusSequenceKey(),
                keyData.classSequenceKey()), () ->
                transactions.inTransaction(tx -> {
                    requestDeduplicator.claim(tx, request);
                    ValidatedAdmission validated = validator.revalidate(tx, command, keyData);
                    String campusCard = campusCards.next(tx, validated.type(), validated.year());
                    String studentNumber = studentNumbers.next(tx, validated.majorCode(),
                            validated.year(), validated.classNumber());
                    return locks.withLock("LOGIN_ID", campusCard, () ->
                            persistAndCompleteDedup(tx, request, validated,
                                    campusCard, studentNumber));
                })));
```

`persistAndCompleteDedup` calls `UserAccountProvisioningPort.createStudentAccount(tx, campusCard, "12345678".toCharArray())`, inserts `tblStudent` without a campus-card column, writes `ADMISSION` to `tblStudentChange`, stores the idempotent response in the same transaction, and returns `mustChangePassword=true`. `preValidate` derives the fixed sequence keys without touching the database; `revalidate` checks active hierarchy, major/class ownership, matching enrollment year, and sequence capacity again inside the transaction.

- [ ] **Step 4: Verify admission atomically**

Run: `mvn -pl vcampus-server -am -Dtest=StudentAdmissionCoordinatorTest,StudentAdmissionConcurrencyTest,UserAccountProvisioningPortTest test`

Expected: PASS for all fault points, exact lock order, 20 unique simultaneous admissions, request replay, class/year mismatch, inactive organization, both capacity errors, account/profile one-to-one uniqueness, and no nested transaction.

- [ ] **Step 5: Commit atomic admission**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student vcampus-server/src/main/java/edu/seu/vcampus/server/student vcampus-server/src/test/java/edu/seu/vcampus/server/student
git commit -m "feat(student): add atomic student admission"
```

### Task 4: Profile Changes, Search Privacy, and Eligibility Port

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentEligibility.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentSummary.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentQueryPort.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentChangeRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/StudentProfileUpdateTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/StudentQueryPortTest.java`

**Interfaces:**
- Consumes: student/session identity and `expectedVersion`.
- Produces: `updateContact`, `updateEnrollment`, `changeStatus`, privacy-aware search, and `StudentQueryPort` exactly as specified.

- [ ] **Step 1: Write atomic change and eligibility tests**

```java
@ParameterizedTest
@EnumSource(value = StudentStatus.class, names = {"SUSPENDED", "GRADUATED", "WITHDRAWN"})
void nonActiveStatusIsNotEligible(StudentStatus status) {
    repository.save(student(status));
    assertThat(port.getEnrollmentEligibility("user-1").eligible()).isFalse();
}

@Test
void staleUpdateDoesNotWriteChangeHistory() {
    assertThatThrownBy(() -> service.changeStatus(changeStatus(0)))
            .isInstanceOf(ConcurrentModificationException.class);
    assertThat(changes.count()).isZero();
}
```

- [ ] **Step 2: Run profile and port tests**

Run: `mvn -pl vcampus-server -am -Dtest=StudentProfileUpdateTest,StudentQueryPortTest test`

Expected: FAIL until change transactions and port exist.

- [ ] **Step 3: Implement profile transactions and restricted summaries**

```java
public StudentEligibility getEnrollmentEligibility(String userId) {
    Student student = repository.findByUserId(userId)
            .orElseThrow(StudentNotFoundException::new);
    boolean eligible = student.status() == ACTIVE;
    return new StudentEligibility(student.studentId(), student.status(), eligible,
            eligible ? "ELIGIBLE" : "STATUS_" + student.status());
}
```

Use the specified `StudentSummary` without `email` or `phone`; do not serialize full `StudentView` to teacher searches.

- [ ] **Step 4: Run service tests**

Run: `mvn -pl vcampus-server -am -Dtest=StudentProfileUpdateTest,StudentQueryPortTest,StudentSearchPrivacyTest test`

Expected: PASS for status eligibility, audit atomicity, versions, owner checks, and privacy.

- [ ] **Step 5: Commit the student service contract**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student vcampus-server/src/main/java/edu/seu/vcampus/server/student vcampus-server/src/test
git commit -m "feat(student): add profile changes and eligibility port"
```

### Task 5: Message Handlers and Seven UI-Spec-Compliant Swing Pages

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/handler/StudentHandlers.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/service/StudentClientService.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/MyStudentProfilePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentSearchPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentDetailPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentAdmissionDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/UpdateContactDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/EnrollmentChangeDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/OrganizationManagementPanel.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/handler/StudentHandlersTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/StudentUiTest.java`
- Modify: `docs/ui-review/manifest.md`

**Interfaces:**
- Consumes: message router, authorization, async client connection.
- Produces: ten `STUDENT_*` handlers and seven pages composed from the shared UI design system.

- [ ] **Step 1: Write command permissions and concurrent-edit UI tests**

```java
@Test
void staleContactEditPromptsRefreshAndKeepsTypedValues() {
    ProfileRobot robot = launchProfile(clientFailing("COMMON_CONCURRENT_MODIFICATION"));
    robot.setEmail("new@seu.edu.cn").save().await();
    assertThat(robot.email()).isEqualTo("new@seu.edu.cn");
    assertThat(robot.error()).contains("刷新");
}

@Test
void admissionDialogNeverAcceptsGeneratedIdentifiersOrInitialPassword() {
    AdmissionDialogRobot robot = launchAdmissionDialog();
    assertThat(robot.inputLabels()).doesNotContain("一卡通号", "学号", "用户编号", "初始密码");
    robot.select(UNDERGRADUATE, "090", 2024, 1).submit().await();
    assertThat(robot.successSummary()).contains("一卡通号", "学号", "12345678", "首次登录");
}

@Test
void studentPagesPassSharedUiAudit() {
    UiAuditResult audit = UiComplianceAudit.inspect(studentPages());
    assertThat(audit.pagesWithoutTemplate()).isEmpty();
    assertThat(audit.pagesMissingRequiredStates()).isEmpty();
    assertThat(audit.privateThemeClasses()).isEmpty();
    assertThat(audit.inaccessibleControls()).isEmpty();
}
```

- [ ] **Step 2: Run handler and UI tests**

Run: `mvn -pl vcampus-server,vcampus-client -am -Dtest=StudentHandlersTest,StudentUiTest test`

Expected: FAIL before handlers/pages exist.

- [ ] **Step 3: Register exact commands and implement async pages**

```java
router.register("STUDENT_UPDATE_CONTACT", handler(
        UpdateStudentContactCommand.class, service::updateContact));
router.register("STUDENT_CHANGE_STATUS", adminHandler(
        ChangeStudentStatusCommand.class, service::changeStatus));
```

Populate department → major → class controls asynchronously and clear invalid child selections whenever the parent changes.

Map `StudentSearchPanel` to the query-list template; map `MyStudentProfilePanel` and `StudentDetailPanel` to the detail template; map `OrganizationManagementPanel` to the management template; and use shared 560/720 px dialog structure for admission, contact, and enrollment changes. Display `rowVersion`, preserve typed values on conflict, implement all required page states, and use shared latest-request/disposal guards.

- [ ] **Step 4: Run the complete module verification**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am verify`

Expected: PASS for all commands, atomic admission and first login, privacy, concurrency, UI design-system compliance, screenshot manifest entries, and an end-to-end admit/login/search/update flow.

- [ ] **Step 5: Commit the completed module**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student vcampus-client/src/main/java/edu/seu/vcampus/client/student vcampus-server/src/test vcampus-client/src/test docs/ui-review/manifest.md
git commit -m "feat(student): complete student management module"
```
