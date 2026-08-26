# Task 3 report — Normal Enrollment and Capacity Concurrency

## Implementation

- Added serializable `EnrollCommand` and `EnrollmentView` records. Both use the
  baseline `@Serial`/`serialVersionUID = 1L` convention; the view contains only
  enrollment-facing fields and no persistence entity.
- Added the focused `CourseService.enroll` API and a concurrency-safe
  implementation. It resolves the authenticated student, acquires exactly
  `STUDENT:<studentId>` and then `OFFERING:<offeringId>`, and performs one Access
  transaction while both application locks remain held.
- The transaction repeats session/role and ACTIVE eligibility checks, then checks
  offering `OPEN`, term/window state at the injected `Clock` instant, same-course
  active enrollment within the term, three-dimensional schedule conflicts, and
  capacity. It creates or reactivates a retained enrollment as `NORMAL/ACTIVE` and
  increments `enrolledCount` in that same transaction.
- Added course-owned gateway interfaces and minimal records for session identity and
  student eligibility. They do not import user/student packages or access another
  module's repositories, and can later be adapted directly to the planned
  `AuthorizationPort` and `StudentQueryPort`.
- Added typed rule exceptions with stable codes and safe messages for forbidden,
  ineligible, full, duplicate, and schedule-conflict outcomes. Existing
  `EnrollmentClosedException` supplies `COURSE_ENROLLMENT_NOT_OPEN` for offering or
  term/window closure.

Commit: `c656080 feat(course): add concurrency-safe enrollment`

## Files

- `vcampus-common/src/main/java/edu/seu/vcampus/common/course/EnrollCommand.java`
- `vcampus-common/src/main/java/edu/seu/vcampus/common/course/EnrollmentView.java`
- `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/`
  - `CourseService.java`, `CourseServiceImpl.java`
  - `CourseAuthorizationGateway.java`, `CourseSessionIdentity.java`
  - `CourseStudentGateway.java`, `StudentEnrollmentEligibility.java`
- `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/`
  - `CourseForbiddenException.java`
  - `StudentIneligibleException.java`
  - `OfferingFullException.java`
  - `DuplicateEnrollmentException.java`
  - `ScheduleConflictException.java`
- `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/`
  - `ConcurrentEnrollmentTest.java`
  - `CourseEnrollmentServiceTest.java`

## TDD evidence

### RED 1 — enrollment surface absent

Command:

```text
/private/tmp/vcampus-maven/maven/3.9.16/libexec/bin/mvn -Dmaven.repo.local=/private/tmp/vcampus-m2 -pl vcampus-server -am -Dtest=ConcurrentEnrollmentTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Observed before production enrollment code:

```text
[ERROR] COMPILATION ERROR
package edu.seu.vcampus.common.course does not exist
cannot find symbol: CourseService
cannot find symbol: CourseServiceImpl
cannot find symbol: CourseSessionIdentity
cannot find symbol: StudentEnrollmentEligibility
12 errors
vcampus-common ..................................... SUCCESS
vcampus-server ..................................... FAILURE
BUILD FAILURE
```

This was the expected failure: the real-UCanAccess concurrency tests could not
compile because the requested DTO, service, and narrow gateway surface did not yet
exist.

### GREEN 1 — required contention cases

The same focused command after the minimal locked transaction implementation:

```text
Running edu.seu.vcampus.server.course.service.ConcurrentEnrollmentTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The tests use separate connections to a fresh UCanAccess database and the production
`AccessCourseRepository`, `TransactionManager`, and `StripedResourceLockManager`.
They cover distinct students competing for the last seat and concurrent requests by
the same student. Both assert the active SQL row count equals the persisted offering
count after contention.

### RED 2 / GREEN 2 — repeat authorization while locked

The first implementation repeated ACTIVE eligibility under the locks but did not
repeat session/role resolution. A focused test used an authorization gateway that
returned `STUDENT` before locking and `TEACHER` inside the operation:

```text
/private/tmp/vcampus-maven/maven/3.9.16/libexec/bin/mvn -Dmaven.repo.local=/private/tmp/vcampus-m2 -pl vcampus-server -am '-Dtest=CourseEnrollmentServiceTest#rechecksSessionAndRoleInsideTheLockedTransaction' -Dsurefire.failIfNoSpecifiedTests=false test

Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expecting code to raise a throwable.
BUILD FAILURE
```

After resolving the session again inside the held-lock transaction and rejecting a
changed role or user identity:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Mutation check — same course across different offerings

