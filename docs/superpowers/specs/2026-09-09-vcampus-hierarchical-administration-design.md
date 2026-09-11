# vCampus 层次化管理权限设计

## 1. 目标与范围

vCampus 采用“基础角色 + 管理范围 + 实时范围校验”模型，取代现有 `ADMIN` 对所有业务模块拥有全部权限的模型。

本设计覆盖用户模块的角色、权限、会话和治理关系，以及学籍模块的学院范围授权。课程、图书馆和商城暂不增加下级管理层：其模块管理员可以直接管理本模块事务。本设计不修改 Foundation 的消息、Socket、事务、锁和请求去重接口。

## 2. 角色模型

`tblUser.roleCode` 保持单一基础角色，角色为：

| 角色 | 责任 | 明确不允许 |
| --- | --- | --- |
| `SUPER_ADMIN` | 维护模块管理员分配；查看平台治理审计 | 直接处理任何模块的具体业务事务 |
| `MODULE_ADMIN` | 管理自己获分配的模块；在学籍模块中仅管理学院管理员 | 在学籍模块中查询、创建或修改具体学生学籍 |
| `STUDENT_COLLEGE_ADMIN` | 处理唯一负责学院内学生的具体学籍事务 | 管理其他学院学生；管理学院管理员关系 |
| `STUDENT`、`TEACHER` | 保持既有学生、教师的业务权限 | 获得治理权限 |

旧 `ADMIN` 是弃用角色，不再用于授权判断。已有默认 `ADMIN` 账号迁移为唯一的 `SUPER_ADMIN`；模块管理员和学院管理员均以数据库种子数据预设。

## 3. 管理范围数据

新增以下表：

### 3.1 `tblManagedModule`

字段：`moduleCode`、`moduleName`、`isActive`、`rowVersion`。

- `moduleCode` 为主键，初始记录固定为 `STUDENT`、`COURSE`、`LIBRARY`、`SHOP`、`USER`。
- 只有有效模块可以分配模块管理员。
- 每个启用模块必须始终至少有一名有效模块管理员。

### 3.2 `tblModuleAdministrator`

字段：`moduleCode`、`userId`、`isActive`、`rowVersion`、`createdAt`、`updatedAt`。

- 主键为 `(moduleCode, userId)`。
- `moduleCode` 外键关联 `tblManagedModule`。
- 一名 `MODULE_ADMIN` 可以负责多个模块；一个模块可以有多名有效模块管理员。
- 每个启用模块必须始终至少有一名有效模块管理员。

### 3.3 `tblStudentCollegeAdministrator`

字段：`departmentId`、`userId`、`isActive`、`rowVersion`、`createdAt`、`updatedAt`。

- 主键为 `(departmentId, userId)`。
- `userId` 上有唯一索引，因此一名 `STUDENT_COLLEGE_ADMIN` 只能负责一个学院。
- 一个学院可以有多名有效学院管理员。
- 每个启用 `tblDepartment` 记录必须始终至少有一名有效学院管理员。

学生的当前学院不在用户或授权表冗余存储。服务端经由学生的当前班级、专业和 `tblDepartment` 实时得到 `departmentId`。

## 4. 权限与授权规则

静态角色权限从 `tblRolePermission` 读取；模块和学院范围不是会话内可信快照。模块业务授权必须同时满足基础角色和有效模块分配，不能仅凭 `MODULE_ADMIN` 角色取得。

- `SUPER_ADMIN` 仅有 `PLATFORM_MODULE_ADMIN_READ`、`PLATFORM_MODULE_ADMIN_WRITE` 和 `PLATFORM_GOVERNANCE_AUDIT_READ`。
- `MODULE_ADMIN` 对 `USER`、`COURSE`、`LIBRARY`、`SHOP` 等已分配模块可直接获得相应模块的业务权限；对 `STUDENT` 模块只获得 `STUDENT_COLLEGE_ADMIN_READ`、`STUDENT_COLLEGE_ADMIN_WRITE`，不获得 `STUDENT_READ` 或 `STUDENT_WRITE`。
- `STUDENT_COLLEGE_ADMIN` 获得 `STUDENT_READ`、`STUDENT_WRITE` 等学籍业务权限，但每次涉及目标学生的管理命令都必须再次校验目标学生当前 `departmentId` 与管理员的有效学院绑定一致。
- 学生转学院后，原学院管理员立即不能操作该学生；新学院管理员立即可以操作。客户端提交的学院标识不参与授权决定。

