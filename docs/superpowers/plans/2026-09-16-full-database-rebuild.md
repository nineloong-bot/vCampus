# vCampus Full Database Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the vCampus Access database from schema and deterministic sources with 120 realistic students, complete academic data, empty initial enrollments, explicit library overdue scenarios, and the specified commerce coverage.

**Architecture:** Replace the oversized legacy fixture with a deterministic Python dataset generator split by people, academics, transfers, library, and commerce. Validate the in-memory graph first, import it into a new temporary Access database through the existing Java/UCanAccess builder, then run database-level invariants and smoke checks before backing up and replacing the distribution database.

**Tech Stack:** Python 3 standard library and `unittest`; Java 21; Maven; UCanAccess; Microsoft Access `.accdb`.

**Spec:** `DATABASE_REBUILD_STANDARD.md`

## Global Constraints

- `vcampus-database/schema` and `vcampus-database/seed` remain the schema and seed source of truth.
- Only Microsoft Access through UCanAccess is supported.
- Student logins are exactly `213240001`–`213240040`, `213250001`–`213250040`, and `213260001`–`213260040`.
- Business data must not contain `test`, `测试`, `demo`, `演示`, `fake`, `sample`, `bulk`, `dummy`, or `example`.
- Every student starts with password `123456` and `mustChangePassword=TRUE`.
- Only `AUTUMN` and `SPRING` academic seasons are valid.
- Initial enrollment, drop, and enrollment-adjustment tables are empty; exactly one selection phase is open.
- Build into a temporary path, validate it, back up the existing release database, and only then replace it.
- Preserve unrelated user changes in the dirty worktree.

---

### Task 1: Replace legacy fixture expectations with the approved data contract

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/test_generation.py`
- Modify: `vcampus-database/demo/full-test-data/tools/generate.py`

**Interfaces:**
- Consumes: module-level `generate(add, now)` functions.
- Produces: deterministic `generated.sql`, `counts.tsv`, `accounts.json`, and `账号清单.txt`.

- [ ] **Step 1: Write failing tests for the complete contract**

Add focused tests asserting 120 students across three exact login ranges, two departments/five majors, no empty classes, eight administrator logins/roles, complete student fields, five submitted math-to-computer transfers, empty enrollment tables, one open selection phase, two offerings per selectable course, realistic varied credits, 1–3 overdue loans with manifest entries, and all commerce quantities from the specification.

- [ ] **Step 2: Run the generator tests and verify contract failures**

Run:

```bash
python3 -m unittest discover -s vcampus-database/demo/full-test-data/tools -p 'test_generation.py' -v
```

Expected: failures reference the old 2,400-student, multi-college, pre-enrolled, `bulk-*` dataset.

- [ ] **Step 3: Simplify the generator orchestration**

Set a single data timestamp/version, call each module exactly once, validate the complete row graph, update number sequences for all 120 accounts, and write a versioned account/scenario manifest without banned business terms.

- [ ] **Step 4: Re-run focused tests**

Run the command from Step 2. Expected: remaining failures are confined to unimplemented module data, not orchestration errors.

### Task 2: Generate organizations, administrators, teachers, and complete student profiles

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/people.py`
- Modify: `vcampus-database/demo/full-test-data/tools/test_generation.py`

**Interfaces:**
- Produces: `tblDepartment`, `tblMajor`, `tblClass`, `tblUser`, `tblStudent`, `tblStudentCollegeAdministrator`, `tblNumberSequence` rows and account manifest entries.
- Consumed by: academics, transfer, library, and commerce generators through stable IDs and login mappings.

- [ ] **Step 1: Verify people tests fail for the old organization graph**

Run the people-specific test class/methods and confirm failures are caused by the old counts, banned IDs, and incomplete profile fields.

- [ ] **Step 2: Implement the minimal deterministic people graph**

