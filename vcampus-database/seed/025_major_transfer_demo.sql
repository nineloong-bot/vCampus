-- ============================================================
-- 025_major_transfer_demo.sql
-- Transfer batch and option catalog, with a batch of submitted
-- applications targeting Electronic Information Engineering
-- ============================================================

-- ── Batch 1: CLOSED (historical) ──
INSERT INTO tblMajorTransferBatch
    (batchId, batchName, batchStatus, applicationStart, applicationEnd,
     publicityStart, publicityEnd, effectiveDate, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001000',
     '2025-2026学年秋季转专业',
     'CLOSED',
     #2025-09-01 00:00:00#,
     #2025-09-30 23:59:59#,
     #2025-10-15 00:00:00#,
     #2025-10-22 23:59:59#,
     #2025-11-01 00:00:00#,
     1, #2025-08-15 10:00:00#, #2025-11-01 10:00:00#);

-- ── Batch 2: OPEN (current) ──
INSERT INTO tblMajorTransferBatch
    (batchId, batchName, batchStatus, applicationStart, applicationEnd,
     publicityStart, publicityEnd, effectiveDate, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001001',
     '2025-2026学年春季转专业',
     'OPEN',
     #2026-03-01 00:00:00#,
     #2026-12-31 23:59:59#,
     NULL, NULL, NULL,
     0, #2026-02-15 10:00:00#, #2026-02-15 10:00:00#);

-- ── Options for Batch 2 ──
-- Option A: 计科 -> 软件工程 (名额5)
INSERT INTO tblMajorTransferOption
    (optionId, batchId, targetMajorId, targetDepartmentId,
     targetMajorName, targetDepartmentName, grades,
     receiveQuota, interviewQuota, writtenPassScore, interviewPassScore,
     writtenWeightPct, interviewWeightPct, difficultyQuotaExempt,
     requirements, isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001011',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000112',
     '00000000-0000-0000-0000-000000000101',
     '软件工程', '计算机科学与工程学院', '1,2',
     5, 3, 60, 60,
     60, 40, FALSE,
     '申请转入软件工程专业，需通过笔试和面试', TRUE,
     0, #2026-02-15 10:00:00#, #2026-02-15 10:00:00#);

-- Option B: 计科 -> 数学与应用数学 (名额3)
INSERT INTO tblMajorTransferOption
    (optionId, batchId, targetMajorId, targetDepartmentId,
     targetMajorName, targetDepartmentName, grades,
     receiveQuota, interviewQuota, writtenPassScore, interviewPassScore,
     writtenWeightPct, interviewWeightPct, difficultyQuotaExempt,
     requirements, isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001012',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000113',
     '00000000-0000-0000-0000-000000000111',
     '数学与应用数学', '数学学院', '1,2',
     3, 2, 65, 60,
     50, 50, FALSE,
     '申请转入数学与应用数学专业', TRUE,
     0, #2026-02-15 10:00:00#, #2026-02-15 10:00:00#);

-- Option C: 计科 -> 电子信息工程 (名额4)
INSERT INTO tblMajorTransferOption
    (optionId, batchId, targetMajorId, targetDepartmentId,
     targetMajorName, targetDepartmentName, grades,
     receiveQuota, interviewQuota, writtenPassScore, interviewPassScore,
     writtenWeightPct, interviewWeightPct, difficultyQuotaExempt,
     requirements, isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000132',
     '00000000-0000-0000-0000-000000000131',
     '电子信息工程', '信息科学与工程学院', '1,2',
     4, 3, 55, 55,
     60, 40, FALSE,
     '欢迎有硬件与通信兴趣的同学申请', TRUE,
     0, #2026-02-15 10:00:00#, #2026-02-15 10:00:00#);

-- ============================================================
-- Submitted Applications targeting 电子信息工程 (Option C)
-- All applicants are eligible 2023 undergraduate students from 计科
-- ============================================================

-- App 1: 李明(09023101) -> SUBMITTED
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001021',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000104',
     'ORDINARY', 'SUBMITTED',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023101', '2023',
     '李明', '对硬件系统与嵌入式软件开发兴趣浓厚，希望转入电子信息工程专业深入学习电路与通信技术。',
     0, 1, #2026-03-05 10:00:00#, #2026-03-05 09:00:00#, #2026-03-05 10:00:00#);

-- App 2: 张伟(09023102) -> SUBMITTED
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001022',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000210',
     'ORDINARY', 'SUBMITTED',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023102', '2023',
     '张伟', '希望从事软硬件协同开发，在校自学了单片机和数字电路，申请转入电子信息工程专业。',
     0, 1, #2026-03-06 11:30:00#, #2026-03-06 10:00:00#, #2026-03-06 11:30:00#);

-- App 3: 李娜(09023103) -> SUBMITTED
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001023',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000211',
     'ORDINARY', 'SUBMITTED',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023103', '2023',
     '李娜', '对信号处理和现代通信系统有极高热情，高数与线代成绩优秀，申请转入电子信息工程专业。',
     0, 1, #2026-03-07 14:00:00#, #2026-03-07 12:00:00#, #2026-03-07 14:00:00#);

-- App 4: 王强(09023104) -> SUBMITTED
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001024',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000212',
     'ORDINARY', 'SUBMITTED',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023104', '2023',
     '王强', '积极参与电子设计竞赛，对微控制器及传感器通信兴趣浓厚，申请转入电子信息工程。',
     0, 1, #2026-03-08 09:30:00#, #2026-03-08 09:00:00#, #2026-03-08 09:30:00#);

-- App 5: 刘洋(09023105) -> SUBMITTED
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001025',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000213',
     'ORDINARY', 'SUBMITTED',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023105', '2023',
     '刘洋', '立志从事智能硬件与物联网感知技术研究，申请转入电信工程专业。',
     0, 1, #2026-03-09 15:20:00#, #2026-03-09 14:00:00#, #2026-03-09 15:20:00#);

-- App 6: 陈晨(09023106) -> SUBMITTED
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001026',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000214',
     'ORDINARY', 'SUBMITTED',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023106', '2023',
     '陈晨', '对无线通信与高频电路有深厚兴趣，申请转入电子信息工程专业。',
     0, 1, #2026-03-10 10:00:00#, #2026-03-10 09:30:00#, #2026-03-10 10:00:00#);

-- App 7: 周婷(09023107) -> SUBMITTED
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001027',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000226',
     'ORDINARY', 'SUBMITTED',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023107', '2023',
     '周婷', '希望从事智能仪器与信号处理方向，特申请转入电子信息工程专业。',
     0, 1, #2026-03-11 16:45:00#, #2026-03-11 16:00:00#, #2026-03-11 16:45:00#);

-- App 8: 吴浩(09023108) -> SUBMITTED
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001028',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000227',
     'ORDINARY', 'SUBMITTED',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023108', '2023',
     '吴浩', '对集成电路设计与数字电子技术充满向往，申请转入电信专业系统学习。',
     0, 1, #2026-03-12 11:10:00#, #2026-03-12 10:30:00#, #2026-03-12 11:10:00#);

-- App 9: 孙丽(09023109) -> SUBMITTED
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001029',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000228',
     'ORDINARY', 'SUBMITTED',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023109', '2023',
     '孙丽', '对现代通信网络与信息传输算法有浓厚研究兴趣，申请转专业。',
     0, 1, #2026-03-13 14:30:00#, #2026-03-13 14:00:00#, #2026-03-13 14:30:00#);

-- App 10: 郑鑫(09023110) -> SUBMITTED
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001030',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000229',
     'ORDINARY', 'SUBMITTED',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023110', '2023',
     '郑鑫', '希望在信息与通信工程学科进一步发展，申请转入电子信息工程专业。',
     0, 1, #2026-03-14 09:15:00#, #2026-03-14 08:30:00#, #2026-03-14 09:15:00#);

-- App 11: 朱琳(09023111) -> DRAFT (用于测试草稿隔离)
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001031',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000230',
     'ORDINARY', 'DRAFT',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023111', '2023',
     '朱琳', '',
     0, 0, #2026-03-15 10:00:00#, #2026-03-15 10:00:00#);

-- 历史演示申请与当前学生/专业快照已不再匹配；重建数据库时同步清除，
-- 避免脏数据再次进入运行库。正式测试场景由 full-test-data 生成器提供。
DELETE FROM tblMajorTransferAttachment WHERE applicationId IN
    ('00000000-0000-0000-0000-000000001021', '00000000-0000-0000-0000-000000001022',
     '00000000-0000-0000-0000-000000001023', '00000000-0000-0000-0000-000000001024',
     '00000000-0000-0000-0000-000000001025', '00000000-0000-0000-0000-000000001026',
     '00000000-0000-0000-0000-000000001027', '00000000-0000-0000-0000-000000001028',
     '00000000-0000-0000-0000-000000001029', '00000000-0000-0000-0000-000000001030',
     '00000000-0000-0000-0000-000000001031');
DELETE FROM tblMajorTransferReview WHERE applicationId IN
    ('00000000-0000-0000-0000-000000001021', '00000000-0000-0000-0000-000000001022',
     '00000000-0000-0000-0000-000000001023', '00000000-0000-0000-0000-000000001024',
     '00000000-0000-0000-0000-000000001025', '00000000-0000-0000-0000-000000001026',
     '00000000-0000-0000-0000-000000001027', '00000000-0000-0000-0000-000000001028',
     '00000000-0000-0000-0000-000000001029', '00000000-0000-0000-0000-000000001030',
     '00000000-0000-0000-0000-000000001031');
DELETE FROM tblMajorTransferExecution WHERE applicationId IN
    ('00000000-0000-0000-0000-000000001021', '00000000-0000-0000-0000-000000001022',
     '00000000-0000-0000-0000-000000001023', '00000000-0000-0000-0000-000000001024',
     '00000000-0000-0000-0000-000000001025', '00000000-0000-0000-0000-000000001026',
     '00000000-0000-0000-0000-000000001027', '00000000-0000-0000-0000-000000001028',
     '00000000-0000-0000-0000-000000001029', '00000000-0000-0000-0000-000000001030',
     '00000000-0000-0000-0000-000000001031');
DELETE FROM tblMajorTransferApplication WHERE applicationId IN
    ('00000000-0000-0000-0000-000000001021', '00000000-0000-0000-0000-000000001022',
     '00000000-0000-0000-0000-000000001023', '00000000-0000-0000-0000-000000001024',
     '00000000-0000-0000-0000-000000001025', '00000000-0000-0000-0000-000000001026',
     '00000000-0000-0000-0000-000000001027', '00000000-0000-0000-0000-000000001028',
     '00000000-0000-0000-0000-000000001029', '00000000-0000-0000-0000-000000001030',
     '00000000-0000-0000-0000-000000001031');
