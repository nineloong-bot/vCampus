# Curriculum-backed Course Catalog and Editor UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make training-plan courses the only source for catalog creation, add bidirectional course lookup with locked metadata, restore the college transfer workspace, and visually align embedded course editors.

**Architecture:** Add a narrow course-module read port over canonical training-plan tables and expose immutable catalog candidates through the existing course protocol. Keep the stable `nineloong` list/detail composition for college transfer processing, but use `EmbeddedEditorHost` only when an editing action replaces that whole workspace. Shared editor-card styling keeps course and offering forms visually consistent.

**Tech Stack:** Java 21, Swing, Maven, Microsoft Access/UCanAccess, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-16-curriculum-course-catalog-and-editor-ui-design.md`

## Global Constraints

- Microsoft Access through UCanAccess remains the only database.
- `tblTrainingPlan`, `tblTrainingPlanCourse`, and `tblTrainingPlanPrerequisite` remain the sole production curriculum source.
- Only `AUTUMN` and `SPRING` are supported teaching seasons.
- New Java files stay under 200 physical lines and public APIs receive useful JavaDoc.
- Preserve local changes to `vcampus-distribution/config/client.properties`, `vcampus-distribution/data/vCampus.accdb`, and the untracked Word temporary file.
- Every production behavior starts with a focused failing test.

---

### Task 1: Persist complete training-plan course metadata

**Files:**
- Modify: `vcampus-database/schema/030_training_plan.sql`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/TrainingPlanCourseView.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/SaveTrainingPlanCourseCommand.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/domain/TrainingPlanCourse.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/TrainingPlanRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/TrainingPlanServiceImpl.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/TrainingPlanManagementPanel.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/TrainingPlanServiceTest.java`

**Interfaces:**
- Produces: `TrainingPlanCourseView.totalHours()` and matching command/domain field.
- Produces: canonical course type and owning department already carried by the training-plan entry and its plan.

- [ ] **Step 1: Write a failing persistence test**

Add a test that saves a training-plan course with `totalHours = 48`, reloads the plan detail, and asserts code, name, credits, total hours, type, and department metadata survive the round trip.

- [ ] **Step 2: Verify the focused test fails**

Run:

```bash
mvn -pl vcampus-server -Dtest=TrainingPlanServiceTest test
```

Expected: compilation or assertion failure because `totalHours` is absent.

- [ ] **Step 3: Add the canonical field and compatibility migration**

Add `totalHours LONG` to new schemas, extend the command/view/domain records, update repository mapping, and add an idempotent startup migration. Backfill from the linked `tblCourse.totalHours` where `courseId` is present; leave genuinely incomplete legacy rows unavailable as catalog candidates until the responsible college administrator edits them. Add required total-hours input to the training-plan course editor so every new or updated row is complete. Compatibility constructors remain only where older tests or serialized fixtures require them.

- [ ] **Step 4: Verify training-plan tests pass**

Run the focused test from Step 2 and the existing training-plan repository tests.

- [ ] **Step 5: Commit**

```bash
git add vcampus-database/schema/030_training_plan.sql vcampus-common/src/main/java/edu/seu/vcampus/common/student vcampus-server/src/main/java/edu/seu/vcampus/server/student vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/TrainingPlanManagementPanel.java
git commit -m "feat: persist training plan course hours"
```

### Task 2: Expose conflict-safe curriculum catalog candidates

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CurriculumCourseCandidate.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CurriculumCourseCandidateQuery.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/repository/CurriculumCatalogCandidateRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CurriculumCatalogCandidateService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/handler/CourseHandlers.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/CurriculumCatalogCandidateServiceTest.java`

**Interfaces:**
- Produces: `PageResult<CurriculumCourseCandidate> searchCurriculumCandidates(CurriculumCourseCandidateQuery query)`.
- Candidate fields: stable `planCourseId`, code, name, credits, total hours, course type, department id/name, and conflict status.

- [ ] **Step 1: Write failing candidate aggregation tests**

Cover keyword matching by code and name, exclusion of inactive training-plan rows and already cataloged codes, identical-code deduplication, and rejection of conflicting metadata.

- [ ] **Step 2: Verify candidate tests fail**

```bash
mvn -pl vcampus-server -Dtest=CurriculumCatalogCandidateServiceTest test
```

Expected: missing candidate service/types.

- [ ] **Step 3: Implement the narrow repository and service**

Query canonical training-plan tables joined to plan, major, and department. Aggregate by normalized course code, compare all locked metadata, paginate after filtering, and return safe conflict messages without exposing SQL.

- [ ] **Step 4: Register the read command**

Register `COURSE_CURRICULUM_CANDIDATE_SEARCH` for course administrators through the existing authorization and safe-response boundary.

- [ ] **Step 5: Verify focused and handler tests pass**

```bash
mvn -pl vcampus-server -Dtest=CurriculumCatalogCandidateServiceTest,CourseHandlersTest test
```

- [ ] **Step 6: Commit**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/course vcampus-server/src/main/java/edu/seu/vcampus/server/course
git commit -m "feat: expose curriculum course candidates"
```

