CREATE TABLE tblShopGovApplication (
 applicationId VARCHAR(36) PRIMARY KEY,
 subjectName VARCHAR(128) NOT NULL,
 licenseNumber VARCHAR(128) NOT NULL
);
CREATE TABLE tblShopQualification (
 qualificationId VARCHAR(36) PRIMARY KEY,
 shopId VARCHAR(36) NOT NULL,
 licenseType VARCHAR(64) NOT NULL,
 licenseNumber VARCHAR(128) NOT NULL,
 expiresOn DATETIME NOT NULL,
 qualificationStatus VARCHAR(16) NOT NULL,
 reviewReason VARCHAR(256),
 submittedAt DATETIME NOT NULL
);
CREATE TABLE tblShopProductRestriction (
 productId VARCHAR(36) PRIMARY KEY,
 emergencyBlocked YESNO DEFAULT FALSE NOT NULL,
 qualificationBlocked YESNO DEFAULT FALSE NOT NULL,
 expiryRestoreEligible YESNO DEFAULT FALSE NOT NULL
);
CREATE TABLE tblShopGovCase (
 caseId VARCHAR(36) PRIMARY KEY,
 actorId VARCHAR(36) NOT NULL,
 caseKind VARCHAR(24) NOT NULL,
 objectId VARCHAR(36) NOT NULL,
 shopId VARCHAR(36) NOT NULL,
 reason VARCHAR(256) NOT NULL,
 explanation MEMO NOT NULL,
 caseStatus VARCHAR(16) NOT NULL,
 resultText MEMO,
 createdAt DATETIME NOT NULL
);
CREATE TABLE tblShopGovAudit (
 auditId VARCHAR(36) PRIMARY KEY,
 actorId VARCHAR(36) NOT NULL,
 objectId VARCHAR(36) NOT NULL,
 actionName VARCHAR(32) NOT NULL,
 reason MEMO NOT NULL,
 occurredAt DATETIME NOT NULL,
 beforeState VARCHAR(32),
 afterState VARCHAR(32),
 linkedId VARCHAR(36)
);
CREATE TABLE tblShopGovReceipt (
 receiptKey VARCHAR(200) PRIMARY KEY,
 fingerprint VARCHAR(64) NOT NULL,
 resultId VARCHAR(36) NOT NULL
);
