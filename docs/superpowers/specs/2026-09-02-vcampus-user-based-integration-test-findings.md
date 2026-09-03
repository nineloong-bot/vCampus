# vCampus 以 User Management 为基线的多模块集成测试问题记录

**记录日期：** 2026-09-02  
**集成分支：** `integration/user-all-modules`  
**集成基线：** `origin/feat/user-management@e00aa3c`  
**计划合并顺序：** `course-user-management` → `nineloong` → `user-library` → `SHOP`  
**执行规则：** 只合并、构建、测试和记录；发现问题不修改业务代码、测试或数据库内容。

## 环境与来源确认

用户下载到 `E:\summer-school\vCampus\download` 的四份 GitHub 分支快照均不包含 `.git`。逐文件比对确认它们分别与以下本地远端引用完全一致，因此本轮使用对应 Git 引用保留提交历史：

| 模块 | 下载目录 | Git 引用 | 下载快照比对 |
| --- | --- | --- | --- |
| 登录与用户管理 | `user-management/vCampus-feat-user-management` | `origin/feat/user-management@e00aa3c` | 完全一致 |
| 课程 | `course/vCampus-course-user-management` | `origin/course-user-management` | 完全一致 |
| 学籍 | `information/vCampus-nineloong` | `origin/nineloong` | 完全一致 |
| 图书馆 | `library/vCampus-user-library` | `origin/user-library` | 完全一致 |

上一次以 `SHOP` 为基线的 Student + Shop 合并已按用户要求废弃；本地 `SHOP` 已恢复到 `origin/SHOP@4632002`。本轮另建集成测试分支，不修改已发布的 `SHOP` 历史。

## INTEGRATION-BASELINE-001：集成测试所需示例账号数据尚未准备

**阶段：** 尚未合并 Course、Student、Library 或 Shop，仅验证 User Management 基线。  
**命令：** `mvn clean verify`  
**结果：** `BUILD FAILURE`

**测试证据：**

- Common：3 项通过，0 失败，0 错误。
- Server：179 项，1 项失败，0 错误。
- Client：由于 Server 失败而未执行。
- 失败测试：`DemoDistributionAccountsTest.distributionDatabaseContainsTheThreeVerifiedCourseDemoAccounts`。
- 失败断言：发行数据库应包含 3 个已经验证的 Course 演示账号，实际检查结果为 `false`。

**排除项：** 首次未清理构建目录时曾出现旧 Shop 测试类导致的 `NoClassDefFoundError`；执行 `mvn clean verify` 后该现象消失，确认它只是跨分支残留的 `target` 产物，不计为源码缺陷。

**定性修正：** 登录与用户管理模块的开发者事先不知道 Course 集成测试所需的具体示例账号，因此基线数据库没有这 3 个账号属于合理的数据准备缺口，不认定为登录模块功能缺陷。

**影响：**

- 在补齐约定的集成测试数据以前，User Management 基线无法通过这项跨模块发行数据库测试。
- 后续合并测试必须将这项失败标为“合并前已存在”，不能归因于 Course、Student、Library 或 Shop。
- 该失败与 Course 演示账号和随仓库提交的 `vcampus-distribution/data/vCampus.accdb` 内容一致性有关。

**当前处理：** 按用户要求只记录，不修改测试、种子 SQL 或数据库文件，继续后续模块合并验证。

## INTEGRATION-COURSE-001：Course 无法直接合并到 User Management 基线

**操作：** 在 `origin/feat/user-management@e00aa3c` 基线上尝试合并 `origin/course-user-management`。  
**结果：** 合并产生 14 个未解决冲突，无法在不作实现取舍的情况下形成可构建版本。

**冲突文件：**

