# Task 8 报告：Demo 已支付订单、端到端与最终验证

## 状态

Task 8 已按严格 TDD 完成实现与验证。Demo 初始化器现在写入两个独立买家会话所需的固定订单夹具；Socket 端到端测试在新购买发生前验证本人两笔 PAID 的倒序、完整明细、PENDING 排除和双向用户隔离，随后继续完成原有加购、结算、成功支付与 requestId 重放 exactly-once 验证。

## 固定夹具表

所有下表订单 ID、订单号、时间、数量和支付渠道均为固定值。金额来自 Task 5 固定目录中的 canonical SKU；`unitPrice × quantity = lineAmount = orderAmount = group total = payment amount`。

| 买家 | groupId | orderId / orderNumber | itemId | productId / skuId | 数量 | 单价 / 行与总金额 | createdAt / paidAt（UTC） | paymentId / paymentNumber / attemptId | 状态 / 渠道 |
| --- | --- | --- | --- | --- | ---: | ---: | --- | --- | --- |
| `demo-buyer` | `demo-group-buyer-paid-new` | `demo-order-buyer-paid-new` / `DEMO-B-PAID-002` | `demo-item-buyer-paid-new` | `demo-daily-001` / `demo-daily-001-sku-1` | 3 | ¥6.82 / ¥20.46 | `2026-08-29T09:00:00Z` / `2026-08-29T09:05:00Z` | `demo-payment-buyer-paid-new` / `DEMO-PAY-B-002` / `demo-attempt-buyer-paid-new` | PAID / SUCCEEDED / WECHAT |
| `demo-buyer` | `demo-group-buyer-paid-old` | `demo-order-buyer-paid-old` / `DEMO-B-PAID-001` | `demo-item-buyer-paid-old` | `demo-stationery-002` / `demo-stationery-002-sku-1` | 2 | ¥3.35 / ¥6.70 | `2026-08-25T08:00:00Z` / `2026-08-25T08:05:00Z` | `demo-payment-buyer-paid-old` / `DEMO-PAY-B-001` / `demo-attempt-buyer-paid-old` | PAID / SUCCEEDED / ALIPAY |
| `demo-other-buyer` | `demo-group-other-paid` | `demo-order-other-paid` / `DEMO-O-PAID-001` | `demo-item-other-paid` | `demo-books-001` / `demo-books-001-sku-1` | 1 | ¥32.70 / ¥32.70 | `2026-08-27T10:00:00Z` / `2026-08-27T10:05:00Z` | `demo-payment-other-paid` / `DEMO-PAY-O-001` / `demo-attempt-other-paid` | PAID / SUCCEEDED / BANK_CARD |
| `demo-buyer` | `demo-group-buyer-pending` | `demo-order-buyer-pending` / `DEMO-B-PENDING-001` | `demo-item-buyer-pending` | `demo-medicine-001` / `demo-medicine-001-sku-1` | 1 | ¥7.61 / ¥7.61 | `2026-08-30T07:00:00Z` / `NULL` | `demo-payment-buyer-pending` / `DEMO-PAY-B-PENDING-001` / 无 | PENDING_PAYMENT / PENDING / 无成功渠道 |

新增登录用户为 `demo-other-buyer` / `DEMO_OTHER_BUYER`，使用与其他本地 Demo 账号相同的固定测试密码散列。三笔 PAID 各有成功 Payment 与 SUCCEEDED attempt；PENDING 有金额一致的 PENDING Payment、无 `paidAt` 和成功 attempt。

## TDD：RED

