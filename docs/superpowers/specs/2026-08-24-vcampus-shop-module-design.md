# 虚拟校园多商户商城模块设计

## 1. 目标与范围

本模块实现类似主流电商的校园多商户商城：商城首页、分类、搜索、价格筛选、商品排序、店铺、商品 SKU、购物车、跨店订单、店主申请、商家履约和微信/支付宝/银行卡模拟支付。模块不接入真实金融平台，不保存支付账号或银行卡信息，不实现退款。

## 2. 身份与权限

- 学生、教师：买家能力；可浏览、加购、下单、支付和确认收货。
- 已批准店主：在原身份上增加店主能力；只能管理自己的店铺、商品和订单。
- 管理员：审核开店申请、停用店铺/商品、查询平台订单及支付日志。
- 一个用户最多拥有一家有效店铺。申请人必须是有效学生或教师账户。

## 3. 店主申请与店铺状态

店主申请状态为 `DRAFT → PENDING → APPROVED` 或 `DRAFT → PENDING → REJECTED → DRAFT`。新申请先保存为草稿，再由申请人显式提交；待审核申请不可编辑。驳回后，申请人修改申请时回到 `DRAFT`，随后可以再次提交。同一用户只能有一个未结束申请；已有已批准店铺的用户不能再次申请。

店铺状态独立于申请状态，按照 `ACTIVE ↔ SUSPENDED` 变化。管理员停用店铺时必须填写原因，恢复店铺不改变已批准申请。审核通过、将申请标记为 `APPROVED` 和创建 `ACTIVE` 店铺必须在同一事务完成。

## 4. 商品与店铺规则

- 商品属于一个店铺，至少有一个 SKU 才能上架。
- SKU 定义规格名称、价格、库存和状态。
- 店铺停用时全部商品从买家查询结果中隐藏。
- 商品下架不影响历史订单快照。
- 订单明细保存下单时名称、规格、价格和店铺名称，不能依赖商品当前值展示历史。
- 店主更新库存不能使 `stockQuantity` 小于 `reservedQuantity`。
- 商品详情必须包含所属店铺的摘要信息和“进入店铺”入口；买家店铺主页只展示正常营业店铺及其可售商品。

## 5. 商品筛选与排序

商城首页、商品搜索和买家店铺主页均支持可选的最低价格与最高价格筛选。价格使用 `BigDecimal` 表示，筛选边界包含最低价和最高价；任一边界未填写时表示该方向不设限制。价格不得为负，且最低价格不得高于最高价格。

一个商品可能包含多个 SKU。商品列表以“最低可售 SKU 价格”作为展示、筛选和排序价格，并显示为“¥xx 起”。可售 SKU 必须处于启用状态，且 `stockQuantity - reservedQuantity > 0`。如果商品没有可售 SKU，则不进入列表。

商品支持以下排序方式：

- `SALES_DESC`：按照 `tblProduct.salesCount` 从高到低排序，作为默认排序。
- `PRICE_DESC`：按照最低可售 SKU 价格从高到低排序。

