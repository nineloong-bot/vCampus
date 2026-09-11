-- ============================================================
-- 021_more_students.sql
-- Departments, majors, classes, and student accounts
-- All passwords are "123456"
-- ============================================================

-- ── Departments ──
INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000111', 'MATH', '数学学院', TRUE, 0);

INSERT INTO tblStudentCollegeAdministrator
    (departmentId, userId, isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000111',
     '00000000-0000-0000-0000-000000000203', TRUE, 0, NOW(), NOW());

INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000131', 'EE', '信息科学与工程学院', TRUE, 0);

INSERT INTO tblStudentCollegeAdministrator
    (departmentId, userId, isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000131',
     '00000000-0000-0000-0000-000000000208', TRUE, 0, NOW(), NOW());

INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000141', 'FL', '外国语学院', TRUE, 0);

INSERT INTO tblStudentCollegeAdministrator
    (departmentId, userId, isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000141',
     '00000000-0000-0000-0000-000000000209', TRUE, 0, NOW(), NOW());

-- ── Majors ──
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000112', '00000000-0000-0000-0000-000000000101',
        '091', '软件工程', '1,2,3,4', TRUE, 0);

INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000113', '00000000-0000-0000-0000-000000000111',
        '070', '数学与应用数学', '1,2,3', TRUE, 0);

INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000132', '00000000-0000-0000-0000-000000000131',
        '040', '电子信息工程', '1,2,3,4', TRUE, 0);

INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000142', '00000000-0000-0000-0000-000000000141',
        '050', '英语', '1,2,3,4', TRUE, 0);

-- ── Classes ──
-- 软件工程2301
INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000114', '00000000-0000-0000-0000-000000000112',
        '091-2023-01', '软件工程2301班', 2023, 1, TRUE, 0);

-- 计科2302
INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000102',
        '090-2023-02', '计算机科学与技术2302班', 2023, 2, TRUE, 0);

-- 数学2301
INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000115', '00000000-0000-0000-0000-000000000113',
        '070-2023-01', '数学与应用数学2301班', 2023, 1, TRUE, 0);

-- 电子信息工程2301
INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000133', '00000000-0000-0000-0000-000000000132',
        '040-2023-01', '电子信息工程2301班', 2023, 1, TRUE, 0);

-- 英语2301
INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000143', '00000000-0000-0000-0000-000000000142',
        '050-2023-01', '英语2301班', 2023, 1, TRUE, 0);

-- 软件工程2401
INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000116', '00000000-0000-0000-0000-000000000112',
        '091-2024-01', '软件工程2401班', 2024, 1, TRUE, 0);

-- 计科2401
INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000106', '00000000-0000-0000-0000-000000000102',
        '090-2024-01', '计算机科学与技术2401班', 2024, 1, TRUE, 0);

-- 数学2401
INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000117', '00000000-0000-0000-0000-000000000113',
        '070-2024-01', '数学与应用数学2401班', 2024, 1, TRUE, 0);

-- 电信2401
INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000134', '00000000-0000-0000-0000-000000000132',
        '040-2024-01', '电子信息工程2401班', 2024, 1, TRUE, 0);

-- 英语2401
INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES ('00000000-0000-0000-0000-000000000144', '00000000-0000-0000-0000-000000000142',
        '050-2024-01', '英语2401班', 2024, 1, TRUE, 0);

-- ============================================================
-- Original16 students (existing accounts, preserved)
-- ============================================================

-- 213230002 张伟 计科2301
INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000010', '213230002',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000011', '213230003',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000012', '213230004',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000013', '213230005',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000014', '213230006',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000015', '213230007',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000016', '213230008',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000017', '213230009',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000018', '213230010',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000019', '213230011',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000020', '213230012',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000021', '213230013',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000022', '213230014',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000023', '213240001',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000024', '213240002',
    'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
    120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

