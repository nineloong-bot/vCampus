# Full Access dataset and training-plan alignment design

## Goal

Replace the small release database with a reproducible Microsoft Access dataset based on
the former `sTeven44` full-data fixture, while retaining the current merged schema and
making course selection read the student module's training plans.

## Confirmed requirements

- Microsoft Access remains the only database technology.
- The release database contains at least 1,000 students, 50 teachers, 120 courses and 240
  teaching classes.
- Students cover ten majors and the 2023, 2024, 2025 and 2026 cohorts.
- Terms and plan data cover `SUMMER`, `AUTUMN` and `SPRING`.
- `tblTrainingPlan` and `tblTrainingPlanCourse` are the canonical plan tables.
- Course selection resolves its candidate set through the existing `CurriculumRepository`
  boundary, backed by the canonical training-plan tables rather than a second plan copy.
- The generated database, generator snapshot, validation tools and team instructions must
  agree and be reproducible.

## Data model

The integer `semester` on `tblTrainingPlanCourse` is retained for compatibility, but its
canonical meaning becomes a twelve-position curriculum term ordinal:

`semester = (academicYearNo - 1) * 3 + AcademicSeason.curriculumTermOrdinal()`

Thus each of four academic years contains summer, autumn and spring. Course selection
converts the ordinal back to `academicYearNo` and `AcademicSeason`. Course nature, category
and offering unit are stored on each plan-course row, with compatibility defaults for older
rows. Prerequisite edges live in `tblTrainingPlanPrerequisite` and refer to catalog course IDs.

The full generator creates 1,000 students evenly across ten majors and four cohorts. It
keeps the established test card numbers and unified password, creates one class per
major/cohort, and emits forty published active plans with 120 course positions each.

## Release database

The database is rebuilt from the current `vcampus-database/schema` and seed scripts, then
the deterministic full-data SQL snapshot is imported. This avoids copying the stale
`sTeven44` binary, which lacks current tables and term columns. The resulting file replaces
`vcampus-distribution/data/vCampus.accdb` only after validation succeeds.

## Verification

- Repository tests prove selection reads the canonical training-plan tables and handles
  all three seasons.
- Generator tests prove cohort distribution, three-season terms and aligned plans.
- Dataset validation checks counts, foreign-key-like relationships, plan/catalog equality,
  selectable courses for every cohort and all account passwords.
- Maven module tests and a packaged server startup smoke test run before commit and push.
