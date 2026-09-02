# Course and User Management vCampus Integration Design

Date: 2026-09-01

Branch: `course-user-management`

Status: Approved design, pending implementation

## 1. Objective

Integrate the existing course-selection module with the latest authenticated vCampus client shell so that login, session handling, permissions, navigation, and visual language are consistent with the team's user-management, Shop, and Library work.

The implementation must also:

- allow a student to drop an active enrollment during either the normal enrollment window or the adjustment window;
- keep late-add and change-offering operations restricted to the adjustment window;
- replace administrator ID- and format-heavy forms with guided structured controls;
- preserve server-side authorization, optimistic locking, audit history, and concurrency safety;
- provide automated verification and a runnable three-role demo.

## 2. Evidence Reviewed

The design is based on the following remote branches and artifacts fetched on 2026-09-01:

- `origin/feat/user-management`: latest split login screen, `UserUiCoordinator`, account/logout flow, shared `MainFrame`, identity header, permission navigation, application status bar, and theme installer.
- `origin/SHOP`: one top-level `shop` destination with a Shop-owned internal card workspace and an authenticated demo.
- `origin/user-library`: one top-level `library` destination with permission-filtered internal tabs.
- `origin/nineloong`: one top-level student module with role-dependent content inside the module.
- `origin/as811`: shared-shell screenshots and module architecture diagram.

The current branch was also inspected. Its course pages are currently registered directly as global navigation destinations, its login/user integration predates the latest user-management work, normal enrollment has no drop command, and administrator forms require raw IDs, comma-delimited schedules, and strictly formatted date-time text.

## 3. Chosen Integration Approach

### 3.1 Decision

Use the latest user-management shell as the shared application foundation, resolve its conflicts deliberately, and embed course selection as one module-owned workspace under the top-level `course` destination.

Do not merge Shop or Library wholesale. Their code is a reference for module boundaries and navigation ownership, avoiding unrelated features and conflict surface.

### 3.2 Alternatives Rejected

1. **Copy only the visual styles into the current course-specific `MainFrame`.** This would preserve duplicate shell logic and continue to make course subpages global navigation items.
2. **Keep a standalone course window after login.** This violates the requirement that course selection be embedded in vCampus and prevents modules from sharing identity, connection status, account, and logout behavior.
3. **Merge all feature branches.** This would expand scope to unfinished unrelated modules and make ownership/conflict resolution ambiguous.

## 4. Shared Application Shell

The final client uses the shared vCampus window dimensions, palette, typography, identity header, connection status, footer, and permission navigation from the latest user-management branch.

The global left navigation contains only these stable module destinations:

1. 学籍档案 (`student`)
2. 课程中心 (`course`)
3. 图书借阅 (`library`)
4. 校园商城 (`shop`)
5. 账户设置 (`account`)

Course-owned pages must never add buttons to this global navigation.

`UserUiCoordinator` remains the owner of the authentication lifecycle:

- show login;
- require initial-password change when necessary;
- create the authenticated shell only after unrestricted login succeeds;
- install the account page and the course workspace using the same authenticated connection;
- clear the session and return to login on logout, password-change completion, account disablement, or session expiry;
- ensure terminal authentication handoff occurs once.

The logged-in `LoginResult` is the source of the user identity, role, and permission set. Client-side filtering controls which course tabs are created, while every server command continues to enforce the session and role independently.

## 5. Course Workspace

Create one `CourseWorkspacePanel` registered as `page.course`. It owns a `JTabbedPane`, follows the Library workspace pattern, and refreshes the selected page when it becomes visible.

Tabs are created by role:

| Role | Tabs |
| --- | --- |
| Student | 教学班查询, 我的选课, 我的课表, 退改补, 重修 |
| Teacher | 教学班查询, 教师课表 |
| Administrator | 学期管理, 课程目录, 教学班管理, 修读结果导入, 选退记录 |

The existing course panels remain module-internal components. `CourseUiComposition` changes from a map of global page IDs into a factory for one permission-filtered workspace.

The first useful tab is selected automatically. Only the selected tab refreshes after navigation, avoiding unnecessary parallel server calls. Async results from hidden or removed panels remain guarded from updating stale Swing state.

## 6. Enrollment and Drop Rules