-- ── Original16 students ──
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000210', '00000000-0000-0000-0000-000000000010',
    '09023102', 'UNDERGRADUATE', '张伟', '男', 'zhangwei@seu.edu.cn', '13800000002',
    '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000211', '00000000-0000-0000-0000-000000000011',
    '09023103', 'UNDERGRADUATE', '李娜', '女', 'lina@seu.edu.cn', '13800000003',
    '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000212', '00000000-0000-0000-0000-000000000012',
    '09023104', 'UNDERGRADUATE', '王强', '男', 'wangqiang@seu.edu.cn', '13800000004',
    '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000213', '00000000-0000-0000-0000-000000000013',
    '09023105', 'UNDERGRADUATE', '刘洋', '女', 'liuyang@seu.edu.cn', '13800000005',
    '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000214', '00000000-0000-0000-0000-000000000014',
    '09123101', 'UNDERGRADUATE', '陈晨', '女', 'chenchen@seu.edu.cn', '13800000006',
    '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000215', '00000000-0000-0000-0000-000000000015',
    '09123102', 'UNDERGRADUATE', '赵敏', '女', 'zhaomin@seu.edu.cn', '13800000007',
    '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000216', '00000000-0000-0000-0000-000000000016',
    '09123103', 'UNDERGRADUATE', '周杰', '男', 'zhoujie@seu.edu.cn', '13800000008',
    '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'SUSPENDED', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000217', '00000000-0000-0000-0000-000000000017',
    '09123104', 'UNDERGRADUATE', '吴桐', '男', 'wutong@seu.edu.cn', '13800000009',
    '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000218', '00000000-0000-0000-0000-000000000018',
    '07023101', 'UNDERGRADUATE', '孙悦', '女', 'sunyue@seu.edu.cn', '13800000010',
    '00000000-0000-0000-0000-000000000115', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000219', '00000000-0000-0000-0000-000000000019',
    '07023102', 'UNDERGRADUATE', '郑凯', '男', 'zhengkai@seu.edu.cn', '13800000011',
    '00000000-0000-0000-0000-000000000115', #2023-09-01#, 'GRADUATED', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000220', '00000000-0000-0000-0000-000000000020',
    '07023103', 'UNDERGRADUATE', '何雨', '女', 'heyu@seu.edu.cn', '13800000012',
    '00000000-0000-0000-0000-000000000115', #2023-09-01#, 'WITHDRAWN', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000221', '00000000-0000-0000-0000-000000000021',
    '04023101', 'UNDERGRADUATE', '林峰', '男', 'linfeng@seu.edu.cn', '13800000013',
    '00000000-0000-0000-0000-000000000133', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000222', '00000000-0000-0000-0000-000000000022',
    '05023101', 'UNDERGRADUATE', '苏雨', '女', 'suyu@seu.edu.cn', '13800000014',
    '00000000-0000-0000-0000-000000000143', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000223', '00000000-0000-0000-0000-000000000023',
    '09023201', 'UNDERGRADUATE', '杨帆', '男', 'yangfan@seu.edu.cn', '13800000015',
    '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
    classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000224', '00000000-0000-0000-0000-000000000024',
    '09124101', 'UNDERGRADUATE', '许晴', '女', 'xuqing@seu.edu.cn', '13800000016',
    '00000000-0000-0000-0000-000000000116', #2024-09-01#, 'ACTIVE', 0, NOW(), NOW());

-- ============================================================
-- 50 new students
-- Password hash/salt for "123456" (same as existing students)
-- ============================================================

