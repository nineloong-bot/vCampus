# Course runtime integration

The `course` branch deliberately does not import classes that currently exist only on
teammate branches. After the user and student modules are merged, bind their published
ports to the course composition with `CourseRuntimeAdapters`:

```java
CourseAuthorizationGateway courseAuthorization = CourseRuntimeAdapters.authorization(
        authorization::requireSession,
        UserIdentity::userId,
        identity -> identity.role().name(),
        identity -> !identity.restricted(),
        (userId, expectedRole) -> userQueries.findActiveUser(userId)
                .map(identity -> identity.role().name().equals(expectedRole))
                .orElse(false));

CourseStudentGateway courseStudents = CourseRuntimeAdapters.students(
        studentQueries::getEnrollmentEligibility,
        StudentEligibility::studentId,
        eligibility -> eligibility.status().name(),
        studentQueries::existsActiveStudent);

CourseComposition courses = CourseComposition.create(
        connections, courseAuthorization, courseStudents,
        Clock.systemUTC(), applicationLocks);
courses.register(router);
```

Clients resolve the operational term through `COURSE_GET_CURRENT_TERM`; they must not
independently choose an item from `COURSE_TERM_LIST`. This keeps offering searches, phase
display, enrollments, and schedules on the same server-authoritative term.

Expected upstream contracts, verified against the current remote teammate branches:

- user module: `AuthorizationPort.requireSession`, `UserIdentity`, and
  `UserQueryPort.findActiveUser`;
- student module: `StudentQueryPort.getEnrollmentEligibility` and
  `StudentEligibility`;
- application bootstrap: reuse the same `ConnectionProvider`, `MessageRouter`, and
  application-owned `ResourceLockManager`; do not construct a second database or router.

The shared UCanAccess JDBC URL must leave `immediatelyReleaseResources` disabled (its
default). UCanAccess 5.1.3 can deadlock its driver-wide resource bookkeeping when one
thread opens a connection while another closes with immediate release enabled. The course
demo and real-Access concurrency fixtures use the safe default; resource release occurs when
the server JVM exits.

The `usable` predicate is intentionally required: it prevents a first-password
restricted session from reaching course commands. Assigned teaching users are checked
through `findActiveUser`, so disabled accounts cannot be attached to a teaching class.
