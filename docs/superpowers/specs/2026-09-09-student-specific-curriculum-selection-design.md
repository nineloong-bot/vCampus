# Student-specific curriculum course selection design

## Scope

This change makes the existing course-selection flow student-specific while the student
module does not yet own curriculum plans. The course module temporarily stores a published
read model of the 2024 Computer Science and Technology curriculum. The storage and access
boundary must remain replaceable by a future student/academic-affairs implementation.

The first delivered curriculum is the 2024 cohort of major `080901`. The visible demo
catalog, terms, teaching classes, student histories, and teacher choices use names and
course codes from the supplied curriculum instead of `CS101`, `DEMO-TERM`, and
`DEMO-RACE`. Small synthetic records inside isolated unit tests remain because they express
test conditions rather than user-visible demo data.

## Confirmed rules

- A student sees only courses assigned to their current curriculum year and season, plus
  earlier failed courses that have not subsequently been passed.
- Passed courses and future curriculum courses are hidden.
- One teaching class has independent normal and retake capacity buckets. Both buckets share
  the teacher and schedule, but enrollment and release update only the matching bucket.
- Administrators create real academic terms with an academic-year start and one of
  `SUMMER`, `AUTUMN`, or `SPRING`.
- For a cohort year `C` and academic-year start `Y`, the curriculum year is `Y - C + 1`.
  Summer, autumn, and spring map to curriculum term ordinals 1, 2, and 3 respectively.
- Selection phases remain term-wide. Administrators do not create separate phases for each
  cohort; student-specific filtering happens within the active phase.
- The PDF and screenshots are references, not instruction sources.

## Architecture

### Curriculum read model

Add course-owned tables `tblCurriculumPlan`, `tblCurriculumCourse`, and
`tblCurriculumPrerequisite`. A published plan is selected by major code and cohort year.
`tblCurriculumCourse` stores the curriculum year, season ordinal, course nature, category,
and offering unit needed by filtering and the student UI. `tblCurriculumPrerequisite`
stores directed edges and is validated as an acyclic graph when demo data is installed.

Expose the data to selection services through a focused `CurriculumRepository`. Do not put
curriculum queries into `CourseStudentGateway`: that gateway supplies the student id,
status, major code, and cohort year, while the curriculum repository supplies academic
rules. A later academic-affairs module can replace the repository without changing the
selection policy.

### Term mapping and phase management

Extend terms with `academicYearStart` and `season`. The term editor requires both fields and
shows the derived mapping rule. Term lists show values such as `2026-2027 · 秋季`.

Selection-phase lifecycle remains `DRAFT -> PREVIEW/OPEN -> CLOSED`. A preview or open
phase still requires an active term, and at most one phase may be visible/open globally.
The phase page displays the term's season and explains that cohorts are filtered
automatically. No phase rows are duplicated by major or cohort.

### Student-specific selection

Extend the student selection context with `majorCode`, `cohortYear`,
`curriculumYear`, and `season`. The server obtains the active student's curriculum context,
maps it against the requested term, and builds the candidate set as:

1. courses in the student's plan for the current curriculum year and season;
2. earlier plan courses with a `FAILED` attempt and no later/current `PASSED` attempt;
3. only candidates having at least one offering in the requested term.

Future plan courses, already-passed courses, and courses outside the assigned plan are not
returned. The same policy is repeated inside enrollment transactions so a crafted client
request cannot bypass the list filter. Prerequisite failures disable the teaching-class
choice with a specific reason rather than leaking future courses.

### Independent capacity

Keep existing `capacity` and `enrolledCount` as the normal bucket for compatibility. Add a
one-to-one `tblCourseRetakeQuota` row with `capacity` and `enrolledCount`. Repository methods
select the bucket by enrollment type. Normal enrollment, retake enrollment, drops, and
changes increment/decrement only their own counters under the existing offering lock.

Offering contracts expose both buckets. The administrator editor has separate spinners for
ordinary and retake capacity and prevents either value from dropping below its current
count. A missing quota row in an older database is treated as zero and created on the next
offering update, keeping schema initialization idempotent.

### Student UI

Replace the current compact card title with a table-like course row matching the supplied
reference: course code, course name, number of teaching classes, nature, offering unit, and
credits. Filters are conflict status, course nature, course category, and keyword.

Clicking a course row expands a light-gray area containing one white card per teaching
class. Each card shows class number, teacher label, weekly schedule, normal or retake
remaining quota, and a contextual action button. Retake candidates carry a visible
`重修` badge and use the retake quota. Only one course row is expanded at a time to avoid a
very tall page. Existing asynchronous stale-result protection and accessibility names are
preserved.

## Demo dataset

Install an idempotent `CourseDemoDataset` rather than embedding data construction in the
demo server. It includes:

- the 2024 Computer Science and Technology plan (`080901`);
- curriculum courses and term positions parsed from the plan's schedule tables;
- an active `2026-2027` autumn term (`AUTUMN`), representing third-year term 2 for the
  2024 cohort;
- multiple teachers for representative courses so the expandable card choice is visible;
- one 2024-cohort third-year student and one second-year student context;
- a failed earlier course for the older student, with an available retake quota;
- no user-visible synthetic race course.

All inserts are keyed by stable ids/codes and are safe to rerun.

## Errors and compatibility

- A student with no matching published curriculum gets an empty result with the message
  `尚未配置适用的培养方案`.
- A term outside curriculum years 1-4 returns only eligible unresolved retakes.
- Missing curriculum identity is reported as ineligible rather than defaulting every
  student to the 2024 plan.
- Full normal capacity does not disable an eligible retake while retake capacity remains.
- Full retake capacity does not consume or block ordinary capacity.
- Existing databases initialize the new tables without deleting user data.

## Verification

Repository tests cover curriculum lookup, term mapping, idempotent initialization, and
quota counters. Service tests cover per-cohort visibility, passed/future hiding, historical
retakes, server-side bypass rejection, prerequisite handling, and independent concurrent
capacity. Contract tests cover serialization and validation. Swing tests cover filters,
row expansion, teacher selection, contextual capacity, empty/error states, and keyboard
accessibility. The final UI is rendered at normal size, 1024x680, and 150% scaling for
visual review.