-- ── 计科2301: 24 more (09023106-09023129) ──
INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000025', '213230015', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000225', '00000000-0000-0000-0000-000000000025', '09023106', 'UNDERGRADUATE', '黄磊', '男', 'huanglei@seu.edu.cn', '13800000017', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000026', '213230016', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000226', '00000000-0000-0000-0000-000000000026', '09023107', 'UNDERGRADUATE', '周婷', '女', 'zhouting@seu.edu.cn', '13800000018', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000027', '213230017', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000227', '00000000-0000-0000-0000-000000000027', '09023108', 'UNDERGRADUATE', '吴浩', '男', 'wuhao@seu.edu.cn', '13800000019', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000028', '213230018', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000228', '00000000-0000-0000-0000-000000000028', '09023109', 'UNDERGRADUATE', '孙丽', '女', 'sunli@seu.edu.cn', '13800000020', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000029', '213230019', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000229', '00000000-0000-0000-0000-000000000029', '09023110', 'UNDERGRADUATE', '郑鑫', '男', 'zhengxin@seu.edu.cn', '13800000021', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000030', '213230020', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000230', '00000000-0000-0000-0000-000000000030', '09023111', 'UNDERGRADUATE', '朱琳', '女', 'zhulin@seu.edu.cn', '13800000022', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000031', '213230021', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000231', '00000000-0000-0000-0000-000000000031', '09023112', 'UNDERGRADUATE', '陆涛', '男', 'lutao@seu.edu.cn', '13800000023', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000032', '213230022', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000232', '00000000-0000-0000-0000-000000000032', '09023113', 'UNDERGRADUATE', '马晓', '女', 'maxiao@seu.edu.cn', '13800000024', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000033', '213230023', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000233', '00000000-0000-0000-0000-000000000033', '09023114', 'UNDERGRADUATE', '胡斌', '男', 'hubin@seu.edu.cn', '13800000025', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000034', '213230024', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000234', '00000000-0000-0000-0000-000000000034', '09023115', 'UNDERGRADUATE', '高雅', '女', 'gaoya@seu.edu.cn', '13800000026', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000035', '213230025', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000235', '00000000-0000-0000-0000-000000000035', '09023116', 'UNDERGRADUATE', '罗杰', '男', 'luojie@seu.edu.cn', '13800000027', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000036', '213230026', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000236', '00000000-0000-0000-0000-000000000036', '09023117', 'UNDERGRADUATE', '梁静', '女', 'liangjing@seu.edu.cn', '13800000028', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000037', '213230027', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000237', '00000000-0000-0000-0000-000000000037', '09023118', 'UNDERGRADUATE', '韩冰', '男', 'hanbing@seu.edu.cn', '13800000029', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000038', '213230028', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000238', '00000000-0000-0000-0000-000000000038', '09023119', 'UNDERGRADUATE', '唐欣', '女', 'tangxin@seu.edu.cn', '13800000030', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000039', '213230029', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000239', '00000000-0000-0000-0000-000000000039', '09023120', 'UNDERGRADUATE', '谢鹏', '男', 'xiepeng@seu.edu.cn', '13800000031', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000040', '213230030', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000240', '00000000-0000-0000-0000-000000000040', '09023121', 'UNDERGRADUATE', '曹雪', '女', 'caoxue@seu.edu.cn', '13800000032', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000041', '213230031', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000241', '00000000-0000-0000-0000-000000000041', '09023122', 'UNDERGRADUATE', '邓超', '男', 'dengchao@seu.edu.cn', '13800000033', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000042', '213230032', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000242', '00000000-0000-0000-0000-000000000042', '09023123', 'UNDERGRADUATE', '冯瑶', '女', 'fengyao@seu.edu.cn', '13800000034', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000043', '213230033', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000243', '00000000-0000-0000-0000-000000000043', '09023124', 'UNDERGRADUATE', '蒋磊', '男', 'jianglei@seu.edu.cn', '13800000035', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000044', '213230034', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000244', '00000000-0000-0000-0000-000000000044', '09023125', 'UNDERGRADUATE', '沈洁', '女', 'shenjie@seu.edu.cn', '13800000036', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000045', '213230035', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000245', '00000000-0000-0000-0000-000000000045', '09023126', 'UNDERGRADUATE', '董亮', '男', 'dongliang@seu.edu.cn', '13800000037', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000046', '213230036', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000246', '00000000-0000-0000-0000-000000000046', '09023127', 'UNDERGRADUATE', '袁媛', '女', 'yuanyuan@seu.edu.cn', '13800000038', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000047', '213230037', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000247', '00000000-0000-0000-0000-000000000047', '09023128', 'UNDERGRADUATE', '贺翔', '男', 'hexiang@seu.edu.cn', '13800000039', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000048', '213230038', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000248', '00000000-0000-0000-0000-000000000048', '09023129', 'UNDERGRADUATE', '郭蕾', '女', 'guolei@seu.edu.cn', '13800000040', '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

