# Virtual Campus User Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver teacher account application, internal student-account provisioning, authentication, restricted first-login sessions, password changes, account administration, RBAC, audit logs, Socket handlers, and eight UI-spec-compliant Swing pages.

**Architecture:** The user module owns account and security tables and publishes `UserQueryPort`, `AuthorizationPort`, and the server-internal `UserAccountProvisioningPort`. Handlers translate the eight public commands to `UserService`; teacher applications and account writes use published resource keys, while student provisioning joins the caller's existing Access transaction without nesting or committing it.

**Tech Stack:** JDK 21, Maven, Swing, UCanAccess, PBKDF2WithHmacSHA256, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-08-24-vcampus-user-module-design.md`, `docs/superpowers/specs/2026-08-24-vcampus-overall-architecture-design.md`, and `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`

## Global Constraints

- Complete the foundation plan first.
- Preserve the exact eight `USER_*` command names and DTO signatures from the spec.
- Never log passwords, salts, hashes, or full session tokens.
- Lock teacher application by `LOGIN_ID:<normalizedLoginId>` and mutable accounts by `USER:<userId>`.
- `USER_REGISTER` accepts `TeacherAccountApplicationCommand` with no role field and always creates `PENDING/TEACHER`; only `ACTIVE` accounts may log in.
- Student accounts are created only by `UserAccountProvisioningPort` as `ACTIVE/STUDENT` with `mustChangePassword=TRUE`; the Port is never registered as a Socket command.
- A restricted first-login session may call only `USER_GET_CURRENT`, `USER_CHANGE_PASSWORD`, and `USER_LOGOUT`; successful password change revokes it and returns to login.
- Complete the shared UI design-system plan before Task 6; user pages only compose its tokens, templates, components, and states.
- Keep Java files at or below 200 lines and document public APIs.

---

### Task 1: User Schema and Repository

**Files:**
- Create: `vcampus-database/schema/010_user.sql`
- Create: `vcampus-database/seed/010_roles_permissions.sql`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/domain/UserAccount.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/repository/UserRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/repository/AccessUserRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/repository/AuditRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/repository/AccessAuditRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/user/repository/AccessUserRepositoryTest.java`

**Interfaces:**
- Consumes: `TransactionManager`.
- Produces: `findById`, `findByNormalizedLoginId`, `insert`, `updateWithVersion`, and paged `search` repository methods.

- [ ] **Step 1: Write failing persistence tests for uniqueness and optimistic locking**

```java
@Test
void rejectsDuplicateNormalizedLoginIdAndStaleVersion() {
    repository.insert(account("Alice", 0));
    assertThatThrownBy(() -> repository.insert(account("alice", 0)))
            .isInstanceOf(DuplicateLoginIdException.class);
    UserAccount saved = repository.findByNormalizedLoginId("ALICE").orElseThrow();
    repository.updateWithVersion(saved.withStatus(ACTIVE), 0);
    assertThatThrownBy(() -> repository.updateWithVersion(saved.withRole(ADMIN), 0))
            .isInstanceOf(ConcurrentModificationException.class);
}
```

- [ ] **Step 2: Run the repository test**

Run: `mvn -pl vcampus-server -am -Dtest=AccessUserRepositoryTest test`

Expected: FAIL because schema and repository classes are missing.

- [ ] **Step 3: Implement all five user/security tables exactly as specified**

```java
public interface UserRepository {
    Optional<UserAccount> findById(Connection connection, String userId);
    Optional<UserAccount> findByNormalizedLoginId(Connection connection,
                                                  String normalizedLoginId);
    void insert(Connection connection, UserAccount account);
    void updateWithVersion(Connection connection, UserAccount account,
                           long expectedVersion);
    PageResult<UserAccount> search(Connection connection, UserSearchQuery query);
}
```

- [ ] **Step 4: Run repository integration tests**

Run: `mvn -pl vcampus-server -am -Dtest=AccessUserRepositoryTest test`

Expected: PASS against an isolated test `.accdb` copy.

- [ ] **Step 5: Commit the repository slice**

```bash
git add vcampus-database/schema/010_user.sql vcampus-database/seed/010_roles_permissions.sql vcampus-server/src/main/java/edu/seu/vcampus/server/user vcampus-server/src/test/java/edu/seu/vcampus/server/user
git commit -m "feat(user): add account persistence"
```

