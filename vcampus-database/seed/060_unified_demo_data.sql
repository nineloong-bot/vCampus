INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode,
     accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt,
     rowVersion, createdAt, updatedAt)
VALUES ('demo-shop-draft-user', 'SHOPDRAFT',
    'qX+wANpmojiY0I1qjpBBoUCjiFP6bZJnWg5qgeHmNh4=',
    'mW5pbqIFUpGT2Zlkq7TsSA==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0,
    NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode,
     accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt,
     rowVersion, createdAt, updatedAt)
VALUES ('demo-shop-pending-user', 'SHOPPENDING',
    'qX+wANpmojiY0I1qjpBBoUCjiFP6bZJnWg5qgeHmNh4=',
    'mW5pbqIFUpGT2Zlkq7TsSA==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0,
    NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations, roleCode,
     accountStatus, mustChangePassword, failedLoginCount, lockedUntil, lastLoginAt,
     rowVersion, createdAt, updatedAt)
VALUES ('demo-shop-owner-user', 'SHOPOWNER',
    'qX+wANpmojiY0I1qjpBBoUCjiFP6bZJnWg5qgeHmNh4=',
    'mW5pbqIFUpGT2Zlkq7TsSA==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0,
    NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblTerm
    (termId, termCode, termName, startDate, endDate, enrollmentStartAt,
     enrollmentEndAt, adjustmentStartAt, adjustmentEndAt, termStatus,
     rowVersion, createdAt, updatedAt)
VALUES ('demo-term-2026', '2026-2027-1', '2026-2027学年第一学期', #2026-09-01#,
    #2027-01-15#, #2026-08-20#, #2026-09-30#, #2026-10-01#, #2026-10-07#,
    'ACTIVE', 0, NOW(), NOW());

INSERT INTO tblCourse
    (courseId, courseCode, courseName, credit, totalHours, description,
     isActive, rowVersion, createdAt, updatedAt)
VALUES ('demo-course-java', 'CS201', 'Java程序设计', 4.0, 64,
    '用于选课、退课与时间冲突测试', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourse
    (courseId, courseCode, courseName, credit, totalHours, description,
     isActive, rowVersion, createdAt, updatedAt)
VALUES ('demo-course-math', 'MATH101', '高等数学', 5.0, 80,
    '用于满员教学班测试', TRUE, 0, NOW(), NOW());

INSERT INTO tblCourseOffering
    (offeringId, termId, courseId, teacherUserId, className, capacity,
     enrolledCount, offeringStatus, rowVersion, createdAt, updatedAt)
VALUES ('demo-offering-java-a', 'demo-term-2026', 'demo-course-java',
    '00000000-0000-0000-0000-000000000002', 'Java程序设计-A', 40, 1,
    'OPEN', 0, NOW(), NOW());

INSERT INTO tblCourseOffering
    (offeringId, termId, courseId, teacherUserId, className, capacity,
     enrolledCount, offeringStatus, rowVersion, createdAt, updatedAt)
VALUES ('demo-offering-math-full', 'demo-term-2026', 'demo-course-math',
    '00000000-0000-0000-0000-000000000002', '高等数学-满员班', 1, 1,
    'OPEN', 0, NOW(), NOW());

INSERT INTO tblCourseSchedule
    (scheduleId, offeringId, dayOfWeek, startPeriod, endPeriod, startWeek, endWeek, classroom)
VALUES ('demo-schedule-java-a', 'demo-offering-java-a', 1, 1, 2, 1, 16, '教一-101');

INSERT INTO tblCourseSchedule
    (scheduleId, offeringId, dayOfWeek, startPeriod, endPeriod, startWeek, endWeek, classroom)
VALUES ('demo-schedule-math-full', 'demo-offering-math-full', 1, 1, 2, 1, 16, '教一-102');

INSERT INTO tblEnrollment
    (enrollmentId, offeringId, studentId, enrollmentType, enrollmentStatus,
     enrolledAt, droppedAt, rowVersion, createdAt, updatedAt)
VALUES ('demo-enrollment-java', 'demo-offering-java-a',
    '00000000-0000-0000-0000-000000000104', 'NORMAL', 'ACTIVE', NOW(), NULL,
    0, NOW(), NOW());

INSERT INTO tblBook
    (bookId, isbn, title, author, publisher, publishDate, category, description,
     isActive, rowVersion)
VALUES ('demo-book-java', '9787111213826', 'Java核心技术', 'Cay S. Horstmann',
    '机械工业出版社', #2024-01-01#, '计算机', '用于借阅流程测试', TRUE, 0);

INSERT INTO tblBook
    (bookId, isbn, title, author, publisher, publishDate, category, description,
     isActive, rowVersion)
VALUES ('demo-book-campus', '9787300000001', '大学生活指南', '测试编写组',
    '东南大学出版社', #2025-09-01#, '综合', '用于馆藏检索测试', TRUE, 0);

INSERT INTO tblBookCopy
    (copyId, bookId, barcode, locationCode, copyStatus, rowVersion)
VALUES ('demo-copy-java-overdue', 'demo-book-java', 'LIB-DEMO-001', '九龙湖-一层',
    'BORROWED', 0);

INSERT INTO tblBookCopy
    (copyId, bookId, barcode, locationCode, copyStatus, rowVersion)
VALUES ('demo-copy-java-available', 'demo-book-java', 'LIB-DEMO-002', '九龙湖-一层',
    'AVAILABLE', 0);

INSERT INTO tblBookCopy
    (copyId, bookId, barcode, locationCode, copyStatus, rowVersion)
VALUES ('demo-copy-campus-borrowed', 'demo-book-campus', 'LIB-DEMO-003', '四牌楼-二层',
    'BORROWED', 0);

INSERT INTO tblBookLoan
    (loanId, copyId, borrowerUserId, borrowedAt, dueAt, returnedAt,
     renewCount, loanStatus, rowVersion)
VALUES ('demo-loan-overdue', 'demo-copy-java-overdue',
    '00000000-0000-0000-0000-000000000003', #2026-07-01#, #2026-07-31#,
    NULL, 0, 'ACTIVE', 0);

INSERT INTO tblBookLoan
    (loanId, copyId, borrowerUserId, borrowedAt, dueAt, returnedAt,
     renewCount, loanStatus, rowVersion)
VALUES ('demo-loan-current', 'demo-copy-campus-borrowed',
    '00000000-0000-0000-0000-000000000003', #2026-08-25#, #2026-10-30#,
    NULL, 0, 'ACTIVE', 0);

INSERT INTO tblSellerApplication
    (applicationId, applicantUserId, shopName, description, category, contact,
     applicationStatement, applicationStatus, reviewReason, reviewerUserId,
     submittedAt, reviewedAt, rowVersion)
VALUES ('demo-app-draft', 'demo-shop-draft-user', '草稿文具店', '申请草稿示例',
    '文具', '13800000011', '计划经营学习用品', 'DRAFT', NULL, NULL, NULL, NULL, 0);

INSERT INTO tblSellerApplication
    (applicationId, applicantUserId, shopName, description, category, contact,
     applicationStatement, applicationStatus, reviewReason, reviewerUserId,
     submittedAt, reviewedAt, rowVersion)
VALUES ('demo-app-pending', 'demo-shop-pending-user', '待审核书店', '待审核申请示例',
    '图书', '13800000012', '计划经营教材与参考书', 'PENDING', NULL, NULL,
    NOW(), NULL, 0);

INSERT INTO tblSellerApplication
    (applicationId, applicantUserId, shopName, description, category, contact,
     applicationStatement, applicationStatus, reviewReason, reviewerUserId,
     submittedAt, reviewedAt, rowVersion)
VALUES ('demo-app-approved', 'demo-shop-owner-user', '校园文具铺', '已通过店铺申请',
    '文具', '13800000013', '提供常用学习用品', 'APPROVED', '资料完整',
    '00000000-0000-0000-0000-000000000001', #2026-08-01#, #2026-08-02#, 0);

INSERT INTO tblShop
    (shopId, ownerUserId, shopName, normalizedShopName, description, category,
     contact, shopStatus, suspensionReason, suspendedByUserId, suspendedAt,
     rowVersion, createdAt, updatedAt)
VALUES ('demo-shop-stationery', 'demo-shop-owner-user', '校园文具铺', '校园文具铺',
    '测试商品和订单的示例店铺', '文具', '13800000013', 'ACTIVE', NULL, NULL,
    NULL, 0, NOW(), NOW());

INSERT INTO tblProduct
    (productId, shopId, productName, normalizedProductName, category, description,
     coverImageUrl, productStatus, salesCount, rowVersion, createdAt, updatedAt)
VALUES ('demo-product-pen', 'demo-shop-stationery', '中性笔', '中性笔', '文具',
    '包含黑色和红色两个商品种类', NULL, 'ACTIVE', 20, 0, NOW(), NOW());

INSERT INTO tblProduct
    (productId, shopId, productName, normalizedProductName, category, description,
     coverImageUrl, productStatus, salesCount, rowVersion, createdAt, updatedAt)
VALUES ('demo-product-notebook', 'demo-shop-stationery', '笔记本', '笔记本', '文具',
    '下架商品示例', NULL, 'INACTIVE', 3, 0, NOW(), NOW());

INSERT INTO tblProduct
    (productId, shopId, productName, normalizedProductName, category, description,
     coverImageUrl, productStatus, salesCount, rowVersion, createdAt, updatedAt)
VALUES ('demo-product-draft', 'demo-shop-stationery', '草稿商品', '草稿商品', '文具',
    '草稿状态不可直接上架', NULL, 'DRAFT', 0, 0, NOW(), NOW());

INSERT INTO tblProductSku
    (skuId, productId, skuName, unitPrice, stockQuantity, reservedQuantity,
     isActive, rowVersion)
VALUES ('demo-sku-pen-black', 'demo-product-pen', '黑色', 2.50, 100, 0, TRUE, 0);

INSERT INTO tblProductSku
    (skuId, productId, skuName, unitPrice, stockQuantity, reservedQuantity,
     isActive, rowVersion)
VALUES ('demo-sku-pen-red', 'demo-product-pen', '红色', 2.50, 80, 0, TRUE, 0);

INSERT INTO tblProductSku
    (skuId, productId, skuName, unitPrice, stockQuantity, reservedQuantity,
     isActive, rowVersion)
VALUES ('demo-sku-notebook-a5', 'demo-product-notebook', 'A5', 8.00, 30, 0, TRUE, 0);

INSERT INTO tblCart (cartId, userId, updatedAt)
VALUES ('demo-cart-student', '00000000-0000-0000-0000-000000000003', NOW());

INSERT INTO tblCartItem
    (cartItemId, cartId, skuId, quantity, rowVersion, createdAt, updatedAt)
VALUES ('demo-cart-item-black', 'demo-cart-student', 'demo-sku-pen-black', 2, 0, NOW(), NOW());

INSERT INTO tblCartItem
    (cartItemId, cartId, skuId, quantity, rowVersion, createdAt, updatedAt)
VALUES ('demo-cart-item-red', 'demo-cart-student', 'demo-sku-pen-red', 1, 0, NOW(), NOW());

INSERT INTO tblOrderGroup
    (orderGroupId, buyerUserId, totalAmount, groupStatus, createdAt, rowVersion)
VALUES ('demo-order-group-pending', '00000000-0000-0000-0000-000000000003',
    5.00, 'PENDING_PAYMENT', NOW(), 0);

INSERT INTO tblOrder
    (orderId, orderGroupId, shopId, orderNumber, orderAmount, orderStatus,
     createdAt, paidAt, shippedAt, completedAt, rowVersion)
VALUES ('demo-order-pending', 'demo-order-group-pending', 'demo-shop-stationery',
    'DEMO-PENDING-001', 5.00, 'PENDING_PAYMENT', NOW(), NULL, NULL, NULL, 0);

INSERT INTO tblOrderItem
    (orderItemId, orderId, skuId, productNameSnapshot, skuNameSnapshot,
     shopNameSnapshot, unitPrice, quantity, lineAmount)
VALUES ('demo-order-item-pending', 'demo-order-pending', 'demo-sku-pen-black',
    '中性笔', '黑色', '校园文具铺', 2.50, 2, 5.00);

INSERT INTO tblPayment
    (paymentId, orderGroupId, paymentNumber, successfulChannel, amount,
     paymentStatus, completedAt, rowVersion)
VALUES ('demo-payment-pending', 'demo-order-group-pending', 'PAY-DEMO-001', NULL,
    5.00, 'PENDING', NULL, 0);

INSERT INTO tblOrderGroup
    (orderGroupId, buyerUserId, totalAmount, groupStatus, createdAt, rowVersion)
VALUES ('demo-order-group-paid', '00000000-0000-0000-0000-000000000003',
    2.50, 'PAID', #2026-08-20#, 0);

INSERT INTO tblOrder
    (orderId, orderGroupId, shopId, orderNumber, orderAmount, orderStatus,
     createdAt, paidAt, shippedAt, completedAt, rowVersion)
VALUES ('demo-order-paid', 'demo-order-group-paid', 'demo-shop-stationery',
    'DEMO-PAID-001', 2.50, 'PAID', #2026-08-20#, #2026-08-20#, NULL, NULL, 0);

INSERT INTO tblOrderItem
    (orderItemId, orderId, skuId, productNameSnapshot, skuNameSnapshot,
     shopNameSnapshot, unitPrice, quantity, lineAmount)
VALUES ('demo-order-item-paid', 'demo-order-paid', 'demo-sku-pen-red',
    '中性笔', '红色', '校园文具铺', 2.50, 1, 2.50);

INSERT INTO tblPayment
    (paymentId, orderGroupId, paymentNumber, successfulChannel, amount,
     paymentStatus, completedAt, rowVersion)
VALUES ('demo-payment-paid', 'demo-order-group-paid', 'PAY-DEMO-002', 'WECHAT',
    2.50, 'SUCCEEDED', #2026-08-20#, 0);