-- ── 计科2302: 29 more (09023202-09023230) ──
INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000049', '213230039', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000249', '00000000-0000-0000-0000-000000000049', '09023202', 'UNDERGRADUATE', '龚伟', '男', 'gongwei@seu.edu.cn', '13800000041', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000050', '213230040', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000250', '00000000-0000-0000-0000-000000000050', '09023203', 'UNDERGRADUATE', '方圆', '女', 'fangyuan@seu.edu.cn', '13800000042', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000051', '213230041', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000251', '00000000-0000-0000-0000-000000000051', '09023204', 'UNDERGRADUATE', '潘凯', '男', 'pankai@seu.edu.cn', '13800000043', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000052', '213230042', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000252', '00000000-0000-0000-0000-000000000052', '09023205', 'UNDERGRADUATE', '丁悦', '女', 'dingyue@seu.edu.cn', '13800000044', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000053', '213230043', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000253', '00000000-0000-0000-0000-000000000053', '09023206', 'UNDERGRADUATE', '魏东', '男', 'weidong@seu.edu.cn', '13800000045', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000054', '213230044', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000254', '00000000-0000-0000-0000-000000000054', '09023207', 'UNDERGRADUATE', '任萍', '女', 'renping@seu.edu.cn', '13800000046', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000055', '213230045', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000255', '00000000-0000-0000-0000-000000000055', '09023208', 'UNDERGRADUATE', '孔明', '男', 'kongming@seu.edu.cn', '13800000047', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000056', '213230046', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000256', '00000000-0000-0000-0000-000000000056', '09023209', 'UNDERGRADUATE', '白露', '女', 'bailu@seu.edu.cn', '13800000048', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000057', '213230047', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000257', '00000000-0000-0000-0000-000000000057', '09023210', 'UNDERGRADUATE', '崔浩', '男', 'cuihao@seu.edu.cn', '13800000049', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000058', '213230048', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000258', '00000000-0000-0000-0000-000000000058', '09023211', 'UNDERGRADUATE', '秦芳', '女', 'qinfang@seu.edu.cn', '13800000050', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000059', '213230049', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000259', '00000000-0000-0000-0000-000000000059', '09023212', 'UNDERGRADUATE', '尤磊', '男', 'youlei@seu.edu.cn', '13800000051', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000060', '213230050', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000260', '00000000-0000-0000-0000-000000000060', '09023213', 'UNDERGRADUATE', '许昊', '男', 'xuhao@seu.edu.cn', '13800000052', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000061', '213230051', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000261', '00000000-0000-0000-0000-000000000061', '09023214', 'UNDERGRADUATE', '程晨', '女', 'chengchen@seu.edu.cn', '13800000053', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000062', '213230052', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000262', '00000000-0000-0000-0000-000000000062', '09023215', 'UNDERGRADUATE', '蔡勇', '男', 'caiyong@seu.edu.cn', '13800000054', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000063', '213230053', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000263', '00000000-0000-0000-0000-000000000063', '09023216', 'UNDERGRADUATE', '彭娟', '女', 'pengjuan@seu.edu.cn', '13800000055', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000064', '213230054', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000264', '00000000-0000-0000-0000-000000000064', '09023217', 'UNDERGRADUATE', '田宇', '男', 'tianyu@seu.edu.cn', '13800000056', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000065', '213230055', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000265', '00000000-0000-0000-0000-000000000065', '09023218', 'UNDERGRADUATE', '夏琳', '女', 'xialin@seu.edu.cn', '13800000057', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000066', '213230056', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000266', '00000000-0000-0000-0000-000000000066', '09023219', 'UNDERGRADUATE', '范博', '男', 'fanbo@seu.edu.cn', '13800000058', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000067', '213230057', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000267', '00000000-0000-0000-0000-000000000067', '09023220', 'UNDERGRADUATE', '江宁', '女', 'jiangning@seu.edu.cn', '13800000059', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000068', '213230058', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000268', '00000000-0000-0000-0000-000000000068', '09023221', 'UNDERGRADUATE', '钟毅', '男', 'zhongyi@seu.edu.cn', '13800000060', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000069', '213230059', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000269', '00000000-0000-0000-0000-000000000069', '09023222', 'UNDERGRADUATE', '卢佳', '女', 'lujia@seu.edu.cn', '13800000061', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000070', '213230060', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000270', '00000000-0000-0000-0000-000000000070', '09023223', 'UNDERGRADUATE', '汪涛', '男', 'wangtao@seu.edu.cn', '13800000062', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000071', '213230061', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000271', '00000000-0000-0000-0000-000000000071', '09023224', 'UNDERGRADUATE', '廖雪', '女', 'liaoxue@seu.edu.cn', '13800000063', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000072', '213230062', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000272', '00000000-0000-0000-0000-000000000072', '09023225', 'UNDERGRADUATE', '姚鹏', '男', 'yaopeng@seu.edu.cn', '13800000064', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000073', '213230063', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000273', '00000000-0000-0000-0000-000000000073', '09023226', 'UNDERGRADUATE', '崔欣', '女', 'cuixin@seu.edu.cn', '13800000065', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000074', '213230064', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000274', '00000000-0000-0000-0000-000000000074', '09023227', 'UNDERGRADUATE', '尹航', '男', 'yinhang@seu.edu.cn', '13800000066', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000075', '213230065', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000275', '00000000-0000-0000-0000-000000000075', '09023228', 'UNDERGRADUATE', '石婷', '女', 'shiting@seu.edu.cn', '13800000067', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000076', '213230066', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000276', '00000000-0000-0000-0000-000000000076', '09023229', 'UNDERGRADUATE', '秦峰', '男', 'qinfeng@seu.edu.cn', '13800000068', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000077', '213230067', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000277', '00000000-0000-0000-0000-000000000077', '09023230', 'UNDERGRADUATE', '严雪', '女', 'yanxue@seu.edu.cn', '13800000069', '00000000-0000-0000-0000-000000000105', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

