-- 全校课程库与跨学科课程申请审批表

CREATE TABLE tblCrossCourseApplication (
    applicationId VARCHAR(36) PRIMARY KEY,
    courseId VARCHAR(36) NOT NULL,
    courseCode VARCHAR(24) NOT NULL,
    courseName VARCHAR(128) NOT NULL,
    credits DECIMAL(4,1) NOT NULL,
    offeringDepartmentId VARCHAR(36) NOT NULL,
    offeringDepartmentName VARCHAR(64) NOT NULL,
    targetDepartmentId VARCHAR(36) NOT NULL,
    targetDepartmentName VARCHAR(64) NOT NULL,
    targetPlanId VARCHAR(36) NOT NULL,
    targetPlanName VARCHAR(128) NOT NULL,
    semester LONG NOT NULL,
    requestedQuota LONG NOT NULL,
    allocatedQuota LONG,
    applicantUserId VARCHAR(36) NOT NULL,
    applicantName VARCHAR(64),
    reason MEMO,
    status VARCHAR(16) NOT NULL,
    reviewerUserId VARCHAR(36),
    reviewComment MEMO,
    reviewedAt DATETIME,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL
);
