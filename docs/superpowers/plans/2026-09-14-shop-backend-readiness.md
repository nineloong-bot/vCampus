# 商城后端开发准备与分阶段计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的网页交互落实到现有 Java / Access 系统，先交付独立虚拟余额模块，再连接完整订单与管理流程。

**Architecture:** 保留现有 Socket → Handler → Service → Repository 边界。余额是独立模块，账户只查询；商城通过窄接口参与同一数据库事务，协调资金、订单与库存。复用库存条件更新与资源锁，不把原型内存状态搬进服务端。

**Tech Stack:** JDK 21、Maven、Swing、Socket、Microsoft Access、UCanAccess。

**Spec:** [统一项目书](../specs/2026-09-13-shop-admin-demo-requirements.md)。业务规则以该文件为准，本文接口和表名是技术设计建议。

## Global Constraints

- 全部为校园虚拟货币，不涉及真实支付、兑换、提现。
- 充值快捷金额100、200、500，单次大于0且不超过1000，最多两位小数。
- 待付款保留30分钟；只有买家确认收货才完成并结算店主收入。
- Access schema和seed是数据库源；先验证临时数据库，不覆盖正在运行的分发库。
- 新Java文件不超过200行；修改超长文件时拆分职责；公开API补充JavaDoc。
- 真实身份取服务端会话，卖家只获得昵称投影；客户端金额不作为扣款依据。
- 金额采用整数分或BigDecimal精确计算；禁止浮点金额。
- 幂等业务记录与业务写入必须同事务；资源锁按稳定顺序获取。
- 每阶段先补失败测试，再实现并验证；每阶段测试结果在交付记录中据实更新。

## 1. 现有实现与差异

下列路径以仓库根目录为基准。Java服务端包根为 `vcampus-server/src/main/java/edu/seu/vcampus/server/`，公共包根为 `vcampus-common/src/main/java/edu/seu/vcampus/common/`。

| 现有文件/机制 | 可复用部分 | 必须调整 |
| --- | --- | --- |
| `vcampus-database/schema/050_shop.sql` | 店铺、SKU、订单、库存预占 | 目前缺少余额、退款、资质、举报、软删除与限制原因数据；新增迁移并验证历史数据 |
| `shop/service/CheckoutService.java` | 条件更新防库存超占，锁与事务 | 15分钟改30分钟；预占身份关联到订单项，支持部分失效 |
| `shop/payment/SimulatedPaymentService.java` | 支付状态与库存事务 | 旧渠道模拟替换为虚拟余额；先拆分超长类 |
| `shop/payment/ReservationExpiryJob.java` | 超时释放与恢复基础 | 适应按店订单独立付款、30分钟期限及部分失效 |
| `shop/service/ShopService.java` | 查询入口 | 停业店铺可查看但无商品；缺货SKU仍展示；取消买家类目导航限制 |
| `shop/service/BuyerOrderService.java` | 买家订单查询 | 当前只有已付款查询，补六栏与待付款懒校验 |
| `common/shop/OrderStatus.java`（公共包） | 协议枚举 | 原PREPARING等状态与新状态机映射，增加退款表达 |
| `common/shop/SellerOrderView.java`（公共包） | 店主订单投影 | 现含buyerUserId，改为所需昵称展示，不泄漏敏感身份 |
| `persistence/TransactionManager.java` | 回滚与连接管理 | synchronized仅约束同一实例；不能声称跨进程安全 |
| `routing/RequestDeduplicator.java` | 通信重试基础 | 业务执行与响应登记不是同一事务，不能独自承担资金幂等 |

数据库额外冲突：商品同名唯一约束与“同名允许导入”冲突；描述/类目必填与名称草稿冲突；SKU价格允许0与发布价大于0冲突；商品图片字段仍为URL；支付表按orderGroup唯一，预占按payment关联，不能直接支持新的按店支付及失效行释放。旧约束不能直接删除了事，应验证数据、调用方和迁移回滚方案。

