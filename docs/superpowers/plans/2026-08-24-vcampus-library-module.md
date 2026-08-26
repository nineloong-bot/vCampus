# Virtual Campus Library Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver searchable books/copies, configurable borrowing policies, concurrent borrow, return, renewal, overdue maintenance, handlers, and nine Swing pages.

**Architecture:** The module separates book metadata from physical copies and writes loan/copy states in one transaction. Borrow operations lock both user and copy; return and renewal use loan/copy locks plus optimistic versions.

**Tech Stack:** JDK 21, Maven, Swing, UCanAccess, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-08-24-vcampus-library-module-design.md`, `docs/superpowers/specs/2026-08-24-vcampus-overall-architecture-design.md`, and `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`

## Global Constraints

- Complete foundation and user plans first.
- Do not implement fines or monetary payment.
- Preserve all `LIBRARY_*` commands and role-specific policy values.
- Effective active loans include `ACTIVE` and dynamically overdue records.
- Borrow locks `LIBRARY_USER:<userId>` and `BOOK_COPY:<copyId>`.
- Borrow acquires `LIBRARY_USER` then `BOOK_COPY`; return acquires `LOAN` then `BOOK_COPY`; renewal acquires `LIBRARY_USER` then `LOAN`.
- Keep all loan history; never physically delete loan rows.
- Complete the shared UI design-system plan before Task 4; library pages must use its fixed shell, templates, components, page states, async lifecycle guards, and screenshot merge gate.

---

### Task 1: Library Schema, Catalog, and Policy Repositories

**Files:**
- Create: `vcampus-database/schema/040_library.sql`
- Create: `vcampus-database/seed/040_library_policy.sql`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/library/repository/BookRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/library/repository/LoanRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/library/repository/LibraryPolicyRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/library/repository/LibraryRepositoryTest.java`

**Interfaces:**
- Consumes: transactions.
- Produces: book/copy CRUD, loan persistence, student/teacher policy lookup.

- [ ] **Step 1: Write ISBN/barcode uniqueness and policy tests**

```java
@Test
void loadsDifferentPoliciesForStudentAndTeacher() {
    assertThat(policies.require(STUDENT)).extracting(LoanPolicy::maxActiveLoans,
            LoanPolicy::loanDays).containsExactly(5, 30);
    assertThat(policies.require(TEACHER)).extracting(LoanPolicy::maxActiveLoans,
            LoanPolicy::loanDays).containsExactly(10, 60);
}
```

- [ ] **Step 2: Run repository tests**

Run: `mvn -pl vcampus-server -am -Dtest=LibraryRepositoryTest test`

Expected: FAIL before schema/repositories exist.

- [ ] **Step 3: Implement all four tables and indexes**

```java
public interface LoanRepository {
    long countEffectiveLoans(Connection c, String userId, Instant now);
    boolean hasOverdueLoan(Connection c, String userId, Instant now);
    Loan insert(Connection c, Loan loan);
    void update(Connection c, Loan loan, long expectedVersion);
}
```

- [ ] **Step 4: Run Access integration tests**

Run: `mvn -pl vcampus-server -am -Dtest=LibraryRepositoryTest test`

Expected: PASS for uniqueness, indexes, policies, versions, and dynamic overdue queries.

- [ ] **Step 5: Commit persistence**

```bash
git add vcampus-database/schema/040_library.sql vcampus-database/seed/040_library_policy.sql vcampus-server/src/main/java/edu/seu/vcampus/server/library vcampus-server/src/test
git commit -m "feat(library): add library persistence"
```

### Task 2: Concurrent Borrow Service

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/library/BorrowBookCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/library/LoanView.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/library/service/LibraryServiceImpl.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/library/service/LibraryService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/library/service/ConcurrentBorrowTest.java`

**Interfaces:**
- Consumes: authorization identity, policy/repositories, clock, locks, transactions.
- Produces: `LoanView borrow(String sessionToken, BorrowBookCommand)`.

- [ ] **Step 1: Write same-copy and limit-race tests**

```java
@Test
void oneOfTwentyBorrowersGetsTheCopy() throws Exception {
    seedAvailableCopy("copy-1");
    List<Outcome<LoanView>> outcomes = concurrentlyWithDistinctUsers(20,
            token -> service.borrow(token, new BorrowBookCommand("copy-1")));
    assertThat(outcomes.stream().filter(Outcome::isSuccess)).hasSize(1);
    assertThat(copies.require("copy-1").status()).isEqualTo(BORROWED);
    assertThat(loans.activeForCopy("copy-1")).hasSize(1);
}
```

- [ ] **Step 2: Run borrow tests**

Run: `mvn -pl vcampus-server -am -Dtest=ConcurrentBorrowTest test`

Expected: FAIL before borrow service exists.

- [ ] **Step 3: Implement user+copy locked borrow**

```java
return locks.withLocks(List.of(key("LIBRARY_USER", userId), key("BOOK_COPY", copyId)),
        () -> transactions.inTransaction(c -> {
            requireNoOverdue(c, userId);
            requireBelowLimit(c, userId, role);
            BookCopy copy = books.requireAvailableCopy(c, copyId);
            Loan loan = loans.insert(c, Loan.open(copyId, userId, dueAt(role)));
            books.markBorrowed(c, copy, copy.rowVersion());
            return mapper.toView(loan);
        }));
```

- [ ] **Step 4: Run borrow unit/integration/concurrency tests**

Run: `mvn -pl vcampus-server -am -Dtest=ConcurrentBorrowTest,LibraryBorrowServiceTest test`

Expected: PASS for 20-copy race, same-user limit race, overdue block, and rollback consistency.

- [ ] **Step 5: Commit borrowing**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/library vcampus-server/src/main/java/edu/seu/vcampus/server/library vcampus-server/src/test
git commit -m "feat(library): add concurrency-safe borrowing"
```

