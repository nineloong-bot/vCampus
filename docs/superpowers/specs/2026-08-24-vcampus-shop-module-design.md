# 虚拟校园多商户商城模块设计

## 1. 目标与范围

本模块实现类似主流电商的校园多商户商城：商城首页、推荐、分类、搜索、店铺、商品 SKU、购物车、跨店订单、店主申请、商家履约和微信/支付宝/银行卡模拟支付。模块不接入真实金融平台，不保存支付账号或银行卡信息，不实现退款。

## 2. 身份与权限

- 学生、教师：买家能力；可浏览、加购、下单、支付和确认收货。
- 已批准店主：在原身份上增加店主能力；只能管理自己的店铺、商品和订单。
- 管理员：审核开店申请、停用店铺/商品、查询平台订单及支付日志。
- 一个用户最多拥有一家有效店铺。申请人必须是有效学生或教师账户。

## 3. 店主申请与店铺状态

店主申请状态固定为 `DRAFT → PENDING → APPROVED/REJECTED`。驳回后修改时执行 `REJECTED → DRAFT`，再次提交时执行 `DRAFT → PENDING`。驳回必须填写原因；审核通过和创建 `ACTIVE` 店铺在同一事务完成。批准后的停用和恢复属于店铺 `ACTIVE ↔ SUSPENDED` 状态，不改变已批准申请的 `APPROVED` 状态；停用必须填写原因。

## 4. 商品与店铺规则

- 商品属于一个店铺，至少有一个 SKU 才能上架。
- SKU 定义规格名称、价格、库存和状态。
- 店铺停用时全部商品从买家查询和推荐中隐藏。
- 商品下架不影响历史订单快照。
- 订单明细保存下单时名称、规格、价格和店铺名称，不能依赖商品当前值展示历史。
- 店主更新库存不能使 `stockQuantity` 小于 `reservedQuantity`。

## 5. 首页与推荐

推荐为可解释规则算法：

```text
score = categoryAffinity * 0.50
      + salesRank30Days * 0.30
      + freshness * 0.20
```

用户行为只记录 `VIEW`、`ADD_CART` 和 `PURCHASE` 的用户、商品、分类和时间。新用户或推荐计算失败时返回“近 30 天热门 + 新品”。所有候选必须过滤停用店铺、下架商品和无可售库存 SKU。推荐不是结算依据，结算必须重新读取价格和库存。

## 6. 购物车与订单组

- 购物车持久化到服务端；一个用户只有一个活动购物车。
- 同一 SKU 重复加入时合并数量。
- 购物车显示价格仅供预览，结算以事务内实时价格为准。
- 勾选项按 `shopId` 拆成多个子订单，并创建一个 `OrderGroup`。
- 一个支付单关联一个订单组；支付成功后组内所有子订单同时变为已支付。
- 任一商品无效、价格变化未确认或库存不足时，整个结算不创建订单。

## 7. 库存预留与支付

结算成功创建订单后预留库存 15 分钟。可售库存为 `stockQuantity - reservedQuantity`。模拟收银台允许 `WECHAT`、`ALIPAY`、`BANK_CARD` 三个渠道。单次支付尝试产生 `SUCCEEDED`、`FAILED` 或 `CANCELLED` 结果；失败只记录本次尝试，支付单在预留期内仍保持 `PENDING` 并允许重试。用户明确取消时支付单变为 `CANCELLED`，超时任务将其变为 `EXPIRED`。

支付成功事务：锁定支付单、订单组和所有 SKU，校验仍为待支付，扣减实际库存与预留数量，更新全部订单并记录支付结果。失败、取消或过期只释放预留，不扣实际库存。同一支付单重复成功回调只生效一次。

## 8. 状态模型

```text
SellerApplication: DRAFT → PENDING → APPROVED
SellerApplication: PENDING → REJECTED → DRAFT
Payment: PENDING → SUCCEEDED | CANCELLED | EXPIRED
PaymentAttempt: STARTED → SUCCEEDED | FAILED | CANCELLED
Order: PENDING_PAYMENT → PAID → PREPARING → SHIPPED → COMPLETED
Order: PENDING_PAYMENT → CANCELLED
Product: DRAFT → ACTIVE → INACTIVE
Shop: ACTIVE ↔ SUSPENDED
```

