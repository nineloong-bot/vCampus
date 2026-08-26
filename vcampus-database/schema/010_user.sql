CREATE TABLE tblRole (
    roleCode VARCHAR(16) PRIMARY KEY,
    roleName VARCHAR(32) NOT NULL
);

CREATE TABLE tblUser (
    userId VARCHAR(36) PRIMARY KEY,
    loginId VARCHAR(32) NOT NULL,
    passwordHash VARCHAR(256) NOT NULL,
    passwordSalt VARCHAR(128) NOT NULL,
    passwordIterations LONG NOT NULL,
    roleCode VARCHAR(16) NOT NULL,
    accountStatus VARCHAR(16) NOT NULL,
    mustChangePassword YESNO NOT NULL,
    failedLoginCount LONG NOT NULL,
    lockedUntil DATETIME,
    lastLoginAt DATETIME,
    rowVersion LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL
);

CREATE UNIQUE INDEX uk_tblUser_loginId ON tblUser (loginId);
