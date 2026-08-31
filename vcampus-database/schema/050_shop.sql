CREATE TABLE tblSellerApplication (
    applicationId VARCHAR(36) PRIMARY KEY,
    applicantUserId VARCHAR(36) NOT NULL,
    shopName VARCHAR(128) NOT NULL,
    description MEMO NOT NULL,
    category VARCHAR(64) NOT NULL,
    contact VARCHAR(128) NOT NULL,
    applicationStatus VARCHAR(16) NOT NULL,
    reviewReason VARCHAR(256),
    reviewerUserId VARCHAR(36),
    submittedAt DATETIME,
    reviewedAt DATETIME,
    rowVersion LONG NOT NULL DEFAULT 0,
    CONSTRAINT ck_seller_application_status CHECK (applicationStatus IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'))
);

ALTER TABLE tblSellerApplication ADD CONSTRAINT fk_seller_application_applicant
    FOREIGN KEY (applicantUserId) REFERENCES tblUser (userId);

ALTER TABLE tblSellerApplication ADD CONSTRAINT fk_seller_application_reviewer
    FOREIGN KEY (reviewerUserId) REFERENCES tblUser (userId);

CREATE INDEX idx_tblSellerApplication_applicant
    ON tblSellerApplication (applicantUserId);

CREATE INDEX idx_tblSellerApplication_status
    ON tblSellerApplication (applicationStatus);

CREATE TABLE tblShop (
    shopId VARCHAR(36) PRIMARY KEY,
    ownerUserId VARCHAR(36) NOT NULL,
    shopName VARCHAR(128) NOT NULL,
    description MEMO NOT NULL,
    category VARCHAR(64) NOT NULL,
    contact VARCHAR(128) NOT NULL,
    shopStatus VARCHAR(16) NOT NULL,
    suspensionReason VARCHAR(256),
    suspendedByUserId VARCHAR(36),
    suspendedAt DATETIME,
    rowVersion LONG NOT NULL DEFAULT 0,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT uk_tblShop_owner UNIQUE (ownerUserId),
    CONSTRAINT ck_shop_status CHECK (shopStatus IN ('ACTIVE', 'SUSPENDED'))
);

ALTER TABLE tblShop ADD CONSTRAINT fk_shop_owner
    FOREIGN KEY (ownerUserId) REFERENCES tblUser (userId);

ALTER TABLE tblShop ADD CONSTRAINT fk_shop_suspended_by
    FOREIGN KEY (suspendedByUserId) REFERENCES tblUser (userId);

CREATE TABLE tblProduct (
    productId VARCHAR(36) PRIMARY KEY,
    shopId VARCHAR(36) NOT NULL,
    productName VARCHAR(256) NOT NULL,
    normalizedProductName VARCHAR(256) NOT NULL,
    category VARCHAR(64) NOT NULL,
    description MEMO NOT NULL,
    coverImageUrl VARCHAR(2048),
    productStatus VARCHAR(16) NOT NULL,
    salesCount LONG NOT NULL DEFAULT 0,
    rowVersion LONG NOT NULL DEFAULT 0,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT fk_product_shop FOREIGN KEY (shopId) REFERENCES tblShop (shopId),
    CONSTRAINT ck_product_status CHECK (productStatus IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_product_sales CHECK (salesCount >= 0)
);

CREATE INDEX idx_tblProduct_shopId ON tblProduct (shopId);
CREATE UNIQUE INDEX uk_tblProduct_shop_normalized_name
    ON tblProduct (shopId, normalizedProductName);
CREATE INDEX idx_tblProduct_category_status ON tblProduct (category, productStatus);

CREATE TABLE tblProductSku (
    skuId VARCHAR(36) PRIMARY KEY,
    productId VARCHAR(36) NOT NULL,
    skuName VARCHAR(128) NOT NULL,
    unitPrice DECIMAL(12,2) NOT NULL,
    stockQuantity LONG NOT NULL,
    reservedQuantity LONG NOT NULL DEFAULT 0,
    isActive YESNO DEFAULT TRUE NOT NULL,
    rowVersion LONG NOT NULL DEFAULT 0,
    CONSTRAINT fk_sku_product FOREIGN KEY (productId) REFERENCES tblProduct (productId),
    CONSTRAINT ck_sku_price CHECK (unitPrice >= 0),
    CONSTRAINT ck_sku_stock CHECK (stockQuantity >= 0),
    CONSTRAINT ck_sku_reserved CHECK (reservedQuantity >= 0 AND reservedQuantity <= stockQuantity)
);

CREATE INDEX idx_tblProductSku_productId ON tblProductSku (productId);
CREATE INDEX idx_tblProductSku_isActive ON tblProductSku (isActive);

CREATE TABLE tblCart (
    cartId VARCHAR(36) PRIMARY KEY,
    userId VARCHAR(36) NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT uk_tblCart_user UNIQUE (userId),
    CONSTRAINT fk_cart_user FOREIGN KEY (userId) REFERENCES tblUser (userId)
);

CREATE TABLE tblCartItem (
    cartItemId VARCHAR(36) PRIMARY KEY,
    cartId VARCHAR(36) NOT NULL,
    skuId VARCHAR(36) NOT NULL,
    quantity LONG NOT NULL,
    rowVersion LONG NOT NULL DEFAULT 0,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cartId) REFERENCES tblCart (cartId),
    CONSTRAINT fk_cart_item_sku FOREIGN KEY (skuId) REFERENCES tblProductSku (skuId),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity > 0)
);

