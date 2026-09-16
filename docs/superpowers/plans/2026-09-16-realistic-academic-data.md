# Realistic Academic Demo Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the generated Access fixture internally consistent by removing duplicate legacy rows, adding a substantial mathematics-college population, adding valid mathematics-to-computer transfer applications, and giving 2024 computer third-year students passed grades for the first four semesters only.

**Architecture:** Keep the canonical schema and deterministic Python generator. Extend the generator with focused population, transfer, and academic-history helpers; validate generated rows before SQL is written. Rebuild and validate a temporary Access database before replacing any distribution database.

**Tech Stack:** Python 3 `unittest`, Java 21, Maven, UCanAccess, Microsoft Access.

**Spec:** `docs/superpowers/specs/2026-09-15-student-major-transfer-data-quality-design.md`

## Global Constraints

- Use JDK 21 and Maven from the repository root for verification.
- Microsoft Access through UCanAccess is the only supported database.
- Treat `vcampus-database/schema` plus `vcampus-database/seed` as the schema source of truth.
- Preserve `AUTUMN` and `SPRING` and the canonical training-plan tables.
- Do not overwrite the user's existing uncommitted database or configuration changes.
- New Java files must stay within 200 physical lines and public APIs need useful JavaDoc.
- Write a failing automated test before each production behavior change.
- Rebuild databases in a temporary path, validate them, and only then replace a release database.

---

### Task 1: Add failing fixture-contract tests

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/test_generation.py`

- [ ] **Step 1: Add tests for the new contract**

Assert at least 200 mathematics-college students across 2024–2026, at least 10 submitted mathematics-to-computer applications, and for every selected 2024 computer student that every active plan course in semesters 1–4 has exactly one `PASSED` `tblStudentGrade` row while semesters 5–8 have none.

- [ ] **Step 2: Run the focused tests and verify RED**

Run `python3 -m unittest vcampus-database/demo/full-test-data/tools/test_generation.py -v`. The new assertions must fail against the current fixture because the required reverse transfer scenario and student-grade rows do not exist.

### Task 2: Generate a realistic mathematics population and reverse transfer scenario

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/people.py`
- Modify: `vcampus-database/demo/full-test-data/tools/major_transfer.py`
- Modify: `vcampus-database/demo/full-test-data/tools/generate.py`

- [ ] **Step 1: Add deterministic mathematics students and target option**

Generate at least 200 unique active mathematics-college students in existing mathematics majors/classes, without re-emitting existing IDs. Use 2024–2026 cohorts and deterministic account, student, class, and identity values.

- [ ] **Step 2: Add at least ten valid mathematics-to-computer applications**

Add a real target option whose `targetMajorId` belongs to the computer department and emit at least ten `SUBMITTED` applications from 2026 mathematics students. Derive application snapshots from student/class/major records and validate age, grade, active enrollment, and cross-college relationship.

- [ ] **Step 3: Run the focused tests and verify GREEN for population/transfer**

Run the same unittest command and confirm the population and reverse-transfer assertions pass.

### Task 3: Add first-four-semester history for 2024 third-year students

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/courses.py`
- Modify: `vcampus-database/demo/full-test-data/tools/generate.py`
- Modify: `vcampus-database/demo/full-test-data/tools/test_generation.py`

- [ ] **Step 1: Add the failing grade coverage assertion**

Assert that each selected 2024 computer student maps grades only to that student’s canonical training plan, that all plan courses in semesters 1–4 are present once with result `PASSED`, and no grade row references semesters 5–8.

- [ ] **Step 2: Implement grade generation from canonical plan rows**

After course/training-plan rows exist, select deterministic 2024 computer students and emit `tblStudentGrade` rows by joining their plan ID and plan-course IDs. Use recorded semester labels `2024-AUTUMN`, `2025-SPRING`, `2025-AUTUMN`, and `2026-SPRING`; do not create a second curriculum copy.

- [ ] **Step 3: Run the focused tests and verify GREEN**

Run the Python suite and confirm grade coverage and all existing course invariants pass.

### Task 4: Audit duplicates and rebuild a temporary Access database

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/ValidateDataset.java`
- Modify: `vcampus-database/demo/full-test-data/tools/README.md` if validator usage needs documenting

- [ ] **Step 1: Add duplicate-key validation**

Validate primary-key and business-unique fields for users, students, classes, training plans, plan courses, grades, transfer batches, options, and applications; fail with table/key counts but never print credentials or database paths.

- [ ] **Step 2: Run generator and validator against a temporary path**

Generate SQL into a temporary directory, build a temporary `.accdb` from schema plus seeds, run the duplicate audit and existing dataset validator, and confirm the invalid-application audit returns zero.

- [ ] **Step 3: Review the exact diff and only then update release data**

Keep the existing user-modified `Vcampus-distribution/data/vCampus.accdb` untouched unless a separately validated replacement is explicitly required by the final dataset workflow. Do not commit generated binaries, logs, locks, or caches.

### Task 5: Full verification

- [ ] Run `python3 -m unittest discover -s vcampus-database/demo/full-test-data/tools -p 'test_*.py' -v`.
- [ ] Run `mvn test` with JDK 21 from the repository root.
- [ ] Inspect `git diff --check`, `git status --short`, and the exact changed-file diff.
- [ ] Report duplicate findings, population counts, grade coverage, application counts, and remaining user-owned modifications.
