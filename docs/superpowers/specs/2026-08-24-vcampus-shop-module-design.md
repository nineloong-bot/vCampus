# 虚拟校园多商户商城模块设计

## 1. 目标与范围

本模块实现类似主流电商的校园多商户商城：商城首页、推荐、分类、搜索、店铺、商品 SKU、购物车、跨店订单、店主申请、商家履约和微信/支付宝/银行卡模拟支付。模块不接入真实金融平台，不保存支付账号或银行卡信息，不实现退款。

## 2. 身份与权限

- 学生、教师：买家能力；可浏览、加购、下单、支付和确认收货。
- 已批准店主：在原身份上增加店主能力；只能管理自己的店铺、商品和订单。
- 管理员：审核开店申请、停用店铺/商品、查询平台订单及支付日志。
- 一个用户最多拥有一家有效店铺。申请人必须是有效学生或教师账户。

## 3. 店主状态

`DRAFT → PENDING → APPROVED/REJECTED`；驳回后修改可再次提交；批准后可被 `SUSPENDED`，管理员可恢复。驳回和停用必须填写原因。审核通过和创建店铺在同一事务完成。

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

```text
tblSellerApplication(applicationId PK, applicantUserId FK, shopName,
                     description, category, contact, applicationStatus,
                     reviewReason NULL, reviewerUserId NULL,
                     submittedAt, reviewedAt NULL, rowVersion)
tblShop(shopId PK, ownerUserId UNIQUE FK, shopName, description,
        category, contact, shopStatus, rowVersion, createdAt, updatedAt)
tblProduct(productId PK, shopId FK, productName, category, description,
           productStatus, salesCount, rowVersion, createdAt, updatedAt)
tblProductSku(skuId PK, productId FK, skuName, unitPrice DECIMAL(12,2),
              stockQuantity INTEGER, reservedQuantity INTEGER,
              isActive, rowVersion)
tblProductBehavior(behaviorId PK, userId FK, productId FK, category,
                   behaviorType, createdAt)
tblCart(cartId PK, userId UNIQUE FK, updatedAt)
tblCartItem(cartItemId PK, cartId FK, skuId FK, quantity,
            rowVersion, createdAt, updatedAt)
tblOrderGroup(orderGroupId PK, buyerUserId FK, totalAmount DECIMAL(12,2),
              groupStatus, createdAt, rowVersion)
tblOrder(orderId PK, orderGroupId FK, shopId FK,
         orderNumber UNIQUE, orderAmount DECIMAL(12,2), orderStatus,
         createdAt, paidAt NULL, shippedAt NULL, completedAt NULL,
         rowVersion)
tblOrderItem(orderItemId PK, orderId FK, skuId, productNameSnapshot,
             skuNameSnapshot, shopNameSnapshot, unitPrice, quantity,
             lineAmount)
tblPayment(paymentId PK, orderGroupId UNIQUE FK, paymentNumber UNIQUE,
           successfulChannel NULL, amount DECIMAL(12,2), paymentStatus,
           completedAt NULL, rowVersion)
tblPaymentAttempt(attemptId PK, paymentId FK, channel,
                  attemptStatus, createdAt, completedAt NULL)
tblInventoryReservation(reservationId PK, paymentId FK, skuId FK,
                        quantity, reservationStatus, expiresAt,
                        releasedAt NULL)
```

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
