# Course User Management Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge user management into the completed course module so a real login session opens the existing role-appropriate course UI and authorizes every course request.

**Architecture:** Preserve both Git histories with a non-fast-forward merge, then compose user authentication and course services around one router, database connection provider, and lock manager. The client shares one `ClientConnection`; login installs its session token and hands role-filtered existing course panels to `MainFrame`. Until the student module is merged, a focused adapter maps active `STUDENT` accounts to `studentId=userId`.

**Tech Stack:** Java 21, Maven, Swing, Java sockets/object serialization, UCanAccess 5.1.3, JUnit 5, AssertJ, Mockito

**Spec:** `docs/superpowers/specs/2026-08-30-course-user-integration-design.md`

## Global Constraints

- Work only on branch `course-user-management` in `/private/tmp/java-summer-course-course-user-integration`.
- Preserve the histories of `course` and `origin/feat/user-management` in a two-parent merge commit.
- Use exactly one production `MessageRouter`, `ConnectionProvider`, and application `ResourceLockManager`.
- Do not enable UCanAccess `immediatelyReleaseResources=true`.
- Reject restricted first-password sessions at the course authorization boundary.
- The temporary student mapping accepts only active `STUDENT` accounts and maps `studentId` to the same `userId`.
- Server authorization is authoritative; client page filtering is only a usability layer.
- Run Maven with `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` as `JAVA_HOME`.
- Never log or retain passwords, hashes, salts, or full session tokens.

---

### Task 1: Preserve Both Module Histories

**Files:**
- Merge: `origin/feat/user-management`
- Resolve: `.gitignore`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/ClientMain.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
- Resolve: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
- Resolve: `vcampus-server/pom.xml`
- Preserve: all `common/course`, `client/course`, `server/course`, `common/user`, `client/user`, `server/user`, `server/security`, and `server/session` sources and tests

**Interfaces:**
- Consumes: branch heads `course` and `origin/feat/user-management`.
- Produces: a compiling two-parent merge commit containing both modules.

- [ ] **Step 1: Start the merge without committing**

```bash
git merge --no-ff --no-commit origin/feat/user-management
git status --short
```

Expected: conflicts only in files independently changed by both branches; course-only source files are not deletions.

- [ ] **Step 2: Resolve shared files and remove unrelated office artifacts**

Retain connection/configuration plus user imports in `ClientMain`; retain user-aware and empty constructors in `MainFrame`; retain shutdown behavior plus compile-safe user runtime creation in `ServerMain`; retain both distribution-doc and schema-copy executions in `vcampus-server/pom.xml`. Remove the user branch's progress-report DOCX and editor lock file from the merge, and keep the course branch's existing project documents.

```bash
git diff --name-only --diff-filter=U
git diff --check
```

Expected: both commands print no unresolved conflict or whitespace errors after resolution.

- [ ] **Step 3: Verify both source trees survived**

```bash
test -f vcampus-server/src/main/java/edu/seu/vcampus/server/course/composition/CourseComposition.java
test -f vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserServiceImpl.java
test -f vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiComposition.java
test -f vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/LoginFrame.java
```

- [ ] **Step 4: Compile and commit the merge**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin:$PATH mvn -DskipTests compile
git add -A
git commit -m "merge: integrate user management history"
git show --no-patch --format='%P' HEAD
```

Expected: `BUILD SUCCESS`; the last command prints two parent hashes.

---

### Task 2: Add the Temporary User-Backed Student Gateway

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/composition/TemporaryUserStudentGateway.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/composition/TemporaryUserStudentGatewayTest.java`

**Interfaces:**
- Consumes: `UserQueryPort.findActiveUser(String)`, `UserIdentity.role()`, `CourseStudentGateway`.
- Produces: `TemporaryUserStudentGateway.create(UserQueryPort): CourseStudentGateway`.

- [ ] **Step 1: Write failing adapter tests**

```java
@Test void mapsAnActiveStudentUserIdToTheTemporaryStudentId() {
    UserQueryPort users = users(Map.of("user-1", identity("user-1", UserRole.STUDENT)));
    CourseStudentGateway gateway = TemporaryUserStudentGateway.create(users);
    assertThat(gateway.getEnrollmentEligibility("user-1"))
            .isEqualTo(new StudentEnrollmentEligibility("user-1", "ACTIVE"));
    assertThat(gateway.existsActiveStudent("user-1")).isTrue();
}

@ParameterizedTest
@EnumSource(value = UserRole.class, names = {"TEACHER", "ADMIN"})
void rejectsNonStudentRoles(UserRole role) {
    CourseStudentGateway gateway = TemporaryUserStudentGateway.create(
            users(Map.of("user-1", identity("user-1", role))));
    assertThatThrownBy(() -> gateway.getEnrollmentEligibility("user-1"))
            .isInstanceOf(StudentIneligibleException.class);
    assertThat(gateway.existsActiveStudent("user-1")).isFalse();
}
```

