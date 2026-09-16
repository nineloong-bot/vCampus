-- One-time convergence from the original college identifiers to the canonical organization tree.
INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('MIGRATION:CANONICAL_ORG_V1', 1, 1, 0, NOW());

UPDATE tblStudentCollegeAdministrator SET departmentId='bulk-dept-01' WHERE departmentId='00000000-0000-0000-0000-000000000101';
UPDATE tblStudentCollegeAdministrator SET departmentId='bulk-dept-02' WHERE departmentId='00000000-0000-0000-0000-000000000111';
UPDATE tblStudentCollegeAdministrator SET departmentId='bulk-dept-09' WHERE departmentId='00000000-0000-0000-0000-000000000131';
UPDATE tblStudentCollegeAdministrator SET departmentId='bulk-dept-03' WHERE departmentId='00000000-0000-0000-0000-000000000141';

UPDATE tblClass SET majorId='bulk-major-02' WHERE majorId='00000000-0000-0000-0000-000000000102';
UPDATE tblClass SET majorId='bulk-major-01' WHERE majorId='00000000-0000-0000-0000-000000000112';
UPDATE tblClass SET majorId='bulk-major-03' WHERE majorId='00000000-0000-0000-0000-000000000113';
UPDATE tblClass SET majorId='bulk-major-12' WHERE majorId='00000000-0000-0000-0000-000000000132';
UPDATE tblClass SET majorId='bulk-major-05' WHERE majorId='00000000-0000-0000-0000-000000000142';

UPDATE tblStudent SET classId='00000000-0000-0000-0000-000000000106', enrollmentDate=#2024-09-01# WHERE userId='00000000-0000-0000-0000-000000000023';
UPDATE tblStudent SET classId='00000000-0000-0000-0000-000000000106', enrollmentDate=#2024-09-01# WHERE userId='00000000-0000-0000-0000-000000000403';

UPDATE tblCourse SET departmentId='bulk-dept-01', departmentName='计算机学院' WHERE departmentId='00000000-0000-0000-0000-000000000101';
UPDATE tblCourse SET departmentId='bulk-dept-02', departmentName='数学学院' WHERE departmentId='00000000-0000-0000-0000-000000000111';
UPDATE tblCourse SET departmentId='bulk-dept-09', departmentName='信息科学与工程学院' WHERE departmentId='00000000-0000-0000-0000-000000000131';
UPDATE tblCourse SET departmentId='bulk-dept-03', departmentName='外国语学院' WHERE departmentId='00000000-0000-0000-0000-000000000141';

UPDATE tblMajorTransferOption SET targetDepartmentId='bulk-dept-01', targetDepartmentName='计算机学院', targetMajorId='bulk-major-02', targetMajorName='计算机科学' WHERE targetMajorId='00000000-0000-0000-0000-000000000102';
UPDATE tblMajorTransferOption SET targetDepartmentId='bulk-dept-01', targetDepartmentName='计算机学院', targetMajorId='bulk-major-01', targetMajorName='软件工程' WHERE targetMajorId='00000000-0000-0000-0000-000000000112';
UPDATE tblMajorTransferOption SET targetDepartmentId='bulk-dept-02', targetDepartmentName='数学学院', targetMajorId='bulk-major-03', targetMajorName='数学应用' WHERE targetMajorId='00000000-0000-0000-0000-000000000113';
UPDATE tblMajorTransferOption SET targetDepartmentId='bulk-dept-09', targetDepartmentName='信息科学与工程学院', targetMajorId='bulk-major-12', targetMajorName='电子信息科学' WHERE targetMajorId='00000000-0000-0000-0000-000000000132';
UPDATE tblMajorTransferOption SET targetDepartmentId='bulk-dept-03', targetDepartmentName='外国语学院', targetMajorId='bulk-major-05', targetMajorName='英语' WHERE targetMajorId='00000000-0000-0000-0000-000000000142';