## 2. 模块接口与事务约定（拟定）

新增包建议：公共 `wallet`，服务端 `wallet/service`、`wallet/repository`、`wallet/handler`；商城只引用钱包端口。最终消息码需检查现有注册表后分配，禁止与旧码冲突。

- `WalletQueryPort.getBalance(userId)`：返回余额、待结算金额及版本；账户模块只消费查询，不修改钱包表。
- `WalletService.recharge(actor, requestId, amount)`：会话限定本人，校验金额；返回操作号、结果和最新余额。
- `WalletPostingPort.post(transactionContext, operation)`：商城传入同一连接的事务上下文；执行借贷分录，不自行开启第二个事务或提交。
- 业务操作至少包含稳定businessKey、类型、来源订单、金额及参与账户；相同key不同内容拒绝，相同内容重试返回原结果。
- 统一付款：锁定全部目标店铺订单及付款人，服务端重新验证并计算总额；余额不足全部不变；成功时资金、订单、库存全部提交。
- 收货结算：从订单待结算资金转给店主；退款从待结算退给买家。不要在付款和收货时重复计入店主余额。
- 资金不变量：订单待结算余额不得为负；每笔内部转移借贷相等；充值有明确系统来源分录。流水只能追加，不覆盖历史。
- 数据库条件更新与唯一键共同兜底；客户端requestId保留到结果明确。重启后仍可查询相同操作结果。

## 3. 分阶段交付

每阶段单独细化实施计划并交付可测试代码；不要一次性重写商城。以下任务尚未实施，勾选只能依据实际产物和测试。

### 阶段一：独立余额基础（首个开发批次）

**文件职责：** 新建 `vcampus-database/schema/051_shop_wallet.sql`（编号执行前核查）、公共 `wallet/WalletBalance.java` 与 `WalletOperationResult.java`；服务端 `wallet/repository/WalletRepository.java`、`AccessWalletRepository.java`、`wallet/service/WalletService.java`、`WalletPostingService.java`、`wallet/handler/WalletHandlers.java`。在实际bootstrap注册入口接入；读取入口代码后再定位修改位置。

**数据建议：** 钱包账户、不可变流水、业务操作幂等表。用户账户唯一；操作key唯一；金额非负检查；流水关联业务操作和订单。建表同时提供临时库构建验证，不能只提交实体类。

**测试：** 新建服务端测试 `wallet/WalletServiceTest.java`、`WalletConcurrencyTest.java`、`WalletHandlersTest.java`，沿用现有临时Access测试方式。

