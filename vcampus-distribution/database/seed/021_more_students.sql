INSERT INTO tblDepartment
    (departmentId, departmentCode, departmentName, isActive, rowVersion)
VALUES
    ('00000000-0000-0000-0000-000000000111', 'MATH', '数学学院', TRUE, 0);

INSERT INTO tblMajor
    (majorId, departmentId, majorCode, majorName, isActive, rowVersion)
VALUES
    ('00000000-0000-0000-0000-000000000112', '00000000-0000-0000-0000-000000000101',
     '091', '软件工程', TRUE, 0);

INSERT INTO tblMajor
    (majorId, departmentId, majorCode, majorName, isActive, rowVersion)
VALUES
    ('00000000-0000-0000-0000-000000000113', '00000000-0000-0000-0000-000000000111',
     '070', '数学与应用数学', TRUE, 0);

INSERT INTO tblClass
    (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES
    ('00000000-0000-0000-0000-000000000114', '00000000-0000-0000-0000-000000000112',
     '091-2023-01', '软件工程2301班', 2023, 1, TRUE, 0);

INSERT INTO tblClass
    (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES
    ('00000000-0000-0000-0000-000000000115', '00000000-0000-0000-0000-000000000113',
     '070-2023-01', '数学与应用数学2301班', 2023, 1, TRUE, 0);

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus,
     mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000010', '213230002',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
     120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus,
     mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000011', '213230003',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
     120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus,
     mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000012', '213230004',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
     120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus,
     mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000013', '213230005',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
     120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus,
     mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000014', '213230006',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
     120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus,
     mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000015', '213230007',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
     120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus,
     mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000016', '213230008',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
     120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus,
     mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000017', '213230009',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
     120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus,
     mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000018', '213230010',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
     120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus,
     mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000019', '213230011',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
     120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode, accountStatus,
     mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000020', '213230012',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=', 'ea2khaykRdPtuvyuFbeskw==',
     120000, 'STUDENT', 'ACTIVE', FALSE, 0, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
     classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000210', '00000000-0000-0000-0000-000000000010',
     '09023102', 'UNDERGRADUATE', '张伟', '男', 'zhangwei@seu.edu.cn', '13800000002',
     '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
     classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000211', '00000000-0000-0000-0000-000000000011',
     '09023103', 'UNDERGRADUATE', '李娜', '女', 'lina@seu.edu.cn', '13800000003',
     '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
     classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000212', '00000000-0000-0000-0000-000000000012',
     '09023104', 'UNDERGRADUATE', '王强', '男', 'wangqiang@seu.edu.cn', '13800000004',
     '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
     classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000213', '00000000-0000-0000-0000-000000000013',
     '09023105', 'UNDERGRADUATE', '刘洋', '女', 'liuyang@seu.edu.cn', '13800000005',
     '00000000-0000-0000-0000-000000000103', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
     classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000214', '00000000-0000-0000-0000-000000000014',
     '09123101', 'UNDERGRADUATE', '陈晨', '女', 'chenchen@seu.edu.cn', '13800000006',
     '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
     classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000215', '00000000-0000-0000-0000-000000000015',
     '09123102', 'UNDERGRADUATE', '赵敏', '女', 'zhaomin@seu.edu.cn', '13800000007',
     '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
     classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000216', '00000000-0000-0000-0000-000000000016',
     '09123103', 'UNDERGRADUATE', '周杰', '男', 'zhoujie@seu.edu.cn', '13800000008',
     '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'SUSPENDED', 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
     classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000217', '00000000-0000-0000-0000-000000000017',
     '09123104', 'UNDERGRADUATE', '吴桐', '男', 'wutong@seu.edu.cn', '13800000009',
     '00000000-0000-0000-0000-000000000114', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
     classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000218', '00000000-0000-0000-0000-000000000018',
     '07023101', 'UNDERGRADUATE', '孙悦', '女', 'sunyue@seu.edu.cn', '13800000010',
     '00000000-0000-0000-0000-000000000115', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
     classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000219', '00000000-0000-0000-0000-000000000019',
     '07023102', 'UNDERGRADUATE', '郑凯', '男', 'zhengkai@seu.edu.cn', '13800000011',
     '00000000-0000-0000-0000-000000000115', #2023-09-01#, 'GRADUATED', 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender, email, phone,
     classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000220', '00000000-0000-0000-0000-000000000020',
     '07023103', 'UNDERGRADUATE', '何雨', '女', 'heyu@seu.edu.cn', '13800000012',
     '00000000-0000-0000-0000-000000000115', #2023-09-01#, 'WITHDRAWN', 0, NOW(), NOW());

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

UPDATE tblNumberSequence SET currentValue = 12
WHERE sequenceKey = 'CAMPUS_CARD_GLOBAL';

UPDATE tblNumberSequence SET currentValue = 5
WHERE sequenceKey = 'STUDENT_NUMBER:090:23:1';

INSERT INTO tblNumberSequence
    (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:091:23:1', 4, 99, 0, NOW());

INSERT INTO tblNumberSequence
    (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:070:23:1', 3, 99, 0, NOW());