To prove the service test catches the specified course-level duplicate rule, the
course-id comparison was temporarily disabled with `apply_patch`:

```text
/private/tmp/vcampus-maven/maven/3.9.16/libexec/bin/mvn -Dmaven.repo.local=/private/tmp/vcampus-m2 -pl vcampus-server -am '-Dtest=CourseEnrollmentServiceTest#rejectsSameCourseInTermEvenWhenOfferingDiffers' -Dsurefire.failIfNoSpecifiedTests=false test

Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expecting code to raise a throwable.
BUILD FAILURE
```

The comparison was restored with `apply_patch`; the same test then reported one
test, zero failures, and `BUILD SUCCESS`.

## Focused verification

Default contention uses
`Integer.getInteger("course.test.concurrentClients", 20)` in test configuration:

```text
/private/tmp/vcampus-maven/maven/3.9.16/libexec/bin/mvn -Dmaven.repo.local=/private/tmp/vcampus-m2 -pl vcampus-server -am -Dtest=ConcurrentEnrollmentTest,CourseEnrollmentServiceTest -Dsurefire.failIfNoSpecifiedTests=false test

CourseEnrollmentServiceTest: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
ConcurrentEnrollmentTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Configuration override proof:

```text
/private/tmp/vcampus-maven/maven/3.9.16/libexec/bin/mvn -Dmaven.repo.local=/private/tmp/vcampus-m2 -pl vcampus-server -am -Dcourse.test.concurrentClients=7 -Dtest=ConcurrentEnrollmentTest -Dsurefire.failIfNoSpecifiedTests=false test

Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Common + server verification

No Swing/client tests were run in the sandbox.

```text
/private/tmp/vcampus-maven/maven/3.9.16/libexec/bin/mvn -Dmaven.repo.local=/private/tmp/vcampus-m2 -pl vcampus-common,vcampus-server -am -Dsurefire.failIfNoSpecifiedTests=false verify

vcampus-common: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
vcampus-server: Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
vcampus-common ..................................... SUCCESS
vcampus-server ..................................... SUCCESS
BUILD SUCCESS
```

The shade phase emitted only the baseline duplicate-license/manifest and
module-info warnings; compilation, tests, packaging, and JaCoCo reports succeeded.

## Self-review

- The exact lock order is asserted as concrete `ResourceKey` values. Eligibility,
  authorization, offering state, term/window, duplicate, conflict, and capacity
  validations all execute again or exclusively inside the transaction while locks
  are held.
- Duplicate detection compares the target course ID against every active offering
  in the same term, rather than checking only the target offering ID.
- Schedule evaluation delegates each row pair to the existing inclusive
  day/week/period policy; service integration covers a true overlap, while the
  Task 2 domain suite continues to cover all non-overlap dimensions.
- Enrollment business time comes only from the injected `Clock`; no client-supplied
  time or system clock is read by service code.
- The two public common records are serializable and contain no database or Swing
  types. Public service/gateway APIs and typed exceptions have JavaDoc.
- Production code contains no concurrency-client constant and no numeric grade.
- No foundation signature and no user/student package was modified.

## Concerns

- The course-owned gateways are intentionally temporary integration seams because
  the teammate-owned upstream ports are absent on this branch. Wiring should use
  adapters when those ports land; this task does not guess or duplicate their
  packages or signatures.
- Capacity safety depends on application wiring sharing one `ResourceLockManager`
  across course service instances, as the overall architecture intends. The tests
  use one shared production lock manager. The application must not create an
  independent lock manager per request.
- Repository audit timestamps continue to use repository-owned server time from
  Task 1. The enrollment decision and `enrolledAt` value use the supplied `Clock`,
  which is the time relevant to the window rule and client-visible result.

## Takeover / fix round 1 of 5

### Partial-work audit and implementation

The takeover began with uncommitted changes only in `CourseServiceImpl` and
`CourseEnrollmentServiceTest`. The partial service change already placed repeated
session/role and eligibility gateway reads under the acquired `STUDENT` then
`OFFERING` locks and before `TransactionManager.inTransaction`. The accompanying
test used a real UCanAccess database and a one-slot, instrumented
`ConnectionProvider` that rejects a second connection while an existing one is
open. I retained both pieces rather than replacing them.

The fix extends the coverage and implementation as follows:

- The real-UCanAccess last-seat test now creates one `CourseServiceImpl` per
  competing client, all explicitly injected with the same
  `StripedResourceLockManager`. It still proves one winner and SQL active rows
  equal `enrolledCount`; it makes no static-lock or bootstrap change.
