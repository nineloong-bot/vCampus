# vCampus Shop 下一阶段实施路线

> **执行约束：** 由主代理在当前隔离 worktree 内使用 `superpowers:executing-plans` 逐项执行；不使用子代理。每项任务遵循红—绿—重构，并在检查点完成本地提交。

**Goal:** 在保持 Shop 模块边界的前提下，交付图片卡片式买家目录、开店审批、卖家经营、管理员治理和完整 Demo。

**Spec:** `docs/superpowers/specs/2026-08-31-vcampus-shop-seller-admin-design.md`

## 顺序与依赖

1. `2026-08-31-shop-buyer-catalog-implementation-plan.md`
   - 先提交现有连接状态修复。
   - 修复支付返回、SKU 数量与搜索交互。
   - 建立商品类型/SKU、HTTPS 封面和可替换卡片网格。
   - 建立管理员禁购和店主自购服务端边界。
2. `2026-08-31-shop-application-implementation-plan.md`
   - 补齐申请材料、店名唯一性和会话级管理员授权。
   - 贯通申请、审核、停用/恢复协议与 UI。
3. `2026-08-31-shop-management-implementation-plan.md`
   - 贯通卖家商品与订单管理。
   - 贯通管理员全店与全商品管理。
4. `2026-08-31-shop-demo-release-implementation-plan.md`
   - 重建五店、100 个商品类型、多 SKU 和图片 URL 数据。
   - 更新四个固定账号、端到端测试、说明和打包回归。

## 全局检查点

- 每次暂存前运行 `git diff --check`，并用 `git diff --cached --name-only` 确认只包含 Shop 范围文件。
- 保留未跟踪的 `logs/`，不暂存、不删除、不清理。
- 不修改 Foundation、User、Socket、Router、事务框架和公共网络接口实现。
- 不执行 push、merge、rebase、删除、回滚或清理。
- 每个阶段结束后运行对应模块测试；最后运行 Common、Server、Client 全量测试及 PowerShell 脚本测试。