### Task 2: Password Policy and Concurrent Teacher Account Application

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/user/TeacherAccountApplicationCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/user/UserView.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/PasswordHasher.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserServiceImpl.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/user/service/TeacherAccountApplicationTest.java`

**Interfaces:**
- Consumes: repository, transaction manager, resource locks, audit repository.
- Produces: `UserView UserService.applyForTeacherAccount(TeacherAccountApplicationCommand)`.

- [ ] **Step 1: Write password-policy and 20-thread teacher-application tests**

```java
@Test
void onlyOneConcurrentTeacherApplicationWins() throws Exception {
    List<Outcome<UserView>> results = concurrently(20,
            () -> service.applyForTeacherAccount(
                    new TeacherAccountApplicationCommand(
                            "teacher01", "Password1".toCharArray())));
    assertThat(results.stream().filter(Outcome::isSuccess)).hasSize(1);
    assertThat(results.stream().filter(Outcome::isFailure)).hasSize(19);
}
```

- [ ] **Step 2: Run the teacher-application tests**

Run: `mvn -pl vcampus-server -am -Dtest=TeacherAccountApplicationTest test`

Expected: FAIL because teacher account application is not implemented.

- [ ] **Step 3: Implement PBKDF2 and locked teacher application**

```java
return locks.withLock("LOGIN_ID", normalized, () ->
        transactions.inTransaction(connection -> {
            repository.findByNormalizedLoginId(connection, normalized)
                    .ifPresent(value -> { throw new LoginIdExistsException(); });
            UserAccount account = factory.pendingTeacher(
                    command.loginId(), hasher.hash(command.password()));
            repository.insert(connection, account);
            audit.record(connection, account.userId(), "USER_REGISTER", "SUCCESS");
            return mapper.toView(account);
        }));
```

- [ ] **Step 4: Verify policy and concurrency**

Run: `mvn -pl vcampus-server -am -Dtest=TeacherAccountApplicationTest test`

Expected: PASS; the DTO exposes no role option, weak passwords return `AUTH_PASSWORD_POLICY_VIOLATION`, duplicates return `USER_LOGIN_ID_EXISTS`, and the persisted account is always `PENDING/TEACHER`.

- [ ] **Step 5: Commit teacher account application**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/user vcampus-server/src/main/java/edu/seu/vcampus/server/user vcampus-server/src/test/java/edu/seu/vcampus/server/user
git commit -m "feat(user): add teacher account application"
```

### Task 3: Transaction-Bound Student Account Provisioning Port

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserAccountProvisioningPort.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/ProvisionedUserAccount.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserAccountProvisioningService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/user/service/UserAccountProvisioningPortTest.java`

**Interfaces:**
- Consumes: an already-open `TransactionContext`, generated nine-digit campus-card number, and the fixed initial password supplied by the student admission coordinator.
- Produces: `ProvisionedUserAccount createStudentAccount(TransactionContext, String campusCardNumber, char[] initialPassword)` without opening, committing, or rolling back a transaction.

- [ ] **Step 1: Write shared-transaction and fixed-account tests**

```java
@Test
void createsActiveStudentInCallerTransactionAndRollsBackWithCaller() {
    assertThatThrownBy(() -> transactions.inTransaction(transaction -> {
        ProvisionedUserAccount account = port.createStudentAccount(
                transaction, "213242478", "12345678".toCharArray());
        assertThat(account).extracting(ProvisionedUserAccount::loginId,
                ProvisionedUserAccount::role, ProvisionedUserAccount::status)
                .containsExactly("213242478", STUDENT, ACTIVE);
        throw new InjectedAdmissionFailure();
    })).isInstanceOf(InjectedAdmissionFailure.class);
    assertThat(repository.findByNormalizedLoginId("213242478")).isEmpty();
    assertThat(audit.findByAction("STUDENT_ACCOUNT_PROVISIONED")).isEmpty();
}
```

- [ ] **Step 2: Run provisioning tests and confirm failure**

Run: `mvn -pl vcampus-server -am -Dtest=UserAccountProvisioningPortTest test`

Expected: FAIL because the internal Port is absent.

- [ ] **Step 3: Implement provisioning without a nested transaction**

```java
public ProvisionedUserAccount createStudentAccount(TransactionContext transaction,
        String campusCardNumber, char[] initialPassword) {
    requireCampusCardFormat(campusCardNumber);
    UserAccount account = factory.activeStudent(campusCardNumber,
            hasher.hash(initialPassword), true);
    repository.insert(transaction.connection(), account);
    audit.record(transaction.connection(), account.userId(),
            "STUDENT_ACCOUNT_PROVISIONED", "SUCCESS");
    return mapper.toProvisioned(account);
}
```

Clear the supplied password array in `finally`. Do not expose a handler, call `TransactionManager`, commit, roll back, or generate an independent `requestId`.

- [ ] **Step 4: Verify transaction ownership and visibility**

Run: `mvn -pl vcampus-server -am -Dtest=UserAccountProvisioningPortTest test`

Expected: PASS for commit, injected rollback, campus-card format validation, duplicate `LOGIN_ID`, fixed `ACTIVE/STUDENT/mustChangePassword=TRUE`, audit atomicity, and absence from `MessageRouter` registrations.

- [ ] **Step 5: Commit the internal Port**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/user/service vcampus-server/src/test/java/edu/seu/vcampus/server/user/service
git commit -m "feat(user): add student account provisioning port"
```

