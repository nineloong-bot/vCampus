CREATE TABLE tblMajorTransferBatch (
    batchId VARCHAR(36) PRIMARY KEY,
    batchName VARCHAR(128) NOT NULL,
    batchStatus VARCHAR(16) NOT NULL,
    applicationStart DATETIME NOT NULL,
    applicationEnd DATETIME NOT NULL,
    publicityStart DATETIME,
    publicityEnd DATETIME,
    effectiveDate DATETIME,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL
);

CREATE INDEX idx_tblMajorTransferBatch_status ON tblMajorTransferBatch (batchStatus);

CREATE TABLE tblMajorTransferOption (
    optionId VARCHAR(36) PRIMARY KEY,
    batchId VARCHAR(36) NOT NULL,
    targetMajorId VARCHAR(36) NOT NULL,
    targetDepartmentId VARCHAR(36) NOT NULL,
    targetMajorName VARCHAR(64) NOT NULL,
    targetDepartmentName VARCHAR(64) NOT NULL,
    grades VARCHAR(64) NOT NULL,
    receiveQuota LONG NOT NULL,
    interviewQuota LONG NOT NULL,
    writtenPassScore DOUBLE,
    interviewPassScore DOUBLE,
    writtenWeightPct LONG NOT NULL,
    interviewWeightPct LONG NOT NULL,
    difficultyQuotaExempt YESNO NOT NULL,
    requirements MEMO,
    isActive YESNO NOT NULL,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT fk_tblMajorTransferOption_batch FOREIGN KEY (batchId)
        REFERENCES tblMajorTransferBatch (batchId)
);

CREATE INDEX idx_tblMajorTransferOption_batch ON tblMajorTransferOption (batchId);
CREATE INDEX idx_tblMajorTransferOption_major ON tblMajorTransferOption (targetMajorId);

CREATE TABLE tblMajorTransferApplication (
    applicationId VARCHAR(36) PRIMARY KEY,
    batchId VARCHAR(36) NOT NULL,
    studentId VARCHAR(36) NOT NULL,
    applicationType VARCHAR(16) NOT NULL,
    applicationStatus VARCHAR(24) NOT NULL,
    optionId VARCHAR(36) NOT NULL,
    fromDepartmentId VARCHAR(36) NOT NULL,
    fromDepartmentName VARCHAR(64) NOT NULL,
    fromMajorId VARCHAR(36) NOT NULL,
    fromMajorName VARCHAR(64) NOT NULL,
    fromClassId VARCHAR(36) NOT NULL,
    fromClassName VARCHAR(64) NOT NULL,
    fromStudentNumber VARCHAR(8) NOT NULL,
    fromGrade VARCHAR(16),
    studentName VARCHAR(64) NOT NULL,
    reason MEMO,
    writtenScore DOUBLE,
    interviewScore DOUBLE,
    finalScore DOUBLE,
    baseStudentVersion LONG NOT NULL,
    applicationVersion LONG NOT NULL,
    submittedAt DATETIME,
    sourceReviewerUserId VARCHAR(36),
    sourceReviewedAt DATETIME,
    sourceComment VARCHAR(512),
    qualificationReviewerUserId VARCHAR(36),
    qualificationReviewedAt DATETIME,
    qualificationComment VARCHAR(512),
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT fk_tblMajorTransferApplication_batch FOREIGN KEY (batchId)
        REFERENCES tblMajorTransferBatch (batchId),
    CONSTRAINT fk_tblMajorTransferApplication_option FOREIGN KEY (optionId)
        REFERENCES tblMajorTransferOption (optionId)
);

CREATE UNIQUE INDEX uk_tblMajorTransferApplication_batch_student
    ON tblMajorTransferApplication (batchId, studentId);
CREATE INDEX idx_tblMajorTransferApplication_student
    ON tblMajorTransferApplication (studentId);
CREATE INDEX idx_tblMajorTransferApplication_option
    ON tblMajorTransferApplication (optionId);
CREATE INDEX idx_tblMajorTransferApplication_status
    ON tblMajorTransferApplication (applicationStatus);

CREATE TABLE tblMajorTransferAttachment (
    attachmentId VARCHAR(36) PRIMARY KEY,
    applicationId VARCHAR(36) NOT NULL,
    fileName VARCHAR(256) NOT NULL,
    contentType VARCHAR(32) NOT NULL,
    fileSize LONG NOT NULL,
    content BLOB NOT NULL,
    createdAt DATETIME NOT NULL,
    CONSTRAINT fk_tblMajorTransferAttachment_application FOREIGN KEY (applicationId)
        REFERENCES tblMajorTransferApplication (applicationId)
);

CREATE INDEX idx_tblMajorTransferAttachment_application
    ON tblMajorTransferAttachment (applicationId);

CREATE TABLE tblMajorTransferReview (
    reviewId VARCHAR(36) PRIMARY KEY,
    applicationId VARCHAR(36) NOT NULL,
    reviewStage VARCHAR(32) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reviewerUserId VARCHAR(36) NOT NULL,
    comment VARCHAR(512),
    sourceVerified YESNO,
    noMisconduct YESNO,
    admissionAllowed YESNO,
    createdAt DATETIME NOT NULL,
    CONSTRAINT fk_tblMajorTransferReview_application FOREIGN KEY (applicationId)
        REFERENCES tblMajorTransferApplication (applicationId)
);

CREATE UNIQUE INDEX uk_tblMajorTransferReview_app_stage
    ON tblMajorTransferReview (applicationId, reviewStage);
CREATE INDEX idx_tblMajorTransferReview_application
    ON tblMajorTransferReview (applicationId);

CREATE TABLE tblMajorTransferExecution (
    executionId VARCHAR(36) PRIMARY KEY,
    applicationId VARCHAR(36) NOT NULL,
    toClassId VARCHAR(36) NOT NULL,
    toClassName VARCHAR(64) NOT NULL,
    toMajorId VARCHAR(36) NOT NULL,
    toMajorName VARCHAR(64) NOT NULL,
    toDepartmentId VARCHAR(36) NOT NULL,
    toDepartmentName VARCHAR(64) NOT NULL,
    courseRecognitionStatus VARCHAR(16) NOT NULL,
    operatorUserId VARCHAR(36) NOT NULL,
    effectiveDate DATETIME NOT NULL,
    createdAt DATETIME NOT NULL,
    CONSTRAINT fk_tblMajorTransferExecution_application FOREIGN KEY (applicationId)
        REFERENCES tblMajorTransferApplication (applicationId)
);

CREATE INDEX idx_tblMajorTransferExecution_application
    ON tblMajorTransferExecution (applicationId);