主要排序值相同时，按照商品创建时间从新到旧排序。所有查询必须过滤停用店铺和未上架商品。列表价格只用于展示和筛选，结算仍须在事务内重新读取实时价格和库存。

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
Payment: PENDING → SUCCEEDED | CANCELLED | EXPIRED
PaymentAttempt: STARTED → SUCCEEDED | FAILED | CANCELLED
Order: PENDING_PAYMENT → PAID → PREPARING → SHIPPED → COMPLETED
Order: PENDING_PAYMENT → CANCELLED
Product: DRAFT → ACTIVE → INACTIVE
Shop: ACTIVE ↔ SUSPENDED
```

买家只能取消待支付订单组；支付成功后由店主推进备货和发货，买家确认收货。买家确认某个子订单收货后更新该子订单；当订单组内所有子订单均为 `COMPLETED` 时，在同一事务中将订单组更新为 `COMPLETED`。超出课程范围的退款请求以客服提示结束，不改变支付状态。

## 9. Swing 页面

买家：`M-01 ShopHomePanel`、`M-02 ProductSearchPanel`、`M-03 ProductDetailPanel`、`M-04 BuyerShopPanel`、`M-05 CartPanel`、`M-06 CheckoutPanel`、`M-07 SimulatedCashierDialog`、`M-08 MyOrdersPanel`、`M-09 OrderDetailPanel`。

店主：`M-10 SellerApplicationPanel`、`M-11 SellerDashboardPanel`、`M-12 ShopProfilePanel`、`M-13 ProductManagementPanel`、`M-14 SellerOrderPanel`。

管理员：`M-15 SellerReviewPanel`、`M-16 ShopGovernancePanel`、`M-17 PlatformOrderPanel`。

`ProductDetailPanel` 显示店铺名称和“进入店铺”按钮。点击后由统一页面导航器打开 `BuyerShopPanel`；该页面显示店铺名称、简介、经营分类、联系方式以及分页商品列表，点击店内商品可再次打开 `ProductDetailPanel`。

客户端使用 `MainFrame` 中的统一导航器和 `CardLayout` 切换页面。页面只提交 `productId` 或 `shopId` 导航请求，不直接创建或持有目标页面。返回历史只保存页面类型与编号参数，最多保留最近 20 条；目标路由与当前路由完全相同时忽略重复跳转。由用户点击产生的“商品详情 → 店铺主页 → 商品详情”属于正常导航，不构成自动循环。

导航路由至少包含商城首页、商品搜索、商品详情和买家店铺主页。用户从首页或搜索结果进入商品详情后，必须能够按访问顺序返回原页面；历史项只保存查询条件或实体编号，不保存 Swing 页面实例。

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
enum ProductSortMode { SALES_DESC, PRICE_DESC }
enum SellerReviewDecision { APPROVE, REJECT }

record HomeProductQuery(BigDecimal minPrice, BigDecimal maxPrice,
                        ProductSortMode sortMode,
                        int pageNumber, int pageSize)
        implements Serializable {}
record ProductSearchQuery(String keyword, String category,
                          BigDecimal minPrice, BigDecimal maxPrice,
                          ProductSortMode sortMode,
                          int pageNumber, int pageSize)
        implements Serializable {}
record ShopProductQuery(String shopId, String keyword, String category,
                        BigDecimal minPrice, BigDecimal maxPrice,
                        ProductSortMode sortMode,
                        int pageNumber, int pageSize)
        implements Serializable {}

record ShopSummary(String shopId, String shopName)
        implements Serializable {}
record ShopDetail(String shopId, String shopName, String description,
                  String category, String contact, ShopStatus shopStatus)
        implements Serializable {}
record PlatformOrderQuery(String orderNumber, String buyerUserId,
                          String shopId, OrderStatus orderStatus,
                          int pageNumber, int pageSize)
        implements Serializable {}
record PaymentSearchQuery(String paymentNumber, PaymentStatus paymentStatus,
                          PaymentChannel channel,
                          int pageNumber, int pageSize)
        implements Serializable {}

record SellerApplicationQuery(String applicantUserId,
                              SellerApplicationStatus status,
                              int pageNumber, int pageSize)
        implements Serializable {}
record SellerApplicationView(String applicationId, String applicantUserId,
                             String shopName, String description,
                             String category, String contact,
                             SellerApplicationStatus status,
                             String reviewReason, String reviewerUserId,
                             Instant submittedAt, Instant reviewedAt,
                             long rowVersion)
        implements Serializable {}
record ShopView(String shopId, String ownerUserId, String shopName,
                String description, String category, String contact,
                ShopStatus status, String suspensionReason,
                String suspendedByUserId, Instant suspendedAt,
                long rowVersion)
        implements Serializable {}

record ProductSummary(String productId, String shopId, String shopName,
                      String productName, String category,
                      BigDecimal minimumPrice, long salesCount,
                      Instant createdAt)
        implements Serializable {}
record ProductSkuView(String skuId, String skuName, BigDecimal unitPrice,
                      long availableQuantity, boolean active,
                      long rowVersion)
        implements Serializable {}
record ProductDetail(String productId, String productName, String category,
                     String description, ProductStatus status,
                     long salesCount, ShopSummary shop,
                     List<ProductSkuView> skus, Instant createdAt)
        implements Serializable {}
record ProductView(String productId, String productName, String category,
                   String description, ProductStatus status,
                   long salesCount, long rowVersion,
                   List<ProductSkuView> skus)
        implements Serializable {}

record CartItemView(String cartItemId, String productId, String productName,
                    String skuId, String skuName, String shopId,
                    String shopName, BigDecimal displayedUnitPrice,
                    int quantity, long rowVersion)
        implements Serializable {}
record CartView(String cartId, List<CartItemView> items,
                BigDecimal displayedTotal)
        implements Serializable {}
record OrderItemView(String orderItemId, String productName,
                     String skuName, String shopName,
                     BigDecimal unitPrice, int quantity,
                     BigDecimal lineAmount)
        implements Serializable {}
record OrderSummary(String orderId, String orderGroupId, String orderNumber,
                    String shopId, String shopName, BigDecimal orderAmount,
                    OrderStatus status, Instant createdAt)
        implements Serializable {}
record OrderSearchQuery(OrderStatus status, int pageNumber, int pageSize)
        implements Serializable {}
record SellerOrderQuery(OrderStatus status, String orderNumber,
                        int pageNumber, int pageSize)
        implements Serializable {}
record SellerOrderView(String orderId, String orderGroupId,
                       String orderNumber, String buyerUserId,
                       BigDecimal orderAmount, OrderStatus status,
                       List<OrderItemView> items, Instant createdAt,
                       long rowVersion)
        implements Serializable {}
record PlatformOrderView(String orderId, String orderGroupId,
                         String orderNumber, String buyerUserId,
                         String shopId, String shopName,
                         BigDecimal orderAmount, OrderStatus status,
                         Instant createdAt)
        implements Serializable {}
record PaymentView(String paymentId, String orderGroupId,
                   String paymentNumber, BigDecimal amount,
                   PaymentStatus status, PaymentChannel successfulChannel,
                   Instant expiresAt, Instant completedAt,
                   long rowVersion)
        implements Serializable {}
record CheckoutResult(String orderGroupId, String paymentId,
                      String paymentNumber, BigDecimal totalAmount,
                      Instant expiresAt, List<OrderSummary> orders)
        implements Serializable {}

record UpdateShopCommand(String shopName, String description,
                         String category, String contact,
                         long expectedVersion)
        implements Serializable {}
record CreateSkuCommand(String skuName, BigDecimal unitPrice,
                        long stockQuantity, boolean active)
        implements Serializable {}
record UpsertSkuCommand(String skuId, String skuName, BigDecimal unitPrice,
                        long stockQuantity, boolean active,
                        long expectedVersion)
        implements Serializable {}
record CreateProductCommand(String productName, String category,
                            String description,
                            List<CreateSkuCommand> skus)
        implements Serializable {}
record UpdateProductCommand(String productId, String productName,
                            String category, String description,
                            List<UpsertSkuCommand> skus,
                            long expectedVersion)
        implements Serializable {}
record ChangeProductStatusCommand(String productId, ProductStatus targetStatus,
                                  long expectedVersion)
        implements Serializable {}
record UpdateOrderStatusCommand(String orderId, OrderStatus expectedStatus,
                                OrderStatus targetStatus,
                                long expectedVersion)
        implements Serializable {}

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
record SaveSellerDraftCommand(String applicationId, String shopName,
                              String description, String category,
                              String contact, long expectedVersion)
        implements Serializable {}
record SubmitSellerApplicationCommand(String applicationId,
                                      long expectedVersion)
        implements Serializable {}
record ReviewSellerApplicationCommand(String applicationId,
                                      SellerReviewDecision decision,
                                      String reason, long expectedVersion)
        implements Serializable {}
record SuspendShopCommand(String shopId, String reason,
                          long expectedVersion)
        implements Serializable {}
record ResumeShopCommand(String shopId, long expectedVersion)
        implements Serializable {}
```

