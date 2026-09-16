-- Canonical pre-2023 plan retained for seed-only environments.
INSERT INTO tblTrainingPlan
    (planId, majorId, enrollmentYear, planName, minElectiveCount, minElectiveCredits,
     isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('seed-plan-802-2022',
     'bulk-major-02', 2022, '计算机科学 2022 级培养方案',
     2, 4.0, TRUE, 0, NOW(), NOW());

INSERT INTO tblTrainingPlanCourse
    (planCourseId, planId, courseCode, courseName, credits, courseType, semester,
     isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('seed-plan-course-cs2303', 'seed-plan-802-2022',
     'CS2303', '人工智能导论', 2.0, 'ELECTIVE', 3, TRUE, 0, NOW(), NOW());

INSERT INTO tblTrainingPlanCourse
    (planCourseId, planId, courseCode, courseName, credits, courseType, semester,
     isActive, rowVersion, createdAt, updatedAt)
VALUES
    ('seed-plan-course-cs2304', 'seed-plan-802-2022',
     'CS2304', 'Web 应用开发', 2.0, 'ELECTIVE', 4, TRUE, 0, NOW(), NOW());
