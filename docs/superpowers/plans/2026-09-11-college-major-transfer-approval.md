# College Major-Transfer Approval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge `feat/user-management` into the current branch and enforce source-college and target-college approval boundaries throughout the major-transfer workflow.

**Architecture:** Reuse the user-management branch's `COLLEGE_ADMIN` role and `tblStudentCollegeAdministrator` binding. Add a server-side transfer authorization service that derives source and target colleges from persisted application data; handlers separate central `STUDENT_ADMIN` commands from college approval commands, and repository queries filter college-visible applications at the database boundary.

**Tech Stack:** Java 21, Swing, Maven, JUnit 5, AssertJ, Microsoft Access, UCanAccess 5.1.3.

**Spec:** `docs/superpowers/specs/2026-09-11-college-major-transfer-approval-design.md`

## Global Constraints

- Keep the current branch's transfer, training-plan, seed-data, and UCanAccess initialization fixes.
- Keep `feat/user-management` account management, session revocation, hierarchical roles, module administration, and college administration.
- `STUDENT_ADMIN` must not execute source-college or target-college approvals.
- `COLLEGE_ADMIN` must not execute central transfer-management commands.
- Server-side persisted data is the only authority for college scope.
- One college may have multiple administrators; one administrator may have only one active college binding.
- Rebuild release binaries and the release database only after source and tests are green.

---

### Task 1: Merge the user-management branch and restore a green baseline

**Files:**
- Merge: `feat/user-management`
- Resolve: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
- Resolve: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/handler/StudentHandlers.java`
- Resolve: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/SeededStudentDatasetTest.java`
- Resolve: `vcampus-database/seed/010_roles_permissions.sql`
- Resolve: `vcampus-database/seed/020_test_accounts.sql`
- Resolve: `vcampus-database/seed/021_more_students.sql`
- Resolve if reported by Git: `.gitignore`, client shell/page factories, distribution files, and documentation files

**Interfaces:**
- Consumes: current `nineloong` branch and local `feat/user-management` at `d9d85a6`
- Produces: one merge commit containing both feature sets and compiling role names `SUPER_ADMIN`, `STUDENT_ADMIN`, `COLLEGE_ADMIN`, `COURSE_ADMIN`, `LIBRARY_ADMIN`, `SHOP_ADMIN`, `USER_ADMIN`, `STUDENT`, and `TEACHER`

- [ ] **Step 1: Record the pre-merge state**

Run:

```bash
git status --short --branch
git log -1 --oneline HEAD
git log -1 --oneline feat/user-management
```

Expected: current branch is `nineloong`, the worktree is clean, and the approved design commit is at HEAD.

- [ ] **Step 2: Start the merge**

Run:

```bash
git merge --no-ff feat/user-management
```

Expected: Git either creates a merge commit or stops with an explicit conflict list.

- [ ] **Step 3: Resolve conflicts semantically**

For every conflict, remove all conflict markers and preserve these concrete behaviors:

```text
ServerMain:
  assemble user-management governance/session services
  register transfer and training-plan handlers

DatabaseInitializer and seed files:
  keep YESNO -> BOOLEAN normalization
  remove leading SQL line comments before execution
  persist schema files separately
  retain hierarchical roles, college bindings, transfer demo data, and training-plan demo data

StudentHandlers/client page factory:
  use hierarchical roles from feat/user-management
  retain current transfer and training-plan navigation

Distribution binaries/database:
  do not choose either stale binary; regenerate in Task 6
```

Run:

```bash
rg -n '^(<<<<<<<|=======|>>>>>>>)' . --glob '!target/**' --glob '!vCampus-release/**'
git status --short
```

Expected: no conflict markers and no unmerged paths.

- [ ] **Step 4: Compile the merged source**

Run:

```bash
mvn -DskipTests compile
```

Expected: all four reactor modules report `SUCCESS`.

- [ ] **Step 5: Run the merged baseline tests**

Run:

```bash
mvn test
```

Expected: all tests pass before adding transfer-scope behavior. Fix only semantic merge regressions at this step.

- [ ] **Step 6: Complete the merge commit**

Run:

```bash
git add -A
git commit
```

Expected commit subject: `Merge branch 'feat/user-management' into nineloong`.

---