首次保存草稿时 `applicationId` 为空且 `expectedVersion=0`；后续修改必须携带服务器返回的申请编号和版本号。提交操作只接受当前用户自己的 `DRAFT` 申请。审核决定为 `REJECT` 时 `reason` 非空；停用店铺时 `reason` 非空。

分页返回统一使用公共基础模块定义的 `PageResult<T>`，商城模块不得重复定义同名分页类型。所有 DTO 必须实现 `Serializable`，金额使用 `BigDecimal`，时间使用 `Instant`。

`ProductDetail` 只包含一个 `ShopSummary`，用于展示店铺名称和发起跳转；`ShopDetail` 不内嵌商品列表，店内商品通过 `ShopProductQuery` 分页获取。`ProductSummary` 也不得内嵌完整 `ShopDetail`，从而避免 Socket DTO 循环引用和重复传输。

## 11. 服务接口

```java
public interface ShopService {
    PageResult<ProductSummary> getHomeProducts(HomeProductQuery query);
    PageResult<ProductSummary> searchProducts(ProductSearchQuery query);
    ProductDetail getProduct(String productId);
    ShopDetail getShop(String shopId);
    PageResult<ProductSummary> getShopProducts(ShopProductQuery query);
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
    SellerApplicationView saveDraft(String sessionToken,
                                    SaveSellerDraftCommand command);
    SellerApplicationView submitApplication(
            String sessionToken, SubmitSellerApplicationCommand command);
    SellerApplicationView getMyApplication(String sessionToken);
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
    void resumeShop(ResumeShopCommand command);
    PageResult<PlatformOrderView> searchPlatformOrders(
            PlatformOrderQuery query);
    PageResult<PaymentView> searchPayments(PaymentSearchQuery query);
}
```