- The bounded provider gateway test remains real-database backed: both course
  gateways open and close a connection through the provider. It would throw on a
  nested gateway read while the outer course transaction connection is held.
- `enroll` captures one `Instant` after locked gateway revalidation and immediately
  before the course transaction. The same value is passed to both the term-window
  rule and the inserted/reactivated enrollment.
- Two real-database rollback tests inject a failure only at
  `changeEnrolledCount`: one after a new enrollment insert and one after a dropped
  row has been reactivated. They assert persisted SQL state and the offering count,
  rather than asserting proxy/mock invocation details.

### TDD evidence

The partial gateway test had no recoverable prior RED evidence. Its first observed
takeover execution was the focused baseline below, which passed; no earlier RED is
claimed.

```text
/private/tmp/vcampus-maven/maven/3.9.16/libexec/bin/mvn -Dmaven.repo.local=/private/tmp/vcampus-m2 -pl vcampus-server -am '-Dtest=CourseEnrollmentServiceTest,ConcurrentEnrollmentTest' -Dsurefire.failIfNoSpecifiedTests=false test

CourseEnrollmentServiceTest: Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
ConcurrentEnrollmentTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The new advancing-clock regression was then run against the pre-fix production
code and failed for the intended reason: its window check used the first instant
but its persisted enrollment used a second one.

```text
/private/tmp/vcampus-maven/maven/3.9.16/libexec/bin/mvn -Dmaven.repo.local=/private/tmp/vcampus-m2 -pl vcampus-server -am '-Dtest=CourseEnrollmentServiceTest#usesOneCapturedInstantForTheWindowDecisionAndEnrollmentTime' -Dsurefire.failIfNoSpecifiedTests=false test

CourseEnrollmentServiceTest.usesOneCapturedInstantForTheWindowDecisionAndEnrollmentTime
expected: 2026-08-10T00:00:00Z
 but was: 2026-08-10T00:02:00Z
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

After capturing and threading one operation instant, the focused green run was:

```text
/private/tmp/vcampus-maven/maven/3.9.16/libexec/bin/mvn -Dmaven.repo.local=/private/tmp/vcampus-m2 -pl vcampus-server -am '-Dtest=CourseEnrollmentServiceTest,ConcurrentEnrollmentTest' -Dsurefire.failIfNoSpecifiedTests=false test

CourseEnrollmentServiceTest: Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
ConcurrentEnrollmentTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Required verification

Default 20-client coverage is included in the 17-test focused run above. The
required property override also passed:

```text
/private/tmp/vcampus-maven/maven/3.9.16/libexec/bin/mvn -Dmaven.repo.local=/private/tmp/vcampus-m2 -pl vcampus-server -am -Dcourse.test.concurrentClients=7 -Dtest=ConcurrentEnrollmentTest -Dsurefire.failIfNoSpecifiedTests=false test

ConcurrentEnrollmentTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Common and server verification:

```text
/private/tmp/vcampus-maven/maven/3.9.16/libexec/bin/mvn -Dmaven.repo.local=/private/tmp/vcampus-m2 -pl vcampus-common,vcampus-server -am -Dsurefire.failIfNoSpecifiedTests=false verify

vcampus-common: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
vcampus-server: Tests run: 62, Failures: 0, Errors: 0, Skipped: 0
vcampus-common ..................................... SUCCESS
vcampus-server ..................................... SUCCESS
BUILD SUCCESS
```

`git diff --check` also completed without output. Verify emitted the existing
Shade overlap/module-info warnings only.

### Files

- `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/CourseEnrollmentServiceTest.java`
- `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/ConcurrentEnrollmentTest.java`
- `.superpowers/sdd/2026-08-24-vcampus-course-module/task-3-report.md`

### Self-review and concerns

- The shared-lock test uses multiple independently constructed services and the
  same production lock manager; it checks persisted SQL rows and the counter after
  simultaneous real-UCanAccess work.
- The provider test checks observable connection availability, not a gateway mock:
  a transaction held open during either gateway call would deterministically be
  rejected. Session/role and student-ID drift rejection remain covered by the
  existing locked revalidation tests.
- The rollback proxy is deliberately only a failure injection seam. The assertions
  inspect the actual database: no newly inserted enrollment remains; a retained
  row remains `DROPPED` with its original details; and counts remain unchanged.
- Task 6 still owns the actual application bootstrap. It must instantiate one
  application-scoped lock manager and inject it into every course service; this fix
  intentionally does not add static locks or alter `ServerMain`.