-- ── 软工2301: 26 more (09123105-09123130) ──
INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000078', '213230068', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000278', '00000000-0000-0000-0000-000000000078', '09123105', 'UNDERGRADUATE', '顾明', '男', 'guming@seu.edu.cn', '13800000070', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000079', '213230069', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000279', '00000000-0000-0000-0000-000000000079', '09123106', 'UNDERGRADUATE', '侯丽', '女', 'houli@seu.edu.cn', '13800000071', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000080', '213230070', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000280', '00000000-0000-0000-0000-000000000080', '09123107', 'UNDERGRADUATE', '龚涛', '男', 'gongtao@seu.edu.cn', '13800000072', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000081', '213230071', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000281', '00000000-0000-0000-0000-000000000081', '09123108', 'UNDERGRADUATE', '沈芳', '女', 'shenfang@seu.edu.cn', '13800000073', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000082', '213230072', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000282', '00000000-0000-0000-0000-000000000082', '09123109', 'UNDERGRADUATE', '施鹏', '男', 'shipeng@seu.edu.cn', '13800000074', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000083', '213230073', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000283', '00000000-0000-0000-0000-000000000083', '09123110', 'UNDERGRADUATE', '姜雪', '女', 'jiangxue@seu.edu.cn', '13800000075', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000084', '213230074', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000284', '00000000-0000-0000-0000-000000000084', '09123111', 'UNDERGRADUATE', '范昊', '男', 'fanhao@seu.edu.cn', '13800000076', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000085', '213230075', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000285', '00000000-0000-0000-0000-000000000085', '09123112', 'UNDERGRADUATE', '洪梅', '女', 'hongmei@seu.edu.cn', '13800000077', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000086', '213230076', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000286', '00000000-0000-0000-0000-000000000086', '09123113', 'UNDERGRADUATE', '薛宁', '男', 'xuening@seu.edu.cn', '13800000078', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000087', '213230077', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000287', '00000000-0000-0000-0000-000000000087', '09123114', 'UNDERGRADUATE', '雷蕾', '女', 'leilei@seu.edu.cn', '13800000079', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000088', '213230078', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000288', '00000000-0000-0000-0000-000000000088', '09123115', 'UNDERGRADUATE', '贺磊', '男', 'helei@seu.edu.cn', '13800000080', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000089', '213230079', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000289', '00000000-0000-0000-0000-000000000089', '09123116', 'UNDERGRADUATE', '方瑜', '女', 'fangyu@seu.edu.cn', '13800000081', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000090', '213230080', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000290', '00000000-0000-0000-0000-000000000090', '09123117', 'UNDERGRADUATE', '龙飞', '男', 'longfei@seu.edu.cn', '13800000082', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000091', '213230081', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000291', '00000000-0000-0000-0000-000000000091', '09123118', 'UNDERGRADUATE', '万红', '女', 'wanhong@seu.edu.cn', '13800000083', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000092', '213230082', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000292', '00000000-0000-0000-0000-000000000092', '09123119', 'UNDERGRADUATE', '段超', '男', 'duanchao@seu.edu.cn', '13800000084', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000093', '213230083', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000293', '00000000-0000-0000-0000-000000000093', '09123120', 'UNDERGRADUATE', '雷雪', '女', 'leixue@seu.edu.cn', '13800000085', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000094', '213230084', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000294', '00000000-0000-0000-0000-000000000094', '09123121', 'UNDERGRADUATE', '丁杰', '男', 'dingjie@seu.edu.cn', '13800000086', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000095', '213230085', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000295', '00000000-0000-0000-0000-000000000095', '09123122', 'UNDERGRADUATE', '沈佳', '女', 'shenjia@seu.edu.cn', '13800000087', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000096', '213230086', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000296', '00000000-0000-0000-0000-000000000096', '09123123', 'UNDERGRADUATE', '彭斌', '男', 'pengbin@seu.edu.cn', '13800000088', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000097', '213230087', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000297', '00000000-0000-0000-0000-000000000097', '09123124', 'UNDERGRADUATE', '袁婷', '女', 'yuanting@seu.edu.cn', '13800000089', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000098', '213230088', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000298', '00000000-0000-0000-0000-000000000098', '09123125', 'UNDERGRADUATE', '蒋涛', '男', 'jiangtao@seu.edu.cn', '13800000090', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000099', '213230089', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000299', '00000000-0000-0000-0000-000000000099', '09123126', 'UNDERGRADUATE', '韩雨', '女', 'hanyu@seu.edu.cn', '13800000091', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000100', '213230090', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000300', '00000000-0000-0000-0000-000000000100', '09123127', 'UNDERGRADUATE', '余鹏', '男', 'yupeng@seu.edu.cn', '13800000092', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000101', '213230091', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000101', '09123128', 'UNDERGRADUATE', '潘琳', '女', 'panlin@seu.edu.cn', '13800000093', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000102', '213230092', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000102', '09123129', 'UNDERGRADUATE', '章昊', '男', 'zhanghao@seu.edu.cn', '13800000094', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000103', '213230093', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000103', '09123130', 'UNDERGRADUATE', '叶欣', '女', 'yexin@seu.edu.cn', '13800000095', '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