## 12. 消息命令

买家命令：`SHOP_HOME`、`SHOP_SEARCH_PRODUCTS`、`SHOP_GET_PRODUCT`、`SHOP_GET_SHOP`、`SHOP_GET_SHOP_PRODUCTS`、`SHOP_GET_CART`、`SHOP_CART_ADD`、`SHOP_CART_UPDATE`、`SHOP_CART_REMOVE`、`SHOP_CHECKOUT`、`SHOP_SIMULATE_PAYMENT`、`SHOP_GET_MY_ORDERS`、`SHOP_CANCEL_ORDER_GROUP`、`SHOP_CONFIRM_RECEIPT`。`SHOP_HOME`、`SHOP_SEARCH_PRODUCTS` 和 `SHOP_GET_SHOP_PRODUCTS` 均接受价格区间与排序参数；排序参数为空时按 `SALES_DESC` 处理。

店主命令：`SHOP_SAVE_SELLER_DRAFT`、`SHOP_SUBMIT_SELLER_APPLICATION`、`SHOP_GET_SELLER_APPLICATION`、`SHOP_UPDATE_PROFILE`、`SHOP_CREATE_PRODUCT`、`SHOP_UPDATE_PRODUCT`、`SHOP_CHANGE_PRODUCT_STATUS`、`SHOP_GET_SELLER_ORDERS`、`SHOP_UPDATE_ORDER_STATUS`。

管理员命令：`SHOP_SEARCH_SELLER_APPLICATIONS`、`SHOP_REVIEW_SELLER_APPLICATION`、`SHOP_SUSPEND`、`SHOP_RESUME`、`SHOP_SEARCH_PLATFORM_ORDERS`、`SHOP_SEARCH_PAYMENTS`。全部写命令必须携带幂等键；同一用户以同一幂等键重复发送相同命令时返回首次结果，不得重复改变数据。

## 13. 数据库

买家店铺主页复用现有 `tblShop.shopId`、`tblProduct.shopId` 和 `tblProductSku.productId` 关系，不新增数据库表。

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
| `suspensionReason` | 最近一次停用原因 | `VARCHAR(256)` | 可空；`SUSPENDED` 时非空 |
| `suspendedByUserId` | 最近一次执行停用的管理员编号 | `VARCHAR(36)` | 可空；外键关联 `tblUser.userId` |
| `suspendedAt` | 最近一次停用时间 | `DATETIME` | 可空；`SUSPENDED` 时非空 |
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
| `salesCount` | 累计成交件数 | `LONG` | 非空；默认 `0`；支付成功时按成交数量累加，幂等回调不得重复累加 |
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

### 13.5 `tblCart` 购物车表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `cartId` | 购物车内部编号 | `VARCHAR(36)` | 主键；UUID |
| `userId` | 所属用户编号 | `VARCHAR(36)` | 非空；唯一；外键关联 `tblUser.userId` |
| `updatedAt` | 最后更新时间 | `DATETIME` | 非空 |

### 13.6 `tblCartItem` 购物车商品项表

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

### 13.7 `tblOrderGroup` 订单组表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `orderGroupId` | 订单组内部编号 | `VARCHAR(36)` | 主键；UUID |
| `buyerUserId` | 买家用户编号 | `VARCHAR(36)` | 非空；外键关联 `tblUser.userId` |
| `totalAmount` | 订单组总金额 | `DECIMAL(12,2)` | 非空；等于所有子订单金额之和 |
| `groupStatus` | 订单组状态 | `VARCHAR(24)` | 非空；`PENDING_PAYMENT/PAID/COMPLETED/CANCELLED` |
| `createdAt` | 创建时间 | `DATETIME` | 非空 |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

索引：`idx_tblOrderGroup_buyer_time`、`idx_tblOrderGroup_status`。

### 13.8 `tblOrder` 店铺子订单表

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