Also test missing/inactive accounts return `false` from `existsActiveStudent` and throw `StudentIneligibleException` from eligibility lookup.

- [ ] **Step 2: Verify RED**

```bash
mvn -pl vcampus-server -Dtest=TemporaryUserStudentGatewayTest test
```

Expected: test compilation fails because the adapter is missing.

- [ ] **Step 3: Implement the minimal adapter**

```java
public static CourseStudentGateway create(UserQueryPort users) {
    Objects.requireNonNull(users, "users");
    return CourseStudentGateway.of(userId -> users.findActiveUser(userId)
                    .filter(identity -> identity.role() == UserRole.STUDENT)
                    .map(identity -> new StudentEnrollmentEligibility(identity.userId(), "ACTIVE"))
                    .orElseThrow(StudentIneligibleException::new),
            studentId -> users.findActiveUser(studentId)
                    .map(identity -> identity.role() == UserRole.STUDENT)
                    .orElse(false));
}
```

- [ ] **Step 4: Verify GREEN and commit**

```bash
mvn -pl vcampus-server -Dtest=TemporaryUserStudentGatewayTest,CourseRuntimeAdaptersTest test
git add vcampus-server/src/main/java/edu/seu/vcampus/server/course/composition/TemporaryUserStudentGateway.java vcampus-server/src/test/java/edu/seu/vcampus/server/course/composition/TemporaryUserStudentGatewayTest.java
git commit -m "feat(course): adapt active student users temporarily"
```

---

### Task 3: Compose User and Course Services in Production

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ApplicationRuntime.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ApplicationSchemaInitializer.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/ApplicationRuntimeTest.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/ApplicationSchemaInitializerTest.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
- Modify: `vcampus-server/pom.xml`

**Interfaces:**
- Consumes: `UserServiceImpl`, `AuthorizationService`, `CourseRuntimeAdapters.authorization`, `TemporaryUserStudentGateway.create`, `CourseComposition.create`.
- Produces: `ApplicationRuntime.create(ConnectionProvider, Path, Clock)`, `router()`, and `course()`.

- [ ] **Step 1: Write a failing application composition test**

Create a temporary Access database, apply `001_common.sql`, `010_user.sql`, `010_roles_permissions.sql`, build `ApplicationRuntime`, insert active accounts through existing repository fixtures, and assert a student login token can call `COURSE_TERM_LIST` but cannot call `COURSE_TERM_CREATE`. Assert administrator creation succeeds, a restricted token is rejected, and `runtime.course().resourceLocks()` is the application lock manager.

```java
ResponseBody<?> login = runtime.router().route(message("USER_LOGIN", null,
        new LoginCommand("STUDENT1", "Password7".toCharArray(), "client-1")), context);
LoginResult result = (LoginResult) login.data();
assertThat(route(runtime, "COURSE_TERM_LIST", result.sessionToken(), EmptyRequest.INSTANCE).success()).isTrue();
assertThat(route(runtime, "COURSE_TERM_CREATE", result.sessionToken(), term()).code())
        .isEqualTo("COMMON_FORBIDDEN");
```

- [ ] **Step 2: Verify RED**

```bash
mvn -pl vcampus-server -Dtest=ApplicationRuntimeTest test
```

Expected: compilation fails because `ApplicationRuntime` is absent.

- [ ] **Step 3: Implement the shared composition**

```java
CourseAuthorizationGateway courseAuthorization = CourseRuntimeAdapters.authorization(
        authorization::requireSession,
        UserIdentity::userId,
        identity -> identity.role().name(),
        identity -> !identity.restricted(),
        (userId, role) -> users.findActiveUser(userId)
                .map(identity -> identity.role().name().equals(role)).orElse(false));
CourseStudentGateway students = TemporaryUserStudentGateway.create(users);
CourseComposition courses = CourseComposition.create(
        connections, courseAuthorization, students, clock, locks);
new UserHandlers(router, users, authorization);
courses.register(router);
```

Create exactly one `SessionRegistry`, `MessageRouter`, `StripedResourceLockManager`, and `ConnectionProvider`. The user service may create its own `TransactionManager` around the shared provider.

- [ ] **Step 4: Install and package both schemas idempotently**

