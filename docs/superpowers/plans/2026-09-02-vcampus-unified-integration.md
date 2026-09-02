# vCampus Unified Multi-Module Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver one vCampus client, one server on port 8888, and one reproducibly generated Access database containing testable User, Student, Course, Library, and Shop data.

**Architecture:** Keep `feat/user-management` as the authentication, session, and authorization foundation. Merge each business branch with history, then integrate its client pages and server handlers through the shared `MainFrame`, `ServerMain`, and database initializer. Generate the distribution database from ordered schema and seed scripts instead of merging binary databases or module-built JARs.

**Tech Stack:** Java 21, Swing, Maven multi-module build, socket request router, UCanAccess/Microsoft Access, JUnit 5, PowerShell and batch distribution scripts.

**Spec:** `docs/superpowers/specs/合并的设计.md`

## Global Constraints

- Work only in `E:\summer-school\vCampus\.worktrees\shop-auth-demo` on `integration/user-all-modules`.
- Preserve untracked `logs/`, `.superpowers/brainstorm/`, integration findings, `vcampus-database/demo/`, and `vcampus-distribution/data/vCampus.pre-integration-20260902.accdb`.
- Merge sources in this order: Course, Student, Library, Shop.
- Authentication, sessions, and the user service contract come from `origin/feat/user-management`.
- Business-domain behavior comes from each module's latest branch.
- Ignore branch versions of `vcampus-distribution/lib/vCampusClient.jar`, `vcampus-distribution/lib/vCampusServer.jar`, and binary `vcampus-distribution/data/vCampus.accdb` during merges.
- Do not rename public network messages or DTOs merely to align UI wording.
- The integrated server uses one router, one session registry, one database, and port `8888`.
- Use test-driven development for new integration behavior: observe the expected test failure before production changes.
- Do not push, rebase, delete, clean, or reset protected user files without separate authorization.

---

### Task 1: Merge Course History and Preserve the User Foundation

