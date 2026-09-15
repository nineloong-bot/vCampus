CREATE TABLE tblShopOrderState (
    orderId VARCHAR(36) PRIMARY KEY,
    lifecycle VARCHAR(24) NOT NULL,
    expiresAt DATETIME NOT NULL,
    refundReason VARCHAR(500),
    CONSTRAINT fk_shop_order_state FOREIGN KEY (orderId) REFERENCES tblOrder (orderId)
);
CREATE TABLE tblShopOrderLineState (
    orderItemId VARCHAR(36) PRIMARY KEY,
    productId VARCHAR(36) NOT NULL,
    lineState VARCHAR(16) NOT NULL,
    CONSTRAINT fk_shop_order_line FOREIGN KEY (orderItemId) REFERENCES tblOrderItem (orderItemId)
);
CREATE TABLE tblShopOrderReceipt (
    receiptKey VARCHAR(200) PRIMARY KEY,
    requestDigest VARCHAR(64) NOT NULL,
    orderIds MEMO NOT NULL
);
CREATE TABLE tblShopInventoryMovement (
    movementId VARCHAR(36) PRIMARY KEY,
    orderItemId VARCHAR(36) NOT NULL,
    skuId VARCHAR(36) NOT NULL,
    movementKind VARCHAR(16) NOT NULL,
    quantity LONG NOT NULL,
    createdAt DATETIME NOT NULL,
    CONSTRAINT uk_shop_inventory_movement UNIQUE (orderItemId, movementKind)
);
CREATE INDEX idx_shop_order_expiry ON tblShopOrderState (lifecycle, expiresAt);

CREATE TABLE tblShopOrderEvent (
    eventId VARCHAR(36) PRIMARY KEY,
    orderId VARCHAR(36) NOT NULL,
    actionName VARCHAR(24) NOT NULL,
    actorUserId VARCHAR(36),
    reason VARCHAR(500),
    previousState VARCHAR(24) NOT NULL,
    nextState VARCHAR(24) NOT NULL,
    createdAt DATETIME NOT NULL,
    CONSTRAINT fk_shop_order_event FOREIGN KEY (orderId) REFERENCES tblOrder (orderId)
);
