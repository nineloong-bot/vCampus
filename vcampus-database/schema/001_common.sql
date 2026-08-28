CREATE TABLE tblRequestDedup (
    requestId VARCHAR(36) PRIMARY KEY,
    userId VARCHAR(36),
    clientInstanceId VARCHAR(36) NOT NULL,
    command VARCHAR(64) NOT NULL,
    processingStatus VARCHAR(16) NOT NULL,
    resultCode VARCHAR(64),
    responseSnapshot MEMO,
    createdAt DATETIME NOT NULL,
    completedAt DATETIME
);

CREATE INDEX idx_tblRequestDedup_createdAt
    ON tblRequestDedup (createdAt);
