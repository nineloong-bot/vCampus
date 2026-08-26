CREATE TABLE tblDepartment (
    departmentId VARCHAR(36) PRIMARY KEY,
    departmentCode VARCHAR(16) NOT NULL,
    departmentName VARCHAR(64) NOT NULL,
    isActive YESNO NOT NULL,
    rowVersion LONG NOT NULL
);

CREATE UNIQUE INDEX uk_tblDepartment_departmentCode
    ON tblDepartment (departmentCode);

CREATE TABLE tblMajor (
    majorId VARCHAR(36) PRIMARY KEY,
    departmentId VARCHAR(36) NOT NULL,
    majorCode VARCHAR(3) NOT NULL,
    majorName VARCHAR(64) NOT NULL,
    isActive YESNO NOT NULL,
    rowVersion LONG NOT NULL,
    CONSTRAINT fk_tblMajor_department FOREIGN KEY (departmentId)
        REFERENCES tblDepartment (departmentId)
);

CREATE UNIQUE INDEX uk_tblMajor_majorCode ON tblMajor (majorCode);
CREATE INDEX idx_tblMajor_departmentId ON tblMajor (departmentId);

CREATE TABLE tblClass (
    classId VARCHAR(36) PRIMARY KEY,
    majorId VARCHAR(36) NOT NULL,
    classCode VARCHAR(24) NOT NULL,
    className VARCHAR(64) NOT NULL,
    enrollmentYear LONG NOT NULL,
    classNumber LONG NOT NULL,
    isActive YESNO NOT NULL,
    rowVersion LONG NOT NULL,
    CONSTRAINT fk_tblClass_major FOREIGN KEY (majorId)
        REFERENCES tblMajor (majorId)
);

CREATE UNIQUE INDEX uk_tblClass_classCode ON tblClass (classCode);
CREATE UNIQUE INDEX uk_tblClass_major_year_number
    ON tblClass (majorId, enrollmentYear, classNumber);
CREATE INDEX idx_tblClass_majorId ON tblClass (majorId);
CREATE INDEX idx_tblClass_enrollmentYear ON tblClass (enrollmentYear);

CREATE TABLE tblNumberSequence (
    sequenceKey VARCHAR(64) PRIMARY KEY,
    currentValue LONG NOT NULL,
    maxValue LONG NOT NULL,
    rowVersion LONG NOT NULL,
    updatedAt DATETIME NOT NULL
);

INSERT INTO tblNumberSequence
    (sequenceKey, currentValue, maxValue, rowVersion, updatedAt)
VALUES ('CAMPUS_CARD_GLOBAL', 0, 9999, 0, NOW());

CREATE TABLE tblStudent (
    studentId VARCHAR(36) PRIMARY KEY,
    userId VARCHAR(36) NOT NULL,
    studentNumber VARCHAR(8) NOT NULL,
    studentType VARCHAR(16) NOT NULL,
    studentName VARCHAR(64) NOT NULL,
    gender VARCHAR(16) NOT NULL,
    email VARCHAR(128),
    phone VARCHAR(32),
    classId VARCHAR(36) NOT NULL,
    enrollmentDate DATETIME NOT NULL,
    studentStatus VARCHAR(16) NOT NULL,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT fk_tblStudent_class FOREIGN KEY (classId) REFERENCES tblClass (classId)
);

CREATE UNIQUE INDEX uk_tblStudent_userId ON tblStudent (userId);
CREATE UNIQUE INDEX uk_tblStudent_studentNumber ON tblStudent (studentNumber);
CREATE INDEX idx_tblStudent_classId ON tblStudent (classId);
CREATE INDEX idx_tblStudent_studentStatus ON tblStudent (studentStatus);

CREATE TABLE tblStudentChange (
    changeId VARCHAR(36) PRIMARY KEY,
    studentId VARCHAR(36) NOT NULL,
    changeType VARCHAR(24) NOT NULL,
    oldValue MEMO,
    newValue MEMO NOT NULL,
    reason VARCHAR(256) NOT NULL,
    operatorUserId VARCHAR(36) NOT NULL,
    effectiveDate DATETIME NOT NULL,
    createdAt DATETIME NOT NULL,
    CONSTRAINT fk_tblStudentChange_student FOREIGN KEY (studentId) REFERENCES tblStudent (studentId)
);

CREATE INDEX idx_tblStudentChange_studentId ON tblStudentChange (studentId);
