-- 培养方案与成绩表

CREATE TABLE tblTrainingPlan (
    planId VARCHAR(36) PRIMARY KEY,
    majorId VARCHAR(36) NOT NULL,
    enrollmentYear LONG NOT NULL,
    planName VARCHAR(128) NOT NULL,
    minElectiveCount LONG NOT NULL,
    minElectiveCredits DECIMAL(4,1) NOT NULL,
    isActive YESNO NOT NULL,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT uk_tblTrainingPlan_major_year UNIQUE (majorId, enrollmentYear),
    CONSTRAINT fk_tblTrainingPlan_major FOREIGN KEY (majorId)
        REFERENCES tblMajor (majorId)
);

CREATE INDEX idx_tblTrainingPlan_major ON tblTrainingPlan (majorId);

CREATE TABLE tblTrainingPlanCourse (
    planCourseId VARCHAR(36) PRIMARY KEY,
    planId VARCHAR(36) NOT NULL,
    courseCode VARCHAR(16) NOT NULL,
    courseName VARCHAR(64) NOT NULL,
    credits DECIMAL(4,1) NOT NULL,
    courseType VARCHAR(16) NOT NULL,
    semester LONG NOT NULL,
    isActive YESNO NOT NULL,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT uk_tblTrainingPlanCourse_plan_code UNIQUE (planId, courseCode),
    CONSTRAINT fk_tblTrainingPlanCourse_plan FOREIGN KEY (planId)
        REFERENCES tblTrainingPlan (planId)
);

CREATE INDEX idx_tblTrainingPlanCourse_plan
    ON tblTrainingPlanCourse (planId);

CREATE TABLE tblStudentGrade (
    gradeId VARCHAR(36) PRIMARY KEY,
    studentId VARCHAR(36) NOT NULL,
    planCourseId VARCHAR(36) NOT NULL,
    result VARCHAR(8) NOT NULL,
    recordedSemester VARCHAR(16),
    operatorUserId VARCHAR(36) NOT NULL,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT uk_tblStudentGrade_student_course UNIQUE (studentId, planCourseId),
    CONSTRAINT fk_tblStudentGrade_student FOREIGN KEY (studentId)
        REFERENCES tblStudent (studentId),
    CONSTRAINT fk_tblStudentGrade_course FOREIGN KEY (planCourseId)
        REFERENCES tblTrainingPlanCourse (planCourseId)
);

CREATE INDEX idx_tblStudentGrade_student ON tblStudentGrade (studentId);
CREATE INDEX idx_tblStudentGrade_course ON tblStudentGrade (planCourseId);