Generate two departments, five majors, fifteen non-empty classes, 120 students, eight administrators, and enough teachers for all offerings. Generate checksum-valid Chinese resident IDs, coherent dates, unique phones/emails, full profile fields, exact login ranges, password hashes for `123456`, and college bindings for `CSADMIN` and `MATHADMIN`.

- [ ] **Step 3: Validate people graph and password policy**

Run the focused tests. Expected: exact account ranges, profile completeness, identity checksum, role matrix, and banned-text checks pass.

### Task 3: Generate realistic curricula, grades, terms, and empty selection state

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/courses.py`
- Modify: `vcampus-database/demo/full-test-data/tools/test_generation.py`

**Interfaces:**
- Produces: `tblTrainingPlan`, `tblTrainingPlanCourse`, `tblTrainingPlanPrerequisite`, `tblStudentGrade`, `tblTerm`, `tblCourseSelectionPhase`, `tblCourse`, `tblCourseOffering`, `tblCourseSchedule`, and `tblCourseRetakeQuota`.
- Guarantees: no rows in `tblEnrollment` or `tblEnrollmentAdjustment`.

- [ ] **Step 1: Verify academic tests fail against uniform-credit legacy courses**

Run academic test methods and confirm failures for uniform credits, multiple colleges, populated enrollment tables, and excessive offerings.

- [ ] **Step 2: Implement five eight-semester curricula**

Use official Southeast University course-plan references to define public foundation, disciplinary foundation, core, elective, interdisciplinary, practical, and graduation courses with varied credits and acyclic prerequisites. Generate historical mandatory grades for 2024 students' first four semesters and 2025 students' first two semesters; generate no grades for 2026 students.

- [ ] **Step 3: Implement one open term and two complete offerings per selectable course**

Generate exactly one open selection phase, teacher/schedule/classroom/capacity fields for each offering, zero enrolled counts, and no enrollment or adjustment rows.

- [ ] **Step 4: Run academic tests**

Expected: curricula cover all eight term positions, credits vary, prerequisites are acyclic, required historical grades exist, and selection tables are empty.

### Task 4: Generate transfer and library scenarios with explicit user manifests

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/major_transfer.py`
- Modify: `vcampus-database/demo/full-test-data/tools/library.py`
- Modify: `vcampus-database/demo/full-test-data/tools/test_generation.py`

**Interfaces:**
- Produces: one open transfer batch, computer-target options, five `SUBMITTED` math-student applications, books/copies/policies/loans/reservations, and overdue manifest data.

- [ ] **Step 1: Verify scenario tests fail against old directions and counts**

Confirm failures identify computer-to-math transfers and the oversized overdue population.

- [ ] **Step 2: Implement five math-to-computer submitted applications**

Use existing 2024/2025 math students, retain source snapshots matching current profiles, set coherent dates, and target computer science, software engineering, or artificial intelligence.

- [ ] **Step 3: Implement realistic library inventory and 1–3 overdue loans**

Create real books and ISBNs, multiple copy states, normal/renewed/returned/reserved loans, and two overdue users with reproducible fines under the configured policy. Return manifest fields for login, name, book, barcode, due date, overdue days, fine, and wallet balance.

- [ ] **Step 4: Run transfer/library tests**

Expected: five submitted applications and explicit overdue scenarios pass all consistency checks.

