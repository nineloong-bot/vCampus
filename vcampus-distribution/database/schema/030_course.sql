CREATE TABLE tblTerm (
    termId VARCHAR(36) NOT NULL,
    termCode VARCHAR(24) NOT NULL,
    termName VARCHAR(64) NOT NULL,
    startDate DATETIME NOT NULL,
    endDate DATETIME NOT NULL,
    enrollmentStartAt DATETIME NOT NULL,
    enrollmentEndAt DATETIME NOT NULL,
    adjustmentStartAt DATETIME NOT NULL,
    adjustmentEndAt DATETIME NOT NULL,
    termStatus VARCHAR(16) NOT NULL,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT pk_tblTerm PRIMARY KEY (termId),
    CONSTRAINT uk_tblTerm_termCode UNIQUE (termCode)
);

CREATE TABLE tblCourseSelectionPhase (
    phaseId VARCHAR(36) NOT NULL,
    termId VARCHAR(36) NOT NULL,
    phaseType VARCHAR(16) NOT NULL,
    displayTitle VARCHAR(64) NOT NULL,
    phaseStatus VARCHAR(16) NOT NULL,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT pk_tblCourseSelectionPhase PRIMARY KEY (phaseId),
    CONSTRAINT fk_tblCourseSelectionPhase_term FOREIGN KEY (termId) REFERENCES tblTerm (termId)
);

CREATE INDEX idx_tblCourseSelectionPhase_termId ON tblCourseSelectionPhase (termId);
CREATE INDEX idx_tblCourseSelectionPhase_status ON tblCourseSelectionPhase (phaseStatus);

CREATE TABLE tblCourse (
    courseId VARCHAR(36) NOT NULL,
    courseCode VARCHAR(24) NOT NULL,
    courseName VARCHAR(128) NOT NULL,
    credit DECIMAL(4,1) NOT NULL,
    totalHours LONG NOT NULL,
    description MEMO,
    isActive YESNO NOT NULL,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT pk_tblCourse PRIMARY KEY (courseId),
    CONSTRAINT uk_tblCourse_courseCode UNIQUE (courseCode)
);

CREATE TABLE tblCourseOffering (
    offeringId VARCHAR(36) NOT NULL,
    termId VARCHAR(36) NOT NULL,
    courseId VARCHAR(36) NOT NULL,
    teacherUserId VARCHAR(36) NOT NULL,
    className VARCHAR(64) NOT NULL,
    capacity LONG NOT NULL,
    enrolledCount LONG NOT NULL,
    offeringStatus VARCHAR(16) NOT NULL,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT pk_tblCourseOffering PRIMARY KEY (offeringId),
    CONSTRAINT fk_tblCourseOffering_term FOREIGN KEY (termId) REFERENCES tblTerm (termId),
    CONSTRAINT fk_tblCourseOffering_course FOREIGN KEY (courseId) REFERENCES tblCourse (courseId)
);

CREATE INDEX idx_tblCourseOffering_termId ON tblCourseOffering (termId);
CREATE INDEX idx_tblCourseOffering_courseId ON tblCourseOffering (courseId);
CREATE INDEX idx_tblCourseOffering_teacherUserId ON tblCourseOffering (teacherUserId);

CREATE TABLE tblCourseSchedule (
    scheduleId VARCHAR(36) NOT NULL,
    offeringId VARCHAR(36) NOT NULL,
    dayOfWeek LONG NOT NULL,
    startPeriod LONG NOT NULL,
    endPeriod LONG NOT NULL,
    startWeek LONG NOT NULL,
    endWeek LONG NOT NULL,
    classroom VARCHAR(64) NOT NULL,
    CONSTRAINT pk_tblCourseSchedule PRIMARY KEY (scheduleId),
    CONSTRAINT fk_tblCourseSchedule_offering FOREIGN KEY (offeringId)
        REFERENCES tblCourseOffering (offeringId)
);

CREATE INDEX idx_tblCourseSchedule_offeringId ON tblCourseSchedule (offeringId);

CREATE TABLE tblEnrollment (
    enrollmentId VARCHAR(36) NOT NULL,
    offeringId VARCHAR(36) NOT NULL,
    studentId VARCHAR(36) NOT NULL,
    enrollmentType VARCHAR(16) NOT NULL,
    enrollmentStatus VARCHAR(16) NOT NULL,
    enrolledAt DATETIME NOT NULL,
    droppedAt DATETIME,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT pk_tblEnrollment PRIMARY KEY (enrollmentId),
    CONSTRAINT fk_tblEnrollment_offering FOREIGN KEY (offeringId)
        REFERENCES tblCourseOffering (offeringId),
    CONSTRAINT uk_tblEnrollment_student_offering UNIQUE (studentId, offeringId)
);

CREATE INDEX idx_tblEnrollment_studentId ON tblEnrollment (studentId);
CREATE INDEX idx_tblEnrollment_offeringId ON tblEnrollment (offeringId);

CREATE TABLE tblEnrollmentAdjustment (
    adjustmentId VARCHAR(36) NOT NULL,
    studentId VARCHAR(36) NOT NULL,
    adjustmentType VARCHAR(16) NOT NULL,
    sourceOfferingId VARCHAR(36),
    targetOfferingId VARCHAR(36),
    operationResult VARCHAR(16) NOT NULL,
    failureCode VARCHAR(64),
    operatedAt DATETIME NOT NULL,
    CONSTRAINT pk_tblEnrollmentAdjustment PRIMARY KEY (adjustmentId)
);

CREATE INDEX idx_tblEnrollmentAdjustment_studentId ON tblEnrollmentAdjustment (studentId);

CREATE TABLE tblCourseAttempt (
    attemptId VARCHAR(36) NOT NULL,
    studentId VARCHAR(36) NOT NULL,
    courseId VARCHAR(36) NOT NULL,
    termId VARCHAR(36) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    sourceReference VARCHAR(128) NOT NULL,
    importedAt DATETIME NOT NULL,
    CONSTRAINT pk_tblCourseAttempt PRIMARY KEY (attemptId),
    CONSTRAINT fk_tblCourseAttempt_course FOREIGN KEY (courseId) REFERENCES tblCourse (courseId),
    CONSTRAINT fk_tblCourseAttempt_term FOREIGN KEY (termId) REFERENCES tblTerm (termId),
    CONSTRAINT uk_tblCourseAttempt_sourceReference UNIQUE (sourceReference)
);

CREATE INDEX idx_tblCourseAttempt_student_course ON tblCourseAttempt (studentId, courseId);