**Files:**
- Merge: `origin/course-user-management`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/ClientMain.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/InitialPasswordChangeDialog.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/LoginFrame.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserUiCoordinator.java`
- Resolve: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
- Resolve: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/AdminUserService.java`
- Resolve: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserService.java`
- Resolve: `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserServiceImpl.java`
- Resolve corresponding User UI/service tests reported by the merge.
- Keep ours: `vcampus-distribution/lib/vCampusClient.jar`
- Keep ours: `vcampus-distribution/lib/vCampusServer.jar`

**Interfaces:**
- Consumes: `UserUiCoordinator`, `MainFrame`, `ApplicationRuntime`, `UserService` from the User Management baseline.
- Produces: Course sources and history in the integration branch without replacing the latest User authentication contract.

- [ ] **Step 1: Capture the pre-merge identity and protected-file status**

Run:

```powershell
git branch --show-current
git rev-parse --short HEAD
git status --short
```

Expected: branch `integration/user-all-modules`; only the known untracked protected files are present.

- [ ] **Step 2: Merge Course and inventory conflicts**

Run:

```powershell
git merge --no-ff origin/course-user-management -m "merge: integrate course module"
git diff --name-only --diff-filter=U
```

Expected: the known entrypoint/User conflicts are reported. Resolve text conflicts semantically: retain the baseline User implementation while preserving Course imports and composition hooks. Select the current branch for both JARs.

- [ ] **Step 3: Verify the merge contains no markers or unresolved paths**

Run:

```powershell
git diff --name-only --diff-filter=U
rg -n "^(<<<<<<<|=======|>>>>>>>)" vcampus-client vcampus-common vcampus-server vcampus-database
```

Expected: both commands produce no conflict findings.

- [ ] **Step 4: Compile the imported Course sources**

Run:

```powershell
mvn -pl vcampus-common,vcampus-server,vcampus-client -am -DskipTests package
```

Expected: compilation succeeds; any compile failure is recorded before integration behavior is changed.

- [ ] **Step 5: Commit the resolved merge if Git did not complete it automatically**

```powershell
git add -- vcampus-client vcampus-common vcampus-server vcampus-database vcampus-distribution
git commit -m "merge: integrate course module with user foundation"
```

### Task 2: Merge the Latest Student Module

**Files:**
- Merge: `origin/nineloong`
- Review: `vcampus-server/src/main/java/edu/seu/vcampus/server/network/SocketServer.java`
- Review: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/**`
- Review: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/**`

**Interfaces:**
- Consumes: existing shared `ClientConnection`, `StudentModulePageFactory`, User session identity.
- Produces: the latest Student UI, service, repository, DTO, and test behavior in the integrated tree.

- [ ] **Step 1: Merge without committing and inspect the effective diff**

```powershell
git merge --no-commit --no-ff origin/nineloong
git diff --cached --stat
git diff --cached -- vcampus-server/src/main/java/edu/seu/vcampus/server/network/SocketServer.java
```

Expected: automatic merge succeeds. Confirm the SocketServer change does not create another listener or weaken session handling.

- [ ] **Step 2: Run the Student regression suite**

```powershell
mvn -pl vcampus-common,vcampus-server,vcampus-client -am -Dtest='*Student*Test,*Enrollment*Test' test
```

Expected: Student tests pass, apart from any precisely documented pre-existing baseline test excluded by the pattern.

- [ ] **Step 3: Complete the merge commit**

```powershell
git commit -m "merge: integrate latest student module"
```

### Task 3: Merge Library and Unify Permissions

**Files:**
- Merge: `origin/user-library`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/ClientMain.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserUiCoordinator.java`
- Resolve: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
- Resolve: `vcampus-database/seed/010_roles_permissions.sql`
- Resolve: `vcampus-server/src/test/java/edu/seu/vcampus/server/user/repository/AccessPermissionRepositoryTest.java`
- Keep ours: `vcampus-distribution/data/vCampus.accdb`
- Keep ours: `vcampus-distribution/lib/vCampusClient.jar`
- Keep ours: `vcampus-distribution/lib/vCampusServer.jar`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/user/repository/AccessPermissionRepositoryTest.java`

**Interfaces:**
- Consumes: User role codes and database-backed permission lookup.
- Produces: the union of User, Student, and Library permissions while retaining Library handlers and UI sources.

- [ ] **Step 1: Merge Library and select non-source binary policy**

```powershell
git merge --no-ff origin/user-library -m "merge: integrate library module"
git checkout --ours -- vcampus-distribution/data/vCampus.accdb vcampus-distribution/lib/vCampusClient.jar vcampus-distribution/lib/vCampusServer.jar
git add -- vcampus-distribution/data/vCampus.accdb vcampus-distribution/lib/vCampusClient.jar vcampus-distribution/lib/vCampusServer.jar
```

Expected: only source, seed, and test conflicts remain.

- [ ] **Step 2: Write the failing permission-union test**

Extend `AccessPermissionRepositoryTest` with this database-backed assertion:

```java
assertTrue(repository.findByRole(connection, UserRole.ADMIN)
        .containsAll(Set.of("USER_READ_ALL", "STUDENT_WRITE", "LIBRARY_ADMIN")));
```

`LIBRARY_ADMIN` is the permission code declared by `LibraryHandlers` and the Library seed.

- [ ] **Step 3: Run the permission test and confirm RED**

```powershell
mvn -pl vcampus-server -am -Dtest=AccessPermissionRepositoryTest test
```

Expected: FAIL because the conflicted seed does not yet contain the complete permission union, not because the test database cannot initialize.

- [ ] **Step 4: Resolve the seed and entrypoint conflicts minimally**

Retain all User and Student permissions, add every permission code checked by Library handlers, and grant Library administrator permissions to `ADMIN`. Preserve User Management login/UI behavior while retaining Library source imports for the later composition task.

- [ ] **Step 5: Verify GREEN and complete the merge**

```powershell
mvn -pl vcampus-server -am -Dtest=AccessPermissionRepositoryTest test
git diff --name-only --diff-filter=U
git add -- vcampus-client vcampus-server vcampus-database/seed/010_roles_permissions.sql
git commit -m "merge: integrate library with unified permissions"
```

### Task 4: Merge Shop Sources Without Importing Demo Binaries

**Files:**
- Merge: `origin/SHOP`
- Review: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/**`
- Review: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/**`
- Review: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/**`
- Review: `vcampus-database/schema/050_shop.sql`
- Keep ours: distribution JARs and `vCampus.accdb` if Git later reports binary changes.

**Interfaces:**
- Consumes: User sessions through `ShopUserPort` and `FoundationShopUserAdapter`.
- Produces: all latest Shop features, including cart deletion/partial checkout, application drafts, product states, and Chinese error mapping.

- [ ] **Step 1: Merge Shop without committing and inspect shared-file changes**

```powershell
git merge --no-commit --no-ff origin/SHOP
git diff --cached --name-only
```

Expected: automatic merge succeeds; no shared User implementation is replaced.

- [ ] **Step 2: Run Shop contract and service regressions**

```powershell
mvn -pl vcampus-common,vcampus-server,vcampus-client -am -Dtest='*Shop*Test,*Cart*Test,*Checkout*Test,*Product*Test,*Seller*Test' test
```

Expected: Shop tests pass against the merged User foundation or expose an integration gap that is recorded before Task 5.

- [ ] **Step 3: Complete the merge**

```powershell
git commit -m "merge: integrate latest shop module"
```

### Task 5: Build One Server Runtime and Authorization Boundary

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ApplicationRuntime.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/UnifiedModuleRegistry.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/composition/CourseComposition.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/library/service/LibraryAuthorizationAdapter.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/adapter/FoundationShopUserAdapter.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/ApplicationRuntimeTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/ServerMainSessionConfigurationTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/ServerMainLibraryRegistrationTest.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/UnifiedServerRegistrationTest.java`

**Interfaces:**
- Consumes: `MessageRouter`, one `SessionRegistry`, module handler registration methods, and unified database connection factory.
- Produces: `UnifiedModuleRegistry.registerAll(MessageRouter)` that registers User, Student, Course, Library, and Shop exactly once.

- [ ] **Step 1: Write the failing unified-registration test**

The test starts the application runtime against a temporary database, calls the unified registration method, and asserts representative commands exist and are not duplicated:

```java
assertAll(
    () -> assertTrue(router.hasHandler("USER_LOGIN")),
    () -> assertTrue(router.hasHandler("STUDENT_GET_CURRENT")),
    () -> assertTrue(router.hasHandler("COURSE_SEARCH_OFFERINGS")),
    () -> assertTrue(router.hasHandler("LIBRARY_SEARCH_BOOKS")),
    () -> assertTrue(router.hasHandler("SHOP_HOME")));
```

The representative Student and Library routes are `STUDENT_GET_CURRENT` and `LIBRARY_SEARCH_BOOKS`.

- [ ] **Step 2: Run the test and confirm RED**

```powershell
mvn -pl vcampus-server -am -Dtest=UnifiedServerRegistrationTest test
```

Expected: FAIL because the unified runtime does not yet expose all five module routes.

- [ ] **Step 3: Implement minimal unified assembly**

Construct each module service with the shared connection, transaction, and session dependencies. Use `LibraryAuthorizationAdapter` for Library permission checks and `FoundationShopUserAdapter` for Shop actor resolution, and register each handler group once. Keep module Demo runtimes separate and ensure `ServerMain` starts only the unified runtime on configured port 8888.

- [ ] **Step 4: Verify server GREEN and regression**

```powershell
mvn -pl vcampus-server -am -Dtest='UnifiedServerRegistrationTest,ApplicationRuntimeTest,ServerMainSessionConfigurationTest,ServerMainLibraryRegistrationTest,*HandlersTest' test
```

Expected: all selected tests pass with no duplicate-route exceptions.

- [ ] **Step 5: Commit server integration**

```powershell
git add -- vcampus-server/src/main vcampus-server/src/test
git commit -m "feat(integration): assemble all modules in one server"
```

### Task 6: Build One Authenticated Client Shell

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/ClientMain.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserUiCoordinator.java`
- Use: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentModulePageFactory.java`
- Use: Course UI composition classes under `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/`
- Use: Library UI classes under `vcampus-client/src/main/java/edu/seu/vcampus/client/library/`
- Use: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopUiInstaller.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/UnifiedMainFrameTest.java`
- Update: existing `MainFrameShellTest`, authentication UI tests, and module UI installation tests.

**Interfaces:**
- Consumes: authenticated `UserView`, granted permission set, and one `ClientConnection`.
- Produces: one main window whose page navigator contains every authorized module and whose logout returns to User Management login.

- [ ] **Step 1: Write the failing page-composition tests**

Create tests for `ADMIN`, `STUDENT`, and `TEACHER`. The administrator test asserts:

```java
    assertEquals(Set.of("student", "course", "library", "shop", "account"),
        frame.registeredPageIds());
```

Assert student and teacher visibility according to the design and use existing module IDs where already defined.

- [ ] **Step 2: Run and confirm RED**

```powershell
mvn -pl vcampus-client -am -Dtest=UnifiedMainFrameTest test
```

Expected: FAIL because Course, Library, and Shop are still placeholders or are not installed through the authenticated shell.

- [ ] **Step 3: Implement the minimal client composition**

Replace placeholder registrations with module page factories/installers. Pass the same connection and authenticated identity to all modules. Preserve first-password-change gating and make logout/close ownership explicit so the shared connection is closed once.

- [ ] **Step 4: Verify client GREEN and authentication regressions**

```powershell
mvn -pl vcampus-client -am -Dtest='UnifiedMainFrameTest,MainFrameShellTest,*Login*Test,*Logout*Test,*UiTest' test
```

Expected: unified shell and existing authentication/module UI tests pass.

- [ ] **Step 5: Commit client integration**

```powershell
git add -- vcampus-client/src/main vcampus-client/src/test
git commit -m "feat(integration): expose all modules in authenticated client"
```

### Task 7: Generate a Unified Database and Comprehensive Dataset

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ApplicationSchemaInitializer.java` or the retained unified initializer.
- Include: `vcampus-database/schema/001_common.sql`
- Include: `vcampus-database/schema/010_user.sql`
- Include: `vcampus-database/schema/020_student.sql`
- Include: `vcampus-database/schema/030_course.sql`
- Include: `vcampus-database/schema/040_library.sql`
- Include: `vcampus-database/schema/050_shop.sql`
- Modify: `vcampus-database/seed/010_roles_permissions.sql`
- Create or reorganize numbered integration seed files under `vcampus-database/seed/` for accounts, Student, Course, Library, and Shop.
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/UnifiedDemoDatasetTest.java`
- Update: `vcampus-server/src/test/java/edu/seu/vcampus/server/user/service/DemoDistributionAccountsTest.java`
- Update: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/SeededStudentDatasetTest.java`
- Update: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/composition/CourseSchemaInitializerTest.java`
- Update: Library repository tests under `vcampus-server/src/test/java/edu/seu/vcampus/server/library/repository/`
- Update: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabaseTest.java`

**Interfaces:**
- Consumes: ordered SQL resources and a target Access database path.
- Produces: a complete database containing linked cross-module identities and documented test states.

- [ ] **Step 1: Write failing empty-database generation tests**

Generate into a JUnit temporary directory and assert the six module table groups exist. Assert the administrator receives the union of all administrator permissions, and that every documented test login resolves to the intended role/status.

- [ ] **Step 2: Write failing scenario coverage assertions**

Add direct repository/SQL assertions proving the dataset contains at least one row for every required state: full/conflicting Course offerings; borrowed/overdue Library loans; Shop draft/pending/approved applications, draft/inactive/active products, multi-variant inventory, partial cart selection, and paid/unpaid orders.

- [ ] **Step 3: Run and confirm RED**

```powershell
mvn -pl vcampus-server -am -Dtest='UnifiedDemoDatasetTest,DemoDistributionAccountsTest,SeededStudentDatasetTest' test
```

Expected: FAIL because the current initializer and seeds do not install all module schemas/data.

- [ ] **Step 4: Implement ordered initialization and seeds**

Install schemas in `001, 010, 020, 030, 040, 050` order, then permissions/accounts and module data in dependency order. Reuse the same user UUID for each cross-module person. Use the password hashing format and iteration count expected by User Management. Make reruns against a fresh target deterministic.

- [ ] **Step 5: Verify dataset GREEN**

```powershell
mvn -pl vcampus-server -am -Dtest='UnifiedDemoDatasetTest,DemoDistributionAccountsTest,SeededStudentDatasetTest,*SchemaInitializerTest,*RepositoryTest' test
```

Expected: all selected database and repository tests pass.

- [ ] **Step 6: Commit database integration**

```powershell
git add -- vcampus-database vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap
git commit -m "feat(integration): generate unified demo database"
```

### Task 8: End-to-End Authorization and Cross-Module Smoke Tests

**Files:**
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/integration/UnifiedCampusSocketIntegrationTest.java`
- Create or update client integration tests under `vcampus-client/src/test/java/edu/seu/vcampus/client/integration/`.

**Interfaces:**
- Consumes: unified database, server runtime, client connection, authentication request/results.
- Produces: repeatable proof that one login session can call each authorized module and is rejected from unauthorized administration routes.

- [ ] **Step 1: Write the failing administrator socket flow**

Log in as the comprehensive administrator and call one read/admin operation in User, Student, Course, Library, and Shop using the same session token. Assert successful responses.

- [ ] **Step 2: Write the failing student/teacher boundary flows**

Log in as a student and teacher; assert permitted reads succeed and representative administrator commands return the platform's forbidden response. Assert the shop-owner student can access seller commands while the ordinary student cannot.

- [ ] **Step 3: Run and confirm RED**

```powershell
mvn -pl vcampus-server,vcampus-client -am -Dtest='UnifiedCampusSocketIntegrationTest,*IntegrationTest' test
```

Expected: any remaining identity/authorization wiring defect produces a focused failure.

- [ ] **Step 4: Make only the minimal adapter or permission corrections**

Correct shared identity conversion, handler authorization declarations, or seed grants as identified by each failing assertion. Do not broaden permissions beyond the role matrix.

- [ ] **Step 5: Verify GREEN and commit**

```powershell
mvn -pl vcampus-server,vcampus-client -am -Dtest='UnifiedCampusSocketIntegrationTest,*IntegrationTest' test
git add -- vcampus-server vcampus-client vcampus-database
git commit -m "test(integration): cover unified campus roles and modules"
```

### Task 9: Build Distribution and Manual-Test Package

**Files:**
- Modify: `vcampus-distribution/scripts/start-server.bat`
- Modify: `vcampus-distribution/scripts/start-server.sh`
- Modify: `vcampus-distribution/scripts/start-client.bat`
- Modify: `vcampus-distribution/scripts/start-client.sh`
- Add or update: database reset/build scripts under `vcampus-distribution/scripts/`.
- Modify: `vcampus-distribution/README.md`
- Create: `docs/testing/2026-09-02-vcampus-unified-manual-test-guide.md`
- Test: distribution script tests under `vcampus-server/src/test/` or `vcampus-distribution/scripts/tests/` following the retained project convention.

**Interfaces:**
- Consumes: Maven-built client/server JARs and generated `vcampus-distribution/data/vCampus.accdb`.
- Produces: documented one-command server/client startup and a role/scenario test matrix.

- [ ] **Step 1: Write failing distribution checks**

Assert scripts reference the unified server/client main classes, port 8888, the unified database path, and no module-specific Demo main. Assert README account rows match database logins.

- [ ] **Step 2: Run and confirm RED**

```powershell
mvn -pl vcampus-server -am -Dtest='*Distribution*Test,*DemoScriptsTest' test
```

Expected: FAIL until scripts and documentation point to the unified runtime/data.

- [ ] **Step 3: Update scripts and manual guide**

Document exact working directory, server/client commands, all test accounts and passwords, expected role menus, per-module test procedures, cross-module administrator checks, database reset instructions, and known limitations. Build JARs from the integrated source rather than retaining branch binaries.

- [ ] **Step 4: Verify distribution checks and package**

```powershell
mvn clean package
mvn -pl vcampus-server -am -Dtest='*Distribution*Test,*DemoScriptsTest' test
```

Expected: build and script tests pass; generated artifacts use the unified main classes.

- [ ] **Step 5: Commit the distribution handoff**

```powershell
git add -- vcampus-distribution docs/testing
git commit -m "docs(distribution): package unified campus manual test build"
```

### Task 10: Full Verification and Human Handoff

**Files:**
- Update only if findings require documentation: `docs/superpowers/specs/2026-09-02-vcampus-user-based-integration-test-findings.md`
- No production changes during final verification without returning to a focused RED/GREEN cycle.

**Interfaces:**
- Consumes: complete integrated source, generated database, distribution scripts, and manual guide.
- Produces: evidence-backed release candidate ready for user manual acceptance.

- [ ] **Step 1: Run the complete clean build**

```powershell
mvn clean verify
```

Expected: all reactor modules succeed with zero test failures and errors.

- [ ] **Step 2: Run repository hygiene checks**

```powershell
git diff --check
git status --short
git log --oneline --decorate -12
```

Expected: no whitespace errors or unresolved merge state; only explicitly preserved untracked user files remain.

- [ ] **Step 3: Start the packaged server and client**

Use the documented Windows scripts from `vcampus-distribution`. Confirm one server listens on 8888, the client reaches User Management login, and the comprehensive administrator sees all module entries.

- [ ] **Step 4: Execute the smoke subset of the manual guide**

Verify administrator navigation, ordinary student cross-module access, teacher access, shop-owner seller access, and one forbidden action for a non-administrator. Record exact results and any remaining limitation.

- [ ] **Step 5: Present the release candidate for user acceptance**

Report branch/HEAD, merge commits, automated test counts, startup commands, account table location, preserved untracked files, and the manual test guide. Do not push or merge into `SHOP` until the user explicitly authorizes it.
