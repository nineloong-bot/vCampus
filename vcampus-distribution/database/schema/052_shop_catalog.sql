CREATE TABLE tblProductCatalog (
    productId VARCHAR(36) PRIMARY KEY,
    defaultSkuId VARCHAR(36),
    imageId VARCHAR(32),
    isDeleted YESNO DEFAULT FALSE NOT NULL,
    CONSTRAINT fk_catalog_product FOREIGN KEY (productId) REFERENCES tblProduct (productId)
);

CREATE TABLE tblSkuDraftFields (
    skuId VARCHAR(36) PRIMARY KEY,
    priceMissing YESNO DEFAULT FALSE NOT NULL,
    stockMissing YESNO DEFAULT FALSE NOT NULL,
    CONSTRAINT fk_draft_sku FOREIGN KEY (skuId) REFERENCES tblProductSku (skuId)
);

CREATE TABLE tblCatalogReceipt (
    receiptKey VARCHAR(200) PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    resultData MEMO NOT NULL
);