### Task 3: Return, Renewal, and Overdue Maintenance

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/library/ReturnBookCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/library/RenewLoanCommand.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/library/service/OverdueMaintenanceJob.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/library/service/ReturnRenewTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/library/service/OverdueMaintenanceJobTest.java`

**Interfaces:**
- Consumes: LibraryServiceImpl and clock.
- Produces: `returnBook`, `renew`, and idempotent overdue status refresh.

- [ ] **Step 1: Write duplicate-return, renewal-limit, and overdue tests**

```java
@Test
void duplicateReturnDoesNotChangeCopyTwice() {
    Loan loan = seedActiveLoan("copy-1", "user-1");
    service.returnBook(token, new ReturnBookCommand(loan.loanId(), 0));
    assertThatThrownBy(() -> service.returnBook(token,
            new ReturnBookCommand(loan.loanId(), 1)))
            .isInstanceOf(LoanAlreadyReturnedException.class);
    assertThat(copies.require("copy-1").status()).isEqualTo(AVAILABLE);
}
```

- [ ] **Step 2: Run lifecycle tests**

Run: `mvn -pl vcampus-server -am -Dtest=ReturnRenewTest,OverdueMaintenanceJobTest test`

Expected: FAIL before lifecycle methods/job exist.

- [ ] **Step 3: Implement atomic return, policy renewal, and dynamic overdue update**

```java
Loan renewed = loan.renew(clock.instant().plus(policy.renewalDays(), DAYS));
loans.update(connection, renewed, command.expectedVersion());
```

Return uses `List.of(key("LOAN", loanId), key("BOOK_COPY", copyId))`; renewal uses `List.of(key("LIBRARY_USER", userId), key("LOAN", loanId))`. The maintenance job updates only rows currently `ACTIVE` with `dueAt < now`; rerunning it must update zero additional rows.

- [ ] **Step 4: Run lifecycle verification**

Run: `mvn -pl vcampus-server -am -Dtest=ReturnRenewTest,OverdueMaintenanceJobTest,ConcurrentReturnTest test`

Expected: PASS for versions, ownership, duplicate returns, limits, overdue, and idempotent maintenance.

- [ ] **Step 5: Commit lifecycle behavior**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/library vcampus-server/src/main/java/edu/seu/vcampus/server/library vcampus-server/src/test
git commit -m "feat(library): add return renewal and overdue flows"
```

### Task 4: Handlers, Nine UI-Spec-Compliant Swing Pages, and Acceptance

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/library/handler/LibraryHandlers.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/library/UpdateLibraryPolicyCommand.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/service/LibraryClientService.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/BookSearchPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/BookDetailPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/CurrentLoansPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/LoanHistoryPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/LoanActionDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/BookManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/CopyManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/LoanAdminPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/LibraryPolicyPanel.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/library/handler/LibraryHandlersTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/library/LibraryUiTest.java`
- Modify: `docs/ui-review/manifest.md`

**Interfaces:**
- Consumes: router, authorization, LibraryService, async client.
- Produces: all library commands and nine pages composed from the shared UI design system.

- [ ] **Step 1: Write permission and UI-state tests**

```java
@Test
void overduePanelDisablesBorrowAndShowsDueDate() {
    BookDetailRobot robot = launchDetail(overdueUserClient());
    assertThat(robot.borrowEnabled()).isFalse();
    assertThat(robot.warning()).contains("逾期");
}

@Test
void libraryPagesPassSharedUiAndAsyncLifecycleAudit() {
    UiAuditResult audit = UiComplianceAudit.inspect(libraryPages());
    assertThat(audit.pagesWithoutTemplate()).isEmpty();
    assertThat(audit.pagesMissingRequiredStates()).isEmpty();
    assertThat(audit.privateThemeClasses()).isEmpty();
    assertThat(audit.inaccessibleControls()).isEmpty();
    assertThat(audit.staleOrDisposedAsyncUpdates()).isEmpty();
}
```

- [ ] **Step 2: Run handler/UI tests**

Run: `mvn -pl vcampus-server,vcampus-client -am -Dtest=LibraryHandlersTest,LibraryUiTest test`

Expected: FAIL before handlers/pages exist.

- [ ] **Step 3: Register exact commands and implement pages asynchronously**

```java
router.register("LIBRARY_BORROW", authenticatedHandler(
        BorrowBookCommand.class, service::borrow));
router.register("LIBRARY_UPDATE_POLICY", adminHandler(
        UpdateLibraryPolicyCommand.class, service::updatePolicy));
```

Map book search, current loans, and loan history to the query-list template; map `BookDetailPanel` to the detail template; map book, copy, loan, and policy administration to the management template; and implement `LoanActionDialog` with the shared dialog structure. Use shared tables/pagination/status labels, all required states, visible focus, latest-request/disposal guards, and actionable Chinese feedback that never exposes internal errors.

- [ ] **Step 4: Run full library verification**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am verify`

Expected: PASS for 20 concurrent borrowers, limits, overdue, return/renewal, permissions, UI design-system compliance at required sizes/scaling, screenshot manifest entries, all UI states, and no fine/payment code.

- [ ] **Step 5: Commit the completed module**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/library vcampus-server/src/main/java/edu/seu/vcampus/server/library vcampus-client/src/main/java/edu/seu/vcampus/client/library vcampus-server/src/test vcampus-client/src/test docs/ui-review/manifest.md
git commit -m "feat(library): complete library module"
```