### Task 5: Generate the exact commerce and shared-wallet coverage

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/shop.py`
- Modify: `vcampus-database/demo/full-test-data/tools/shop_orders.py`
- Modify: `vcampus-database/demo/full-test-data/tools/shop_checks.py`
- Modify: `vcampus-database/demo/full-test-data/tools/test_generation.py`

**Interfaces:**
- Produces: seller applications, six shops, 72 products, SKUs, carts, 20 shop orders, qualifications, governance cases/audits, and wallet operations/entries.

- [ ] **Step 1: Verify commerce quantity/status tests fail**

Run commerce-specific tests and record failures against the requested matrix.

- [ ] **Step 2: Implement catalog and cart coverage**

Generate six shops with five active/one suspended, eight seller applications, twelve products per shop, at least twelve multi-SKU products with valid defaults, and cart rows of six/two/zero for three buyers.

- [ ] **Step 3: Implement orders, governance, qualifications, and shared wallets**

Generate 20 shop orders with consistent groups/items/payments/inventory, five qualification states, five report/recovery/remediation cases with audit history, and balanced wallet journals for two buyers, six owners, and overdue-library users.

- [ ] **Step 4: Run commerce tests**

Expected: exact counts, state coverage, referential integrity, money conservation, and inventory consistency pass.

### Task 6: Strengthen Access-level validation and build a clean temporary database

**Files:**
- Modify: `vcampus-database/demo/full-test-data/tools/ValidateDataset.java`
- Modify: `vcampus-database/demo/full-test-data/tools/SmokeDataset.java`
- Modify: `vcampus-database/demo/full-test-data/build-package.ps1`
- Modify: `vcampus-database/demo/full-test-data/README.md`

**Interfaces:**
- Consumes: generated SQL/counts and the schema/seed directories.
- Produces: a validated temporary `.accdb`, database-level check count, and read/write smoke evidence.

- [ ] **Step 1: Add failing database validation expectations**

Add checks for exact organization/student/admin counts, banned business text, login/password policy, empty enrollment tables, one open phase, two offerings per selectable course, five transfer applications, overdue account traceability, and commerce matrix counts.

- [ ] **Step 2: Regenerate SQL and run Python tests**

Run:

```bash
python3 vcampus-database/demo/full-test-data/tools/generate.py vcampus-database/demo/full-test-data/tools
python3 -m unittest discover -s vcampus-database/demo/full-test-data/tools -p 'test_generation.py' -v
```

Expected: all generator tests pass and snapshots are refreshed.

- [ ] **Step 3: Compile the project and dataset tools with JDK 21**

Run:

```bash
mvn -DskipTests package
```

Expected: exit code 0 and current server/distribution JARs available to the dataset builder.

- [ ] **Step 4: Build and validate a new Access file in a temporary directory**

Use `mktemp -d`, compile/run `BuildDataset.java`, then run `ValidateDataset.java` against the new file and generated count snapshot. Never target the release file in this step.

- [ ] **Step 5: Run read/write smoke checks on a disposable copy**

Run `SmokeDataset.java` on a copied temporary database. Expected: login, password-change enforcement, course-selection write, library return/fine, and commerce payment paths operate without corrupting the validated source file.

### Task 7: Back up, replace, and verify the release database

**Files:**
- Replace after validation: `vcampus-distribution/data/vCampus.accdb`
- Modify: `vcampus-database/demo/full-test-data/docs/账号清单.txt`
- Modify: `vcampus-database/demo/full-test-data/docs/课程场景.md`
- Modify: `vcampus-database/demo/full-test-data/docs/商城场景.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: validated temporary Access database and manifests.
- Produces: release database, recoverable backup, and matching documentation.

- [ ] **Step 1: Create a dated backup beside the release database**

Copy the exact existing `vcampus-distribution/data/vCampus.accdb` to an explicit dated `.bak.accdb` path and verify the backup exists and has non-zero size.

- [ ] **Step 2: Replace the release database with the validated file**

Copy the validated temporary database into the release path only after all prior commands succeed.

- [ ] **Step 3: Re-run Access validation against the release file**

Run the same `ValidateDataset` command against `vcampus-distribution/data/vCampus.accdb`. Expected: the same check count and zero failures.

- [ ] **Step 4: Run the full Maven suite with JDK 21**

Run:

```bash
mvn test
```

Expected: exit code 0 with zero test failures.

- [ ] **Step 5: Review the exact diff and generated artifacts**

Run `git status --short`, targeted `git diff --stat`, and banned-text scans over generated business fields. Confirm unrelated user deletions/changes were not modified by this work.

