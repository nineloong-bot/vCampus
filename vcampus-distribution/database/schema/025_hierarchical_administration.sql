CREATE TABLE tblStudentCollegeAdministrator (
    departmentId VARCHAR(36) NOT NULL,
    userId VARCHAR(36) NOT NULL,
    isActive YESNO NOT NULL,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT pk_tblStudentCollegeAdministrator PRIMARY KEY (departmentId, userId),
    CONSTRAINT fk_tblStudentCollegeAdministrator_department FOREIGN KEY (departmentId)
        REFERENCES tblDepartment (departmentId),
    CONSTRAINT fk_tblStudentCollegeAdministrator_user FOREIGN KEY (userId)
        REFERENCES tblUser (userId)
);

CREATE UNIQUE INDEX uk_tblStudentCollegeAdministrator_userId
    ON tblStudentCollegeAdministrator (userId);