买家只能取消待支付订单组；支付成功后由店主推进备货和发货，买家确认收货。超出课程范围的退款请求以客服提示结束，不改变支付状态。

## 9. Swing 页面

买家：`M-01 ShopHomePanel`、`M-02 ProductSearchPanel`、`M-03 ProductDetailPanel`、`M-04 CartPanel`、`M-05 CheckoutPanel`、`M-06 SimulatedCashierDialog`、`M-07 MyOrdersPanel`、`M-08 OrderDetailPanel`。

店主：`M-09 SellerApplicationPanel`、`M-10 SellerDashboardPanel`、`M-11 ShopProfilePanel`、`M-12 ProductManagementPanel`、`M-13 SellerOrderPanel`。

管理员：`M-14 SellerReviewPanel`、`M-15 ShopGovernancePanel`、`M-16 PlatformOrderPanel`。

收银台只展示模拟标识、支付渠道、订单号、金额和成功/失败/取消按钮，不采集真实账号、卡号、密码或验证码。

## 10. DTO

```java
enum SellerApplicationStatus { DRAFT, PENDING, APPROVED, REJECTED }
enum ShopStatus { ACTIVE, SUSPENDED }
enum ProductStatus { DRAFT, ACTIVE, INACTIVE }
enum PaymentChannel { WECHAT, ALIPAY, BANK_CARD }
enum PaymentStatus { PENDING, SUCCEEDED, CANCELLED, EXPIRED }
enum PaymentAttemptStatus { STARTED, SUCCEEDED, FAILED, CANCELLED }
enum OrderStatus { PENDING_PAYMENT, PAID, PREPARING, SHIPPED,
                   COMPLETED, CANCELLED }

record AddCartItemCommand(String skuId, int quantity)
        implements Serializable {}
record UpdateCartItemCommand(String cartItemId, int quantity,
                             long expectedVersion)
        implements Serializable {}
record CheckoutItem(String cartItemId, BigDecimal displayedUnitPrice)
        implements Serializable {}
record CheckoutCommand(List<CheckoutItem> items,
                       boolean acceptLatestPrice)
        implements Serializable {}
record SimulatePaymentCommand(String paymentId, PaymentChannel channel,
                              PaymentAttemptStatus simulatedResult)
        implements Serializable {}
record ApplySellerCommand(String shopName, String description,
                          String category, String contact)
        implements Serializable {}
```

## 11. 服务接口

```java
public interface ShopService {
    PageResult<ProductSummary> getHomeProducts(HomeProductQuery query);
    PageResult<ProductSummary> searchProducts(ProductSearchQuery query);
    ProductDetail getProduct(String productId);
    CartView getCart(String sessionToken);
    CartView addToCart(String sessionToken, AddCartItemCommand command);
    CartView updateCartItem(String sessionToken,
                            UpdateCartItemCommand command);
    CartView removeCartItem(String sessionToken, String cartItemId);
    CheckoutResult checkout(String sessionToken, CheckoutCommand command);
    PaymentView simulatePayment(String sessionToken,
                                SimulatePaymentCommand command);
    PageResult<OrderSummary> getMyOrders(String sessionToken,
                                         OrderSearchQuery query);
    void cancelOrder(String sessionToken, String orderGroupId);
    void confirmReceipt(String sessionToken, String orderId);
}

public interface SellerService {
    SellerApplicationView apply(String sessionToken,
                                ApplySellerCommand command);
    ShopView updateShop(String sessionToken, UpdateShopCommand command);
    ProductView createProduct(String sessionToken,
                              CreateProductCommand command);
    ProductView updateProduct(String sessionToken,
                              UpdateProductCommand command);
    void changeProductStatus(String sessionToken,
                             ChangeProductStatusCommand command);
    PageResult<SellerOrderView> getSellerOrders(String sessionToken,
                                                 SellerOrderQuery query);
    void updateOrderStatus(String sessionToken,
                           UpdateOrderStatusCommand command);
}

public interface ShopAdminService {
    PageResult<SellerApplicationView> searchApplications(
            SellerApplicationQuery query);
    SellerApplicationView reviewApplication(
            ReviewSellerApplicationCommand command);
    void suspendShop(SuspendShopCommand command);
}
```

## 12. 消息命令