### 6.1 Server-authoritative window rules

| Operation | Normal enrollment window | Adjustment window | Outside both windows |
| --- | --- | --- | --- |
| Normal enroll | Allowed | Not allowed through normal-enroll command | Rejected |
| Drop active enrollment | Allowed | Allowed | Rejected |
| Late add | Rejected | Allowed | Rejected |
| Change offering | Rejected | Allowed | Rejected |
| Retake enroll | Follows normal enrollment policy | Not widened | Rejected |

Add a domain policy method that accepts a drop only when the term is not closed and the server time is inside either configured window. The lower bound remains inclusive and the upper bound exclusive.

### 6.2 Protocol and compatibility

Introduce a semantically general `COURSE_DROP` command using the existing `DropCommand` payload. The client `drop` method calls this command.

Keep `COURSE_ADJUSTMENT_DROP` registered temporarily as a compatibility alias, but route it through the same general drop workflow. No client UI should depend on the legacy command after this change.

### 6.3 Transaction and concurrency behavior

The drop workflow preserves the existing safeguards:

- validate authenticated student session and enrollment eligibility;
- lock the student and source offering in deterministic order;
- revalidate identity and eligibility after acquiring locks;
- verify ownership, active status, and expected row version;
- evaluate the term window using server time;
- mark the enrollment `DROPPED` rather than deleting history;
- set `droppedAt`, increment the row version, and decrement enrolled count atomically;
- retain success and failed-business-rule audit records.

The administrator-facing label changes from “退改补审计” to “选退记录” because normal-window drops are now included. Existing storage can retain the `DROP` adjustment type without a schema migration.

### 6.4 Student UI behavior

`MyEnrollmentPanel` stores its row-to-`EnrollmentView` mapping, exposes “退选所选课程” for active rows, and displays the current term phase/window.

Before submission, a confirmation dialog shows the selected course/offering and explains that the operation releases the seat immediately. On success, the panel reloads; the shared course workspace also invalidates the schedule and offering views so they refresh when selected. Dropped history remains visible but cannot be dropped again.

The `退改补` tab continues to own late-add and change-offering actions. Its drop action may delegate to the same general command, and it is enabled only when the adjustment phase is open. This preserves a complete adjustment workflow without incorrectly preventing normal-phase drop from “我的选课”.

## 7. Administrator Form Redesign

The redesign optimizes high-frequency single-record entry. Excel/CSV bulk import is explicitly outside this iteration.

### 7.1 Course editor

- Keep course code and name as normal text inputs with whitespace trimming.
- Use a numeric spinner/model for credits and total hours with valid ranges and increments.
- Keep description multiline and optional.
- Display validation next to the relevant input and keep a summary near the save action.
- Preserve edit row-version handling.

### 7.2 Term editor

- Keep term code/name as text, with suggested code/name values for a new term.
- Replace the six format-sensitive date/date-time text fields with date/time spinners using the Asia/Shanghai campus zone.
- Display status values in Chinese while mapping to protocol enum strings internally.
- Enforce start/end ordering and show a precise field-level message before submitting.
- Preserve the server as the authoritative validator.

### 7.3 Offering editor

Replace raw IDs with loaded choices:

- term: choose from `listTerms`, defaulting to the current term;
- course: searchable active-course choice backed by catalog search;
- teacher: searchable active-teacher choice backed by the existing paged `USER_SEARCH` API, showing login ID while submitting user ID;
- status: Chinese display values mapped internally to `DRAFT`, `OPEN`, `CLOSED`, and `CANCELLED`;
- capacity: bounded numeric spinner that cannot be set below the current enrollment count while editing.

Replace the comma-delimited schedule text area with a row editor. Each row contains:

- weekday combo box;
- start/end period numeric controls;
- start/end week numeric controls;
- room text field;
- remove action.

An “添加上课时间” action creates a sensible default row. Validation identifies the exact row and field, checks period/week ordering, and requires at least one row.

Choice loading is asynchronous. Save remains disabled until required reference data is available, and load failures show retry guidance without freezing the EDT.

### 7.4 Error presentation

Client-side validation messages are specific and remain visible until corrected. Server errors are mapped by stable error code where possible; unknown failures preserve a safe readable message and trace ID rather than always claiming an optimistic-lock conflict.

