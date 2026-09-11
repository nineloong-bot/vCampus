# Full Access Dataset Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the complete Access dataset on `main`, with multi-cohort students, three-season teaching data and course selection backed by student training plans.

**Architecture:** Keep `CurriculumRepository` as the course-module port but replace its temporary curriculum-table adapter with an adapter over `tblTrainingPlan*`. Rebuild the release database deterministically from current schemas, seeds and the full-data generator.

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ, Python 3 standard library, UCanAccess, Microsoft Access `.accdb`.

**Spec:** `docs/superpowers/specs/2026-09-12-full-access-dataset-alignment-design.md`

## Global Constraints

- Microsoft Access is the only database.
- Every new Java source file must remain at or below 200 lines. Existing oversized files
  touched by a necessary compatibility change must not gain new responsibilities; the
  repository-wide historical violations are recorded separately for staged refactoring.
- The release dataset must include summer, autumn and spring.
- Dataset generation must be deterministic and must not overwrite an existing database before validation.

---

### Task 1: Canonical training-plan repository adapter

**Files:**
- Modify: `vcampus-database/schema/030_course.sql`
- Modify: `vcampus-database/schema/030_training_plan.sql`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/AccessCurriculumRepository.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/repository/CurriculumRepositoryTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/domain/CurriculumSelectionPolicyTest.java`

**Interfaces:**
- Consumes: `CurriculumRepository` and `AcademicSeason.curriculumTermOrdinal()`.
- Produces: the same repository API backed by `tblTrainingPlan`, `tblTrainingPlanCourse`, `tblMajor`, `tblDepartment`, `tblCourse` and `tblTrainingPlanPrerequisite`.

- [ ] Write repository tests that create only canonical training-plan rows and expect published lookup, season partitioning and prerequisites to work.
- [ ] Run the focused tests and confirm failure because the adapter still queries `tblCurriculum*`.
- [ ] Add `tblTrainingPlanPrerequisite`, remove temporary curriculum tables from fresh course schema, and implement ordinal conversion in the adapter.
- [ ] Run repository and selection-policy tests until green.
- [ ] Run all server course and student tests.

### Task 2: Complete deterministic dataset generator

**Files:**
- Create: `vcampus-database/demo/full-test-data/tools/test_generation.py`
- Modify: `vcampus-database/demo/full-test-data/tools/people.py`
- Modify: `vcampus-database/demo/full-test-data/tools/courses.py`
- Modify: `vcampus-database/demo/full-test-data/tools/generate.py`
- Regenerate: `vcampus-database/demo/full-test-data/tools/generated.sql`
- Regenerate: `vcampus-database/demo/full-test-data/tools/counts.json`
- Regenerate: `vcampus-database/demo/full-test-data/tools/counts.tsv`
- Regenerate: `vcampus-database/demo/full-test-data/tools/accounts.json`

**Interfaces:**
- Consumes: generator callback `add(table, **fields)`.
- Produces: 1,000 students across ten majors and four cohorts, three-season terms, at least 290 offerings and forty aligned training plans.

- [ ] Add unit tests asserting exactly 250 students per cohort, all three seasons, forty plans, and catalog-backed plan course codes.
- [ ] Run `python -m unittest ...test_generation` and confirm the old generator fails these assertions.
- [ ] Change people generation to create forty cohort classes and distribute students evenly.
- [ ] Change course generation to emit term mapping fields, spring/summer offerings, canonical plans and prerequisite rows.
- [ ] Regenerate the snapshot and run Python tests until green.

### Task 3: Build and validate the release Access database

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/ValidateDataset.java`
- Modify: `vcampus-database/demo/full-test-data/README.md`
- Replace: `vcampus-distribution/data/vCampus.accdb`

**Interfaces:**
- Consumes: current schemas/seeds and deterministic `generated.sql`.
- Produces: the distributable full Access database and validation evidence.

- [ ] Extend validator checks for cohort/major coverage, seasons, canonical plans, catalog alignment and per-cohort selectable courses.
- [ ] Build current distribution JARs.
- [ ] Build a new database at a temporary path and run validator; confirm any missing invariant fails before release replacement.
- [ ] Fix generator/import defects and rerun until validation passes.
- [ ] Replace the release `.accdb` with the validated file and run a server startup smoke test against a copy.

### Task 4: Team instructions and repository rules

**Files:**
- Create: `AGENTS.md`
- Create: `docs/testing/2026-09-12-main运行状态与待讨论问题.md`
- Modify: `README.md`

**Interfaces:**
- Produces: accurate login accounts, server/client commands, dataset inventory, deployment recommendation and enforceable source rules.

- [ ] Document the standard login accounts and the `Test12345` full-dataset password.
- [ ] Document one-command-at-a-time server/client startup from `vcampus-distribution`.
- [ ] Record actual counts and explain why shared server deployment remains deferred while development is active.
- [ ] Add root `AGENTS.md` with Access-only, Java 21, 200-line, JavaDoc, architecture, season and verification rules.
- [ ] Check every documented command/path against the packaged release.

### Task 5: Final verification and integration

**Files:**
- Verify all modified files and generated artifacts.

**Interfaces:**
- Produces: a clean, tested commit pushed to `origin/main`.

- [ ] Run common, server and client Maven test suites.
- [ ] Check modified Java files are at most 200 lines and run dataset validation once more.
- [ ] Review the final diff for accidental binary or user-file changes.
- [ ] Commit the implementation and push `HEAD:main`.
