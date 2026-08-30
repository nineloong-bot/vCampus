CREATE TABLE tblBook (
    bookId VARCHAR(36) PRIMARY KEY,
    isbn VARCHAR(20) NOT NULL,
    title VARCHAR(256) NOT NULL,
    author VARCHAR(128) NOT NULL,
    publisher VARCHAR(128),
    publishDate DATETIME,
    category VARCHAR(64) NOT NULL,
    description MEMO,
    isActive YESNO NOT NULL,
    rowVersion LONG NOT NULL
);
CREATE UNIQUE INDEX uk_tblBook_isbn ON tblBook (isbn);
CREATE INDEX idx_tblBook_title ON tblBook (title);
CREATE INDEX idx_tblBook_author ON tblBook (author);
CREATE INDEX idx_tblBook_category ON tblBook (category);

CREATE TABLE tblBookCopy (
    copyId VARCHAR(36) PRIMARY KEY,
    bookId VARCHAR(36) NOT NULL,
    barcode VARCHAR(32) NOT NULL,
    locationCode VARCHAR(64) NOT NULL,
    copyStatus VARCHAR(16) NOT NULL,
    rowVersion LONG NOT NULL
);
CREATE UNIQUE INDEX uk_tblBookCopy_barcode ON tblBookCopy (barcode);
CREATE INDEX idx_tblBookCopy_bookId ON tblBookCopy (bookId);
CREATE INDEX idx_tblBookCopy_copyStatus ON tblBookCopy (copyStatus);

CREATE TABLE tblBookLoan (
    loanId VARCHAR(36) PRIMARY KEY,
    copyId VARCHAR(36) NOT NULL,
    borrowerUserId VARCHAR(36) NOT NULL,
    borrowedAt DATETIME NOT NULL,
    dueAt DATETIME NOT NULL,
    returnedAt DATETIME,
    renewCount LONG NOT NULL,
    loanStatus VARCHAR(16) NOT NULL,
    rowVersion LONG NOT NULL
);
CREATE INDEX idx_tblBookLoan_copyId ON tblBookLoan (copyId);
CREATE INDEX idx_tblBookLoan_borrower_status ON tblBookLoan (borrowerUserId, loanStatus);
CREATE INDEX idx_tblBookLoan_dueAt ON tblBookLoan (dueAt);

CREATE TABLE tblLibraryPolicy (
    policyId VARCHAR(36) PRIMARY KEY,
    roleCode VARCHAR(16) NOT NULL,
    maxActiveLoans LONG NOT NULL,
    loanDays LONG NOT NULL,
    maxRenewals LONG NOT NULL,
    renewalDays LONG NOT NULL,
    rowVersion LONG NOT NULL
);
CREATE UNIQUE INDEX uk_tblLibraryPolicy_roleCode ON tblLibraryPolicy (roleCode);
