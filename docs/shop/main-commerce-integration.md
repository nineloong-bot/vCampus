# 新版商城接入 main

基线为 `main` 的 `a5fe91d`。工作分支为 `integration/main-commerce-only`，商城功能来源为 `34be9de`，当前商城数据来自2026-09-15保存的本机数据库快照。

## 接入范围

- 新版商城 Swing 页面、预置图片、公共 DTO，以及商品、购物车、订单、库存和店铺监督服务。
- 独立余额模块：查询、虚拟充值、流水、订单扣款、退款和收入结算。
- 共用入口仅接入商城页面、请求去重编号、服务注册及商城定时维护的启动/关闭。
- 账户、登录、学籍、课程、图书和管理员权限沿用 main。图书模块可使用 `WalletFinePort` 对接余额，当前接入不修改 main 的图书页面或处理流程。

## 数据

在 main 数据库副本上迁入30张商城相关表，保留 main 的38张其他业务表。所有账户 ID 和登录身份先校验一致，再迁入依赖记录。订单、库存流水、余额、资金流水和托管记录一起迁移并校验。

详见 [迁移校验与复现命令](main-commerce-data.md) 和 `vcampus-database/tools/main-commerce-manifest.tsv`。该清单描述合并后的数据；`demo/full-test-data` 中已有生成器及账号清单继续描述 main 基线。不要用生成器重建已合并的数据库。

## 余额对接

客户端命令为 `WALLET_GET_BALANCE`、`WALLET_RECHARGE`、`WALLET_GET_HISTORY`，用户身份由服务端会话确定。账户模块可调用 `WalletQueryPort` 查询余额。可信服务通过 `WalletPostingPort` 或 `WalletFinePort` 在自身事务中记账；订单和余额变动同事务提交。

商城命令使用 `SHOP2_*`。虚拟充值快捷金额为100、200、500，单次不超过1000；重试保留请求编号以避免重复充值。客户端和服务端需使用同一次构建的运行包。

## 本地查看

在此工作区运行 `vcampus-distribution/scripts/start-server-with-data.bat`，再运行 `vcampus-distribution/scripts/start-client.bat`。账户及其他模块数据以 main 为准；原有工作区和其数据库继续保留。

合并本分支时应检查源码及 SQL，运行包由合并后的源码重新生成。数据库迁移应在副本上验证，保留原文件备份。

## 验证记录（2026-09-15）

- 公共模块67项、服务端722项（4项跳过）、客户端636项（18项跳过）；最新各测试报告无失败。客户端旧入口断言已随新版接入更新；学籍分页首次超时，未改源码重跑通过。
- 登录会话失效时沿用 main 的登录回调，并发失败只在 EDT 触发一次；回归测试及独立复核通过。
- `mvn -DskipTests package javadoc:aggregate` 构建通过。修正了一处 main 原有 JavaDoc 参数尖括号格式。
- 实际发布服务端在迁移数据库副本上连续启动两次；启动后38张非商城表14,431条记录与 main 完全一致，商城账户引用与资金流水核对通过。
- 本工作区数据库来自校验后的迁移文件；原工作区及其未完成合并均保留。发布目录补齐了与源码一致的初始化 SQL。