角色、模块管理员关系或学院管理员关系变更后，撤销被影响管理员的会话。学籍范围校验仍逐请求访问当前数据，以防止旧会话或学生转学院造成越权。

## 5. 治理命令

治理命令是独立于 `USER_*` 自助命令的公共协议。所有写命令使用既有请求去重、乐观锁和安全审计模式。

| 命令 | 调用者 | 行为 |
| --- | --- | --- |
| `GOVERNANCE_MODULE_ADMIN_SEARCH` | `SUPER_ADMIN` | 查询模块管理员分配 |
| `GOVERNANCE_MODULE_ADMIN_ASSIGN` | `SUPER_ADMIN` | 将预设 `MODULE_ADMIN` 分配给模块 |
| `GOVERNANCE_MODULE_ADMIN_REMOVE` | `SUPER_ADMIN` | 移除模块分配；禁止移除最后一名有效管理员 |
| `GOVERNANCE_MODULE_ADMIN_SWAP` | `SUPER_ADMIN` | 原子互换两个模块管理员的模块分配 |
| `STUDENT_COLLEGE_ADMIN_SEARCH` | 学籍 `MODULE_ADMIN` | 查询学院管理员分配 |
| `STUDENT_COLLEGE_ADMIN_ASSIGN` | 学籍 `MODULE_ADMIN` | 将预设学院管理员分配到学院 |
| `STUDENT_COLLEGE_ADMIN_TRANSFER` | 学籍 `MODULE_ADMIN` | 原子调岗学院管理员；不得让原学院无有效管理员 |
| `STUDENT_COLLEGE_ADMIN_DEACTIVATE` | 学籍 `MODULE_ADMIN` | 停用学院管理员资格；不得让学院无有效管理员 |

这些命令不得创建用户账号、修改学生学籍，或将用户提升为治理角色。管理员账号由数据库预设；具体账号生命周期继续由用户模块的既有受控流程处理。

## 6. 并发、错误与审计

- 分配、移除、调岗、互换和停用操作在一个数据库事务中执行，并按模块、学院和用户资源获取既有资源锁。
- 违反“至少一名有效管理员”规则返回稳定的业务错误码；不依赖客户端事先检查。
- 乐观锁版本不一致返回既有并发冲突错误码。
- 每个成功、拒绝和失败的治理写操作记录脱敏安全审计，包含 actor、target、动作、结果和时间；不写密码、token 或异常堆栈。
- 审计或权限变化不得泄露用户不存在、内部 SQL 或访问路径。

## 7. 学籍模块适配

学籍模块新增内部学院范围授权服务。现有所有“管理员处理目标学生”的 Handler 或 Service 在读取或修改目标学生前调用它。

- `SUPER_ADMIN` 和学籍 `MODULE_ADMIN` 不因身份而绕过学生范围授权。
- `STUDENT_COLLEGE_ADMIN` 仅能操作所属学院的当前学生。
- 学生本人、教师的既有本人查询和教师可见范围规则保持；它们不被学院管理员模型扩大。
- 学院、专业、班级目录维护属于具体学籍事务，按学院管理员范围约束；学籍模块管理员只管理学院管理员关系。

## 8. 迁移与种子数据

1. 增加 `SUPER_ADMIN`、`MODULE_ADMIN`、`STUDENT_COLLEGE_ADMIN` 角色和权限种子。
2. 将既有 `ADMIN` 账号及角色迁移为唯一 `SUPER_ADMIN`，并删除旧 `ADMIN` 的业务授权。
3. 种子数据创建至少一名模块管理员并覆盖每个启用模块。
4. 种子数据创建至少一名学院管理员并覆盖每个启用学院。
5. 启动时或迁移校验中发现启用模块、启用学院缺少有效管理员时，拒绝继续运行并给出安全的配置错误提示。

## 9. 验收测试

- 超管可以治理模块管理员，但不能处理学籍业务。
- 学籍模块管理员可以维护学院管理员关系，但不能查询或修改具体学生。
- 一个学院可有多名学院管理员；一个学院管理员不能有两个学院绑定。
- 不可移除或停用某模块、某学院最后一名有效管理员。
- 学院管理员可操作本学院学生，跨学院操作被拒绝。
- 学生转学院后，原学院管理员立即被拒绝，新学院管理员可以操作。
- 角色或分配变化后旧会话失效；重新登录后导航和权限与新身份一致。
- 并发转交、停用、互换不会产生无管理员的启用模块或学院。
- 原有学生、教师、自助账户和其他模块回归测试继续通过。