买家命令：`SHOP_HOME`、`SHOP_SEARCH_PRODUCTS`、`SHOP_GET_PRODUCT`、`SHOP_GET_CART`、`SHOP_CART_ADD`、`SHOP_CART_UPDATE`、`SHOP_CART_REMOVE`、`SHOP_CHECKOUT`、`SHOP_SIMULATE_PAYMENT`、`SHOP_GET_MY_ORDERS`、`SHOP_CANCEL_ORDER_GROUP`、`SHOP_CONFIRM_RECEIPT`。

店主命令：`SHOP_APPLY_SELLER`、`SHOP_GET_SELLER_APPLICATION`、`SHOP_UPDATE_PROFILE`、`SHOP_CREATE_PRODUCT`、`SHOP_UPDATE_PRODUCT`、`SHOP_CHANGE_PRODUCT_STATUS`、`SHOP_GET_SELLER_ORDERS`、`SHOP_UPDATE_ORDER_STATUS`。

管理员命令：`SHOP_SEARCH_SELLER_APPLICATIONS`、`SHOP_REVIEW_SELLER_APPLICATION`、`SHOP_SUSPEND`、`SHOP_SEARCH_PLATFORM_ORDERS`、`SHOP_SEARCH_PAYMENTS`。全部写命令必须幂等。

## 13. 数据库

### 13.1 `tblSellerApplication` 店主申请表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `applicationId` | 申请内部编号 | `VARCHAR(36)` | 主键；UUID |
| `applicantUserId` | 申请人用户编号 | `VARCHAR(36)` | 非空；外键关联 `tblUser.userId` |
| `shopName` | 拟用店铺名称 | `VARCHAR(128)` | 非空 |
| `description` | 店铺简介 | `LONGTEXT` | 非空 |
| `category` | 经营分类 | `VARCHAR(64)` | 非空 |
| `contact` | 店铺联系方式 | `VARCHAR(128)` | 非空 |
| `applicationStatus` | 申请状态 | `VARCHAR(16)` | 非空；`DRAFT/PENDING/APPROVED/REJECTED` |
| `reviewReason` | 审核意见 | `VARCHAR(256)` | 可空；驳回时非空 |
| `reviewerUserId` | 审核管理员编号 | `VARCHAR(36)` | 可空；外键关联 `tblUser.userId` |
| `submittedAt` | 提交时间 | `DATETIME` | 可空；草稿状态可为空 |
| `reviewedAt` | 审核时间 | `DATETIME` | 可空；未审核时为空 |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

索引：`idx_tblSellerApplication_applicant`、`idx_tblSellerApplication_status`。

### 13.2 `tblShop` 店铺表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `shopId` | 店铺内部编号 | `VARCHAR(36)` | 主键；UUID |
| `ownerUserId` | 店主用户编号 | `VARCHAR(36)` | 非空；唯一；外键关联 `tblUser.userId` |
| `shopName` | 店铺名称 | `VARCHAR(128)` | 非空 |
| `description` | 店铺简介 | `LONGTEXT` | 非空 |
| `category` | 经营分类 | `VARCHAR(64)` | 非空 |
| `contact` | 店铺联系方式 | `VARCHAR(128)` | 非空 |
| `shopStatus` | 店铺状态 | `VARCHAR(16)` | 非空；`ACTIVE/SUSPENDED` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |
| `createdAt` | 店铺创建时间 | `DATETIME` | 非空 |
| `updatedAt` | 最后更新时间 | `DATETIME` | 非空 |

### 13.3 `tblProduct` 商品表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `productId` | 商品内部编号 | `VARCHAR(36)` | 主键；UUID |
| `shopId` | 所属店铺编号 | `VARCHAR(36)` | 非空；外键关联 `tblShop.shopId` |
| `productName` | 商品名称 | `VARCHAR(256)` | 非空 |
| `category` | 商品分类 | `VARCHAR(64)` | 非空 |
| `description` | 商品详情 | `LONGTEXT` | 非空 |
| `productStatus` | 商品状态 | `VARCHAR(16)` | 非空；`DRAFT/ACTIVE/INACTIVE` |
| `salesCount` | 累计成交件数 | `LONG` | 非空；默认 `0` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |
| `createdAt` | 商品创建时间 | `DATETIME` | 非空 |
| `updatedAt` | 最后更新时间 | `DATETIME` | 非空 |