### Task 3: Enforce curriculum-backed catalog creation

**Files:**
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/CreateCourseCommand.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/course/UpdateCourseCommand.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/service/CourseServiceImpl.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/course/domain/CurriculumCourseDefinitionConflictException.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/course/service/CourseManagementServiceTest.java`

**Interfaces:**
- `CreateCourseCommand` identifies the selected `planCourseId`; the server copies authoritative metadata.
- `UpdateCourseCommand` retains the stable course id/version and allows only catalog-owned state changes.

- [ ] **Step 1: Write failing service boundary tests**

Test that creation copies authoritative metadata, rejects an invented/missing plan-course id, rejects conflicting definitions, rejects a duplicate catalog code, and prevents update commands from changing locked metadata.

- [ ] **Step 2: Verify failures are caused by missing validation**

```bash
mvn -pl vcampus-server -Dtest=CourseManagementServiceTest test
```

- [ ] **Step 3: Implement transactional validation**

Within the existing course locks and transaction, reload the candidate by stable id, verify its aggregate definition, and construct the catalog `Course` only from server-side values. Preserve optimistic locking and request deduplication.

- [ ] **Step 4: Map explicit safe errors**

Map missing curriculum course, definition conflict, and duplicate catalog code to user-facing course error codes in `CourseHandlers`.

- [ ] **Step 5: Verify service and socket contract tests pass**

Run the focused test plus `LoginCourseSocketIntegrationTest`.

- [ ] **Step 6: Commit**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/course vcampus-server/src/main/java/edu/seu/vcampus/server/course
git commit -m "fix: require curriculum source for catalog courses"
```

### Task 4: Build bidirectional locked course selection

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CurriculumCourseSelectionModel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CurriculumCourseEditorFields.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseUiGateway.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseClientGateway.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/service/CourseClientService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseEditorPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CurriculumCourseEditorFieldsTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseEditorPanelTest.java`

**Interfaces:**
- Consumes: `searchCurriculumCandidates(CurriculumCourseCandidateQuery)`.
- Produces: one shared selected candidate for code and name fields and a create command containing its stable id.

- [ ] **Step 1: Write failing UI model tests**

Test code search and name search independently, selecting from either field synchronizes both visible values, all metadata fields become read-only, editing either input clears the selection, and submit without a current selection is rejected.

- [ ] **Step 2: Verify UI tests fail**

```bash
mvn -pl vcampus-client -am -Dtest=CurriculumCourseEditorFieldsTest,CourseEditorPanelTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement shared selection state**

Reuse the core autocomplete popup/loading pattern while keeping two text inputs connected to one candidate object. Render candidate detail as code/name plus department, type, credits, and hours. Prevent programmatic synchronization from starting duplicate searches.

- [ ] **Step 4: Replace free-form course creation UI**

For new records show the dual search fields and locked metadata. For existing records render locked metadata directly and allow only enabled state changes. Preserve async guard and dirty-state behavior.

- [ ] **Step 5: Verify UI tests pass**

Run the command from Step 2.

- [ ] **Step 6: Commit**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/course vcampus-client/src/test/java/edu/seu/vcampus/client/course
git commit -m "feat: select catalog courses from training plans"
```

### Task 5: Unify embedded editor card styling

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseEditorCard.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseEditorPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingEditorPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingScheduleEditorPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseEditorVisualStructureTest.java`

**Interfaces:**
- Produces: shared card factory with themed surface, border, padding, bounded content width, and internal action bar.

- [ ] **Step 1: Write failing structural UI tests**

Assert course and offering editors contain a named card component, nonwhite themed background, visible compound border, bounded preferred width, an internal action bar, and a separately bordered schedule group.

- [ ] **Step 2: Verify structural tests fail**