### 13.9 `tblOrderItem` 订单明细表

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

### 13.10 `tblPayment` 聚合支付单表

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

### 13.11 `tblPaymentAttempt` 模拟支付尝试表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `attemptId` | 支付尝试编号 | `VARCHAR(36)` | 主键；UUID |
| `paymentId` | 所属支付单编号 | `VARCHAR(36)` | 非空；外键关联 `tblPayment.paymentId` |
| `channel` | 本次模拟支付渠道 | `VARCHAR(16)` | 非空；`WECHAT/ALIPAY/BANK_CARD` |
| `attemptStatus` | 本次尝试结果 | `VARCHAR(16)` | 非空；`STARTED/SUCCEEDED/FAILED/CANCELLED` |
| `createdAt` | 尝试开始时间 | `DATETIME` | 非空 |
| `completedAt` | 尝试结束时间 | `DATETIME` | 可空；尚未结束时为空 |

索引：`idx_tblPaymentAttempt_payment_time`。

### 13.12 `tblInventoryReservation` 库存预留表

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

- 商品列表查询从启用且有可售库存的 SKU 中计算每个商品的最低价格，再应用闭区间价格筛选和排序。价格参数非法时直接返回错误，不执行查询。
- 店铺详情与店内商品查询必须校验店铺存在且状态为 `ACTIVE`；店内商品查询额外固定 `shopId` 条件，不能返回其他店铺商品。
- 购物车更新锁定 `CART:<userId>`，相同 SKU 合并且数量大于零。
- 结算按 `skuId` 排序锁定所有 SKU，再锁定购物车；事务内重新读取价格、状态和可售库存。
- 价格不一致且 `acceptLatestPrice=false` 时返回最新价格，不创建订单。
- 支付锁定 `PAYMENT:<paymentId>`、`ORDER_GROUP:<id>` 和排序后的 SKU；状态不是 `PENDING` 时返回已有终态。失败尝试只追加 `tblPaymentAttempt`，不释放预留；成功时扣减库存并按订单明细数量累加商品 `salesCount`，明确取消或超时才释放预留。支付状态校验和销量累加必须位于同一事务，确保重复成功回调不会重复累计销量。
- 店主更新商品锁定 `PRODUCT:<id>`/`SKU:<id>` 并校验店铺所有权。
- 审核锁定 `SELLER_APPLICATION:<id>` 和 `USER:<applicantUserId>`，防止重复通过和一人多店。
- 保存草稿、提交申请、审核、停用和恢复均校验 `rowVersion`；恢复只允许将 `SUSPENDED` 店铺变为 `ACTIVE`，并保留最近一次停用审计字段。
- 过期恢复任务与支付回调使用相同支付锁，避免同时释放和扣减库存。

## 15. 错误码

`SHOP_SELLER_APPLICATION_EXISTS`、`SHOP_SELLER_APPLICATION_STATUS_INVALID`、`SHOP_SELLER_NOT_APPROVED`、`SHOP_NOT_FOUND`、`SHOP_NOT_OWNER`、`SHOP_SUSPENDED`、`SHOP_STATUS_INVALID`、`SHOP_PRODUCT_INACTIVE`、`SHOP_SKU_UNAVAILABLE`、`SHOP_PRICE_FILTER_INVALID`、`SHOP_PRICE_CHANGED`、`SHOP_INSUFFICIENT_STOCK`、`SHOP_CART_EMPTY`、`SHOP_ORDER_STATUS_INVALID`、`SHOP_ORDER_NOT_OWNED`、`PAYMENT_ALREADY_COMPLETED`、`PAYMENT_NOT_PENDING`、`PAYMENT_AMOUNT_MISMATCH`。

