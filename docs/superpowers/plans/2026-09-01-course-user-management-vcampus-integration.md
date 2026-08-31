# Course User Management vCampus Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a unified authenticated vCampus client in which the course module is one role-filtered workspace, normal-enrollment drop works correctly, administrator entry is structured, and a verified three-role demo is ready to run.

**Architecture:** Merge the latest user-management history into the existing isolated course branch, retain its shared five-module shell, and install one `CourseWorkspacePanel` into `page.course`. Extend the existing server-authoritative course workflow with a general drop window, then replace raw administrator inputs with asynchronous reference choices and focused structured editors. Finish with real-socket tests, rendered UI evidence, runnable demo packaging, review, and push.

**Tech Stack:** Java 21, Maven, Swing, Java sockets/object serialization, UCanAccess 5.1.3, JUnit 5, AssertJ, Mockito, JaCoCo

**Spec:** `docs/superpowers/specs/2026-09-01-course-user-management-vcampus-integration-design.md`

## Global Constraints

- Work only on branch `course-user-management` in `/private/tmp/java-summer-course-course-user-integration`.
- Preserve the latest `origin/feat/user-management` history with a two-parent merge commit; do not merge Shop or Library.
- The global left navigation is exactly `student`, `course`, `library`, `shop`, `account` in that order.
- Course-owned navigation lives only inside `page.course`.
- Login/session role and permissions come from `LoginResult`; server authorization remains authoritative.
- Drop is allowed only inside the normal enrollment or adjustment window; late-add and change remain adjustment-only.
- Do not delete enrollment history; retain optimistic locking, audit, transaction, and lock invariants.
- Do not add an Excel/CSV bulk course/offering importer in this iteration.
- Do not perform socket/database work on the Swing EDT.
- Do not log passwords, password hashes, salts, or full session tokens.
- Run Maven through Java 21 exactly as `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn ...`.
- The final demo has one shared client launcher and one server-with-demo-data launcher.

---

### Task 1: Merge the Latest Shared Login and vCampus Shell