- [x] 编写金额边界、余额初始值、重复充值、相同key不同金额、跨用户查询拒绝的失败测试。
- [x] 建立Access表、仓储及纯查询接口，账户查询不得产生充值或流水。
- [x] 实现充值与持久化幂等；事务回滚时余额和流水均不变。
- [x] 编写两个并发扣款竞争同一余额、重复请求、重启后重试测试，再实现内部记账端口。
- [x] 连接Socket会话鉴权及错误映射，补不可伪造目标用户的协议测试。
- [x] 运行 `mvn -pl vcampus-server -am -Dtest=WalletServiceTest,WalletConcurrencyTest,WalletHandlersTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认实际执行了这些测试且无失败。
- [x] 提供账户模块对接说明：消息码、DTO、余额单位、加载/失败语义、示例响应；消息码确定后再定稿。

### 阶段二：订单、资金与库存闭环

**修改：** `shop/service/CheckoutService.java`、`BuyerOrderService.java`、`SellerOrderService.java`、`shop/payment/SimulatedPaymentService.java`、`ReservationExpiryJob.java`、订单DTO及schema。拆出付款、退款、收货结算服务，避免增加超长类职责。

**消费：** 阶段一同事务记账端口；**产出：** 下单、按店/统一付款、退款审核、发货、确认收货和超时关闭接口。

- [ ] 先补 `ConcurrentCheckoutTest`、`OrderAmountInvariantTest`、`PaymentExpiryRaceTest`、`ReservationRecoveryTest` 中的新规则失败用例。
- [ ] 将预占关联到订单项，期限改30分钟；制定旧支付记录迁移和旧消息兼容策略。
- [ ] 实现付款与扣余额同事务，加入故障注入验证提交前失败全部回滚。
- [ ] 实现懒校验失效行、重复释放保护、全失效取消，以及订单快照保留。
- [ ] 实现退款申请阻止发货、批准退款/库存恢复、确认收货一次结算。
- [ ] 验证项目书AC-04至AC-09；到期任务重启恢复，付款与超时只允许一种终态。

### 阶段三：商品、图片与Excel

**修改：** `shop/service/ProductService.java`、`CartService.java`、`ShopService.java`、`ProductImageUrl.java`相关调用方，公共商品DTO及schema；新增导入校验/确认服务，使用独立仓储职责。

**消费：** 稳定SKU、订单引用及占用查询；**产出：** 名称草稿、默认规格、预置图片清单、批次预览/确认/错误结果。

- [ ] 补名称草稿、同名允许、零库存展示、图片ID非法、无资质发布失败测试。
- [ ] 明确库存编辑与历史SKU引用边界后，更新草稿和发布校验。
- [ ] 客户端解析Excel传行数据；服务端按商品组验证，确认时重验且幂等，成功均为草稿。
- [ ] 测试跨店修改拒绝、导入组部分错误全部失败、重复确认不重复创建。
- [ ] 实现软删除前检查所有未完成订单；保留订单/SKU/流水并阻断所有恢复路径。
- [ ] 验证AC-01至AC-03、AC-10；同步Swing入口，原型不是正式客户端。

### 阶段四：审核、举报与管理

**修改：** `shop/service/ShopAdminService.java`、`AdminProductService.java`、`SellerApplicationService.java`、`shop/handler/AdminShopHandlers.java`及schema；新增资质、举报、限制、复核、审计职责。

**消费：** 订单取消/释放接口、商品限制判断；**产出：** 两个列表工作区的管理接口和可追溯审计。

- [ ] 先补越权、警告不改状态、紧急下架与停业独立、恢复不可覆盖其他限制测试。
- [ ] 实现文字申请与模拟审核、商品举报、警告及处理结果联动。
- [ ] 实现店铺暂停、清购物车和取消待付款，保留已付款履约；明确批量任务事务边界和失败恢复。
- [ ] 实现开业申请批准、紧急下架整改复核通过仅解锁；权限不可由客户端状态决定。
- [ ] 明确资质类目映射及未付款处理后，实施到期与续期恢复任务。
- [ ] 验证AC-11至AC-14；卖家响应不包含举报人ID或买家敏感资料。

### 阶段五：整体验收与分发

- [ ] 运行 `mvn -pl vcampus-server -am test` 和 `mvn -pl vcampus-client -am test`，检查实际报告。
- [ ] 运行仓库要求的JavaDoc、构建检查和 `git diff --check`。
- [ ] 在临时Access库分别验证新建、历史数据迁移、重启、重复请求和故障回滚。
- [ ] 用Swing客户端按AC-01至AC-14验收；记录版本和结果，再准备分发产物。

## 4. 待收敛项与当前状态

项目书第10节列出昵称来源、资质映射、SKU编辑等局部边界。遇到对应阶段再集中确认，不重复询问已经通过的页面设计。第一批独立余额模块已实现并接入ApplicationRuntime。账户查询、虚拟充值、分页流水及同事务订单资金端口已完成；第二批旧商城订单接入尚未实施。2026-09-14完整Maven回归1303项，0失败、0错误、21跳过；1000笔钱包测试数据另行生成并验证。对接细节见 `../../shop/wallet-api.md`。
