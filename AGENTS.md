# vCampus repository instructions

These rules apply to the entire repository. A more specific `AGENTS.md` may add stricter
rules for a subdirectory, but must not weaken these requirements.

## Runtime and database

- Build and run with JDK 21. Use Maven from the repository root for verification.
- Microsoft Access (`.accdb`) through UCanAccess is the only supported database. Do not
  introduce MySQL, a remote database, or credentials for shared infrastructure without an
  explicit team decision.
- Treat `vcampus-database/schema` plus `vcampus-database/seed` as the schema source of
  truth. Rebuild generated databases in a temporary path, validate them, and only then
  replace `vcampus-distribution/data/vCampus.accdb`.
- Training plans are canonical in `tblTrainingPlan`, `tblTrainingPlanCourse`, and
  `tblTrainingPlanPrerequisite`. Course selection must access them through its repository
  port and must not create a second production copy of curriculum data.
- Preserve the three academic seasons `SUMMER`, `AUTUMN`, and `SPRING`. A four-year plan
  uses twelve positions: `(academicYearNo - 1) * 3 + seasonOrdinal`.

## Java source rules

- New Java files must not exceed 200 physical lines. When modifying an existing oversized
  file, split it toward focused collaborators instead of increasing its responsibilities.
  Do not compress formatting or combine imports merely to evade the limit.
- Keep handlers, services, repositories, DTOs, and UI responsibilities separated. Cross-
  module access goes through a narrow port/gateway, not another module's repository.
- Public packages, types, constructors, and methods require useful JavaDoc. JavaDoc must
  pass the Maven documentation check; do not silence malformed or missing documentation.
- Keep protocol DTOs immutable where practical, validate inputs at boundaries, preserve
  request deduplication for writes, and re-check mutable business rules inside the same
  transaction and lock order used by the mutation.
- Never expose database paths, SQL text, stack traces, password hashes, or internal
  exception details to a client response.

## Tests and delivery

- For a feature or bug fix, add a failing automated test first, then implement the smallest
  production change that makes it pass.
- Before merging, run `mvn test` with JDK 21 and any focused generator/database validators
  affected by the change. Socket tests require permission to bind a loopback port.
- Generated SQL, count snapshots, account lists, the release Access database, packaged
  JARs, and user documentation must describe the same dataset revision.
- Do not commit logs, IDE state, Maven `target` directories, Python caches, local database
  lock/backup files, or secrets. Review the exact diff before committing and pushing.
