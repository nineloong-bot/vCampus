CREATE TABLE tblRole (
    roleCode VARCHAR(16) PRIMARY KEY,
    roleName VARCHAR(32) NOT NULL
);

CREATE TABLE tblPermission (
    permissionCode VARCHAR(64) PRIMARY KEY,
    permissionName VARCHAR(64) NOT NULL
);

CREATE TABLE tblRolePermission (
    roleCode VARCHAR(16) NOT NULL,
    permissionCode VARCHAR(64) NOT NULL,
    CONSTRAINT pk_tblRolePermission PRIMARY KEY (roleCode, permissionCode),
    CONSTRAINT fk_tblRolePermission_role FOREIGN KEY (roleCode)
        REFERENCES tblRole (roleCode),
    CONSTRAINT fk_tblRolePermission_permission FOREIGN KEY (permissionCode)
        REFERENCES tblPermission (permissionCode)
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
    failedLoginCount LONG DEFAULT 0 NOT NULL,
    lockedUntil DATETIME,
    lastLoginAt DATETIME,
    rowVersion LONG DEFAULT 0 NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT fk_tblUser_role FOREIGN KEY (roleCode)
        REFERENCES tblRole (roleCode)
);

CREATE UNIQUE INDEX uk_tblUser_loginId ON tblUser (loginId);
CREATE INDEX idx_tblUser_roleCode ON tblUser (roleCode);
CREATE INDEX idx_tblUser_accountStatus ON tblUser (accountStatus);

CREATE TABLE tblAuditLog (
    auditId VARCHAR(36) PRIMARY KEY,
    userId VARCHAR(36),
    actionCode VARCHAR(64) NOT NULL,
    targetType VARCHAR(32) NOT NULL,
    targetId VARCHAR(36),
    resultCode VARCHAR(64) NOT NULL,
    clientAddress VARCHAR(64),
    createdAt DATETIME NOT NULL,
    CONSTRAINT fk_tblAuditLog_user FOREIGN KEY (userId)
        REFERENCES tblUser (userId)
);