### Task 2: Add transfer-specific college authorization

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/security/MajorTransferCollegeAuthorizationService.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/security/MajorTransferCollegeAuthorizationServiceTest.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepository.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/support/StudentAccessTestDatabase.java`

**Interfaces:**
- Consumes: `TransactionManager`, `tblStudentCollegeAdministrator`, `tblMajorTransferApplication`, and `tblMajorTransferOption`
- Produces: `requireCanRead(String userId, String applicationId)`, `requireSourceApproval(String userId, String applicationId)`, `requireTargetApproval(String userId, String applicationId)`, and `findActiveDepartmentId(Connection connection, String userId)`

- [ ] **Step 1: Write failing authorization tests**

Create tests with two colleges, one application, and one administrator per college:

```java
@Test void sourceAdministratorCanApproveOnlyTheSourceStage() {
    assertThatCode(() -> authorization.requireSourceApproval(SOURCE_ADMIN, APPLICATION))
            .doesNotThrowAnyException();
    assertThatThrownBy(() -> authorization.requireTargetApproval(SOURCE_ADMIN, APPLICATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("COMMON_FORBIDDEN");
}

@Test void targetAdministratorCanApproveOnlyTheTargetStage() {
    assertThatCode(() -> authorization.requireTargetApproval(TARGET_ADMIN, APPLICATION))
            .doesNotThrowAnyException();
    assertThatThrownBy(() -> authorization.requireSourceApproval(TARGET_ADMIN, APPLICATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("COMMON_FORBIDDEN");
}

@Test void unrelatedAdministratorCannotReadApplication() {
    assertThatThrownBy(() -> authorization.requireCanRead(OTHER_ADMIN, APPLICATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("COMMON_FORBIDDEN");
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferCollegeAuthorizationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `MajorTransferCollegeAuthorizationService` does not exist.

- [ ] **Step 3: Implement persisted-scope authorization**

Implement a focused final class whose query joins the application to its option and validates the active user and active binding:

```java
SELECT a.fromDepartmentId, o.targetDepartmentId
FROM tblMajorTransferApplication a
INNER JOIN tblMajorTransferOption o ON a.optionId=o.optionId
WHERE a.applicationId=?
```

Resolve the administrator with:

```java
SELECT a.departmentId
FROM tblStudentCollegeAdministrator a
INNER JOIN tblUser u ON a.userId=u.userId
WHERE a.userId=? AND a.isActive=TRUE
  AND u.roleCode='COLLEGE_ADMIN' AND u.accountStatus='ACTIVE'
```

`requireCanRead` accepts either matching department; source and target methods accept only their corresponding department. Missing applications, inactive bindings, wrong roles, and mismatches all throw `new IllegalArgumentException("COMMON_FORBIDDEN")`.

- [ ] **Step 4: Run the authorization tests and verify GREEN**

Run the command from Step 2.

Expected: all authorization tests pass.

- [ ] **Step 5: Commit the authorization boundary**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/security vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/security vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepository.java vcampus-server/src/test/java/edu/seu/vcampus/server/student/support/StudentAccessTestDatabase.java
git commit -m "feat(transfer): enforce college approval scope"
```

---

### Task 3: Split central and college transfer commands at the handler boundary

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferHandlers.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferRoleAuthorizationTest.java`

**Interfaces:**
- Consumes: `MajorTransferCollegeAuthorizationService` from Task 2 and authenticated `StudentPrincipal`
- Produces: role-separated handler registration where source/target reviews require `COLLEGE_ADMIN`, central management requires `STUDENT_ADMIN`, and reads are scoped by role

- [ ] **Step 1: Write failing handler tests**

Add cases that execute real registered handlers:

```java
@Test void studentAdministratorCannotPerformCollegeReview() { /* expect COMMON_FORBIDDEN */ }
@Test void collegeAdministratorCannotSaveBatchOrRecordScore() { /* expect COMMON_FORBIDDEN */ }
@Test void sourceCollegeAdministratorCanInvokeSourceReview() { /* expect success */ }
@Test void targetCollegeAdministratorCanInvokeQualificationReview() { /* expect success */ }
@Test void unrelatedCollegeAdministratorCannotReadDetailOrAttachment() { /* expect COMMON_FORBIDDEN */ }
```

The test doubles must assert that rejected commands never reach `MajorTransferService`.

- [ ] **Step 2: Run the tests and verify RED**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferRoleAuthorizationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: existing handlers reject the new hierarchical roles or allow the wrong administrator path.

- [ ] **Step 3: Implement role-separated command sets**

Define immutable command groups:

```java
private static final Set<String> CENTRAL_ADMIN_COMMANDS = Set.of(
    "MAJOR_TRANSFER_SAVE_BATCH", "MAJOR_TRANSFER_SAVE_OPTION",
    "MAJOR_TRANSFER_RECORD_SCORE", "MAJOR_TRANSFER_GENERATE_PROPOSAL",
    "MAJOR_TRANSFER_FINALIZE", "MAJOR_TRANSFER_EXECUTE", "MAJOR_TRANSFER_CANCEL");

private static final Set<String> COLLEGE_REVIEW_COMMANDS = Set.of(
    "MAJOR_TRANSFER_REVIEW_SOURCE", "MAJOR_TRANSFER_REVIEW_QUALIFICATION");
```

Before calling the service, require `STUDENT_ADMIN` for central commands and `COLLEGE_ADMIN` plus the Task 2 scope check for reviews. Allow both administrator roles to list/read, but invoke `requireCanRead` for college administrators on detail and attachment access.

- [ ] **Step 4: Assemble the authorization service in ServerMain**

Construct one `MajorTransferCollegeAuthorizationService` from the existing `TransactionManager` and inject it into `MajorTransferHandlers`. Do not create a second connection pool or transaction manager.

- [ ] **Step 5: Run handler and assembly tests**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferRoleAuthorizationTest,ServerMainGovernanceAssemblyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all tests pass.

- [ ] **Step 6: Commit handler authorization**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferHandlers.java vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferRoleAuthorizationTest.java
git commit -m "feat(transfer): separate central and college commands"
```

---

### Task 4: Filter college-visible applications in the database query

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/repository/MajorTransferRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer/handler/MajorTransferHandlers.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/majortransfer/service/MajorTransferCollegeQueryTest.java`

**Interfaces:**
- Consumes: active department ID resolved by Task 2
- Produces: `listApplicationsForCollege(MajorTransferApplicationQuery query, String departmentId)` and repository query `listApplicationsByBatchAndCollege(Connection connection, String batchId, String departmentId)`

- [ ] **Step 1: Write a failing data-leakage test**

Seed three applications: source-only match, target-only match, and unrelated. Assert:

```java
List<MajorTransferApplicationView> visible = service.listApplicationsForCollege(query, COLLEGE_A);
assertThat(visible).extracting(MajorTransferApplicationView::applicationId)
        .containsExactlyInAnyOrder(SOURCE_MATCH, TARGET_MATCH)
        .doesNotContain(UNRELATED);
```

- [ ] **Step 2: Run the query test and verify RED**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferCollegeQueryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the college-scoped query API does not exist.

- [ ] **Step 3: Implement SQL-level filtering**

Use a joined predicate rather than loading all applications:

```sql
SELECT a.*
FROM tblMajorTransferApplication a
INNER JOIN tblMajorTransferOption o ON a.optionId=o.optionId
WHERE a.batchId=?
  AND (a.fromDepartmentId=? OR o.targetDepartmentId=?)
ORDER BY a.createdAt DESC
```

Apply optional status and option filters after retrieval only if the existing repository cannot express them safely; never return unscoped rows to the handler.

- [ ] **Step 4: Route list requests by role**

In `MajorTransferHandlers`, call the existing unscoped list only for `STUDENT_ADMIN`. For `COLLEGE_ADMIN`, resolve the active department and call `listApplicationsForCollege`.

- [ ] **Step 5: Run query, handler, and transfer service tests**

```bash
mvn -pl vcampus-server -am -Dtest=MajorTransferCollegeQueryTest,MajorTransferRoleAuthorizationTest,MajorTransferServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit scoped querying**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/student/majortransfer
git commit -m "feat(transfer): filter applications by managed college"
```

---

### Task 5: Present role-appropriate transfer workspaces in Swing

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentModulePageFactory.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferAdminPanel.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/MajorTransferRoleUiTest.java`

**Interfaces:**
- Consumes: authenticated role from the merged session model and existing `StudentClientService`
- Produces: central-management mode for `STUDENT_ADMIN` and college-approval mode for `COLLEGE_ADMIN`

- [ ] **Step 1: Write failing UI tests**

Instantiate the page/panel for each role and assert component visibility by stable names:

```java
assertThat(find(STUDENT_ADMIN_PAGE, "sourceReviewButton")).isNull();
assertThat(find(STUDENT_ADMIN_PAGE, "saveBatchButton")).isNotNull();
assertThat(find(COLLEGE_ADMIN_PAGE, "sourceReviewButton")).isNotNull();
assertThat(find(COLLEGE_ADMIN_PAGE, "saveBatchButton")).isNull();
assertThat(find(COLLEGE_ADMIN_PAGE, "recordScoreButton")).isNull();
```

- [ ] **Step 2: Run UI tests and verify RED**

```bash
mvn -pl vcampus-client -am -Dtest=MajorTransferRoleUiTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the page factory lacks one or both hierarchical-role views, or exposes the wrong controls.

- [ ] **Step 3: Add an explicit panel mode**

Introduce a small enum inside the panel or its package:

```java
enum TransferAdminMode { CENTRAL_MANAGEMENT, COLLEGE_APPROVAL }
```

Build only the controls permitted for the mode. In college mode, show source review only for `SUBMITTED` rows and target review only for `SOURCE_APPROVED` rows. Keep all server errors authoritative and map `COMMON_FORBIDDEN` to `无权处理该学院的转专业申请`.

- [ ] **Step 4: Map roles in the page factory**

`STUDENT_ADMIN` receives `CENTRAL_MANAGEMENT`; `COLLEGE_ADMIN` receives `COLLEGE_APPROVAL`; neither `SUPER_ADMIN` nor unrelated module administrators receive the transfer administration page.

- [ ] **Step 5: Run UI and client regression tests**

```bash
mvn -pl vcampus-client -am test
```

Expected: all client and dependent-module tests pass.

- [ ] **Step 6: Commit the role-specific UI**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/student vcampus-client/src/test/java/edu/seu/vcampus/client/student
git commit -m "feat(client): add college transfer approval workspace"
```

---

### Task 6: Complete demo data, regression verification, and release packaging

**Files:**
- Modify: `vcampus-database/seed/010_roles_permissions.sql`
- Modify: `vcampus-database/seed/020_test_accounts.sql`
- Modify: `vcampus-database/seed/021_more_students.sql`
- Modify or create: `vcampus-database/seed/026_college_administrators.sql`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/SeededStudentDatasetTest.java`
- Modify: `docs/转专业与培养方案功能说明及测试账号.md`
- Regenerate: `vcampus-distribution/lib/vCampusServer.jar`
- Regenerate: `vcampus-distribution/lib/vCampusClient.jar`
- Regenerate: `vCampus-release/lib/vCampusServer.jar`
- Regenerate: `vCampus-release/lib/vCampusClient.jar`
- Regenerate: `vCampus-release/data/vCampus.accdb`

**Interfaces:**
- Consumes: all previous tasks
- Produces: runnable release with at least one active college administrator per active department and documented test credentials

- [ ] **Step 1: Extend the failing seed verification**

Add assertions that every active department is covered and every seeded college administrator has exactly one active assignment:

```java
assertThat(count(connection, """
    SELECT COUNT(*) FROM tblDepartment d
    WHERE d.isActive=TRUE AND NOT EXISTS (
      SELECT 1 FROM tblStudentCollegeAdministrator a
      WHERE a.departmentId=d.departmentId AND a.isActive=TRUE)
    """)).isZero();
assertThat(count(connection, """
    SELECT COUNT(*) FROM (
      SELECT userId FROM tblStudentCollegeAdministrator
      WHERE isActive=TRUE GROUP BY userId HAVING COUNT(*) > 1)
    """)).isZero();
```

- [ ] **Step 2: Run seed verification and verify RED**

```bash
mvn -pl vcampus-server -am -Dtest=SeededStudentDatasetTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failure identifies active colleges without an administrator.

- [ ] **Step 3: Seed one administrator per active college**

Create stable accounts for computer science, mathematics, electronics, and foreign languages (matching the actual active seed departments), each with role `COLLEGE_ADMIN`, active status, one active binding, and the common local-test initial password hash. Preserve the existing central `STUDENT_ADMIN` account.

- [ ] **Step 4: Update the test-account documentation**

Document account, password, bound college, and exact approval stage for `STUDENT_ADMIN` and each `COLLEGE_ADMIN`. State that credentials are local-test-only.

- [ ] **Step 5: Run the full verification suite**

```bash
mvn clean test
git diff --check
```

Expected: all reactor tests pass with zero failures and the diff has no whitespace errors.

- [ ] **Step 6: Build release binaries**

```bash
mvn -DskipTests package
```

Expected: all reactor modules report `SUCCESS` and refreshed shaded JARs appear in `vcampus-distribution/lib`.

- [ ] **Step 7: Generate and validate a staged database**

```bash
java -cp vcampus-distribution/lib/vCampusServer.jar edu.seu.vcampus.server.bootstrap.DatabaseInitializer vcampus-database/schema vcampus-database/seed /private/tmp/vCampus-college-admin-release.accdb
```

Open the staged file through UCanAccess and verify nonzero users, students, departments, transfer applications, training plans, and zero uncovered active departments before replacing the release database.

- [ ] **Step 8: Refresh release artifacts safely**

Confirm the release database is not open, move the previous database to a dated `/private/tmp` backup, then copy the validated database and shaded JARs into `vCampus-release`. Copy the updated functional guide into `vCampus-release/docs`.

- [ ] **Step 9: Verify the actual release files**

Query `vCampus-release/data/vCampus.accdb` through `vCampus-release/lib/vCampusServer.jar` and repeat the counts and uncovered-college query. Confirm both JAR manifests are runnable and timestamps match the current build.

- [ ] **Step 10: Commit source, tests, docs, and tracked distribution artifacts**

```bash
git add vcampus-database vcampus-server vcampus-client docs vcampus-distribution
git commit -m "feat: add college-scoped major transfer approvals"
```