**Files:**
- Merge: `origin/feat/user-management`
- Resolve: `.gitignore`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/ClientMain.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/theme/*.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/service/UserClientService.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/LoginFrame.java`
- Resolve: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/InitialPasswordChangeDialog.java`
- Resolve: `vcampus-client/src/test/java/edu/seu/vcampus/client/user/LoginDemoUiTest.java`
- Resolve: `vcampus-distribution/lib/vCampusClient.jar`
- Resolve: `vcampus-distribution/lib/vCampusServer.jar`
- Preserve: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/**`
- Preserve: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/**`
- Preserve: `vcampus-database/schema/030_course.sql`

**Interfaces:**
- Consumes: `origin/feat/user-management` at fetched commit `8d53d9e` or a verified descendant.
- Produces: latest `UserUiCoordinator`, split login UI, account/logout flow, `MainFrame(UserView, ClientConnection)`, `PermissionNavigation`, and shared theme classes alongside every existing course source.

- [ ] **Step 1: Verify the remote head and current clean state**

```bash
git fetch origin feat/user-management
git rev-parse origin/feat/user-management
git status --short
```

Expected: the worktree has no source changes; the remote head is `8d53d9e` or a descendant whose diff has been reviewed.

- [ ] **Step 2: Start a non-fast-forward merge without committing**

```bash
git merge --no-ff --no-commit origin/feat/user-management
git diff --name-only --diff-filter=U
```

Expected conflicts: shared entry point, shell, theme, user client/login files, tests, `.gitignore`, and generated distribution binaries. Course-only source files must not be removed.

- [ ] **Step 3: Resolve shared source with explicit ownership**

Keep the user branch versions of the shell components, `LoginFrame`, user account pages, logout flow, `UserUiCoordinator`, and theme tokens. Keep the course branch application composition, course client/service code, demo launchers, and simplified distribution-script naming. In `ClientMain`, temporarily start `UserUiCoordinator`; course installation is added in Task 3. In generated JAR conflicts, keep the course branch artifacts and rebuild them in Task 8 rather than committing stale user-only binaries.

```java
public static void main(String[] args) {
    UiThemeInstaller.install();
    ClientConnection connection = connect(config());
    UserClientService users = new UserClientService(
            connection, UUID.randomUUID().toString(), Duration.ofSeconds(10));
    new UserUiCoordinator(users, connection).start();
}
```

- [ ] **Step 4: Verify merge integrity and compile**

```bash
git diff --name-only --diff-filter=U
test -f vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/shell/PermissionNavigation.java
test -f vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiComposition.java
test -f vcampus-server/src/main/java/edu/seu/vcampus/server/course/handler/CourseHandlers.java
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn -DskipTests compile
```

Expected: no unresolved paths and `BUILD SUCCESS`.

- [ ] **Step 5: Run shared-login and course regression tests**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am \
  -Dtest=MainFrameShellTest,LoginDemoUiTest,LogoutUiTest,CourseUiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass on Java 21.

- [ ] **Step 6: Commit the merge**

```bash
git add -A
git commit -m "merge: update shared user management shell"
git show --no-patch --format='%P' HEAD
```

Expected: two parent hashes.

---

### Task 2: Add a Replaceable Five-Module Shell Seam

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/navigation/PageNavigator.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserUiCoordinator.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/MainFrameShellTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/user/LogoutUiTest.java`

**Interfaces:**
- Consumes: the five placeholder pages registered by `MainFrame`.
- Produces: `PageNavigator.replace(String, JComponent)`, `MainFrame.installPage(String, JComponent)`, and account-page installation without manipulating the content container directly.

- [ ] **Step 1: Write failing navigator and shell replacement tests**

Add these focused assertions to `MainFrameShellTest`:

```java
@Test
void installPageReplacesTheCoursePlaceholderWithoutChangingGlobalNavigation() throws Exception {
    MainFrame[] frame = new MainFrame[1];
    JPanel course = new JPanel();
    course.setName("page.course");
    SwingUtilities.invokeAndWait(() -> {
        frame[0] = new MainFrame(user(), connected());
        frame[0].installPage("course", course);
        component(frame[0], "navigation.course", AbstractButton.class).doClick();
    });
    assertThat(course.isVisible()).isTrue();
    assertThat(frame[0].navigation().getComponentCount()).isEqualTo(5);
    assertThat(Arrays.stream(frame[0].content().getComponents())
            .filter(child -> "page.course".equals(child.getName()))).containsExactly(course);
}
```

Also update `LogoutUiTest` to assert the account page is installed through the shell seam and logout still returns to one login window.

- [ ] **Step 2: Run RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am -Dtest=MainFrameShellTest,LogoutUiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `installPage` is absent.

- [ ] **Step 3: Implement navigator replacement**

Replace the navigator's ID set with a component map and implement:

```java
public void replace(String pageId, JComponent page) {
    Objects.requireNonNull(pageId, "pageId");
    Objects.requireNonNull(page, "page");
    JComponent previous = pages.get(pageId);
    if (previous == null) throw new IllegalArgumentException("Unknown page id: " + pageId);
    container.remove(previous);
    pages.put(pageId, page);
    container.add(page, pageId);
    container.revalidate();
    container.repaint();
}
```

Keep `register` duplicate rejection and `show` EDT handoff unchanged.

- [ ] **Step 4: Expose the shell seam and migrate account installation**

```java
public void installPage(String pageId, JComponent page) {
    pageNavigator.replace(pageId, page);
}
```

In `UserUiCoordinator.replaceAccountPage`, replace manual component removal/addition with:

```java
main.installPage("account", new AccountPanel(
        users, result.user(), result.permissions(),
        () -> returnToLogin(main, PASSWORD_CHANGED),
        () -> returnToLogin(main, LOGGED_OUT)));
```

- [ ] **Step 5: Run GREEN and commit**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am -Dtest=MainFrameShellTest,LogoutUiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
git add vcampus-client/src/main/java/edu/seu/vcampus/client/core \
        vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserUiCoordinator.java \
        vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/MainFrameShellTest.java \
        vcampus-client/src/test/java/edu/seu/vcampus/client/user/LogoutUiTest.java
git commit -m "refactor(client): add replaceable module pages"
```

---

### Task 3: Embed One Role-Filtered Course Workspace

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseWorkspacePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiComposition.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseClientGateway.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserUiCoordinator.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/ClientMain.java`
- Replace expectations: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/AuthenticatedCourseShellTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/integration/LoginCourseSocketIntegrationTest.java`

**Interfaces:**
- Consumes: `CourseUiGateway`, `UserRole`, `LoginResult.permissions()`, one shared `ClientConnection`.
- Produces: `CourseWorkspacePanel(CourseUiGateway, UserRole)`, `CourseUiComposition.workspaceFor(UserRole): CourseWorkspacePanel`, and `UserUiCoordinator(UserClientService, CourseClientService, ClientConnection)`.

- [ ] **Step 1: Write failing workspace tests**

Add to `CourseUiTest`:

```java
@ParameterizedTest
@MethodSource("roleTabs")
void workspaceOwnsRoleFilteredInternalTabs(UserRole role, List<String> expected) throws Exception {
    CourseWorkspacePanel workspace = onEdt(
            () -> new CourseWorkspacePanel(CourseUiGateway.preview(), role));
    JTabbedPane tabs = descendants(workspace).stream()
            .filter(JTabbedPane.class::isInstance).map(JTabbedPane.class::cast)
            .findFirst().orElseThrow();
    assertThat(IntStream.range(0, tabs.getTabCount()).mapToObj(tabs::getTitleAt))
            .containsExactlyElementsOf(expected);
    assertThat(workspace.getName()).isEqualTo("page.course");
}

static Stream<Arguments> roleTabs() {
    return Stream.of(
            Arguments.of(UserRole.STUDENT, List.of("教学班查询", "我的选课", "我的课表", "退改补", "重修")),
            Arguments.of(UserRole.TEACHER, List.of("教学班查询", "教师课表")),
            Arguments.of(UserRole.ADMIN, List.of("学期管理", "课程目录", "教学班管理", "修读结果导入", "选退记录")));
}
```

Rewrite `AuthenticatedCourseShellTest` assertions so every role always sees exactly the five global module buttons, while the course page contains only that role's internal tabs.

- [ ] **Step 2: Run RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am -Dtest=CourseUiTest,AuthenticatedCourseShellTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `CourseWorkspacePanel` is absent and old global-course navigation assertions no longer match.

- [ ] **Step 3: Implement the workspace**

```java
public final class CourseWorkspacePanel extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<Supplier<? extends AbstractCoursePanel>> factories = new ArrayList<>();
    private final Map<Integer, AbstractCoursePanel> loaded = new HashMap<>();
    private final Set<Integer> dirty = new HashSet<>();

    public CourseWorkspacePanel(CourseUiGateway gateway, UserRole role) {
        super(new BorderLayout());
        setName("page.course");
        setBackground(UiColors.BACKGROUND_PAGE);
        switch (role) {
            case STUDENT -> {
                addTab("教学班查询", () -> new OfferingSearchPanel(gateway));
                addTab("我的选课", () -> new MyEnrollmentPanel(gateway));
                addTab("我的课表", () -> new MySchedulePanel(gateway));
                addTab("退改补", () -> new AdjustmentPanel(gateway));
                addTab("重修", () -> new RetakePanel(gateway));
            }
            case TEACHER -> {
                addTab("教学班查询", () -> new OfferingSearchPanel(gateway));
                addTab("教师课表", () -> new MySchedulePanel(gateway));
            }
            case ADMIN -> {
                addTab("学期管理", () -> new TermManagementPanel(gateway));
                addTab("课程目录", () -> new CourseCatalogPanel(gateway));
                addTab("教学班管理", () -> new OfferingManagementPanel(gateway));
                addTab("修读结果导入", () -> new OutcomeImportPanel(gateway));
                addTab("选退记录", () -> new AdjustmentAuditPanel(gateway));
            }
        }
        tabs.addChangeListener(event -> open(tabs.getSelectedIndex()));
        add(tabs, BorderLayout.CENTER);
        open(0);
    }
}
```

`addTab` accepts a `Supplier<? extends AbstractCoursePanel>`, stores it in `factories`, and initially adds an empty named placeholder. `open(index)` calls the selected supplier once, replaces only that tab component, caches it in `loaded`, and invokes `refreshAfterNavigation()` only when a previously loaded dirty tab is reselected. This prevents constructors for hidden tabs from issuing requests.

```java
private void addTab(String title, Supplier<? extends AbstractCoursePanel> factory) {
    factories.add(factory);
    JPanel placeholder = new JPanel(new BorderLayout());
    placeholder.setOpaque(false);
    tabs.addTab(title, placeholder);
}

private void open(int index) {
    if (index < 0) return;
    AbstractCoursePanel page = loaded.computeIfAbsent(index, key -> {
        AbstractCoursePanel created = factories.get(key).get();
        tabs.setComponentAt(key, created);
        return created;
    });
    if (dirty.remove(index)) page.refreshAfterNavigation();
}
```

- [ ] **Step 4: Convert composition to one workspace factory**

```java
public CourseWorkspacePanel workspaceFor(UserRole role) {
    return new CourseWorkspacePanel(gateway, Objects.requireNonNull(role, "role"));
}
```

Remove the maps that exposed `course.*` pages to global navigation. Keep `CourseUiGateway.preview()` for screenshot and unit-test construction.

- [ ] **Step 5: Install courses from the authenticated coordinator**

Add a production constructor that retains one `CourseClientService`:

```java
public UserUiCoordinator(UserClientService users, ClientConnection connection) {
    this(users, null, connection);
}

public UserUiCoordinator(UserClientService users, CourseClientService courses,
                         ClientConnection connection) {
    this.users = Objects.requireNonNull(users);
    this.courses = courses;
    this.connection = Objects.requireNonNull(connection);
}
```

The two-argument constructor preserves user-module tests/previews; production `ClientMain` always supplies the non-null course service. Install the course page only when `courses != null`.

After unrestricted login:

```java
MainFrame main = new MainFrame(result.user(), connection);
main.installPage("course", new CourseUiComposition(courses)
        .workspaceFor(result.user().role()));
replaceAccountPage(main, result);
bindCourseAuthenticationFailure(main);
main.setVisible(true);
```

`bindCourseAuthenticationFailure` registers exactly one listener, clears the token, disposes the shell, removes its listener on window close, and returns to login once for `AUTH_SESSION_EXPIRED`, `AUTH_ACCOUNT_DISABLED`, or `AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED`.

```java
private void bindCourseAuthenticationFailure(MainFrame main) {
    if (courses == null) return;
    AtomicBoolean handedOff = new AtomicBoolean();
    Runnable remove = courses.addAuthenticationFailureListener(failure -> {
        if (!handedOff.compareAndSet(false, true)) return;
        users.clearSession();
        SwingUtilities.invokeLater(() -> {
            main.dispose();
            showLogin("登录状态已失效，请重新登录");
        });
    });
    main.addWindowListener(new WindowAdapter() {
        @Override public void windowClosed(WindowEvent event) { remove.run(); }
    });
}
```

Update `ClientMain` to construct one `CourseClientService(connection)` and pass it to the coordinator.

- [ ] **Step 6: Run role, login, session, and socket tests**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am \
  -Dtest=CourseUiTest,AuthenticatedCourseShellTest,MainFrameShellTest,LoginDemoUiTest,LogoutUiTest,LoginCourseSocketIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: global navigation stays canonical, role tabs match, login opens the embedded course page, and authentication failure returns to login once.

- [ ] **Step 7: Commit**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client \
        vcampus-client/src/test/java/edu/seu/vcampus/client
git commit -m "feat(client): embed courses in shared vCampus shell"
```

---

### Task 4: Generalize Drop to Enrollment and Adjustment Windows

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/TermWindowPolicy.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/DropClosedException.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/EnrollmentAdjustmentService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/handler/CourseHandlers.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/service/CourseClientService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseClientGateway.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/domain/TermWindowPolicyTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/EnrollmentAdjustmentTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/AdjustmentRuleCoverageTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/handler/CourseHandlersTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/service/CourseClientServiceTest.java`

**Interfaces:**
- Consumes: existing `DropCommand`, enrollment/adjustment instants, locking and transaction workflow.
- Produces: `TermWindowPolicy.requireDropOpen(Term, Instant)`, error code `COURSE_DROP_NOT_OPEN`, `CourseService.drop(String, DropCommand)`, `CourseClientService.drop(DropCommand)`, primary `COURSE_DROP`, and compatibility alias `COURSE_ADJUSTMENT_DROP`.

- [ ] **Step 1: Write failing drop-window boundary tests**

Add parameterized cases to `TermWindowPolicyTest`:

```java
@ParameterizedTest
@MethodSource("openDropInstants")
void dropIsOpenDuringEitherMutationWindow(Instant now) {
    assertThatCode(() -> windows.requireDropOpen(activeTerm(), now))
            .doesNotThrowAnyException();
}

static Stream<Instant> openDropInstants() {
    return Stream.of(ENROLLMENT_START, ENROLLMENT_END.minusNanos(1),
            ADJUSTMENT_START, ADJUSTMENT_END.minusNanos(1));
}

@ParameterizedTest
@MethodSource("closedDropInstants")
void dropIsClosedOutsideBothWindows(Instant now) {
    assertThatThrownBy(() -> windows.requireDropOpen(activeTerm(), now))
            .isInstanceOfSatisfying(DropClosedException.class,
                    error -> assertThat(error.code()).isEqualTo("COURSE_DROP_NOT_OPEN"));
}
```

Include before-start, the gap between windows, exact upper bounds, after-end, and a `CLOSED` term.

- [ ] **Step 2: Run policy RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-server -am -Dtest=TermWindowPolicyTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `requireDropOpen` is absent.

- [ ] **Step 3: Implement the minimal domain rule**

```java
public void requireDropOpen(Term term, Instant now) {
    Objects.requireNonNull(term, "term");
    Objects.requireNonNull(now, "now");
    String status = requireStatus(term);
    boolean enrollment = inWindow(now, term.enrollmentStartAt(), term.enrollmentEndAt());
    boolean adjustment = inWindow(now, term.adjustmentStartAt(), term.adjustmentEndAt());
    if ("CLOSED".equals(status) || (!enrollment && !adjustment)) throw new DropClosedException();
}
```

Do not change `requireAdjustmentOpen`; late-add and change continue calling it.

```java
public final class DropClosedException extends CourseRuleException {
    public static final String CODE = "COURSE_DROP_NOT_OPEN";
    public DropClosedException() { super(CODE, CODE + ": drop window is not open"); }
}
```

Add `COURSE_DROP_NOT_OPEN -> 当前不在可退选时间内，请查看选课与退改选开放时间` to `CourseHandlers.userMessage`.

- [ ] **Step 4: Write failing service, route, and client contract tests**

In `EnrollmentAdjustmentTest`, seed a clock inside the normal enrollment window and assert `service.drop(...)` marks the row dropped, sets `droppedAt`, increments version, decrements offering count, and inserts a successful `DROP` audit. Add a gap-window case that leaves both row and count unchanged.

In `CourseHandlersTest`, assert both commands are registered for `STUDENT`, route to `service.drop`, and return `EmptyResponse.INSTANCE`.

In `CourseClientServiceTest`:

```java
@Test
void dropUsesTheGeneralCourseCommand() {
    client.drop(new DropCommand("enrollment-1", 3)).join();
    assertThat(transport.command()).isEqualTo("COURSE_DROP");
}
```

- [ ] **Step 5: Run contract RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-server,vcampus-client -am \
  -Dtest=EnrollmentAdjustmentTest,AdjustmentRuleCoverageTest,CourseHandlersTest,CourseClientServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing `drop` methods and `COURSE_DROP` route fail.

- [ ] **Step 6: Implement the general service and compatibility route**

```java
// CourseService
void drop(String sessionToken, DropCommand command);

// CourseServiceImpl
@Override public void drop(String token, DropCommand command) {
    adjustments.drop(token, command);
}

// CourseHandlers
r.register("COURSE_DROP", write(DropCommand.class, Set.of("STUDENT"),
        (m, b) -> { service.drop(m.sessionToken(), b); return EmptyResponse.INSTANCE; }));
r.register("COURSE_ADJUSTMENT_DROP", write(DropCommand.class, Set.of("STUDENT"),
        (m, b) -> { service.drop(m.sessionToken(), b); return EmptyResponse.INSTANCE; }));
```

Change only the drop workflow inside `EnrollmentAdjustmentService` from `requireAdjustmentOpen` to `requireDropOpen`. Keep locks, revalidation, transaction boundaries, row version, count update, and audits unchanged.

```java
public CompletableFuture<EmptyResponse> drop(DropCommand command) {
    return call("COURSE_DROP", command, WRITE, EmptyResponse.class);
}
```

Keep `dropDuringAdjustment` as a deprecated client delegate to `drop` only if existing callers/tests require binary/source compatibility.

- [ ] **Step 7: Run GREEN, concurrency regressions, and commit**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-server,vcampus-client -am \
  -Dtest=TermWindowPolicyTest,EnrollmentAdjustmentTest,AdjustmentRuleCoverageTest,AdjustmentFailureAuditTest,ConcurrentAdjustmentTest,CourseHandlersTest,CourseClientServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
git add vcampus-server/src/main/java/edu/seu/vcampus/server/course \
        vcampus-server/src/test/java/edu/seu/vcampus/server/course \
        vcampus-client/src/main/java/edu/seu/vcampus/client/course \
        vcampus-client/src/test/java/edu/seu/vcampus/client/course/service/CourseClientServiceTest.java
git commit -m "fix(course): allow drop throughout selection windows"
```

---

### Task 5: Add Immediate Drop to “My Enrollments”

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/MyEnrollmentPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/AdjustmentPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiGateway.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseWorkspacePanel.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/integration/LoginCourseSocketIntegrationTest.java`

**Interfaces:**
- Consumes: `CourseUiGateway.drop(DropCommand)`, current enrollments, current term phase, offering summaries.
- Produces: active-row “退选所选课程”, confirmation seam, post-drop refresh invalidation, and an end-to-end enroll-then-drop scenario in the normal window.

- [ ] **Step 1: Write failing student-panel tests**

Create a package-private confirmation seam:

```java
@FunctionalInterface
interface DropConfirmation {
    boolean confirm(Window owner, String courseLabel);
}
```

Add tests that construct `MyEnrollmentPanel` with one active enrollment, select it, click “退选所选课程”, and assert:

```java
assertThat(submitted.get()).isEqualTo(new DropCommand("enrollment-1", 7));
assertThat(refreshCalls).hasValue(2);
assertThat(button(panel, "退选所选课程").isEnabled()).isTrue();
```

Add cases for a `DROPPED` row, no selection, confirmation rejection, request failure, late async completion after removal, and phases `READ_ONLY`/`CLOSED`. Read-only and closed phases disable the action; phases `ENROLLMENT` and `ADJUSTMENT` enable it for active rows.

- [ ] **Step 2: Run UI RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am -Dtest=CourseUiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing constructor/seam/button behavior fails.

- [ ] **Step 3: Implement row mapping, phase display, and drop**

Store the loaded rows separately from the table model:

```java
private final List<EnrollmentView> enrollments = new ArrayList<>();

private void dropSelected() {
    int selected = table.getSelectedRow();
    if (selected < 0) { showState(ViewState.ERROR, "请先选择要退选的课程"); return; }
    EnrollmentView enrollment = enrollments.get(table.convertRowIndexToModel(selected));
    if (!"ACTIVE".equals(enrollment.enrollmentStatus())) {
        showState(ViewState.ERROR, "该选课记录已退选，请刷新后重试"); return;
    }
    if (!confirmation.confirm(SwingUtilities.getWindowAncestor(this), labelFor(enrollment))) return;
    submitDrop(new DropCommand(enrollment.enrollmentId(), enrollment.rowVersion()));
}
```

Fetch current term/phase with the enrollment list, show the formatted selection and adjustment windows, and retain prior rows on network failure. Disable the action while a request is pending. On success refresh this panel and notify the workspace through a `Runnable onEnrollmentChanged` supplied by `CourseWorkspacePanel`.

- [ ] **Step 4: Keep adjustment behavior adjustment-only**

`AdjustmentPanel` may continue showing “退选所选”, but it calls the same general gateway method and remains enabled only when `phase == ADJUSTMENT`. Do not widen late-add or change buttons.

- [ ] **Step 5: Extend the real-socket integration test**

After the existing normal-window enrollment:

```java
EnrollmentView enrollment = courses.enroll(new EnrollCommand(offering.offeringId())).join();
int beforeDrop = courses.searchOfferings(query).join().items().getFirst().enrolledCount();
courses.drop(new DropCommand(enrollment.enrollmentId(), enrollment.rowVersion())).join();
assertThat(courses.getCurrentEnrollments().join())
        .filteredOn(row -> row.enrollmentId().equals(enrollment.enrollmentId()))
        .extracting(EnrollmentView::enrollmentStatus).containsExactly("DROPPED");
assertThat(courses.searchOfferings(query).join().items().getFirst().enrolledCount())
        .isEqualTo(beforeDrop - 1);
```

- [ ] **Step 6: Run GREEN and commit**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am -Dtest=CourseUiTest,LoginCourseSocketIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
git add vcampus-client/src/main/java/edu/seu/vcampus/client/course \
        vcampus-client/src/test/java/edu/seu/vcampus/client/course \
        vcampus-client/src/test/java/edu/seu/vcampus/client/integration/LoginCourseSocketIntegrationTest.java
git commit -m "feat(course-ui): drop active enrollment immediately"
```

---

### Task 6: Replace Raw Course and Term Inputs with Structured Controls

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseEditorDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/TermEditorDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseFormValidation.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`

**Interfaces:**
- Consumes: existing create/update course and term commands.
- Produces: numeric course controls, date/time term controls in `Asia/Shanghai`, localized status choices, and specific validation messages.

- [ ] **Step 1: Write failing structured-control tests**

Add tests that open each dialog on the EDT and inspect accessible names:

```java
assertThat(component(dialog, "学分", JSpinner.class)).isNotNull();
assertThat(component(dialog, "总学时", JSpinner.class)).isNotNull();
assertThat(descendants(dialog).stream().filter(JTextField.class::isInstance)
        .map(JTextField.class::cast).map(field -> field.getAccessibleContext().getAccessibleName()))
        .doesNotContain("学分", "总学时", "开学日期", "结束日期", "选课开始", "选课结束", "退改补开始", "退改补结束");
assertThat(component(dialog, "学期状态", JComboBox.class).getItemAt(0).toString())
        .isEqualTo("计划中");
```

Submit invalid orderings and assert messages identify “结束日期必须晚于开学日期”, “选课结束必须晚于选课开始”, and “退改补结束必须晚于退改补开始”.

- [ ] **Step 2: Run RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am -Dtest=CourseUiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: dialogs still expose raw text fields.

- [ ] **Step 3: Add focused validation helpers**

```java
static void requireOrdered(LocalDate start, LocalDate end, String message) {
    if (!end.isAfter(start)) throw new IllegalArgumentException(message);
}

static void requireOrdered(Instant start, Instant end, String message) {
    if (!end.isAfter(start)) throw new IllegalArgumentException(message);
}
```

Keep the helper package-private and pure so its boundary tests need no Swing window.

- [ ] **Step 4: Convert course numeric fields**

Use `SpinnerNumberModel` with these exact normal-entry ranges:

```java
private final JSpinner credit = spinner(new BigDecimal("1.0"),
        new BigDecimal("0.5"), new BigDecimal("20.0"), new BigDecimal("0.5"), "学分");
private final JSpinner hours = spinner(32, 1, 1000, 1, "总学时");
```

Convert values to `BigDecimal`/`int` without parsing free text. Preserve course code/name trimming, description, active flag, and row version.

- [ ] **Step 5: Convert term dates and times**

Use `JSpinner` with `SpinnerDateModel`; date fields use `yyyy-MM-dd`, date-time fields use `yyyy-MM-dd HH:mm`. Convert through:

```java
private static JSpinner.DateEditor dateEditor(JSpinner spinner, String pattern) {
    JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, pattern);
    editor.getFormat().setTimeZone(TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai")));
    spinner.setEditor(editor);
    return editor;
}

private static Instant instant(JSpinner spinner) {
    return ((Date) spinner.getValue()).toInstant();
}

private static LocalDate date(JSpinner spinner) {
    return ((Date) spinner.getValue()).toInstant()
            .atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
}
```

Use a localized value object for status display:

```java
record StatusChoice(String code, String label) {
    @Override public String toString() { return label; }
}
```

New-term defaults are internally ordered; editing preserves exact existing values.

- [ ] **Step 6: Run GREEN and commit**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am -Dtest=CourseUiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
git add vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui \
        vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java
git commit -m "refactor(course-ui): structure course and term entry"
```

---

### Task 7: Add Reference Choices and a Structured Offering Schedule Editor

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingScheduleEditorPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingReferenceChoice.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingEditorDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiGateway.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseClientGateway.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiComposition.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserUiCoordinator.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`

**Interfaces:**
- Consumes: `CourseUiGateway.listTerms()`, `searchCatalog(CourseCatalogQuery)`, `UserClientService.searchUsers(UserSearchQuery)`.
- Produces: `CourseUiGateway.searchTeachers(String): CompletableFuture<PageResult<UserSummary>>`, `CourseClientGateway(CourseClientService, UserClientService)`, and `OfferingScheduleEditorPanel.scheduleInputs(): List<CreateOfferingCommand.ScheduleInput>`.

- [ ] **Step 1: Write failing gateway reference-data tests**

Add a client UI gateway test using mocked transports:

```java
gateway.searchTeachers("TEA").join();
assertThat(userQuery.get()).isEqualTo(new UserSearchQuery(
        "TEA", UserRole.TEACHER, AccountStatus.ACTIVE, 0, 100));
```

The preview gateway returns at least two `UserSummary` teachers so screenshots and dialogs do not need a live server.

- [ ] **Step 2: Write failing schedule-editor tests**

```java
@Test
void scheduleEditorCreatesStructuredRowsAndMapsThemWithoutCsvParsing() throws Exception {
    OfferingScheduleEditorPanel editor = onEdt(OfferingScheduleEditorPanel::new);
    SwingUtilities.invokeAndWait(() -> button(editor, "添加上课时间").doClick());
    assertThat(descendants(editor).stream().filter(JComboBox.class::isInstance)).isNotEmpty();
    assertThat(descendants(editor).stream().filter(JSpinner.class::isInstance)).hasSizeGreaterThanOrEqualTo(4);
    assertThat(editor.scheduleInputs()).containsExactly(
            new CreateOfferingCommand.ScheduleInput("MONDAY", 1, 2, 1, 16, "待定"));
}
```

Add exact row errors for start period after end period, start week after end week, blank room, and removal of the final row.

- [ ] **Step 3: Run RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am -Dtest=CourseUiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing teacher API/editor types fail.

- [ ] **Step 4: Implement teacher lookup through the user client**

```java
public CompletableFuture<PageResult<UserSummary>> searchTeachers(String keyword) {
    return users.searchUsers(new UserSearchQuery(
            keyword, UserRole.TEACHER, AccountStatus.ACTIVE, 0, 100));
}
```

The server remains unchanged: it already owns `USER_SEARCH` and requires administrator permission. Update production composition to pass the same `UserClientService` into `CourseClientGateway`; non-administrator workspaces never call teacher lookup.

- [ ] **Step 5: Implement schedule rows as focused components**

Each row owns a localized weekday combo, four bounded spinners, room field, and remove button. Map values without string parsing:

```java
CreateOfferingCommand.ScheduleInput toInput(int rowNumber) {
    int startP = number(startPeriod);
    int endP = number(endPeriod);
    int startW = number(startWeek);
    int endW = number(endWeek);
    if (startP > endP) throw new IllegalArgumentException("第 " + rowNumber + " 行：结束节次不能早于起始节次");
    if (startW > endW) throw new IllegalArgumentException("第 " + rowNumber + " 行：结束周不能早于起始周");
    String room = classroom.getText().trim();
    if (room.isEmpty()) throw new IllegalArgumentException("第 " + rowNumber + " 行：请输入教室");
    return new CreateOfferingCommand.ScheduleInput(day.code(), startP, endP, startW, endW, room);
}
```

Use period range 1–14 and week range 1–30. The initial row defaults to 周一, 1–2 节, 1–16 周, 教室“待定”. Editing converts every existing `ScheduleItem` to one row.

- [ ] **Step 6: Replace offering IDs and CSV text with guided choices**

Use:

- `JComboBox<OfferingReferenceChoice>` for terms, courses, and teachers;
- a course keyword field plus “查询课程” action backed by active catalog search;
- a teacher keyword field plus “查询教师” action backed by `searchTeachers`;
- localized status choice;
- `JSpinner` capacity with minimum `max(1, enrolledCount)` while editing;
- `OfferingScheduleEditorPanel` instead of the schedules `JTextArea`.

```java
record OfferingReferenceChoice(String id, String label) {
    @Override public String toString() { return label; }
}
```

Load terms, the selected/existing course, and teachers asynchronously. Disable save until all required choices have loaded. Preserve existing IDs by inserting their resolved choice even when not on the first search page.

- [ ] **Step 7: Add precise async and validation tests**

Cover:

- default current-term selection;
- display label while commands submit IDs;
- active-teacher filtering;
- save disabled during reference load;
- retry after one load failure;
- no UI mutation from a response after dialog disposal;
- capacity below enrolled count;
- exact row validation;
- server `COMMON_CONCURRENT_MODIFICATION` message asks to refresh;
- unknown `CourseClientException` includes safe message and trace ID when present.

Use incomplete `CompletableFuture` instances to prove each async state without sleeps.

- [ ] **Step 8: Run GREEN and commit**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am -Dtest=CourseUiTest,UserClientServiceTask6Test \
  -Dsurefire.failIfNoSpecifiedTests=false test
git add vcampus-client/src/main/java/edu/seu/vcampus/client/course \
        vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserUiCoordinator.java \
        vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java
git commit -m "feat(course-ui): guide administrator offering entry"
```

---

### Task 8: Update Demo Data, Documentation, and Visual Evidence

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/demo/IntegratedDemoServerMain.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/demo/IntegratedDemoServerMainTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiScreenshotGenerator.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/demo/DistributionDemoScriptsTest.java`
- Modify: `vcampus-distribution/scripts/start-client.sh`
- Modify: `vcampus-distribution/scripts/start-client.bat`
- Modify: `vcampus-distribution/scripts/start-server-with-data.sh`
- Modify: `vcampus-distribution/scripts/start-server-with-data.bat`
- Modify: `vcampus-distribution/scripts/reset-data.sh`
- Modify: `vcampus-distribution/scripts/reset-data.bat`
- Modify: `docs/course-runtime-integration.md`
- Create: `docs/course-user-management-demo-and-test-guide.md`
- Modify: `docs/ui-review/manifest.md`
- Generate: `docs/ui-review/course/integrated-login.png`
- Generate: `docs/ui-review/course/integrated-student-course.png`
- Generate: `docs/ui-review/course/integrated-admin-offering-editor.png`

**Interfaces:**
- Consumes: final production runtime, shared client, structured forms, Java 21 build.
- Produces: deterministic student/teacher/admin demo, two-process launchers, exact credentials/walkthrough, and three current visual review images.

- [ ] **Step 1: Write failing demo-data acceptance tests**

Extend `IntegratedDemoServerMainTest` to open the prepared Access database and assert:

```java
assertThat(loginIds(connection)).contains("DEMO_STUDENT", "DEMO_TEACHER", "DEMO_ADMIN");
assertThat(roleOf(connection, "DEMO_STUDENT")).isEqualTo("STUDENT");
assertThat(roleOf(connection, "DEMO_TEACHER")).isEqualTo("TEACHER");
assertThat(roleOf(connection, "DEMO_ADMIN")).isEqualTo("ADMIN");
assertThat(activeEnrollmentCount(connection, "demo-student")).isPositive();
assertThat(openOfferingCount(connection)).isGreaterThan(1);
```

Run prepare twice and assert IDs/counts remain stable.

- [ ] **Step 2: Run RED and seed missing scenarios**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-server -am -Dtest=IntegratedDemoServerMainTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Add deterministic current-term windows that include the demo clock, one active enrollment that can be dropped, one open unselected offering, teacher-owned schedules, and administrator permissions. Reuse the documented password for all three accounts and never store it outside the demo initializer/docs.

- [ ] **Step 3: Verify launcher contract**

`DistributionDemoScriptsTest` asserts only these user-facing entry points are documented:

```text
start-client.{sh,bat}
start-server-with-data.{sh,bat}
reset-data.{sh,bat}
```

The client launcher never resets/copies data. The server-with-data launcher initializes the demo database, and reset-data replaces only the known demo database path after confirmation/documented behavior.

- [ ] **Step 4: Write the complete Chinese demo/test guide**

The guide must include:

- branch/worktree path and why the main checkout cannot switch to an attached worktree branch;
- Java 21 prerequisite;
- server first, client second startup order;
- the exact three login IDs/passwords;
- role/tab expectations;
- normal-window enroll then immediate drop walkthrough;
- adjustment-only add/change check;
- administrator course, term, and structured offering entry walkthrough;
- database reset semantics;
- automated test commands and expected totals updated from the final run.

- [ ] **Step 5: Generate current screenshots through the real components**

Update `CourseUiScreenshotGenerator` so it renders the latest `LoginFrame`, a student `MainFrame` with embedded `CourseWorkspacePanel`, and the structured administrator `OfferingEditorDialog` at 1280×800 (dialog at its packed supported size). Use a deterministic preview gateway and wait for EDT work before painting.

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-client -am -DskipTests test-compile
/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java \
  -cp "vcampus-client/target/test-classes:vcampus-client/target/classes:vcampus-common/target/classes" \
  edu.seu.vcampus.client.course.ui.CourseUiScreenshotGenerator
```

Inspect every generated PNG for clipping, stale course-only sidebar entries, raw IDs/CSV input, and missing Chinese labels.

- [ ] **Step 6: Run demo/document tests and commit**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn \
  -pl vcampus-server,vcampus-client -am \
  -Dtest=IntegratedDemoServerMainTest,DistributionDemoScriptsTest,CourseDemoNetworkTest,LoginCourseSocketIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
git add vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/demo \
        vcampus-server/src/test/java/edu/seu/vcampus/server/bootstrap/demo \
        vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiScreenshotGenerator.java \
        vcampus-distribution docs
git commit -m "docs(demo): document unified course workflow"
```

---

### Task 9: Full Verification, Real Demo, Review, Package, and Push

**Files:**
- Verify: all tracked source and test files
- Rebuild: `vcampus-distribution/lib/vCampusClient.jar`
- Rebuild: `vcampus-distribution/lib/vCampusServer.jar`
- Update: `docs/course-user-management-demo-and-test-guide.md`
- Review: `git diff origin/course-user-management...HEAD`

**Interfaces:**
- Consumes: Tasks 1–8.
- Produces: passing full suite, real three-role runtime evidence, reviewed final artifacts, clean worktree, and pushed `origin/course-user-management`.

- [ ] **Step 1: Run the complete clean Java 21 test suite**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn clean test
```

Expected: all `vcampus-common`, `vcampus-server`, and `vcampus-client` tests pass. Record exact totals in the demo/test guide.

- [ ] **Step 2: Build and verify distribution artifacts**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn package
```

Copy/build distribution JARs only through the existing Maven distribution executions or repository build script. Verify JAR timestamps/checksums change and each launcher references an existing JAR/main class. Do not hand-edit binary artifacts.

- [ ] **Step 3: Start the real demo server and exercise all roles**

Run the server-with-data launcher in an isolated terminal, wait for its explicit listening message, then run protocol/UI checks with the shared client:

```text
Student: login → 课程中心 → enroll → 我的选课 → immediate drop → 我的课表 refresh
Teacher: login → 课程中心 → 教师课表; no student/admin mutation tabs
Administrator: login → 课程中心 → create/edit course → create/edit offering with choices and schedule rows → 选退记录
```

Confirm forbidden commands through the real socket, logout each role, and stop the server cleanly. Record the commands, observed results, and demo database path in the guide.

- [ ] **Step 4: Perform requirement-by-requirement code review**

```bash
git diff --check origin/course-user-management...HEAD
git diff --stat origin/course-user-management...HEAD
git log --oneline --decorate origin/course-user-management..HEAD
```

Review specifically for:

- extra global course navigation buttons;
- client-only authorization assumptions;
- widened late-add/change windows;
- missing ownership/version/count/audit handling;
- Swing socket calls on the EDT;
- stale async dialog/panel updates;
- raw term/course/teacher IDs or CSV schedule entry still exposed in the normal admin workflow;
- secrets or machine-specific absolute paths in tracked docs/scripts;
- stale generated JARs/screenshots.

Fix every finding with a reproducing test when behavior is affected, rerun the narrow test, and commit fixes as `fix: address final integration review`.

- [ ] **Step 5: Re-run final gates**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /opt/homebrew/bin/mvn clean test
git diff --check origin/course-user-management...HEAD
git status --short
```

Expected: `BUILD SUCCESS`, no whitespace errors, and only intentional tracked artifact updates before the final commit; after committing, the worktree is clean.

- [ ] **Step 6: Commit final evidence and push**

```bash
git add docs vcampus-distribution/lib
git commit -m "chore: finalize unified course demo evidence"
git push origin course-user-management
git status --short --branch
git rev-parse HEAD
git rev-parse origin/course-user-management
```

Expected: local and remote hashes match and status is clean.

---

## Execution Order and Review Gates

1. Task 1 is the merge foundation and must finish before any source implementation.
2. Tasks 2–3 establish the unified shell and must pass before UI feature work.
3. Tasks 4–5 are the complete drop behavior slice and must be reviewed together.
4. Tasks 6–7 are the administrator form slice and must be reviewed together.
5. Tasks 8–9 prove the real demo and delivery contract.

Each task ends with a focused green test run and commit. Do not combine red/green cycles across task boundaries, and do not begin packaging while any behavior test is failing.