Run `CourseEditorVisualStructureTest` and confirm missing named cards/borders.

- [ ] **Step 3: Implement and apply the card primitive**

Build a small shared Swing helper and apply it without changing save semantics. Keep the editor host replacement behavior and ensure scroll panes inherit the themed viewport background.

- [ ] **Step 4: Verify the structural tests pass**

Run the focused course UI suite.

- [ ] **Step 5: Commit**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui
git commit -m "style: align embedded course editor cards"
```

### Task 6: Restore the college transfer workspace

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferBatchChoiceRenderer.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeWorkspaceView.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeProcessingPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeActions.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/MajorTransferCollegeWorkspaceTest.java`

**Interfaces:**
- Uses `nineloong`'s stable toolbar/list/detail/footer structure.
- Uses current `EmbeddedEditorHost.showEditor(...)` only to replace the complete stable workspace during editing.

- [ ] **Step 1: Write failing regression tests from the screenshot**

Assert the batch combo never contains `MajorTransferBatchView[` text, the split pane has two named scrollable cards with nonzero minimum sizes, the empty detail card shows a prompt, switching batches clears detail/actions, and stale async results are ignored.

- [ ] **Step 2: Verify regression tests fail**

```bash
mvn -pl vcampus-client -am -Dtest=MajorTransferCollegeWorkspaceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Restore stable composition and readable rendering**

Extract the list/detail view, add the batch renderer, explicit divider/minimum dimensions, empty state, and request sequence guards. Ensure the host wraps exactly one complete list workspace rather than nested split panes.

- [ ] **Step 4: Verify transfer UI and role tests pass**

Run `MajorTransferCollegeWorkspaceTest`, `MajorTransferRoleUiTest`, and `StudentModulePageFactoryTest`.

- [ ] **Step 5: Commit**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer
git commit -m "fix: restore college transfer workspace layout"
```

### Task 7: Package and screenshot-review real role flows

**Files:**
- Modify: `vcampus-distribution/lib/vCampusClient.jar`
- Modify: `vcampus-distribution/lib/vCampusServer.jar`
- Modify: `vCampus-release/lib/vCampusClient.jar`
- Modify: `vCampus-release/lib/vCampusServer.jar`
- Create: `docs/ui-review/2026-09-16-course-and-transfer/course-search-code.png`
- Create: `docs/ui-review/2026-09-16-course-and-transfer/course-search-name.png`
- Create: `docs/ui-review/2026-09-16-course-and-transfer/course-selected.png`
- Create: `docs/ui-review/2026-09-16-course-and-transfer/offering-create.png`
- Create: `docs/ui-review/2026-09-16-course-and-transfer/offering-edit.png`
- Create: `docs/ui-review/2026-09-16-course-and-transfer/transfer-populated.png`
- Create: `docs/ui-review/2026-09-16-course-and-transfer/transfer-empty.png`

**Interfaces:**
- Uses repository launch scripts and representative `COURSE_ADMIN` and `CS_COLLEGE_ADMIN` accounts.

- [ ] **Step 1: Run focused suites and source checks**

Run all tests named above, `git diff --check`, and verify every new Java file is at most 200 lines.

- [ ] **Step 2: Run the full Maven test suite**

```bash
mvn test
```

Record any pre-existing database-fixture failures separately; do not alter the requested runtime database to satisfy stale fixtures.

- [ ] **Step 3: Build both release packages**

```bash
mvn -DskipTests package
```

Copy the generated distribution JARs to `vCampus-release/lib` and verify matching hashes.

- [ ] **Step 4: Start against a disposable Access copy**

Do not mutate the user's active `vcampus-distribution/data/vCampus.accdb`. Launch the server with a temporary copy and point a temporary client configuration at `127.0.0.1`.

- [ ] **Step 5: Capture and inspect UI states**

Capture at least: new course search by code, new course search by name, selected locked course metadata, new offering editor, edited offering editor, college transfer populated workspace, and college transfer empty state. Check for overlap, raw object text, white edge bands, clipped controls, missing borders, and inaccessible actions; iterate with focused tests if any defect remains.

- [ ] **Step 6: Review exact changes and commit release artifacts**

Stage only feature source/tests/docs/screenshots and the four release JARs. Explicitly exclude local client configuration, active Access database, logs, targets, and Word temporary files.

- [ ] **Step 7: Push after verification**

Push the completed commits to `origin/main` only after the local and remote histories are rechecked.