1. `vcampus-client/src/main/java/edu/seu/vcampus/client/ClientMain.java`
2. `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
3. `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/InitialPasswordChangeDialog.java`
4. `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/LoginFrame.java`
5. `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserUiCoordinator.java`
6. `vcampus-client/src/test/java/edu/seu/vcampus/client/user/InitialPasswordChangeUiTest.java`
7. `vcampus-client/src/test/java/edu/seu/vcampus/client/user/LoginDemoUiTest.java`
8. `vcampus-client/src/test/java/edu/seu/vcampus/client/user/ui/LoginLockoutCountdownUiTest.java`
9. `vcampus-distribution/lib/vCampusClient.jar`（测试发行物，不纳入源码冲突处理）
10. `vcampus-distribution/lib/vCampusServer.jar`（测试发行物，不纳入源码冲突处理）
11. `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
12. `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/AdminUserService.java`
13. `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserService.java`
14. `vcampus-server/src/main/java/edu/seu/vcampus/server/user/service/UserServiceImpl.java`

**影响与判断：**

- 冲突集中在登录界面、主界面装配、服务端启动装配以及用户服务接口与实现，说明 Course 分支包含了另一套用户管理集成改动，不能机械选择一侧。
- 两个发行 JAR 是 Course 组员提供给其他成员测试的构建产物，本次源码集成直接忽略，不列为待解决问题；最终组合版本应从合并后的源码重新构建。
- 按“发现问题只记录、不修改代码”的约束，本轮不解决冲突，并中止合并以恢复 User Management 基线。

## 学籍分支试合并结果

在 User Management 基线执行 `git merge --no-commit --no-ff origin/nineloong`，Git 自动合并成功，没有文本或二进制冲突。该试合并涉及 35 个文件（新增或修改约 2173 行、删除约 664 行），主要位于学籍客户端、公共 DTO、服务端处理器/仓储/服务及其测试，另包含对 `.gitignore` 和 `SocketServer` 的修改。

此结论只表示 Git 层面可合并，不表示四模块组合能够编译或运行。为保持各模块检查相互独立，已中止试合并并恢复基线。

## INTEGRATION-LIBRARY-001：Library 无法直接合并到 User Management 基线

**操作：** 尝试合并 `origin/user-library`。  
**结果：** 产生 9 个未解决冲突：

1. `vcampus-client/src/main/java/edu/seu/vcampus/client/ClientMain.java`
2. `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
3. `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserUiCoordinator.java`
4. `vcampus-database/seed/010_roles_permissions.sql`
5. `vcampus-distribution/data/vCampus.accdb`（二进制冲突）
6. `vcampus-distribution/lib/vCampusClient.jar`（测试发行物，不纳入源码冲突处理）
7. `vcampus-distribution/lib/vCampusServer.jar`（测试发行物，不纳入源码冲突处理）
8. `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
9. `vcampus-server/src/test/java/edu/seu/vcampus/server/user/repository/AccessPermissionRepositoryTest.java`

**影响与判断：** 两个发行 JAR 是 Library 组员提供给其他成员测试的构建产物，本次源码集成直接忽略。仍需处理的内容覆盖客户端/服务端装配、用户界面协调、角色权限种子、权限测试和发行数据库。尤其是 `010_roles_permissions.sql`，表明管理员和图书馆权限对接需要明确合并规则，不能只选择任一分支版本。`vCampus.accdb` 也不能进行内容级自动合并；组合版本需要明确以哪份数据库为基础，并通过 schema/seed 补齐其他模块所需数据。按约束未解决冲突，已中止试合并。

## Shop 分支试合并结果

在 User Management 基线执行 `git merge --no-commit --no-ff origin/SHOP`，Git 自动合并成功，没有文本或二进制冲突。该试合并新增 267 个文件、约 31589 行，内容集中在 Shop 的 common/client/server、数据库 schema、Demo、发行脚本、测试和说明文档。

Shop 当前与 User Management 仅在 Git 文件层面互不冲突；Shop 尚未接入 User Management 的统一 `ClientMain`、`MainFrame`、`ServerMain` 和发行数据库，因此“无冲突”不等于已经完成登录、菜单、权限、数据库和启动入口适配。为保持检查独立，已中止试合并并恢复基线。