`ApplicationSchemaInitializer` applies `001_common.sql`, `010_user.sql`, `010_roles_permissions.sql`, and `030_course.sql` in order while delegating course-owned DDL behavior to `CourseSchemaInitializer`. Seed inserts check their keys before execution so a second startup succeeds. The Maven distribution execution copies all four SQL files. Its test initializes twice and asserts user/course tables and role seeds exist once.

- [ ] **Step 5: Wire `ServerMain` with a safe URL**

```java
String databaseUrl = "jdbc:ucanaccess://" + config.databasePath();
ConnectionProvider connections = () -> DriverManager.getConnection(databaseUrl);
ApplicationRuntime runtime = ApplicationRuntime.create(
        connections, distributionDatabaseDirectory, Clock.systemUTC());
SocketServer server = new SocketServer(config.port(), config.workerThreads(),
        config.maxConnections(), runtime.router());
```

Do not append `immediatelyReleaseResources=true`.

- [ ] **Step 6: Verify and commit**

```bash
mvn -pl vcampus-server -am test
git add vcampus-server vcampus-distribution/database vcampus-database
git commit -m "feat(server): compose login and course runtimes"
```

---

### Task 4: Enforce the Restricted First-Password Client Flow

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/service/UserClientService.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/InitialPasswordChangeDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/LoginFrame.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/user/LoginDemoUiTest.java`

**Interfaces:**
- Consumes: `USER_CHANGE_PASSWORD`, `USER_LOGOUT`, `ChangePasswordCommand`, `LoginResult.mustChangePassword()`.
- Produces: `UserClientService.changePassword(char[], char[])`, `logout()`, and `InitialPasswordChangeDialog(UserClientService, Runnable)`.

- [ ] **Step 1: Write failing service and UI tests**

Assert password change sends `USER_CHANGE_PASSWORD`, clears both caller arrays, and clears the connection token after success. Assert a restricted `LoginResult` invokes the restricted callback without creating `MainFrame`.

```java
verify(connection).send(eq("USER_CHANGE_PASSWORD"), any(ChangePasswordCommand.class), eq(TIMEOUT));
verify(connection).setSessionToken(null);
assertThat(oldPassword).containsOnly('\0');
assertThat(newPassword).containsOnly('\0');
assertThat(mainCreated).isFalse();
assertThat(restrictedFlowOpened).isTrue();
```

- [ ] **Step 2: Verify RED**

```bash
mvn -pl vcampus-client -am -Dtest=LoginDemoUiTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement user commands and the dialog**

`changePassword` and `logout` must be asynchronous, require successful `EmptyResponse`, clear sensitive arrays in `finally`, and call `connection.setSessionToken(null)` only after server confirmation. The dialog has old/new/confirmation fields plus submit/logout controls, disables submission while pending, performs no network wait on the EDT, exposes only safe Chinese errors, and calls its completion callback after success.

- [ ] **Step 4: Split login handoffs**

```java
public LoginFrame(UserClientService users,
                  Consumer<LoginResult> onAuthenticated,
                  Consumer<LoginResult> onPasswordChangeRequired)
```

In `finish`, dispatch using `result.mustChangePassword()`. Keep the two-argument constructor as a compatible delegate.

- [ ] **Step 5: Verify and commit**

```bash
mvn -pl vcampus-client -am -Dtest=LoginDemoUiTest -Dsurefire.failIfNoSpecifiedTests=false test
git add vcampus-client/src/main/java/edu/seu/vcampus/client/user vcampus-client/src/test/java/edu/seu/vcampus/client/user/LoginDemoUiTest.java
git commit -m "feat(client): enforce initial password change before courses"
```

---

### Task 5: Open Existing Course Pages by Logged-In Role

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiComposition.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/ClientMain.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/AuthenticatedCourseShellTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/user/LoginDemoUiTest.java`

**Interfaces:**
- Consumes: `UserView.role()`, `CourseClientService(ClientConnection)`, `CourseUiComposition`, `PageNavigator`.
- Produces: `CourseUiComposition.pagesFor(UserRole): Map<String, JPanel>` and `MainFrame(UserView, CourseClientService, ClientConnection)`.

- [ ] **Step 1: Write failing page-selection tests**

```java
assertThat(composition.pagesFor(UserRole.STUDENT).keySet()).containsExactly(
        "course.offerings", "course.enrollments", "course.schedule", "course.adjustment", "course.retake");
assertThat(composition.pagesFor(UserRole.TEACHER).keySet()).containsExactly(
        "course.offerings", "course.schedule");