索引：`idx_tblProduct_shopId`、`idx_tblProduct_category_status`。

### 13.4 `tblProductSku` 商品规格与库存表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `skuId` | 商品规格编号 | `VARCHAR(36)` | 主键；UUID |
| `productId` | 所属商品编号 | `VARCHAR(36)` | 非空；外键关联 `tblProduct.productId` |
| `skuName` | 规格名称 | `VARCHAR(128)` | 非空；例如颜色和尺寸组合 |
| `unitPrice` | 当前销售单价 | `DECIMAL(12,2)` | 非空；大于或等于 `0` |
| `stockQuantity` | 实际库存数量 | `LONG` | 非空；大于或等于 `0` |
| `reservedQuantity` | 已预留未支付数量 | `LONG` | 非空；默认 `0`，不大于实际库存 |
| `isActive` | 规格是否可售 | `YESNO` | 非空；默认 `TRUE` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

索引：`idx_tblProductSku_productId`、`idx_tblProductSku_isActive`。

### 13.5 `tblProductBehavior` 用户商品行为表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `behaviorId` | 行为记录编号 | `VARCHAR(36)` | 主键；UUID |
| `userId` | 用户编号 | `VARCHAR(36)` | 非空；外键关联 `tblUser.userId` |
| `productId` | 商品编号 | `VARCHAR(36)` | 非空；外键关联 `tblProduct.productId` |
| `category` | 行为发生时的商品分类 | `VARCHAR(64)` | 非空；用于推荐聚合 |
| `behaviorType` | 行为类型 | `VARCHAR(16)` | 非空；`VIEW/ADD_CART/PURCHASE` |
| `createdAt` | 行为发生时间 | `DATETIME` | 非空 |

索引：`idx_tblProductBehavior_user_time`、`idx_tblProductBehavior_product_time`。

### 13.6 `tblCart` 购物车表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `cartId` | 购物车内部编号 | `VARCHAR(36)` | 主键；UUID |
| `userId` | 所属用户编号 | `VARCHAR(36)` | 非空；唯一；外键关联 `tblUser.userId` |
| `updatedAt` | 最后更新时间 | `DATETIME` | 非空 |

### 13.7 `tblCartItem` 购物车商品项表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `cartItemId` | 购物车项编号 | `VARCHAR(36)` | 主键；UUID |
| `cartId` | 所属购物车编号 | `VARCHAR(36)` | 非空；外键关联 `tblCart.cartId` |
| `skuId` | 商品规格编号 | `VARCHAR(36)` | 非空；外键关联 `tblProductSku.skuId` |
| `quantity` | 加购数量 | `LONG` | 非空；大于 `0` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |
| `createdAt` | 首次加入时间 | `DATETIME` | 非空 |
| `updatedAt` | 最后更新时间 | `DATETIME` | 非空 |

唯一索引：`uk_tblCartItem_cart_sku(cartId, skuId)`。

### 13.8 `tblOrderGroup` 订单组表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `orderGroupId` | 订单组内部编号 | `VARCHAR(36)` | 主键；UUID |
| `buyerUserId` | 买家用户编号 | `VARCHAR(36)` | 非空；外键关联 `tblUser.userId` |
| `totalAmount` | 订单组总金额 | `DECIMAL(12,2)` | 非空；等于所有子订单金额之和 |
| `groupStatus` | 订单组状态 | `VARCHAR(24)` | 非空；`PENDING_PAYMENT/PAID/COMPLETED/CANCELLED` |
| `createdAt` | 创建时间 | `DATETIME` | 非空 |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

索引：`idx_tblOrderGroup_buyer_time`、`idx_tblOrderGroup_status`。