CREATE UNIQUE INDEX uk_tblCartItem_cart_sku ON tblCartItem (cartId, skuId);

CREATE TABLE tblOrderGroup (
    orderGroupId VARCHAR(36) PRIMARY KEY,
    buyerUserId VARCHAR(36) NOT NULL,
    totalAmount DECIMAL(12,2) NOT NULL,
    groupStatus VARCHAR(24) NOT NULL,
    createdAt DATETIME NOT NULL,
    rowVersion LONG NOT NULL DEFAULT 0,
    CONSTRAINT fk_order_group_buyer FOREIGN KEY (buyerUserId) REFERENCES tblUser (userId),
    CONSTRAINT ck_order_group_amount CHECK (totalAmount >= 0),
    CONSTRAINT ck_order_group_status CHECK (groupStatus IN ('PENDING_PAYMENT', 'PAID', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_tblOrderGroup_buyer_time ON tblOrderGroup (buyerUserId, createdAt);
CREATE INDEX idx_tblOrderGroup_status ON tblOrderGroup (groupStatus);

CREATE TABLE tblOrder (
    orderId VARCHAR(36) PRIMARY KEY,
    orderGroupId VARCHAR(36) NOT NULL,
    shopId VARCHAR(36) NOT NULL,
    orderNumber VARCHAR(32) NOT NULL,
    orderAmount DECIMAL(12,2) NOT NULL,
    orderStatus VARCHAR(24) NOT NULL,
    createdAt DATETIME NOT NULL,
    paidAt DATETIME,
    shippedAt DATETIME,
    completedAt DATETIME,
    rowVersion LONG NOT NULL DEFAULT 0,
    CONSTRAINT uk_tblOrder_number UNIQUE (orderNumber),
    CONSTRAINT fk_order_group FOREIGN KEY (orderGroupId) REFERENCES tblOrderGroup (orderGroupId),
    CONSTRAINT fk_order_shop FOREIGN KEY (shopId) REFERENCES tblShop (shopId),
    CONSTRAINT ck_order_amount CHECK (orderAmount >= 0),
    CONSTRAINT ck_order_status CHECK (orderStatus IN ('PENDING_PAYMENT', 'PAID', 'PREPARING', 'SHIPPED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_tblOrder_groupId ON tblOrder (orderGroupId);
CREATE INDEX idx_tblOrder_shop_status ON tblOrder (shopId, orderStatus);

CREATE TABLE tblOrderItem (
    orderItemId VARCHAR(36) PRIMARY KEY,
    orderId VARCHAR(36) NOT NULL,
    skuId VARCHAR(36) NOT NULL,
    productNameSnapshot VARCHAR(256) NOT NULL,
    skuNameSnapshot VARCHAR(128) NOT NULL,
    shopNameSnapshot VARCHAR(128) NOT NULL,
    unitPrice DECIMAL(12,2) NOT NULL,
    quantity LONG NOT NULL,
    lineAmount DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (orderId) REFERENCES tblOrder (orderId),
    CONSTRAINT ck_order_item_price CHECK (unitPrice >= 0),
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_item_amount CHECK (lineAmount >= 0)
);

CREATE INDEX idx_tblOrderItem_orderId ON tblOrderItem (orderId);

CREATE TABLE tblPayment (
    paymentId VARCHAR(36) PRIMARY KEY,
    orderGroupId VARCHAR(36) NOT NULL,
    paymentNumber VARCHAR(32) NOT NULL,
    successfulChannel VARCHAR(16),
    amount DECIMAL(12,2) NOT NULL,
    paymentStatus VARCHAR(16) NOT NULL,
    completedAt DATETIME,
    rowVersion LONG NOT NULL DEFAULT 0,
    CONSTRAINT uk_tblPayment_order_group UNIQUE (orderGroupId),
    CONSTRAINT uk_tblPayment_number UNIQUE (paymentNumber),
    CONSTRAINT fk_payment_order_group FOREIGN KEY (orderGroupId) REFERENCES tblOrderGroup (orderGroupId),
    CONSTRAINT ck_payment_channel CHECK (successfulChannel IS NULL OR successfulChannel IN ('WECHAT', 'ALIPAY', 'BANK_CARD')),
    CONSTRAINT ck_payment_amount CHECK (amount >= 0),
    CONSTRAINT ck_payment_status CHECK (paymentStatus IN ('PENDING', 'SUCCEEDED', 'CANCELLED', 'EXPIRED'))
);

CREATE TABLE tblPaymentAttempt (
    attemptId VARCHAR(36) PRIMARY KEY,
    paymentId VARCHAR(36) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    attemptStatus VARCHAR(16) NOT NULL,
    createdAt DATETIME NOT NULL,
    completedAt DATETIME,
    CONSTRAINT fk_payment_attempt_payment FOREIGN KEY (paymentId) REFERENCES tblPayment (paymentId),
    CONSTRAINT ck_payment_attempt_channel CHECK (channel IN ('WECHAT', 'ALIPAY', 'BANK_CARD')),
    CONSTRAINT ck_payment_attempt_status CHECK (attemptStatus IN ('STARTED', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_tblPaymentAttempt_payment_time ON tblPaymentAttempt (paymentId, createdAt);

CREATE TABLE tblInventoryReservation (
    reservationId VARCHAR(36) PRIMARY KEY,
    paymentId VARCHAR(36) NOT NULL,
    skuId VARCHAR(36) NOT NULL,
    quantity LONG NOT NULL,
    reservationStatus VARCHAR(16) NOT NULL,
    expiresAt DATETIME NOT NULL,
    releasedAt DATETIME,
    CONSTRAINT fk_reservation_payment FOREIGN KEY (paymentId) REFERENCES tblPayment (paymentId),
    CONSTRAINT fk_reservation_sku FOREIGN KEY (skuId) REFERENCES tblProductSku (skuId),
    CONSTRAINT ck_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT ck_reservation_status CHECK (reservationStatus IN ('ACTIVE', 'CONSUMED', 'RELEASED'))
);

CREATE UNIQUE INDEX uk_tblInventoryReservation_payment_sku
    ON tblInventoryReservation (paymentId, skuId);

CREATE INDEX idx_tblInventoryReservation_status_expiry
    ON tblInventoryReservation (reservationStatus, expiresAt);