assertThat(composition.pagesFor(UserRole.ADMIN).keySet()).containsExactly(
        "course.terms", "course.catalog", "course.offering-admin",
        "course.outcome-import", "course.adjustment-audit");
```

Also assert navigation labels match the role and the header contains `loginId` and role but not the session token.

- [ ] **Step 2: Verify RED**

```bash
mvn -pl vcampus-client -am -Dtest=AuthenticatedCourseShellTest,CourseUiTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement exhaustive role selection**

```java
public Map<String, JPanel> pagesFor(UserRole role) {
    return switch (Objects.requireNonNull(role)) {
        case STUDENT -> studentPages();
        case TEACHER -> teacherPages();
        case ADMIN -> administrativePages();
    };
}
```

Teacher pages contain only offering search and the teacher schedule, matching server authorization.

- [ ] **Step 4: Install real panels in `MainFrame`**

Register each selected panel with `PageNavigator`, create one navigation button per panel, and show the first page. Reuse theme tokens, connection status, minimum dimensions, and existing Chinese labels. Do not install preview gateways or construction placeholders.

- [ ] **Step 5: Connect login to the shell**

```java
CourseClientService courses = new CourseClientService(connection);
MainFrame main = new MainFrame(result.user(), courses, connection);
main.setVisible(true);
```

Restricted login opens `InitialPasswordChangeDialog`; successful password change opens a fresh `LoginFrame`. Closing the final window closes the shared connection.

- [ ] **Step 6: Verify and commit**

```bash
mvn -pl vcampus-client -am test
git add vcampus-client
git commit -m "feat(client): open role-based course UI after login"
```

---

### Task 6: Prove and Package the End-to-End Workflow

**Files:**
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/integration/LoginCourseSocketIntegrationTest.java`
- Modify: `docs/course-runtime-integration.md`
- Modify: `vcampus-server/pom.xml`
- Modify: `vcampus-client/pom.xml`

**Interfaces:**
- Consumes: production `ApplicationRuntime`, `SocketServer`, `ClientConnection`, `UserClientService`, `CourseClientService`.
- Produces: real-socket proof and packaged documentation of the temporary mapping.

- [ ] **Step 1: Write the failing real-socket test**

Start `ApplicationRuntime` on a temporary Access database and a real `SocketServer`; use production clients to assert student login plus course read success, student admin-command rejection, administrator command success, restricted-session rejection, logout rejection, and teacher offering/schedule success.

```java
LoginResult student = users.login("STUDENT1", "Password7".toCharArray()).join();
assertThat(courses.listTerms().join()).isNotNull();
assertThatThrownBy(() -> courses.createTerm(term()).join())
        .hasRootCauseInstanceOf(CourseClientException.class);
```

- [ ] **Step 2: Verify RED, then correct only exposed integration boundaries**

```bash
mvn -pl vcampus-client -am -Dtest=LoginCourseSocketIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the first run fails at an incomplete production boundary, not fixture setup. Keep corrections within adapters, error mapping, session clearing, or bootstrap wiring; add no roles, commands, or repositories.

- [ ] **Step 3: Update runtime documentation**

Document the shared router/connections/locks, restricted-session behavior, exact page sets, temporary `active STUDENT userId == studentId` rule, replacement seam `TemporaryUserStudentGateway.create(users)` → `CourseRuntimeAdapters.students(StudentQueryPort...)`, and the real-data migration warning.

- [ ] **Step 4: Run full JDK 21 verification**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin:$PATH mvn clean verify
```

Expected: all common, server, and client tests pass with zero failures and errors.

- [ ] **Step 5: Verify artifacts, history, docs, and cleanliness**

```bash
jar tf vcampus-distribution/lib/vCampusServer.jar | rg 'server/(user|course|security|session)/'
jar tf vcampus-distribution/lib/vCampusClient.jar | rg 'client/(user|course)/'
test -f vcampus-distribution/database/schema/010_user.sql
test -f vcampus-distribution/database/schema/030_course.sql
rg -n 'userId.*studentId|TemporaryUserStudentGateway|StudentQueryPort' docs vcampus-distribution/docs
git log --oneline --graph --decorate -15
git status --short
```

Expected: both modules appear in jars, both schemas are packaged, the temporary mapping is documented, the two-parent merge is visible, and the worktree is clean.

- [ ] **Step 6: Commit final integration proof**

```bash
git add vcampus-client/src/test docs vcampus-distribution vcampus-server/pom.xml vcampus-client/pom.xml
git commit -m "test(integration): prove authenticated course workflow"
```