### Task 4: Login, Lockout, Restricted Sessions, and Password Change

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/user/LoginCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/user/LoginResult.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/session/SessionRegistry.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserQueryPort.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/security/AuthorizationPort.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/security/AuthorizationService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/user/service/LoginLockoutTest.java`

**Interfaces:**
- Consumes: user repository and password hasher.
- Produces: `login`, `logout`, `changePassword`, `UserQueryPort`, `AuthorizationPort`.

- [ ] **Step 1: Write lockout, restricted-session, and revocation tests**

```java
@Test
void fifthFailureLocksAccountAndDisableRevokesSession() {
    repeat(5, () -> assertThatThrownBy(() -> login("alice", "wrong"))
            .isInstanceOf(InvalidCredentialsException.class));
    assertThatThrownBy(() -> login("alice", "Password1"))
            .isInstanceOf(AccountLockedException.class);
    clock.advance(Duration.ofMinutes(15));
    String token = login("alice", "Password1").sessionToken();
    service.changeStatus(adminCommand(aliceId, DISABLED));
    assertThatThrownBy(() -> authorization.requireSession(token))
            .isInstanceOf(SessionExpiredException.class);
}

@Test
void initialPasswordSessionAllowsOnlyCurrentPasswordChangeAndLogout() {
    LoginResult result = login("213242478", "12345678");
    assertThat(result.mustChangePassword()).isTrue();
    assertThatThrownBy(() -> authorization.requirePermission(
            result.sessionToken(), "COURSE_READ"))
            .isInstanceOf(InitialPasswordChangeRequiredException.class);
    service.changePassword(result.sessionToken(), change("12345678", "NewPass123"));
    assertThatThrownBy(() -> authorization.requireSession(result.sessionToken()))
            .isInstanceOf(SessionExpiredException.class);
}
```

- [ ] **Step 2: Run login tests**

Run: `mvn -pl vcampus-server -am -Dtest=LoginLockoutTest test`

Expected: FAIL because login/session behavior is absent.

- [ ] **Step 3: Implement account lock and in-memory session registry**

```java
public UserIdentity requireSession(String token) {
    Session session = sessions.get(token);
    if (session == null || session.isExpired(clock.instant())) {
        throw new SessionExpiredException();
    }
    session.touch(clock.instant());
    return session.identity();
}
```

Use `SecureRandom` to generate 32-byte tokens and encode with URL-safe Base64. Never persist or log the complete token.

- [ ] **Step 4: Run user service tests**

Run: `mvn -pl vcampus-server -am -Dtest=LoginLockoutTest,PasswordChangeTest,AuthorizationServiceTest test`

Expected: PASS for lockout, expiry, logout, disable, normal RBAC, restricted-session command allowlist, first-password change, and forced re-login.

- [ ] **Step 5: Commit authentication**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/user vcampus-server/src/main/java/edu/seu/vcampus/server/{user,session,security} vcampus-server/src/test
git commit -m "feat(user): add authentication and sessions"
```

### Task 5: Admin Operations, Handlers, and Audit

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/handler/UserHandlers.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/repository/AuditRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/user/handler/UserHandlersTest.java`

**Interfaces:**
- Consumes: `MessageRouter`, `UserService`, `AuthorizationPort`.
- Produces: all eight registered `USER_*` handlers and stable error mapping.

- [ ] **Step 1: Write command/permission contract tests**

```java
@ParameterizedTest
@CsvSource({"USER_SEARCH,USER_READ_ALL", "USER_UPDATE_ROLE,USER_ROLE_WRITE",
            "USER_CHANGE_STATUS,USER_STATUS_WRITE"})