UPDATE tblMajorTransferApplication SET fromDepartmentId='bulk-dept-01', fromDepartmentName='计算机学院', fromMajorId='bulk-major-02', fromMajorName='计算机科学' WHERE fromMajorId='00000000-0000-0000-0000-000000000102';
UPDATE tblMajorTransferApplication SET fromDepartmentId='bulk-dept-01', fromDepartmentName='计算机学院', fromMajorId='bulk-major-01', fromMajorName='软件工程' WHERE fromMajorId='00000000-0000-0000-0000-000000000112';
UPDATE tblMajorTransferApplication SET fromDepartmentId='bulk-dept-02', fromDepartmentName='数学学院', fromMajorId='bulk-major-03', fromMajorName='数学应用' WHERE fromMajorId='00000000-0000-0000-0000-000000000113';
UPDATE tblMajorTransferApplication SET fromDepartmentId='bulk-dept-09', fromDepartmentName='信息科学与工程学院', fromMajorId='bulk-major-12', fromMajorName='电子信息科学' WHERE fromMajorId='00000000-0000-0000-0000-000000000132';
UPDATE tblMajorTransferApplication SET fromDepartmentId='bulk-dept-03', fromDepartmentName='外国语学院', fromMajorId='bulk-major-05', fromMajorName='英语' WHERE fromMajorId='00000000-0000-0000-0000-000000000142';

UPDATE tblMajorTransferExecution SET toDepartmentId='bulk-dept-01', toDepartmentName='计算机学院', toMajorId='bulk-major-02', toMajorName='计算机科学' WHERE toMajorId='00000000-0000-0000-0000-000000000102';
UPDATE tblMajorTransferExecution SET toDepartmentId='bulk-dept-02', toDepartmentName='数学学院', toMajorId='bulk-major-03', toMajorName='数学应用' WHERE toMajorId='00000000-0000-0000-0000-000000000113';
UPDATE tblMajorTransferExecution SET toDepartmentId='bulk-dept-09', toDepartmentName='信息科学与工程学院', toMajorId='bulk-major-12', toMajorName='电子信息科学' WHERE toMajorId='00000000-0000-0000-0000-000000000132';
UPDATE tblMajorTransferExecution SET toDepartmentId='bulk-dept-03', toDepartmentName='外国语学院', toMajorId='bulk-major-05', toMajorName='英语' WHERE toMajorId='00000000-0000-0000-0000-000000000142';

UPDATE tblCrossCourseApplication SET offeringDepartmentId='bulk-dept-02', offeringDepartmentName='数学学院' WHERE offeringDepartmentId='00000000-0000-0000-0000-000000000111';
UPDATE tblCrossCourseApplication SET offeringDepartmentId='bulk-dept-09', offeringDepartmentName='信息科学与工程学院' WHERE offeringDepartmentId='00000000-0000-0000-0000-000000000131';
UPDATE tblCrossCourseApplication SET targetDepartmentId='bulk-dept-01', targetDepartmentName='计算机学院' WHERE targetDepartmentId='00000000-0000-0000-0000-000000000101';

DELETE FROM tblCrossCourseApplication WHERE targetPlanId='00000000-0000-0000-0000-000000000301';
DELETE FROM tblStudentGrade WHERE planCourseId IN (SELECT planCourseId FROM tblTrainingPlanCourse WHERE planId='00000000-0000-0000-0000-000000000301');
DELETE FROM tblTrainingPlanPrerequisite WHERE planId='00000000-0000-0000-0000-000000000301';
DELETE FROM tblTrainingPlanCourse WHERE planId='00000000-0000-0000-0000-000000000301';
DELETE FROM tblTrainingPlan WHERE planId='00000000-0000-0000-0000-000000000301';

DELETE FROM tblMajor WHERE majorId IN ('00000000-0000-0000-0000-000000000102','00000000-0000-0000-0000-000000000112','00000000-0000-0000-0000-000000000113','00000000-0000-0000-0000-000000000132','00000000-0000-0000-0000-000000000142');
DELETE FROM tblDepartment WHERE departmentId IN ('00000000-0000-0000-0000-000000000101','00000000-0000-0000-0000-000000000111','00000000-0000-0000-0000-000000000131','00000000-0000-0000-0000-000000000141');
