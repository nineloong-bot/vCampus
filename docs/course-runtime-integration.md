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
        eligibility -> eligibility.status().name());

CourseComposition courses = CourseComposition.create(
        connections, courseAuthorization, courseStudents,
        Clock.systemUTC(), applicationLocks);
courses.register(router);
```

Expected upstream contracts, verified against the current remote teammate branches:

- user module: `AuthorizationPort.requireSession`, `UserIdentity`, and
  `UserQueryPort.findActiveUser`;
- student module: `StudentQueryPort.getEnrollmentEligibility` and
  `StudentEligibility`;
- application bootstrap: reuse the same `ConnectionProvider`, `MessageRouter`, and
  application-owned `ResourceLockManager`; do not construct a second database or router.

The `usable` predicate is intentionally required: it prevents a first-password
restricted session from reaching course commands. Assigned teaching users are checked
through `findActiveUser`, so disabled accounts cannot be attached to a teaching class.