1. 先只修改 `ShopAuthDemoDatabaseTest`，新增真实 Access 数据库断言，覆盖另一用户、2 + 1 + 1 订单状态分布、固定 paidAt 排序、四笔金额、canonical 商品/SKU 快照与金额等式、三笔成功 Payment/attempt，以及 PENDING 的空 paidAt。
2. 先只修改 `ShopAuthEndToEndTest`，在原购买动作前通过真实 Socket 登录 `DEMO_BUYER`，期望精确收到 `demo-order-buyer-paid-new`、`demo-order-buyer-paid-old`；随后用第二条 `ClientConnection` 登录 `DEMO_OTHER_BUYER`，验证两会话互不可见。
3. 简报原样命令 `mvn -pl vcampus-server -am -Dtest=ShopAuthDemoDatabaseTest test` 首先在 reactor 的 `vcampus-common` 阶段因没有同名测试而停止：`No tests matching pattern "ShopAuthDemoDatabaseTest" were executed`。这没有执行目标测试。
4. 使用当前多模块仓库的等效入口 `mvn -pl vcampus-server -am -Dtest=ShopAuthDemoDatabaseTest '-Dsurefire.failIfNoSpecifiedTests=false' test` 后取得真实 RED：5 项运行，1 项失败；新增用例期望 `demo-other-buyer` 数量 1，实际 0。
5. `mvn -pl vcampus-client -am -Dtest=ShopAuthEndToEndTest '-Dsurefire.failIfNoSpecifiedTests=false' test` 取得真实 Socket RED：4 项运行，1 项失败；`SHOP_GET_PAID_ORDERS` 返回成功，但本人实际订单为 `[]`，缺少期望的两笔固定 PAID。

PowerShell 中必须用单引号保护 `-Dsurefire.failIfNoSpecifiedTests=false`，否则 `$surefire` 会被当作 PowerShell 变量展开，Maven 会收到错误生命周期参数。

## TDD：GREEN

- `ShopAuthDemoDatabase.initialize` 在固定目录写入完成后调用 `seedOrders`；夹具从 `ShopDemoCatalog.products()` 获取 canonical 商品和 SKU，金额只从固定 SKU 单价与固定数量计算。
- 插入顺序为 order group、order、order item、payment、PAID 时的 successful attempt；PENDING 不写 paidAt 或成功 attempt。
- `mvn -pl vcampus-server -am -Dtest=ShopAuthDemoDatabaseTest '-Dsurefire.failIfNoSpecifiedTests=false' test`：5 项，0 failures，0 errors，BUILD SUCCESS。
- `mvn -pl vcampus-client -am -Dtest=ShopAuthEndToEndTest '-Dsurefire.failIfNoSpecifiedTests=false' test`：4 项，0 failures，0 errors，BUILD SUCCESS。业务日志显示 `demo-buyer` 与 `demo-other-buyer` 分别成功执行 `SHOP_GET_PAID_ORDERS`，随后原购买流程以 ¥5.74 成功结算、支付并重放。

## 人工指南

两份指南均已更新，明确覆盖：

- 原侧栏唯一“校园商城”入口和商城内部公共工具栏；
- 文具/图书/生活用品/药品四分类及数据库级 10 / 30 / 55 / 5 分配；
- 同一关键词输入框对商品名、店铺名、分类、描述、SKU 名五种匹配；
- 首次响应前后筛选入口显隐、筛选展开/收起和返回恢复；
- 查询页码/滚动状态返回、20 条结果滚动到底；
- 从购物车返回商品详情且购物车数量保持；
- `DEMO_BUYER` 两笔 PAID 倒序、PENDING 排除与 `DEMO_OTHER_BUYER` 双向隔离；
- 支付后“继续购物”和“查看已支付订单”两个安全出口，以及均不能返回已完成结算。

## 完整验证

1. `mvn -pl vcampus-common clean verify`
   - Common：5 tests，0 failures，0 errors，0 skipped；BUILD SUCCESS。
2. `mvn -pl vcampus-server -am test`
   - Common：5 tests；Server：161 tests；全部 0 failures，0 errors，0 skipped；BUILD SUCCESS。
3. `mvn -pl vcampus-client -am test`
   - Common：5 tests；Server：161 tests；Client：115 tests；全部 0 failures，0 errors，0 skipped；BUILD SUCCESS。
   - `ShopAuthEndToEndTest` 4/4；日志再次确认两个不同认证用户的订单查询、随后 checkout/payment 与相同 requestId 重放成功。
