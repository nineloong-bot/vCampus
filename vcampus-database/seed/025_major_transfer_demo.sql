-- ============================================================
-- 025_major_transfer_demo.sql
-- Rich demo data covering the full transfer workflow
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
     '欢迎有硬件兴趣的同学申请', TRUE,
     0, #2026-02-15 10:00:00#, #2026-02-15 10:00:00#);

-- ============================================================
-- Applications in various workflow states
-- ============================================================

-- App 1: 李明(213230001) -> DRAFT (理由为空，等待学生填写)
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001021',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000104',
     'ORDINARY', 'DRAFT',
     '00000000-0000-0000-0000-000000001011',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023101', '2023',
     '李明', '',
     0, 0, #2026-03-05 09:00:00#, #2026-03-05 09:00:00#);

-- App 2: 张伟(213230002) -> SUBMITTED (目标: 软件工程)
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
     '00000000-0000-0000-0000-000000001011',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023102', '2023',
     '张伟', '希望从计科转到软工，对软件架构设计有浓厚兴趣，在校期间自学了Java和Spring框架，希望系统学习软件工程方法论。',
     0, 1, #2026-03-10 14:30:00#, #2026-03-08 10:00:00#, #2026-03-10 14:30:00#);

-- App 3: 李娜(213230003) -> SUBMITTED (目标: 数学)
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
     '00000000-0000-0000-0000-000000001012',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023103', '2023',
     '李娜', '对数学研究有强烈兴趣，高等数学和线性代数均取得95分以上成绩，希望转入数学学院深入学习纯数学方向。',
     0, 1, #2026-03-12 09:00:00#, #2026-03-11 16:00:00#, #2026-03-12 09:00:00#);

-- App 4: 王强(213230004) -> SOURCE_APPROVED (目标: 软件工程，原学院已通过)
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, sourceReviewerUserId, sourceReviewedAt, sourceComment,
     createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001024',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000212',
     'ORDINARY', 'SOURCE_APPROVED',
     '00000000-0000-0000-0000-000000001011',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023104', '2023',
     '王强', '对软件工程方向有浓厚兴趣，参加了ACM程序设计竞赛并获得省级奖项，希望转入软件工程专业进一步提升编程能力。',
     0, 2, #2026-03-09 11:00:00#,
     '00000000-0000-0000-0000-000000000001', #2026-03-15 09:00:00#, '学籍审核合格，无违纪记录，招生类别允许',
     #2026-03-07 14:00:00#, #2026-03-15 09:00:00#);

-- App 5: 刘洋(213230005) -> QUALIFIED (目标: 数学，两级审核都通过)
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, sourceReviewerUserId, sourceReviewedAt, sourceComment,
     qualificationReviewerUserId, qualificationReviewedAt, qualificationComment,
     createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001025',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000213',
     'ORDINARY', 'QUALIFIED',
     '00000000-0000-0000-0000-000000001012',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000103', '计算机科学与技术2301班',
     '09023105', '2023',
     '刘洋', '数学分析和概率论成绩优异，对应用数学方向有浓厚兴趣，希望转入数学学院学习金融数学方向。',
     0, 3, #2026-03-08 10:00:00#,
     '00000000-0000-0000-0000-000000000001', #2026-03-14 09:00:00#, '学籍审核合格，无违纪记录，招生类别允许',
     '00000000-0000-0000-0000-000000000001', #2026-03-16 14:00:00#, '成绩优良，符合转入条件',
     #2026-03-06 16:00:00#, #2026-03-16 14:00:00#);