### 13.9 `tblOrder` 店铺子订单表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `orderId` | 子订单内部编号 | `VARCHAR(36)` | 主键；UUID |
| `orderGroupId` | 所属订单组编号 | `VARCHAR(36)` | 非空；外键关联 `tblOrderGroup.orderGroupId` |
| `shopId` | 店铺编号 | `VARCHAR(36)` | 非空；外键关联 `tblShop.shopId` |
| `orderNumber` | 对外订单号 | `VARCHAR(32)` | 非空；唯一 |
| `orderAmount` | 子订单金额 | `DECIMAL(12,2)` | 非空；等于该订单明细金额之和 |
| `orderStatus` | 子订单状态 | `VARCHAR(24)` | 非空；`PENDING_PAYMENT/PAID/PREPARING/SHIPPED/COMPLETED/CANCELLED` |
| `createdAt` | 创建时间 | `DATETIME` | 非空 |
| `paidAt` | 支付成功时间 | `DATETIME` | 可空 |
| `shippedAt` | 店主发货时间 | `DATETIME` | 可空 |
| `completedAt` | 订单完成时间 | `DATETIME` | 可空 |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

索引：`idx_tblOrder_groupId`、`idx_tblOrder_shop_status`。

### 13.10 `tblOrderItem` 订单明细表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `orderItemId` | 订单明细编号 | `VARCHAR(36)` | 主键；UUID |
| `orderId` | 所属子订单编号 | `VARCHAR(36)` | 非空；外键关联 `tblOrder.orderId` |
| `skuId` | 下单时商品规格编号 | `VARCHAR(36)` | 非空；保留逻辑引用，不级联删除 |
| `productNameSnapshot` | 下单时商品名称 | `VARCHAR(256)` | 非空；历史快照 |
| `skuNameSnapshot` | 下单时规格名称 | `VARCHAR(128)` | 非空；历史快照 |
| `shopNameSnapshot` | 下单时店铺名称 | `VARCHAR(128)` | 非空；历史快照 |
| `unitPrice` | 成交单价 | `DECIMAL(12,2)` | 非空；大于或等于 `0` |
| `quantity` | 成交数量 | `LONG` | 非空；大于 `0` |
| `lineAmount` | 明细金额 | `DECIMAL(12,2)` | 非空；等于成交单价乘数量 |

索引：`idx_tblOrderItem_orderId`。

### 13.11 `tblPayment` 聚合支付单表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `paymentId` | 支付单内部编号 | `VARCHAR(36)` | 主键；UUID |
| `orderGroupId` | 订单组编号 | `VARCHAR(36)` | 非空；唯一；外键关联 `tblOrderGroup.orderGroupId` |
| `paymentNumber` | 对外支付单号 | `VARCHAR(32)` | 非空；唯一 |
| `successfulChannel` | 最终成功支付渠道 | `VARCHAR(16)` | 可空；成功后为 `WECHAT/ALIPAY/BANK_CARD` |
| `amount` | 应付金额 | `DECIMAL(12,2)` | 非空；等于订单组总金额 |
| `paymentStatus` | 支付单状态 | `VARCHAR(16)` | 非空；`PENDING/SUCCEEDED/CANCELLED/EXPIRED` |
| `completedAt` | 支付终结时间 | `DATETIME` | 可空；仍待支付时为空 |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

### 13.12 `tblPaymentAttempt` 模拟支付尝试表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `attemptId` | 支付尝试编号 | `VARCHAR(36)` | 主键；UUID |
| `paymentId` | 所属支付单编号 | `VARCHAR(36)` | 非空；外键关联 `tblPayment.paymentId` |
| `channel` | 本次模拟支付渠道 | `VARCHAR(16)` | 非空；`WECHAT/ALIPAY/BANK_CARD` |
| `attemptStatus` | 本次尝试结果 | `VARCHAR(16)` | 非空；`STARTED/SUCCEEDED/FAILED/CANCELLED` |
| `createdAt` | 尝试开始时间 | `DATETIME` | 非空 |
| `completedAt` | 尝试结束时间 | `DATETIME` | 可空；尚未结束时为空 |

索引：`idx_tblPaymentAttempt_payment_time`。

### 13.13 `tblInventoryReservation` 库存预留表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `reservationId` | 预留记录编号 | `VARCHAR(36)` | 主键；UUID |
| `paymentId` | 支付单编号 | `VARCHAR(36)` | 非空；外键关联 `tblPayment.paymentId` |
| `skuId` | 商品规格编号 | `VARCHAR(36)` | 非空；外键关联 `tblProductSku.skuId` |
| `quantity` | 预留数量 | `LONG` | 非空；大于 `0` |
| `reservationStatus` | 预留状态 | `VARCHAR(16)` | 非空；`ACTIVE/CONSUMED/RELEASED` |
| `expiresAt` | 预留过期时间 | `DATETIME` | 非空 |
| `releasedAt` | 消耗或释放时间 | `DATETIME` | 可空；有效预留时为空 |

