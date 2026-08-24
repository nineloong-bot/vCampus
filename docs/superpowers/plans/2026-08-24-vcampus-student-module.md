# Virtual Campus Student Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver organization data, student profiles, enrollment status changes, privacy-aware searches, the `StudentQueryPort`, message handlers, and seven Swing pages.

**Architecture:** The module owns department, major, class, student, and change-history tables. It consumes `UserQueryPort`, exposes only minimal eligibility data to course selection, and commits each profile mutation with its audit record.

**Tech Stack:** JDK 21, Maven, Swing, UCanAccess, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-08-24-vcampus-student-module-design.md` and the overall architecture spec.

## Global Constraints

- Complete foundation and user plans first.
- Never read course tables or depend on a course package.
- Only `ACTIVE` students are eligible to select courses.
- Keep contact fields out of teacher-facing summaries.
- Lock profile writes by `STUDENT:<studentId>` and creation by student number plus user ID.
- Preserve the ten `STUDENT_*` commands defined by the spec.

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

### Task 2: Concurrent Student Creation

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/CreateStudentCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentView.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentServiceImpl.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/CreateStudentTest.java`

**Interfaces:**
- Consumes: `UserQueryPort.findActiveUser`, repositories, transactions, locks.
- Produces: `StudentView createStudent(CreateStudentCommand)`.

- [ ] **Step 1: Write user-binding and concurrent-number tests**

```java
@Test
void oneUserAndOneStudentNumberCanEachBindOnlyOnce() throws Exception {
    List<Outcome<StudentView>> results = concurrently(20,
            () -> service.createStudent(command("user-1", "20260001")));
    assertThat(results.stream().filter(Outcome::isSuccess)).hasSize(1);
    assertThat(repository.countByNumber("20260001")).isEqualTo(1);
}
```

- [ ] **Step 2: Run creation tests**

Run: `mvn -pl vcampus-server -am -Dtest=CreateStudentTest test`

Expected: FAIL until repository and service exist.

- [ ] **Step 3: Implement locked creation with role and class checks**

```java
return locks.withLocks(List.of(key("STUDENT_NUMBER", number), key("USER", userId)),
        () -> transactions.inTransaction(connection -> {
            UserIdentity user = users.findActiveUser(userId)
                    .filter(value -> value.role() == STUDENT)
                    .orElseThrow(StudentUserNotEligibleException::new);
            organization.requireActiveClass(connection, command.classId());
            return mapper.toView(repository.insert(connection,
                    factory.create(command, user)));
        }));
```

- [ ] **Step 4: Verify creation behavior**

Run: `mvn -pl vcampus-server -am -Dtest=CreateStudentTest test`

Expected: PASS for duplicate number, duplicate user, wrong role, inactive class, and 20-thread uniqueness.

- [ ] **Step 5: Commit profile creation**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student vcampus-server/src/main/java/edu/seu/vcampus/server/student vcampus-server/src/test
git commit -m "feat(student): add student profile creation"
```

### Task 3: Profile Changes, Search Privacy, and Eligibility Port

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/StudentEligibility.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/TeacherStudentSummary.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentQueryPort.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentChangeRepository.java`
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

Use a dedicated `TeacherStudentSummary` without `email` or `phone`; do not serialize full `StudentView` to teacher searches.

- [ ] **Step 4: Run service tests**

Run: `mvn -pl vcampus-server -am -Dtest=StudentProfileUpdateTest,StudentQueryPortTest,StudentSearchPrivacyTest test`

Expected: PASS for status eligibility, audit atomicity, versions, owner checks, and privacy.

- [ ] **Step 5: Commit the student service contract**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/student vcampus-server/src/main/java/edu/seu/vcampus/server/student vcampus-server/src/test
git commit -m "feat(student): add profile changes and eligibility port"
```

### Task 4: Message Handlers and Swing Pages

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/handler/StudentHandlers.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/service/StudentClientService.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/MyStudentProfilePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentSearchPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentDetailPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/CreateStudentDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/UpdateContactDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/EnrollmentChangeDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/OrganizationManagementPanel.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/handler/StudentHandlersTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/StudentUiTest.java`

**Interfaces:**
- Consumes: message router, authorization, async client connection.
- Produces: ten `STUDENT_*` handlers and seven pages.

- [ ] **Step 1: Write command permissions and concurrent-edit UI tests**

```java
@Test
void staleContactEditPromptsRefreshAndKeepsTypedValues() {
    ProfileRobot robot = launchProfile(clientFailing("COMMON_CONCURRENT_MODIFICATION"));
    robot.setEmail("new@seu.edu.cn").save().await();
    assertThat(robot.email()).isEqualTo("new@seu.edu.cn");
    assertThat(robot.error()).contains("刷新");
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

- [ ] **Step 4: Run the complete module verification**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am verify`

Expected: PASS for all commands, privacy, concurrency, UI states, and an end-to-end create/search/update flow.

- [ ] **Step 5: Commit the completed module**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student vcampus-client/src/main/java/edu/seu/vcampus/client/student vcampus-server/src/test vcampus-client/src/test
git commit -m "feat(student): complete student management module"
```
