# College-Scoped Student Administration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move concrete student-record work to college administrators, reduce the student administrator to college-admin governance plus global transfer batches, hide transfer drafts, and remove the proposal stage.

**Architecture:** Resolve the authenticated college administrator's active department on the server and pass only that trusted scope into focused handlers and services. Repository queries include the department predicate, while mutations recheck the target inside the same lock and transaction as the write. Global transfer-batch commands remain `STUDENT_ADMIN`; application-specific and college-option commands become scoped `COLLEGE_ADMIN` operations.

**Tech Stack:** Java 21, Maven, Swing, socket command DTOs, UCanAccess/Microsoft Access, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-14-college-scoped-student-administration-design.md`

## Global Constraints

- Use Temurin JDK 21 at `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`.
- UCanAccess and Microsoft Access remain the only database stack.
- New Java files stay below 200 physical lines; public APIs get useful JavaDoc.
- Client-supplied department ids are filters, never authorization evidence.
- Writes retain request deduplication, optimistic locking, stable resource locks, and sanitized errors.
- Rebuild database artifacts in a temporary path from schema and seed before replacing distribution data.
- The starting branch has five unrelated client failures and one client error; introduce no new failures.

---

### Task 1: Remove proposal and hide administrator drafts

**Files:**
- Delete: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/GenerateMajorTransferProposalCommand.java`
- Delete: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/majortransfer/MajorTransferRankingView.java`
- Modify: transfer status/state machine, repository, service, handlers and both transfer UIs.
- Test: `MajorTransferStateMachineTest`, `MajorTransferCollegeQueryTest`, `MajorTransferStudentWorkflowTest`, `MajorTransferUiRegressionTest`.

**Produces:** `ASSESSED -> PENDING_EFFECTIVE`; no proposal route or DTO; every administrative application query excludes `DRAFT`.

- [ ] Write failing tests:

```java
assertThatCode(() -> MajorTransferStateMachine.requireTransition(ASSESSED, PENDING_EFFECTIVE))
        .doesNotThrowAnyException();
assertThat(service.listApplications(query))
        .noneMatch(app -> app.status() == MajorTransferStatus.DRAFT);
```

- [ ] Run them and confirm failure:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -pl vcampus-common,vcampus-server -am -Dtest=MajorTransferStateMachineTest,MajorTransferCollegeQueryTest,MajorTransferStudentWorkflowTest test
```

- [ ] Remove `PROPOSED`, ranking/generation APIs, route and UI. Add `applicationStatus <> 'DRAFT'` to both global and college repository queries. Make finalization require `ASSESSED` and write `PENDING_EFFECTIVE`.
- [ ] Run the focused common/server/client tests.
- [ ] Commit with `refactor: remove major transfer proposal stage`.

### Task 2: Build trusted college scope and scope core student records

**Files:**
- Modify: `StudentCollegeScopeAuthorizationService.java`, `StudentRepository.java`, `StudentService.java`, `StudentServiceImpl.java` and `StudentHandlers.java`.
- Create focused handler collaborators when splitting the existing 290-line handler.
- Test: scope authorization, handler authorization and student search privacy tests.

**Produces:**

```java
String requireActiveDepartment(String administratorUserId);
void requireStudentAccess(Connection connection, String departmentId, String studentId);
PageResult<StudentSummary> searchStudents(StudentSearchQuery query, String trustedDepartmentId);
```

- [ ] Add failing same-college, cross-college, forged-filter and list-filter tests.
- [ ] Run `StudentCollegeScopeAuthorizationServiceTest,StudentHandlersTest,StudentSearchPrivacyTest` and confirm college access is missing/global.
- [ ] Resolve exactly one active binding and add repository SQL predicates through class → major → department.
- [ ] Authorize reads/writes only for `COLLEGE_ADMIN`, checking direct target ids. Remove concrete `STUDENT_ADMIN` access while preserving student self-service and teacher privacy.
- [ ] Re-run focused tests and commit with `feat: scope student administration by college`.

### Task 3: Scope admission, profiles, organizations, plans and grades

**Files:**
- Modify the student/profile handler collaborators, `TrainingPlanHandlers.java`, organization/profile/plan/grade services and their repositories.
- Test the matching service and handler suites under `vcampus-server/src/test/java/edu/seu/vcampus/server/student`.

**Consumes:** trusted department from Task 2.

- [ ] Add failing tests that reject another college's admission destination, student/profile, major/class, plan and grade while allowing same-college targets:

```java
assertThatThrownBy(() -> plans.savePlan(otherCollegePlan, actor, "department-1"))
        .hasMessage("COMMON_FORBIDDEN");
assertThatThrownBy(() -> grades.recordGrade(otherCollegeGrade, actor, "department-1"))
        .hasMessage("COMMON_FORBIDDEN");
```

- [ ] Run the new tests and confirm current global access.
- [ ] Add transaction-aware `requireMajorAccess`, `requireClassAccess` and `requirePlanAccess`. Add department predicates to profile-review and plan searches.
- [ ] For writes, resolve the target again inside the existing transaction and lock order. College admins may maintain majors/classes but never departments.
- [ ] Run all student-domain tests and commit with `feat: delegate college student workflows`.

### Task 4: Complete scoped transfer ownership

