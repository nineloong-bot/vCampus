# Retake Quota, Administrator Enrollment, and Production-like Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every teaching class an independent five-seat retake quota, let course administrators place a student into a class when self-service quota is insufficient, and remove “测试” from user-visible bundled data.

**Architecture:** Keep self-service enrollment rules unchanged and add a dedicated administrator enrollment command whose server-side collaborator resolves the student through the student-module port, locks the student and offering, validates identity and duplicates, records an immutable adjustment audit, and treats administrator placement as an explicit quota override. Generate every Access database from the canonical schema and full-data generator, validate it in a temporary path, then replace the release database and synchronized generated artifacts.

**Tech Stack:** JDK 21, Maven, Swing, Java serialization protocol, UCanAccess, Microsoft Access, Python dataset generator.

**Spec:** User request in the 2026-09-14 Codex task and repository-wide `AGENTS.md`.

## Global Constraints

- Microsoft Access through UCanAccess is the only database.
- `vcampus-database/schema` and `vcampus-database/seed` remain canonical.
- Normal and retake enrollment counters remain independent.
- Every generated teaching class has retake capacity `5`.
- Administrator placement may exceed the self-service retake quota but must preserve database counter consistency, duplicate-course protection, student eligibility checks, locking, request deduplication, and adjustment auditing.
- User-visible generated names and descriptions must not contain `测试`.
- New Java files must stay below 200 physical lines and public APIs require JavaDoc.

---

### Task 1: Explicit five-seat retake quotas

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/courses.py`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessEnrollmentRepository.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingEditorDialog.java`
- Test: `vcampus-database/demo/full-test-data/tools/test_generation.py`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/repository/CourseRepositoryTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/OfferingEditorDialogTest.java`

**Interfaces:**
- Produces: one `tblCourseRetakeQuota(capacity=5,enrolledCount=<actual retakes>)` row per offering.
- Produces: new-offering UI default `retakeCapacity=5` without mirroring normal capacity.

- [ ] Add failing generator, repository fallback, and editor-default tests.
- [ ] Run the focused Python and Maven tests and confirm failures describe missing five-seat defaults.
- [ ] Generate explicit quota rows, change the repository fallback to five, and set the editor default to five.
- [ ] Re-run focused tests and confirm they pass.

### Task 2: Administrator enrollment transaction and protocol

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/AdminEnrollStudentCommand.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseStudentGateway.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/composition/CourseRuntimeAdapters.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentQueryPort.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentServiceImpl.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/AdminEnrollmentService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/handler/CourseHandlers.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/AdminEnrollmentServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/handler/CourseHandlersTest.java`

**Interfaces:**
- Consumes: `AdminEnrollStudentCommand(String studentNumber, String offeringId)` for administrator-added retake enrollment.
- Produces: `CourseService.adminEnrollStudent(AdminEnrollStudentCommand)` and protocol command `COURSE_ADMIN_ENROLL_STUDENT` returning `EnrollmentView`.
- Produces: `CourseStudentGateway.findActiveByStudentNumber(String)` returning `StudentEnrollmentEligibility` or `null`.

- [ ] Add failing DTO, handler, and service tests for role protection, student lookup, duplicate rejection, normal/retake counter selection, retake quota override, and audit row creation.
- [ ] Run focused tests and confirm they fail because the administrator command is absent.
- [ ] Implement the student port lookup and focused administrator transaction collaborator.
- [ ] Register the deduplicated administrator handler and expose the client-safe result.
- [ ] Re-run focused server/common tests and confirm they pass.

### Task 3: Administrator placement UI

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/AdminEnrollmentControl.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/service/CourseClientService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiGateway.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseClientGateway.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingManagementPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/AdminEnrollmentControlTest.java`

**Interfaces:**
- Consumes: selected `OfferingSummary` and a student number entered by the administrator.
- Produces: `CourseUiGateway.adminEnrollStudent(AdminEnrollStudentCommand)` and an “添加学生” action on teaching-class management.

- [ ] Add failing UI tests for command construction, success refresh, and validation.
- [ ] Run focused client tests and confirm failure because the action does not exist.
- [ ] Implement the small dialog, gateway call, and management-page action.
- [ ] Re-run focused client tests and confirm they pass.

### Task 4: Production-like Access dataset and delivery

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/courses.py`
- Modify: other full-data generator modules containing user-visible `测试` values.
- Generate: `vcampus-database/demo/full-test-data/tools/generated.sql`
- Generate: account/count snapshots and documentation affected by the dataset revision.
- Replace after validation: `vcampus-distribution/data/vCampus.accdb`
- Modify: `docs/testing/2026-09-14-测试账号启动命令与数据库内容.md`
- Generate: `vcampus-distribution/lib/vCampusClient.jar`
- Generate: `vcampus-distribution/lib/vCampusServer.jar`

**Interfaces:**
- Produces: a release Access database with 5-seat retake quotas for all offerings and no user-visible `测试` marker.

- [ ] Add failing generator assertions that all offerings have quota 5 and user-visible values omit `测试`.
- [ ] Replace synthetic display labels with realistic, deterministic names while keeping stable IDs and account credentials.
- [ ] Generate SQL and snapshots, build a new Access database in a temporary path, and run the dataset validator.
- [ ] Replace the release database only after validation succeeds, update counts/hash documentation, and package both JARs.
- [ ] Run `mvn test`, generator tests, database validation, `git diff --check`, line-count checks, and inspect the exact staged diff.
- [ ] Commit the requested files, pull/rebase if remote `main` advanced, and push `main` to `origin` without force.