4. `pwsh -File vcampus-distribution/scripts/tests/start-shop-auth-demo-scripts.tests.ps1`
   - exit 0；精确输出 `Shop Auth Demo startup script tests passed.`
5. 对 `vcampus-distribution/scripts/start-shop-auth-demo-server.ps1` 调用 `[System.Management.Automation.Language.Parser]::ParseFile(...)`
   - AST errors：0；exit 0。
6. 对 `vcampus-distribution/scripts/start-shop-auth-demo-client.ps1` 调用 `[System.Management.Automation.Language.Parser]::ParseFile(...)`
   - AST errors：0；exit 0。
7. `git diff --check`
   - exit 0；无空白错误。仅输出 Git 的既有 LF/CRLF 工作区转换提示。
8. `git status --short --branch`
   - 提交前仅有计划内 5 个修改文件与未跟踪 `logs/`；`logs/` 未删除、未修改、未暂存。

首次在受限沙箱执行 Maven 时，固定 JaCoCo 插件尚需从 Central 解析，网络被拒绝；获准执行 Maven 后完成依赖解析，以上列出的 RED/GREEN 与完整验证均来自真正执行到目标测试的后续命令。

## Plan Review Checklist

- [x] 每个验收场景至少有一个自动测试或两份指南中的明确人工步骤。
- [x] Task 8 没有新增协议类型；既有新协议数据仍位于 `common/shop`，本次业务实现仅位于 `server/shop/demo`。
- [x] 没有修改共享外壳、用户业务、Socket、Router、事务或公共网络接口。
- [x] 单入口仍由稳定组件名接管，不依赖组件索引、不重复注册共享 page ID；完整 Client 测试覆盖。
- [x] SKU 关键词仍使用 `EXISTS`，没有修改仓储查询；完整 Server 测试覆盖去重与最低价。
- [x] 买家编号仅来自认证会话；E2E 用两个不同 Socket 登录会话证明互相不可见，客户端请求仍为 `EmptyRequest`。
- [x] Demo 商品总数与四店分配继续由真实 Access 数据库测试断言。
- [x] 显式暂存清单排除 `logs/`；没有 push、merge、rebase 或其他远端写操作。

## 自查

- 差异只包含简报列出的五个文件和本报告；没有协议、Router、会话或查询接口改动。
- 四笔订单均只有一个子订单和一个明细，因此 group total、order amount、payment amount 与 line amount 可逐项精确相等；数据库测试对四笔全部验证。
- PAID 数量、成功 Payment 数量、成功 attempt 数量均为 3；PENDING 明确为 group/order/payment 待支付，且 Socket 返回精确排除。
- `demo-buyer` 的两笔 paidAt 不同，E2E 精确断言新单在前，实际验证 `paidAt DESC` 而非只检查集合。
- E2E 在任何新 checkout 前先断言预置历史；隔离测试不是直接调用 repository，而是第二次真实登录与不同连接。
- 新购买继续使用 `demo-stationery-001-sku-1`，固定历史选择其他 SKU，不改变既有库存 10、销量 500 的购买流程基线。
- `logs/` 保持未跟踪且未暂存。

## 疑虑

- 简报给出的两个聚焦 Maven 原样命令在当前 `-am` reactor 下会被上游模块的 Surefire “无匹配测试”阻断；报告保留原样失败证据，并使用不会改变目标测试选择的 `-Dsurefire.failIfNoSpecifiedTests=false` 等效入口完成真实 RED/GREEN。
- 全量测试仍输出项目既有的 Mockito/Byte Buddy 动态 agent 未来 JDK 警告，客户端测试编译仍有既有 unchecked 提示；所有测试均通过，本任务未扩大范围修改测试基础设施。
- 未发现 Task 8 功能性疑虑。