**Files:**
- Modify `MajorTransferCollegeAuthorizationService.java`, transfer handlers/service/repository and their role/query tests.

**Command matrix:**
- `STUDENT_ADMIN`: save/list global batches only.
- Target `COLLEGE_ADMIN`: save/list own options, record/import scores, finalize, execute and retry.
- Related `COLLEGE_ADMIN`: read submitted non-draft applications if source or target matches.
- Source `COLLEGE_ADMIN`: source approval.
- No concrete application access for `STUDENT_ADMIN`.

- [ ] Add a failing role-command matrix including list/detail/attachment, forged option ids and execute.
- [ ] Run `MajorTransferRoleAuthorizationTest,MajorTransferCollegeQueryTest`.
- [ ] Replace generic admin helpers with explicit global-batch, related-read, source-write and target-write guards.
- [ ] Recheck option/application target college inside score/import/finalize/execute transactions.
- [ ] Run all `*MajorTransfer*Test` tests and commit with `feat: assign transfer processing to target colleges`.

### Task 5: Expose college-administrator governance

**Files:**
- Create immutable common `StudentCollegeAdministratorView` and `StudentCollegeAdministrationSnapshot`.
- Create `StudentCollegeAdministrationHandlers.java` below 200 lines.
- Modify governance repository/service and `ServerMain.java`.
- Test service, handler authorization, deduplication, last-admin protection and session revocation.

**Commands:** `STUDENT_COLLEGE_ADMIN_SEARCH`, `STUDENT_COLLEGE_ADMIN_ASSIGN`, `STUDENT_COLLEGE_ADMIN_TRANSFER`, `STUDENT_COLLEGE_ADMIN_DEACTIVATE`.

- [ ] Write failing list and handler role tests:

```java
assertThat(service.list().administrators()).extracting("departmentId")
        .contains("department-1");
assertForbidden(collegeAdmin, "STUDENT_COLLEGE_ADMIN_TRANSFER", command);
assertAllowed(studentAdmin, "STUDENT_COLLEGE_ADMIN_TRANSFER", command);
```

- [ ] Run governance tests and confirm list/handlers are absent.
- [ ] Implement sanitized snapshot rows and role-locked, deduplicated handlers.
- [ ] Register governance composition in `ServerMain`; preserve audits, lock ordering, optimistic versions and session revocation.
- [ ] Re-run tests and commit with `feat: add college administrator governance`.

### Task 6: Split administrator client workspaces

**Files:**
- Create `CollegeAdministratorManagementPanel.java` below 200 lines.
- Split the 788-line transfer panel into focused batch and college-processing panels, each below 200 lines.
- Modify `StudentClientService.java` and `StudentModulePageFactory.java`.
- Test page tabs, governance interactions and transfer role actions.

- [ ] Write failing UI tests:

```java
assertThat(tabTitles(studentAdminPage)).containsExactly("学院管理员管理", "转专业批次");
assertThat(tabTitles(collegeAdminPage)).contains("学生查询", "组织管理", "资料审核",
        "转专业管理", "培养方案管理", "成绩管理");
```

- [ ] Run `StudentModulePageFactoryTest,MajorTransferRoleUiTest,CollegeAdministratorManagementPanelTest`.
- [ ] Add client methods for all four governance commands.
- [ ] Wire student-admin and college-admin tab sets. Remove every proposal control/label and ensure college pages use server-scoped data.
- [ ] Run client student UI tests and commit with `feat: split student administration workspaces`.

### Task 7: Align seed, Access database and documentation

**Files:**
- Modify `vcampus-database/seed/010_roles_permissions.sql` and `025_major_transfer_demo.sql`.
- Modify affected database tests/snapshots and distribution documentation.
- Rebuild and replace `vcampus-distribution/data/vCampus.accdb` only after temporary validation.
- Rebuild distribution JARs only if the repository's release workflow requires them.

- [ ] Add failing seed assertions: `STUDENT_ADMIN` has college-governance/global-batch permission only; no seeded `PROPOSED` or proposal review remains.
- [ ] Run `HierarchicalAdministrationSchemaTest,DemoDistributionAccountsTest`.
- [ ] Update permissions and convert seeded `PROPOSED` applications to `ASSESSED`; remove proposal-stage records.
- [ ] Build the Access database in a temporary path, validate roles/counts/statuses, then replace the distribution database and synchronize docs/JARs.
- [ ] Commit with `chore: align college administration distribution`.

### Task 8: Final verification and diff audit

- [ ] Search for forbidden remnants:

```bash
rg -n "PROPOSED|MAJOR_TRANSFER_GENERATE_PROPOSAL|GenerateMajorTransferProposalCommand|MajorTransferRankingView" vcampus-common vcampus-server vcampus-client vcampus-database
```

Expected: no production/schema/seed matches.

- [ ] Check new/modified production Java file sizes and `git diff --check`.
- [ ] Run focused college/transfer/student role tests with JDK 21.
- [ ] Run full `mvn test` with loopback binding permitted. Compare any failures with the recorded six client baseline problems.
- [ ] Review `git diff origin/nineloong...HEAD` for secrets, generated junk, missing JavaDoc and unintended main-worktree changes.
- [ ] Commit verification-only corrections with `test: verify college-scoped student administration`.