- 申请重复或申请状态不允许当前操作时分别返回 `SHOP_SELLER_APPLICATION_EXISTS`、`SHOP_SELLER_APPLICATION_STATUS_INVALID`。
- 用户没有已批准且正常营业的店铺、目标资源不存在、不属于当前店主或店铺已停用时，分别返回 `SHOP_SELLER_NOT_APPROVED`、`SHOP_NOT_FOUND`、`SHOP_NOT_OWNER`、`SHOP_SUSPENDED`；不合法的店铺状态转换返回 `SHOP_STATUS_INVALID`。
- 商品已下架、SKU 不可售、价格区间非法、结算价格变化、库存不足或未选择有效购物车项时，分别返回对应的 `SHOP_PRODUCT_INACTIVE`、`SHOP_SKU_UNAVAILABLE`、`SHOP_PRICE_FILTER_INVALID`、`SHOP_PRICE_CHANGED`、`SHOP_INSUFFICIENT_STOCK`、`SHOP_CART_EMPTY`。
- 订单状态不允许操作或订单不属于当前用户时，分别返回 `SHOP_ORDER_STATUS_INVALID`、`SHOP_ORDER_NOT_OWNED`。
- 使用同一用户和同一幂等键重试已经成功的支付命令时直接返回首次成功结果，不返回错误；以新的幂等键操作 `SUCCEEDED` 支付单返回 `PAYMENT_ALREADY_COMPLETED`，操作 `CANCELLED/EXPIRED` 支付单返回 `PAYMENT_NOT_PENDING`。支付单金额与订单组实时总额不一致时返回 `PAYMENT_AMOUNT_MISMATCH`，且不得扣减或释放库存。

## 16. 日志与隐私

记录店主申请审核、商品上下架、结算、支付状态和订单状态变化。不得记录真实支付账号、卡号、验证码或密码；模拟支付也只记录渠道、支付单、金额和结果。

## 17. 测试与验收

- 教师/学生可保存草稿并显式提交；待审核申请不可编辑，驳回后修改回到草稿并可再次提交。
- 管理员通过后才出现店主能力；重复审核不能创建第二家店，一人不能拥有两店。
- 停用必须记录原因、管理员和时间；恢复后店铺重新可见且已批准申请保持 `APPROVED`。
- 店主不能修改其他店铺商品或订单。
- 商品列表过滤停用店铺、下架商品和无可售库存 SKU。
- 商品详情展示 `ShopSummary` 并可进入对应 `BuyerShopPanel`；店铺主页只返回该店铺的可售商品，点击店内商品可进入对应商品详情。
- 店铺不存在时返回 `SHOP_NOT_FOUND`，店铺停用时返回 `SHOP_SUSPENDED`；两种情况都不能展示店铺商品。
- 商品详情与店铺主页反复跳转只能由用户操作触发，不得自动递归创建页面或在 DTO 中互相嵌套完整对象；重复当前路由不新增导航记录，返回历史不超过 20 条。
- 从首页或搜索结果进入商品详情后，返回操作恢复原页面及其查询条件。
- 多 SKU 商品使用最低可售 SKU 价格展示和筛选；价格区间包含上下边界，非法区间返回 `SHOP_PRICE_FILTER_INVALID`。
- 未指定排序方式时按销量从高到低排序；选择价格排序时按最低可售 SKU 价格从高到低排序；主要排序值相同时按创建时间从新到旧排序。
- 相同 SKU 重复加购合并数量，重登后购物车保留。
- 跨店购物车正确生成一个订单组和多个子订单。
- 库存为 5 时并发购买总成交不得超过 5。
- 价格变化时未确认不得创建任何订单。
- 支付成功重复回调只扣库存一次，且商品销量只累计一次。
- 失败尝试后可在预留期内重试且不重复预留；明确取消和超时只释放一次预留。
- 过期任务与成功回调并发时最终状态唯一且库存守恒。
- 最后一个子订单确认收货时，订单组在同一事务中变为 `COMPLETED`。
- 管理员可分页查询平台订单和支付记录，查询操作不得改变业务数据。
- 收银台和日志不出现敏感支付字段。

## 18. 文件边界

```text
vcampus-common/.../shop/{command,query,view,enum}
vcampus-client/.../shop/{buyer,seller,admin,service}
vcampus-server/.../shop/{handler,service,repository,domain,payment,validation}
vcampus-server/src/test/.../shop
```

模块消费 `AuthorizationPort` 和用户身份，不修改用户角色。模拟支付实现位于 `shop.payment`，通过接口隔离，不得嵌入订单服务。

## 19. 下游实现任务

1. 店主申请、审核、店铺状态和权限边界。
2. 商品/SKU、价格库存和店铺所有权。
3. 商品详情进入店铺、买家店铺主页、价格筛选和销量/价格排序。
4. 持久化购物车与并发数量更新。
5. 跨店结算、订单快照、库存预留和价格确认。
6. 模拟支付、幂等回调、超时释放和恢复任务。
7. 买家订单、店主履约和管理员治理。
8. 十七个 Swing 页面、导航测试、并发测试和完整演示脚本。

每个任务只修改商城包、商城表和已批准的公共 DTO；公共协议基础结构及其他模块 Repository 禁止修改。