Optimistic-lock failures explicitly ask the administrator to refresh and review the latest record before retrying.

## 8. Services and Data Flow

`CourseUiGateway` remains the narrow async seam for course pages and gains only the reference-data operations required by course forms. Teacher lookup is supplied through a small adapter over `UserClientService`, rather than coupling course server code to user repositories.

The authenticated client composition creates:

- one shared `ClientConnection`;
- one `UserClientService`;
- one `CourseClientService`;
- one general course UI gateway that can query course data and administrator teacher choices;
- one `CourseWorkspacePanel` for the logged-in role.

No Swing event handler performs blocking socket work. All completion callbacks return to the EDT, and every panel/dialog guards late results after disposal or navigation.

## 9. Demo and Distribution

The deliverable remains a two-process demo:

- one shared client launcher;
- one `server-with-demo-data` launcher that creates/resets the authenticated course demo database as documented.

The demo database provides at least:

- a student account with active enrollments and offerings available for enrollment/drop;
- a teacher account with teacher-visible tabs;
- an administrator account with course, term, offering, audit, and user-search permissions.

Documentation lists the exact accounts/passwords, startup order, database reset behavior, role-by-role walkthrough, and expected results. It also explains that the client is shared and demo data belongs to the server.

## 10. Test Strategy

Implementation follows test-driven development.

### 10.1 Domain and service tests

- drop window accepts the normal enrollment interval;
- drop window accepts the adjustment interval;
- boundaries are inclusive/exclusive as documented;
- closed terms and times outside both windows reject;
- late add and change remain adjustment-only;
- successful normal-window drop updates enrollment, count, version, timestamp, and audit atomically;
- ownership, stale version, invalid session, eligibility drift, and concurrency behavior remain protected;
- both command names route through the same rule while `COURSE_DROP` is the primary contract.

### 10.2 Client tests

- global navigation contains only the five vCampus modules;
- course is one registered top-level destination;
- role-specific internal tabs are correct;
- login, initial-password change, logout, session-expiry handoff, and account page remain functional;
- normal-phase active enrollment can be dropped from “我的选课”;
- dropped rows cannot be resubmitted;
- adjustment add/change availability is unchanged;
- administrator fields are structured controls rather than raw ID/CSV/date-format entry;
- choice loading, validation, async failure, stale result, and optimistic-lock messages are covered.

### 10.3 End-to-end and visual verification

- run the complete Maven test suite using Java 21;
- start a real demo server and client against an isolated demo database;
- log in with student, teacher, and administrator accounts;
- verify navigation/tabs and representative authorized/forbidden operations;
- perform a normal-window enroll then immediate drop and verify capacity restoration;
- create/edit a course and offering using structured controls;
- capture login, student course center, and administrator offering editor screenshots at the supported 1280×800 review size;
- perform a final code review and reconcile every finding before completion.

## 11. Documentation and Git Delivery

Update the integration/demo documentation under `docs/` with:

- branch and worktree explanation where relevant;
- shared-shell architecture and role permissions;
- drop-window business rule;
- administrator form behavior;
- demo accounts and walkthrough;
- automated and manual test evidence;
- known environmental requirement that Maven must run with Java 21.

After verification, commit all source, tests, docs, and intentional distribution artifacts on `course-user-management`, ensure the worktree is clean, and push the branch to `origin/course-user-management`.

## 12. Acceptance Criteria

The work is complete only when all of the following are evidenced:

1. Login uses the team's latest shared vCampus design and session lifecycle.
2. The global left navigation remains the five-module vCampus navigation for all roles.
3. Course selection is one embedded module with role-filtered internal tabs.
4. A student can drop an active enrollment during normal enrollment and adjustment, but not outside both windows.
5. Late-add and change remain adjustment-only.
6. Administrator course/term/offering entry no longer requires raw IDs, comma-delimited schedules, or manually formatted date-time strings for normal use.
7. Server-side authorization, optimistic locking, audit, and concurrency invariants remain enforced.
8. Automated tests and the real three-role demo pass on Java 21.
9. Demo/test documentation and credentials are complete and usable.
10. Final code review findings are resolved, changes are committed, and `course-user-management` is pushed.
