CREATE TABLE tblWalletAccount (
    userId VARCHAR(36) NOT NULL PRIMARY KEY,
    balanceCents DECIMAL(15,0) NOT NULL,
    rowVersion INTEGER NOT NULL,
    CONSTRAINT fk_wallet_user FOREIGN KEY (userId) REFERENCES tblUser(userId),
    CONSTRAINT ck_wallet_balance CHECK (balanceCents >= 0)
);
CREATE TABLE tblWalletOperation (
    operationId VARCHAR(36) NOT NULL PRIMARY KEY,
    businessKey VARCHAR(160) NOT NULL UNIQUE,
    operationType VARCHAR(16) NOT NULL,
    actorId VARCHAR(36) NOT NULL,
    peerId VARCHAR(36) NOT NULL,
    orderKey VARCHAR(64) NOT NULL,
    amountCents DECIMAL(15,0) NOT NULL,
    balanceAfter DECIMAL(15,0) NOT NULL,
    createdAt TIMESTAMP NOT NULL,
    CONSTRAINT ck_wallet_amount CHECK (amountCents > 0)
);
CREATE TABLE tblWalletEscrow (
    orderKey VARCHAR(64) NOT NULL PRIMARY KEY,
    buyerId VARCHAR(36) NOT NULL,
    sellerId VARCHAR(36) NOT NULL,
    amountCents DECIMAL(15,0) NOT NULL,
    escrowStatus VARCHAR(16) NOT NULL,
    CONSTRAINT ck_escrow_amount CHECK (amountCents > 0),
    CONSTRAINT ck_escrow_status CHECK (escrowStatus IN ('HELD','REFUNDED','SETTLED'))
);
CREATE TABLE tblWalletEntry (
    entryId VARCHAR(36) NOT NULL PRIMARY KEY,
    operationId VARCHAR(36) NOT NULL,
    accountKind VARCHAR(16) NOT NULL,
    accountKey VARCHAR(64) NOT NULL,
    deltaCents DECIMAL(15,0) NOT NULL,
    CONSTRAINT fk_entry_operation FOREIGN KEY (operationId) REFERENCES tblWalletOperation(operationId),
    CONSTRAINT ck_entry_kind CHECK (accountKind IN ('USER','ESCROW','SYSTEM'))
);
ALTER TABLE tblWalletEscrow ADD CONSTRAINT fk_escrow_buyer FOREIGN KEY (buyerId) REFERENCES tblUser(userId);
ALTER TABLE tblWalletEscrow ADD CONSTRAINT fk_escrow_seller FOREIGN KEY (sellerId) REFERENCES tblUser(userId);
