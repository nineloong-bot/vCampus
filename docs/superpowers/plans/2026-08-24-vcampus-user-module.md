# Virtual Campus User Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver registration, authentication, sessions, password changes, account administration, RBAC, audit logs, Socket handlers, and Swing user pages.

**Architecture:** The user module owns account and security tables and publishes `UserQueryPort` and `AuthorizationPort`. Handlers translate typed messages to `UserService`; password and account writes run under user/name locks and Access transactions.

**Tech Stack:** JDK 21, Maven, Swing, UCanAccess, PBKDF2WithHmacSHA256, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-08-24-vcampus-user-module-design.md` and `docs/superpowers/specs/2026-08-24-vcampus-overall-architecture-design.md`

## Global Constraints

- Complete the foundation plan first.
- Preserve the exact eight `USER_*` command names and DTO signatures from the spec.
- Never log passwords, salts, hashes, or full session tokens.
- Lock registration by normalized username and mutable accounts by `USER:<userId>`.
- Public registration creates `PENDING`; only `ACTIVE` accounts may log in.
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
- Produces: `findById`, `findByNormalizedUsername`, `insert`, `updateWithVersion`, and paged `search` repository methods.

- [ ] **Step 1: Write failing persistence tests for uniqueness and optimistic locking**

```java
@Test
void rejectsDuplicateNormalizedUsernameAndStaleVersion() {
    repository.insert(account("Alice", 0));
    assertThatThrownBy(() -> repository.insert(account("alice", 0)))
            .isInstanceOf(DuplicateUsernameException.class);
    UserAccount saved = repository.findByNormalizedUsername("alice").orElseThrow();
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
    Optional<UserAccount> findByNormalizedUsername(Connection connection,
                                                   String username);
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

### Task 2: Password Policy and Concurrent Registration

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/user/RegisterUserCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/user/UserView.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/PasswordHasher.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserServiceImpl.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/user/service/UserRegistrationTest.java`

**Interfaces:**
- Consumes: repository, transaction manager, resource locks, audit repository.
- Produces: `UserView UserService.register(RegisterUserCommand)`.

- [ ] **Step 1: Write password-policy and 20-thread registration tests**

```java
@Test
void onlyOneConcurrentRegistrationWins() throws Exception {
    List<Outcome<UserView>> results = concurrently(20,
            () -> service.register(new RegisterUserCommand(
                    "student01", "Password1".toCharArray(), STUDENT)));
    assertThat(results.stream().filter(Outcome::isSuccess)).hasSize(1);
    assertThat(results.stream().filter(Outcome::isFailure)).hasSize(19);
}
```

- [ ] **Step 2: Run the registration tests**

Run: `mvn -pl vcampus-server -am -Dtest=UserRegistrationTest test`

Expected: FAIL because registration is not implemented.

- [ ] **Step 3: Implement PBKDF2 and locked registration**

```java
return locks.withLock("USER_NAME", normalized, () ->
        transactions.inTransaction(connection -> {
            repository.findByNormalizedUsername(connection, normalized)
                    .ifPresent(value -> { throw new UsernameExistsException(); });
            UserAccount account = factory.pending(command, hasher.hash(command.password()));
            repository.insert(connection, account);
            audit.record(connection, account.userId(), "USER_REGISTER", "SUCCESS");
            return mapper.toView(account);
        }));
```

- [ ] **Step 4: Verify policy and concurrency**

Run: `mvn -pl vcampus-server -am -Dtest=UserRegistrationTest test`

Expected: PASS; weak passwords return `AUTH_PASSWORD_POLICY_VIOLATION`, duplicates return `USER_USERNAME_EXISTS`.

- [ ] **Step 5: Commit registration**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/user vcampus-server/src/main/java/edu/seu/vcampus/server/user vcampus-server/src/test/java/edu/seu/vcampus/server/user
git commit -m "feat(user): add secure registration"
```

### Task 3: Login, Lockout, Sessions, and Password Change

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

- [ ] **Step 1: Write lockout and session revocation tests**

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

Expected: PASS for lockout, expiry, logout, disable, password change, and RBAC.

- [ ] **Step 5: Commit authentication**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/user vcampus-server/src/main/java/edu/seu/vcampus/server/{user,session,security} vcampus-server/src/test
git commit -m "feat(user): add authentication and sessions"
```

### Task 4: Admin Operations, Handlers, and Audit

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

### Task 5: Swing Pages and End-to-End Acceptance

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/service/UserClientService.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/LoginFrame.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/RegisterDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/AccountPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/ChangePasswordDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserRoleDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/SecurityAuditPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/user/UserUiTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/user/UserEndToEndTest.java`

**Interfaces:**
- Consumes: async `ClientConnection` and eight commands.
- Produces: seven user screens and logged-in session handoff to `MainFrame`.

- [ ] **Step 1: Write UI state and Socket E2E tests**

```java
@Test
void failedLoginKeepsUsernameClearsPasswordAndShowsMessage() {
    LoginFrameRobot robot = launchLoginFrame(failingClient("AUTH_INVALID_CREDENTIALS"));
    robot.enter("alice", "wrong").submit().await();
    assertThat(robot.username()).isEqualTo("alice");
    assertThat(robot.password()).isEmpty();
    assertThat(robot.error()).contains("用户名或密码错误");
}
```

- [ ] **Step 2: Run UI/E2E tests**

Run: `mvn -pl vcampus-client,vcampus-server -am -Dtest=UserUiTest,UserEndToEndTest test`

Expected: FAIL because pages and client service are absent.

- [ ] **Step 3: Implement pages with asynchronous calls**

```java
userClient.login(username, password)
        .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            submitButton.setEnabled(true);
            passwordField.setText("");
            if (error == null) sessionConsumer.accept(result);
            else errorLabel.setText(errorMapper.toChinese(error));
        }));
```

- [ ] **Step 4: Run full user verification**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am verify`

Expected: PASS, including concurrent registration, five-failure lockout, disable-session revocation, RBAC, UI states, and no sensitive log content.

- [ ] **Step 5: Commit the completed module**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/user vcampus-client/src/test vcampus-server/src/test
git commit -m "feat(user): complete account user experience"
```