-- App 6: 陈晨(213230006) -> QUALIFIED (目标: 电信，两级审核都通过)
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, sourceReviewerUserId, sourceReviewedAt, sourceComment,
     qualificationReviewerUserId, qualificationReviewedAt, qualificationComment,
     createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001026',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000214',
     'ORDINARY', 'QUALIFIED',
     '00000000-0000-0000-0000-000000001013',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000112', '软件工程',
     '00000000-0000-0000-0000-000000000114', '软件工程2301班',
     '09123101', '2023',
     '陈晨', '对嵌入式系统和硬件开发有浓厚兴趣，参加了电子设计竞赛，希望转入电子信息工程专业学习硬件与通信技术。',
     0, 3, #2026-03-10 09:00:00#,
     '00000000-0000-0000-0000-000000000001', #2026-03-16 09:00:00#, '学籍审核合格，无违纪记录，招生类别允许',
     '00000000-0000-0000-0000-000000000001', #2026-03-18 14:00:00#, '符合转入条件',
     #2026-03-09 11:00:00#, #2026-03-18 14:00:00#);

-- App 7: 赵敏(213230007) -> REJECTED (目标: 软件工程，原学院审核不通过)
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, sourceReviewerUserId, sourceReviewedAt, sourceComment,
     createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001027',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000215',
     'ORDINARY', 'REJECTED',
     '00000000-0000-0000-0000-000000001011',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000112', '软件工程',
     '00000000-0000-0000-0000-000000000114', '软件工程2301班',
     '09123102', '2023',
     '赵敏', '希望从软工转到计科，对底层系统开发更感兴趣。',
     0, 2, #2026-03-11 10:00:00#,
     '00000000-0000-0000-0000-000000000001', #2026-03-17 09:00:00#, '本学期有一门课程不及格，学籍审核未通过',
     #2026-03-09 15:00:00#, #2026-03-17 09:00:00#);

-- App 8: 杨帆(213240001) -> SUBMITTED (目标: 软件工程，学困生类型)
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001028',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000223',
     'DIFFICULTY', 'SUBMITTED',
     '00000000-0000-0000-0000-000000001011',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000102', '计算机科学与技术',
     '00000000-0000-0000-0000-000000000105', '计算机科学与技术2302班',
     '09023201', '2023',
     '杨帆', '因个人发展方向调整，希望从计科转到软工。已提交学困生证明材料。',
     0, 1, #2026-03-15 10:00:00#, #2026-03-14 09:00:00#, #2026-03-15 10:00:00#);

-- App 9: 林峰(213230013) -> CANCELLED (目标: 软件工程，学生撤回后管理员取消)
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     submittedAt, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001029',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000221',
     'ORDINARY', 'CANCELLED',
     '00000000-0000-0000-0000-000000001011',
     '00000000-0000-0000-0000-000000000131', '信息科学与工程学院',
     '00000000-0000-0000-0000-000000000132', '电子信息工程',
     '00000000-0000-0000-0000-000000000133', '电子信息工程2301班',
     '04023101', '2023',
     '林峰', '原计划转到软工，但经过考虑决定留在电信专业深耕。',
     0, 2, #2026-03-12 14:00:00#, #2026-03-10 09:00:00#, #2026-03-20 10:00:00#);