-- ── 软工2401: 9 more (09124102-09124110) ──
INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000104', '213240003', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000104', '09124102', 'UNDERGRADUATE', '贺超', '男', 'hechao@seu.edu.cn', '13800000096', '00000000-0000-0000-0000-000000000116', #2024-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000105', '213240004', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000305', '00000000-0000-0000-0000-000000000105', '09124103', 'UNDERGRADUATE', '潘婷', '女', 'panting@seu.edu.cn', '13800000097', '00000000-0000-0000-0000-000000000116', #2024-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000106', '213240005', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000306', '00000000-0000-0000-0000-000000000106', '09124104', 'UNDERGRADUATE', '丁博', '男', 'dingbo@seu.edu.cn', '13800000098', '00000000-0000-0000-0000-000000000116', #2024-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000107', '213240006', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000307', '00000000-0000-0000-0000-000000000107', '09124105', 'UNDERGRADUATE', '梁萍', '女', 'liangping@seu.edu.cn', '13800000099', '00000000-0000-0000-0000-000000000116', #2024-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000108', '213240007', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000308', '00000000-0000-0000-0000-000000000108', '09124106', 'UNDERGRADUATE', '宋杰', '男', 'songjie@seu.edu.cn', '13800000100', '00000000-0000-0000-0000-000000000116', #2024-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000109', '213240008', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000309', '00000000-0000-0000-0000-000000000109', '09124107', 'UNDERGRADUATE', '曹悦', '女', 'caoyue@seu.edu.cn', '13800000101', '00000000-0000-0000-0000-000000000116', #2024-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000110', '213240009', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000310', '00000000-0000-0000-0000-000000000110', '09124108', 'UNDERGRADUATE', '魏强', '男', 'weiqiang@seu.edu.cn', '13800000102', '00000000-0000-0000-0000-000000000116', #2024-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000111', '213240010', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000311', '00000000-0000-0000-0000-000000000111', '09124109', 'UNDERGRADUATE', '陆琳', '女', 'lulin@seu.edu.cn', '13800000103', '00000000-0000-0000-0000-000000000116', #2024-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000112', '213240011', 'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());
INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000000312', '00000000-0000-0000-0000-000000000112', '09124110', 'UNDERGRADUATE', '范昊', '男', 'fanhao2@seu.edu.cn', '13800000104', '00000000-0000-0000-0000-000000000116', #2024-09-01#, 'ACTIVE', 0, NOW(), NOW());