唯一索引：`uk_tblInventoryReservation_payment_sku(paymentId, skuId)`；查询索引：`idx_tblInventoryReservation_status_expiry`。

数据库约束要求价格、数量和金额非负；订单金额等于明细金额之和；支付金额等于订单组总额。

## 14. 事务与并发

- 购物车更新锁定 `CART:<userId>`，相同 SKU 合并且数量大于零。
- 结算按 `skuId` 排序锁定所有 SKU，再锁定购物车；事务内重新读取价格、状态和可售库存。
- 价格不一致且 `acceptLatestPrice=false` 时返回最新价格，不创建订单。
- 支付锁定 `PAYMENT:<paymentId>`、`ORDER_GROUP:<id>` 和排序后的 SKU；状态不是 `PENDING` 时返回已有终态。失败尝试只追加 `tblPaymentAttempt`，不释放预留；成功、明确取消或超时才改变支付单终态。
- 店主更新商品锁定 `PRODUCT:<id>`/`SKU:<id>` 并校验店铺所有权。
- 审核锁定 `SELLER_APPLICATION:<id>` 和 `USER:<applicantUserId>`，防止重复通过和一人多店。
- 过期恢复任务与支付回调使用相同支付锁，避免同时释放和扣减库存。

## 15. 错误码

`SHOP_SELLER_APPLICATION_EXISTS`、`SHOP_SELLER_NOT_APPROVED`、`SHOP_NOT_OWNER`、`SHOP_SUSPENDED`、`SHOP_PRODUCT_INACTIVE`、`SHOP_SKU_UNAVAILABLE`、`SHOP_PRICE_CHANGED`、`SHOP_INSUFFICIENT_STOCK`、`SHOP_CART_EMPTY`、`SHOP_ORDER_STATUS_INVALID`、`SHOP_ORDER_NOT_OWNED`、`PAYMENT_ALREADY_COMPLETED`、`PAYMENT_NOT_PENDING`、`PAYMENT_AMOUNT_MISMATCH`。

## 16. 日志与隐私

记录店主申请审核、商品上下架、结算、支付状态和订单状态变化。不得记录真实支付账号、卡号、验证码或密码；模拟支付也只记录渠道、支付单、金额和结果。

## 17. 测试与验收

- 教师/学生可申请，管理员通过后才出现店主能力；一人不能拥有两店。
- 店主不能修改其他店铺商品或订单。
- 推荐过滤停用店铺、下架商品和无库存 SKU；算法失败降级为热门新品。
- 相同 SKU 重复加购合并数量，重登后购物车保留。
- 跨店购物车正确生成一个订单组和多个子订单。
- 库存为 5 时并发购买总成交不得超过 5。
- 价格变化时未确认不得创建任何订单。
- 支付成功重复回调只扣库存一次。
- 失败尝试后可在预留期内重试且不重复预留；明确取消和超时只释放一次预留。
- 过期任务与成功回调并发时最终状态唯一且库存守恒。
- 收银台和日志不出现敏感支付字段。

## 18. 文件边界

```text
vcampus-common/.../shop/{command,query,view,enum}
vcampus-client/.../shop/{buyer,seller,admin,service}
vcampus-server/.../shop/{handler,service,repository,domain,recommendation,payment,validation}
vcampus-server/src/test/.../shop
```

模块消费 `AuthorizationPort` 和用户身份，不修改用户角色。模拟支付实现位于 `shop.payment`，通过接口隔离，不得嵌入订单服务。

## 19. 下游实现任务

1. 店主申请、审核、店铺状态和权限边界。
2. 商品/SKU、价格库存和店铺所有权。
3. 行为记录、推荐评分和热门新品降级。
4. 持久化购物车与并发数量更新。
5. 跨店结算、订单快照、库存预留和价格确认。
6. 模拟支付、幂等回调、超时释放和恢复任务。
7. 买家订单、店主履约和管理员治理。
8. 十六个 Swing 页面、并发测试和完整演示脚本。

每个任务只修改商城包、商城表和已批准的公共 DTO；公共协议基础结构及其他模块 Repository 禁止修改。