-- App 10: 许晴(213240002) -> DRAFT (目标: 数学，年级不匹配，24级不在允许范围)
INSERT INTO tblMajorTransferApplication
    (applicationId, batchId, studentId, applicationType, applicationStatus,
     optionId, fromDepartmentId, fromDepartmentName, fromMajorId, fromMajorName,
     fromClassId, fromClassName, fromStudentNumber, fromGrade,
     studentName, reason, baseStudentVersion, applicationVersion,
     createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000001030',
     '00000000-0000-0000-0000-000000001001',
     '00000000-0000-0000-0000-000000000224',
     'ORDINARY', 'DRAFT',
     '00000000-0000-0000-0000-000000001012',
     '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
     '00000000-0000-0000-0000-000000000112', '软件工程',
     '00000000-0000-0000-0000-000000000116', '软件工程2401班',
     '09124101', '2024',
     '许晴', '',
     0, 0, #2026-03-20 10:00:00#, #2026-03-20 10:00:00#);

-- ── Review records for completed reviews ──

-- App 4 (王强): source review
INSERT INTO tblMajorTransferReview
    (reviewId, applicationId, reviewStage, decision, reviewerUserId, comment,
     sourceVerified, noMisconduct, admissionAllowed, createdAt)
VALUES
    ('00000000-0000-0000-0000-000000001040',
     '00000000-0000-0000-0000-000000001024',
     'SOURCE_REVIEW', 'APPROVE',
     '00000000-0000-0000-0000-000000000001',
     '学籍审核合格，无违纪记录，招生类别允许',
     TRUE, TRUE, TRUE, #2026-03-15 09:00:00#);

-- App 5 (刘洋): source + qualification reviews
INSERT INTO tblMajorTransferReview
    (reviewId, applicationId, reviewStage, decision, reviewerUserId, comment,
     sourceVerified, noMisconduct, admissionAllowed, createdAt)
VALUES
    ('00000000-0000-0000-0000-000000001041',
     '00000000-0000-0000-0000-000000001025',
     'SOURCE_REVIEW', 'APPROVE',
     '00000000-0000-0000-0000-000000000001',
     '学籍审核合格，无违纪记录，招生类别允许',
     TRUE, TRUE, TRUE, #2026-03-14 09:00:00#);

INSERT INTO tblMajorTransferReview
    (reviewId, applicationId, reviewStage, decision, reviewerUserId, comment,
     sourceVerified, noMisconduct, admissionAllowed, createdAt)
VALUES
    ('00000000-0000-0000-0000-000000001042',
     '00000000-0000-0000-0000-000000001025',
     'QUALIFICATION_REVIEW', 'APPROVE',
     '00000000-0000-0000-0000-000000000001',
     '成绩优良，符合转入条件',
     NULL, NULL, NULL, #2026-03-16 14:00:00#);

-- App 6 (陈晨): source + qualification reviews
INSERT INTO tblMajorTransferReview
    (reviewId, applicationId, reviewStage, decision, reviewerUserId, comment,
     sourceVerified, noMisconduct, admissionAllowed, createdAt)
VALUES
    ('00000000-0000-0000-0000-000000001043',
     '00000000-0000-0000-0000-000000001026',
     'SOURCE_REVIEW', 'APPROVE',
     '00000000-0000-0000-0000-000000000001',
     '学籍审核合格，无违纪记录，招生类别允许',
     TRUE, TRUE, TRUE, #2026-03-16 09:00:00#);

INSERT INTO tblMajorTransferReview
    (reviewId, applicationId, reviewStage, decision, reviewerUserId, comment,
     sourceVerified, noMisconduct, admissionAllowed, createdAt)
VALUES
    ('00000000-0000-0000-0000-000000001044',
     '00000000-0000-0000-0000-000000001026',
     'QUALIFICATION_REVIEW', 'APPROVE',
     '00000000-0000-0000-0000-000000000001',
     '符合转入条件',
     NULL, NULL, NULL, #2026-03-18 14:00:00#);

-- App 7 (赵敏): source review rejected
INSERT INTO tblMajorTransferReview
    (reviewId, applicationId, reviewStage, decision, reviewerUserId, comment,
     sourceVerified, noMisconduct, admissionAllowed, createdAt)
VALUES
    ('00000000-0000-0000-0000-000000001045',
     '00000000-0000-0000-0000-000000001027',
     'SOURCE_REVIEW', 'REJECT',
     '00000000-0000-0000-0000-000000000001',
     '本学期有一门课程不及格，学籍审核未通过',
     TRUE, TRUE, TRUE, #2026-03-17 09:00:00#);

-- App 9 (林峰): cancel review
INSERT INTO tblMajorTransferReview
    (reviewId, applicationId, reviewStage, decision, reviewerUserId, comment,
     sourceVerified, noMisconduct, admissionAllowed, createdAt)
VALUES
    ('00000000-0000-0000-0000-000000001046',
     '00000000-0000-0000-0000-000000001029',
     'SOURCE_REVIEW', 'REJECT',
     '00000000-0000-0000-0000-000000000001',
     '学生本人申请取消转专业申请',
     NULL, NULL, NULL, #2026-03-20 10:00:00#);
