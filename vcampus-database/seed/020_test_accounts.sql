INSERT INTO tblDepartment
    (departmentId, departmentCode, departmentName, isActive, rowVersion)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'CS', '计算机科学与工程学院', TRUE, 0);

INSERT INTO tblMajor
    (majorId, departmentId, majorCode, majorName, isActive, rowVersion)
VALUES
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000101',
     '090', '计算机科学与技术', TRUE, 0);

INSERT INTO tblClass
    (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion)
VALUES
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000102',
     '090-2023-01', '计算机科学与技术2301班', 2023, 1, TRUE, 0);

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000002', 'TEACHER01',
     'M1ugBCfSgAJo783+gcwzquDQVReMTum8FhPOuhiVkGM=',
     'w0HAs1RaS7syKoOJIwBhoA==', 120000, 'TEACHER', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000003', '213230001',
     'FtNp+sYhvrqEZzDBbVS6tsK5gQJV2h4jIvXtKhiZjbg=',
     'ea2khaykRdPtuvyuFbeskw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblStudent
    (studentId, userId, studentNumber, studentType, studentName, gender,
     email, phone, classId, enrollmentDate, studentStatus, rowVersion,
     createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000003',
     '09023101', 'UNDERGRADUATE', '测试学生', '男',
     'student@seu.edu.cn', '13800000000', '00000000-0000-0000-0000-000000000103',
     NOW(), 'ACTIVE', 0, NOW(), NOW());

UPDATE tblNumberSequence SET currentValue = 1
    WHERE sequenceKey = 'CAMPUS_CARD_GLOBAL';

UPDATE tblStudent SET
    namePinyin = 'CESHI XUESHENG',
    politicalStatus = '共青团员',
    ethnicity = '汉族',
    maritalStatus = '未婚',
    idDocumentType = '居民身份证',
    idDocumentNumber = '320101200501010011',
    birthDate = #2005-01-01#,
    nativePlace = '江苏省',
    countryRegion = '中国',
    birthplace = '江苏省南京市',
    studentOriginPlace = '江苏省南京市',
    householdRegistrationType = '非农业家庭户口',
    householdBeforeEnrollment = '江苏省南京市',
    householdAfterEnrollment = '江苏省南京市',
    overseasChineseStatus = '否',
    religion = '无宗教信仰',
    leagueMember = TRUE,
    leagueJoinDate = #2020-12-12#,
    partyMember = FALSE,
    healthStatus = '健康或良好',
    bloodType = 'A',
    weightKg = 58,
    heightCm = 172,
    specialties = '魔方',
    hobbies = '乒乓球',
    onlyChild = FALSE,
    enrolled = TRUE,
    onCampus = TRUE,
    campus = '九龙湖校区',
    educationLevel = '本科',
    trainingMode = '非定向',
    programLengthYears = 4,
    attendanceMode = 'RESIDENT',
    expectedGraduationDate = #2027-07-30#,
    counselorName = '张航'
WHERE studentId = '00000000-0000-0000-0000-000000000104';

INSERT INTO tblNumberSequence
    (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('STUDENT_NUMBER:090:23:1', 1, 99, 0, NOW());