void adminCommandsRequirePermission(String command, String permission) {
    assertThatThrownBy(() -> handlers.handle(command, studentContext()))
            .isInstanceOf(ForbiddenException.class);
    verify(authorization).requirePermission(anyString(), eq(permission));
}
```

- [ ] **Step 2: Run handler tests**

Run: `mvn -pl vcampus-server -am -Dtest=UserHandlersTest test`

Expected: FAIL until handlers and audit persistence exist.

- [ ] **Step 3: Implement handlers and protect the last administrator**

```java
router.register("USER_UPDATE_ROLE", handler(USER_ROLE_WRITE,
        UpdateUserRoleCommand.class, service::updateRole));
router.register("USER_CHANGE_STATUS", handler(USER_STATUS_WRITE,
        ChangeUserStatusCommand.class, service::changeStatus));
```

Audit every result without sensitive fields. Prevent disabling or demoting the only active administrator in the same transaction.

- [ ] **Step 4: Run handler and audit tests**

Run: `mvn -pl vcampus-server -am -Dtest=UserHandlersTest,AuditRepositoryTest test`

Expected: PASS for all command names, permissions, error codes, and audit redaction.

- [ ] **Step 5: Commit handlers**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/user vcampus-server/src/test/java/edu/seu/vcampus/server/user
git commit -m "feat(user): expose account message handlers"
```

### Task 6: Eight UI-Spec-Compliant Swing Pages and End-to-End Acceptance

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/service/UserClientService.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/LoginFrame.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/TeacherAccountApplicationDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/InitialPasswordChangeDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/AccountPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/ChangePasswordDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserRoleDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/SecurityAuditPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/user/UserUiTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/user/UserEndToEndTest.java`
- Modify: `docs/ui-review/manifest.md`

**Interfaces:**
- Consumes: async `ClientConnection` and eight commands.
- Produces: eight user screens, restricted-session handoff to `InitialPasswordChangeDialog`, and normal-session handoff to `MainFrame`.

- [ ] **Step 1: Write UI state and Socket E2E tests**

```java
@Test
void failedLoginKeepsLoginIdClearsPasswordAndShowsMessage() {
    LoginFrameRobot robot = launchLoginFrame(failingClient("AUTH_INVALID_CREDENTIALS"));
    robot.enter("alice", "wrong").submit().await();
    assertThat(robot.loginId()).isEqualTo("alice");
    assertThat(robot.password()).isEmpty();
    assertThat(robot.error()).contains("用户名或密码错误");
}

@Test
void restrictedLoginNeverShowsMainFrameAndReturnsToLoginAfterPasswordChange() {
    LoginFrameRobot login = launchLoginFrame(restrictedLoginClient());
    login.enter("213242478", "12345678").submit().await();
    assertThat(login.initialPasswordDialogShowing()).isTrue();
    assertThat(login.mainFrameShowing()).isFalse();
    login.changeInitialPassword("NewPass123").await();
    assertThat(login.loginFrameShowing()).isTrue();
}

@Test
void userPagesUseSharedTemplatesStatesAndAccessibleFocus() {
    UiAuditResult audit = UiComplianceAudit.inspect(userPages());
    assertThat(audit.pagesWithoutTemplate()).isEmpty();
    assertThat(audit.pagesMissingRequiredStates()).isEmpty();
    assertThat(audit.privateThemeClasses()).isEmpty();
    assertThat(audit.inaccessibleControls()).isEmpty();
}
```

- [ ] **Step 2: Run UI/E2E tests**

Run: `mvn -pl vcampus-client,vcampus-server -am -Dtest=UserUiTest,UserEndToEndTest test`

Expected: FAIL because pages and client service are absent.

- [ ] **Step 3: Implement asynchronous pages with shared UI contracts**

```java
userClient.login(loginId, password)
        .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            submitButton.setEnabled(true);
            passwordField.setText("");
            if (error == null) sessionConsumer.accept(result);
            else errorLabel.setText(errorMapper.toChinese(error));
        }));
```

Use the authentication layout for `LoginFrame`, `TeacherAccountApplicationDialog`, and `InitialPasswordChangeDialog`; map `UserManagementPanel` and `SecurityAuditPanel` to the query-list template and `AccountPanel` to the detail template. Use shared dialog structure for password and role changes, all required page states, latest-request lifecycle guards, visible focus, actionable Chinese errors, and no private visual constants.

- [ ] **Step 4: Run full user verification**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am verify`

Expected: PASS, including concurrent teacher application, transaction-bound student provisioning, restricted first login, five-failure lockout, disable-session revocation, RBAC, UI design-system compliance, screenshot manifest entries, and no sensitive log content.

- [ ] **Step 5: Commit the completed module**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/user vcampus-client/src/test vcampus-server/src/test docs/ui-review/manifest.md
git commit -m "feat(user): complete account user experience"
```
