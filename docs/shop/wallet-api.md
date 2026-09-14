# 独立虚拟余额模块对接说明

状态：第一批后端实现。账户模块消费查询；商城订单集成在第二批完成。金额为校园虚拟货币，所有返回金额均为整数分。

## Socket协议

使用现有 `Message` / `ResponseBody`，`type=REQUEST`，登录 `sessionToken` 必填。身份只取服务端会话；接口不接受要操作的用户ID，初始密码待修改、失效及非正常账号会被拒绝。

| command | body（common.wallet包） | 成功data |
| --- | --- | --- |
| WALLET_GET_BALANCE | EmptyRequest.INSTANCE | WalletBalance(balanceCents,pendingCents,version) |
| WALLET_RECHARGE | RechargeCommand(BigDecimal amount) | WalletOperationResult(operationId,balanceCents) |
| WALLET_GET_HISTORY | WalletHistoryQuery(page,pageSize) | PageResult＜WalletEntryView＞ |

充值示例：`new RechargeCommand(new BigDecimal("100.00"))`。快捷金额100、200、500；自定义大于0且不超过1000，最多两位小数。不要使用double构造金额。每次新的充值意图生成新UUID作为requestId（不超过100字符），超时/网络中断保留原requestId重试。相同身份、相同requestId、相同金额返回原回执；更换金额会返回幂等冲突。

回执余额是原操作完成时的快照，不是重试时的实时余额；获取最新余额请调用WALLET_GET_BALANCE。没有钱包记录时返回0，纯查询不创建钱包、不产生流水。

流水采用从1开始的分页，每页1至100条；包含类型、时间、正负变动金额、变动后余额、关联订单和操作号。充值的关联订单为空。只返回本人记录，不返回其他用户身份。排序为时间倒序，再按操作号倒序。

## 错误处理

| code | 客户端行为 |
| --- | --- |
| WALLET_INVALID_AMOUNT | 提示金额范围及小数位要求 |
| WALLET_INVALID_REQUEST | 检查请求字段与分页范围 |
| WALLET_INSUFFICIENT_BALANCE | 保持订单待付款，提供充值入口（第二批订单对接） |
| WALLET_IDEMPOTENCY_CONFLICT | 原请求内容不能更换；核对原操作结果 |
| WALLET_RETRY_REQUIRED | 结果未明确时保留原requestId重试，不创建新的充值意图 |
| WALLET_BALANCE_LIMIT | 达到存储金额边界，拒绝整笔操作 |
| AUTH_SESSION_EXPIRED及会话撤销错误 | 重新登录 |
| AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED | 先修改初始密码 |
| AUTH_FORBIDDEN | 账号当前不能使用 |

数据库故障不向客户端返回SQL、路径或异常堆栈。存储上限为DECIMAL(15,0)对应的999999999999999分，这是防溢出的技术边界；更小的业务总额限制尚未设定。

## 可信后端模块接口

`WalletQueryPort.getBalance(userId)`供账户服务使用；调用者必须先鉴权，不能将任意前端userId直接传入。

`WalletPostingPort.post(TransactionContext, WalletPosting)`仅供可信订单服务使用，不注册客户端扣款、退款或转账命令。WalletPosting含businessKey、orderKey、buyerId、sellerId、amountCents与HOLD/REFUND/SETTLE。

- HOLD：买家可用余额转入订单待结算资金；每个订单只能建立一次。
- REFUND：精确匹配原订单、买卖双方、全额金额，只能从HELD转为REFUNDED。
- SETTLE：精确匹配原订单，从HELD转为SETTLED并计入店主可用余额。
- 相同businessKey同内容重放返回原回执，不重复记账；不同内容拒绝。
- 调用方必须使用非自动提交连接，将订单更新和钱包变化放入同一事务；出现任何异常必须回滚整个事务，不能捕获后继续提交。
- 多店统一支付在同一个外层事务中调用各店HOLD；任意一步失败全部回滚。发货资格、退款审核、确认收货条件由订单服务在同一事务内校验。

正式商城订单服务已接入该资金端口，支付、退款和确认收货结算与订单状态在同一事务中完成。接口与流程见 [订单服务契约](order-api.md)。

## 数据与并发

`051_shop_wallet.sql`新增钱包账户、操作回执、订单待结算、借贷流水四张表。运行时添加缺失表与外键，不重置余额。正式打包时Maven自动把schema复制到分发资源；手工部署需同时更新服务端、公共DTO及该SQL。

充值使用共享资源锁与事务；所有账户变动使用版本条件更新，唯一操作键与订单唯一键防重复。每笔操作的两条流水金额互为相反数。数据库竞争失败时回滚并由上层重试，不用简单的“先查余额再无条件更新”。测试覆盖多个服务/事务管理器实例；不承诺多个独立服务端进程同时写同一Access文件的部署方式。

## 测试数据

运行：

```powershell
mvn -pl vcampus-server -am '-Dtest=WalletDemoDatasetTest' '-Dwallet.demo.generate=true' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

每次在 `vcampus-server/target/wallet-demo/dataset-*` 创建新目录，包含wallet-demo.accdb、operations.csv、manifest.json。固定生成1000笔操作：500充值、250消费、125退款、125收入；另有2000条平衡分录与250条订单资金记录。测试身份buyer/seller/other用于钱包集成验证，不是可登录的全校演示账号。此库不得替换分发运行库。

## 界面对齐约束

后续整批已接入正式 Swing 余额、充值、流水和购物支付页面，当前运行方式见 [commerce-delivery.md](commerce-delivery.md)。下列“本批”记录保留独立余额后端阶段的验收范围。

本批只实现后端，不改变已确认网页布局。后续Swing接入以 `prototypes/shop-buyer/wallet-ui.js`、`wallet.css`和公共样式为依据：余额/待结算区域、充值快捷按钮、明细列表保持原结构；钱包背景#eaf2e7、收入#2f7250、错误#a13d2f。请求进行中禁用重复提交，余额查询失败展示失败状态，不以0冒充加载结果。

## 本批验证记录（2026-09-14）

- JDK21完整 `mvn test`：1303项，0失败、0错误、21跳过（含按需数据生成及环境相关测试）。
- 钱包专项：精确金额边界、无钱包纯查询、重复请求、余额上限、并发最后余额竞争、退款/结算互斥、事务失败回滚、历史快照、分页与隐私、非自动提交要求、重复初始化及正式路由注册。
- 1000笔数据生成单独执行成功；复制到新路径的Access库重放原充值后，回执、当前余额和操作数保持正确。
- 独立代码审查发现的流水缺少变动后余额问题已修复并复核。
- 本批未修改Demo样式或正式Swing页面，未替换运行数据库和打包JAR；后续页面对接继续使用已确认Demo。

- `mvn javadoc:aggregate` 与 `git diff --check` 通过；数据库初始化工具一处旧JavaDoc参数注释已修正为代码标记，不改变运行行为。
