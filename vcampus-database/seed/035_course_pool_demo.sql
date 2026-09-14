-- ============================================================
-- 035_course_pool_demo.sql
-- University-wide course catalog pool and cross-course demo data
-- ============================================================

-- ── CS 计算机科学与工程学院 ──
INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003001', 'CS2301', '程序设计基础',
        '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院', 4.0, 64,
        'C/C++语言与算法逻辑，计算机核心基础课程', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003002', 'CS2302', '数据结构与算法',
        '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院', 4.0, 64,
        '经典数据结构与算法设计分析', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003003', 'CS2303', '人工智能导论',
        '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院', 2.0, 32,
        '机器学习与深度学习现代应用前沿', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003004', 'CS2304', 'Web 应用开发',
        '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院', 2.0, 32,
        '现代全栈 Web 开发与工程实战体系', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003005', 'CS2305', '计算机网络与安全',
        '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院', 3.0, 48,
        'TCP/IP 体系结构与网络安全防御实务', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003006', 'CS2306', 'Python 数据分析与可视化',
        '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院', 2.0, 32,
        '面向全校跨学科学生的数据科学技能与绘图实战', TRUE, 0, NOW(), NOW());

-- ── MATH 数学学院 ──
INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003101', 'MATH101', '高等数学(上)',
        '00000000-0000-0000-0000-000000000111', '数学学院', 5.0, 80,
        '一元微积分与空间解析几何基础', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003102', 'MATH102', '线性代数与矩阵论',
        '00000000-0000-0000-0000-000000000111', '数学学院', 3.0, 48,
        '线性方程组、矩阵对角化与二次型理论', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003103', 'MATH201', '概率论与数理统计',
        '00000000-0000-0000-0000-000000000111', '数学学院', 3.0, 48,
        '随机变量概率分布与统计推断基础', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003104', 'MATH301', '金融数学与量化投资',
        '00000000-0000-0000-0000-000000000111', '数学学院', 2.0, 32,
        '金融衍生品定价、量化交易策略与跨学科建模', TRUE, 0, NOW(), NOW());

-- ── EE 信息科学与工程学院 ──
INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003201', 'EE101', '信号与系统分析',
        '00000000-0000-0000-0000-000000000131', '信息科学与工程学院', 4.0, 64,
        '连续与离散系统的时域与变换域分析', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003202', 'EE201', '智能传感器与物联网',
        '00000000-0000-0000-0000-000000000131', '信息科学与工程学院', 2.5, 40,
        '微型传感器、嵌入式系统与物联网协议工程', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003203', 'EE301', '机器人控制技术导论',
        '00000000-0000-0000-0000-000000000131', '信息科学与工程学院', 2.0, 32,
        '自主移动机器人动力学与跨学科控制架构', TRUE, 0, NOW(), NOW());

-- ── FL 外国语学院 ──
INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003301', 'FL101', '科技学术英语交流',
        '00000000-0000-0000-0000-000000000141', '外国语学院', 2.0, 32,
        '国际学术会议发言与高水平论文撰写表达', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003302', 'FL201', '跨文化交际与商务谈判',
        '00000000-0000-0000-0000-000000000141', '外国语学院', 2.0, 32,
        '跨国文化认知、涉外礼仪与商务谈判技巧', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse (courseId, courseCode, courseName, departmentId, departmentName, credit, totalHours, description, isActive, rowVersion, createdAt, updatedAt)
VALUES ('00000000-0000-0000-0000-000000003303', 'FL301', '实用第二外语(日语)',
        '00000000-0000-0000-0000-000000000141', '外国语学院', 2.0, 32,
        '假名发音、实用日常会话与跨学科文化拓展', TRUE, 0, NOW(), NOW());

-- ── 示例跨学科申请 ──
-- 计算机学院向数学学院申请引入 MATH301 金融数学与量化投资（已审批通过，分配30名额）
INSERT INTO tblCrossCourseApplication (
    applicationId, courseId, courseCode, courseName, credits,
    offeringDepartmentId, offeringDepartmentName,
    targetDepartmentId, targetDepartmentName,
    targetPlanId, targetPlanName, semester,
    requestedQuota, allocatedQuota, applicantUserId, applicantName,
    reason, status, reviewerUserId, reviewComment, reviewedAt,
    rowVersion, createdAt, updatedAt)
VALUES (
    '00000000-0000-0000-0000-000000004001',
    '00000000-0000-0000-0000-000000003104', 'MATH301', '金融数学与量化投资', 2.0,
    '00000000-0000-0000-0000-000000000111', '数学学院',
    '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
    '00000000-0000-0000-0000-000000000301', '计算机科学与技术 2023 级培养方案', 4,
    30, 30, '00000000-0000-0000-0000-000000000202', '计算机系教务管理员',
    '为计科本科生开拓量化金融与跨学科算法应用视野', 'APPROVED',
    '00000000-0000-0000-0000-000000000203', '同意计科专业跨学科修读，分配30个选课名额', NOW(),
    1, NOW(), NOW());

-- 将该跨学科课程写入计算机 2023 培养方案中
INSERT INTO tblTrainingPlanCourse (
    planCourseId, planId, courseCode, courseName, credits, courseType, semester,
    isActive, rowVersion, createdAt, updatedAt,
    courseId, offeringDepartmentId, offeringDepartmentName, allocatedQuota)
VALUES (
    '00000000-0000-0000-0000-000000000315',
    '00000000-0000-0000-0000-000000000301',
    'MATH301', '金融数学与量化投资', 2.0, 'CROSS_DISCIPLINARY', 4,
    TRUE, 0, NOW(), NOW(),
    '00000000-0000-0000-0000-000000003104',
    '00000000-0000-0000-0000-000000000111', '数学学院', 30);

-- 计算机学院向信息工程学院申请引入 EE201 智能传感器与物联网（待审批，申请40名额）
INSERT INTO tblCrossCourseApplication (
    applicationId, courseId, courseCode, courseName, credits,
    offeringDepartmentId, offeringDepartmentName,
    targetDepartmentId, targetDepartmentName,
    targetPlanId, targetPlanName, semester,
    requestedQuota, allocatedQuota, applicantUserId, applicantName,
    reason, status, reviewerUserId, reviewComment, reviewedAt,
    rowVersion, createdAt, updatedAt)
VALUES (
    '00000000-0000-0000-0000-000000004002',
    '00000000-0000-0000-0000-000000003202', 'EE201', '智能传感器与物联网', 2.5,
    '00000000-0000-0000-0000-000000000131', '信息科学与工程学院',
    '00000000-0000-0000-0000-000000000101', '计算机科学与工程学院',
    '00000000-0000-0000-0000-000000000301', '计算机科学与技术 2023 级培养方案', 3,
    40, NULL, '00000000-0000-0000-0000-000000000202', '计算机系教务管理员',
    '培养计算机与硬件物联网嵌入式系统跨学科复合人才', 'PENDING',
    NULL, NULL, NULL,
    0, NOW(), NOW());
