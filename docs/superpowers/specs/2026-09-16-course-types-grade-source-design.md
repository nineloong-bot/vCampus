# Course Types and Grade Source Design

## Goal

Make the training-plan module the single source of truth for course type and completed-course results used by course selection.

## Course type contract

The only supported values are `REQUIRED`, `ELECTIVE`, and `CROSS_DISCIPLINARY`, matching `edu.seu.vcampus.common.student.CourseType`. Student selection displays them as “必修”, “选修”, and “跨学科”. Query validation, server filtering, DTO validation, and Swing labels use the same values. No conversion to “限选” or “任选” remains.

## Academic result boundary

The student module exposes a read-only `StudentAcademicRecordQueryPort`. It resolves grade rows by internal `studentId` and canonical catalog `courseId`, following `tblStudentGrade -> tblTrainingPlanCourse`. The course module consumes those results through a narrow `CourseAcademicRecordGateway`; it never imports a student repository.

Passed-course exclusion, prerequisite checks, retake eligibility, and normal-versus-retake selection all use this gateway. The production composition wires the existing student grade service into the course gateway. Course-owned `tblCourseAttempt` is removed from the schema and generated data; `tblStudentGrade` is the only source of pass/fail truth.

## Removing manual outcome import

Remove the outcome-import Swing pages, gateway/client calls, common command DTO, course service operation, and demo seeding through that operation. No course-administrator navigation entry or callable protocol remains. Grade entry stays exclusively in the student module’s grade-management flow.

## Verification

Tests cover all three course-type values in DTO validation, filter mapping, and row labels; prove curriculum and retake decisions use the academic-record gateway; prove the course workspace has no outcome-import tab; and ensure removed protocol/client symbols no longer compile into the product.
