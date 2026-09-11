-- 计算机科学与技术 2023 级示例培养方案，供管理员和学生端联调使用。
INSERT INTO tblTrainingPlan
    (planId, majorId, enrollmentYear, planName, minElectiveCount, minElectiveCredits,
     isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000301',
     '00000000-0000-0000-0000-000000000102', 2023, '计算机科学与技术 2023 级培养方案',
     2, 4.0, TRUE, 0, NOW(), NOW());

INSERT INTO tblTrainingPlanCourse
    (planCourseId, planId, courseCode, courseName, credits, courseType, semester,
     isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000311', '00000000-0000-0000-0000-000000000301',
     'CS2301', '程序设计基础', 4.0, 'REQUIRED', 1, TRUE, 0, NOW(), NOW());

INSERT INTO tblTrainingPlanCourse
    (planCourseId, planId, courseCode, courseName, credits, courseType, semester,
     isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000312', '00000000-0000-0000-0000-000000000301',
     'CS2302', '数据结构', 4.0, 'REQUIRED', 3, TRUE, 0, NOW(), NOW());

INSERT INTO tblTrainingPlanCourse
    (planCourseId, planId, courseCode, courseName, credits, courseType, semester,
     isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000313', '00000000-0000-0000-0000-000000000301',
     'CS2303', '人工智能导论', 2.0, 'ELECTIVE', 3, TRUE, 0, NOW(), NOW());

INSERT INTO tblTrainingPlanCourse
    (planCourseId, planId, courseCode, courseName, credits, courseType, semester,
     isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000314', '00000000-0000-0000-0000-000000000301',
     'CS2304', 'Web 应用开发', 2.0, 'ELECTIVE', 4, TRUE, 0, NOW(), NOW());
