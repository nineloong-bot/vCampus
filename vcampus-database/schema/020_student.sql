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