-- ── Fill common profile fields for all students ──
UPDATE tblStudent SET
    politicalStatus = '共青团员', ethnicity = '汉族', maritalStatus = '未婚',
    countryRegion = '中国', nativePlace = '江苏省', birthplace = '江苏省南京市',
    studentOriginPlace = '江苏省南京市', householdRegistrationType = '非农业家庭户口',
    householdBeforeEnrollment = '江苏省南京市', householdAfterEnrollment = '江苏省南京市',
    overseasChineseStatus = '否', religion = '无宗教信仰', leagueMember = TRUE,
    partyMember = FALSE, healthStatus = '健康或良好', onlyChild = FALSE,
    enrolled = TRUE, onCampus = TRUE, campus = '九龙湖校区', educationLevel = '本科',
    trainingMode = '非定向', programLengthYears = 4, attendanceMode = 'RESIDENT',
    expectedGraduationDate = #2027-07-30#, counselorName = '张航'
WHERE studentId >= '00000000-0000-0000-0000-000000000210';

-- ── Number sequences ──
UPDATE tblNumberSequence SET currentValue = 112 WHERE sequenceKey = 'CAMPUS_CARD_GLOBAL';
UPDATE tblNumberSequence SET currentValue = 29 WHERE sequenceKey = 'STUDENT_NUMBER:090:23:1';

INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:091:23:1', 30, 99, 0, NOW());

INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:070:23:1', 3, 99, 0, NOW());

INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:040:23:1', 1, 99, 0, NOW());

INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:050:23:1', 1, 99, 0, NOW());

INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:090:23:2', 30, 99, 0, NOW());

INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:091:24:1', 10, 99, 0, NOW());

INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:090:24:1', 1, 99, 0, NOW());

INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:070:24:1', 1, 99, 0, NOW());

INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:040:24:1', 1, 99, 0, NOW());

INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:050:24:1', 1, 99, 0, NOW());
