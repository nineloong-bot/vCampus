# 虚拟校园用户管理模块设计

## 1. 目标与范围

本模块负责登录、登出、密码修改、账户状态、基础角色、会话撤销和安全审计，并为学籍模块提供学生账户内部创建接口。学生使用一卡通号作为登录标识，学生账户只能由管理员执行新生录取时创建，不提供学生自助注册。

本模块不维护学生学籍、教师档案、店铺资格或业务资源权限，也不生成一卡通号或学号。编号生成和新生录取事务由学籍模块负责。

## 2. 角色与权限

| 用例 | 未登录 | 学生 | 教师 | 管理员 |
|---|---:|---:|---:|---:|
| 登录 | 是 | 是 | 是 | 是 |
| 教师账户申请 | 是 | 否 | 是 | 否 |
| 学生自助注册 | 否 | 否 | 否 | 否 |
| 查看本人、修改密码、登出 | 否 | 是 | 是 | 是 |
| 查询全部账户 | 否 | 否 | 否 | 是 |
| 调整角色和状态 | 否 | 否 | 否 | 是 |
| 查看安全审计 | 否 | 否 | 否 | 是 |

基础角色为 `STUDENT`、`TEACHER`、`ADMIN`。公开注册只允许申请 `TEACHER`，初始状态为 `PENDING`；请求 `STUDENT` 必须返回 `AUTH_STUDENT_SELF_REGISTRATION_DISABLED`，请求 `ADMIN` 必须返回 `COMMON_FORBIDDEN`。学生账户由学籍录取事务直接创建为 `ACTIVE`，并设置 `mustChangePassword=TRUE`。初始管理员由种子数据创建，后续管理员由已有管理员调整角色。管理员不得禁用当前唯一的有效管理员账户。

## 3. 状态与首次登录模型

```text
PENDING → ACTIVE → DISABLED
PENDING → CANCELLED
ACTIVE → CANCELLED
DISABLED → ACTIVE
```

连续五次密码错误后设置 `lockedUntil = now + 15 minutes`。成功验证密码后清零失败次数。禁用、注销和安全重置密码时撤销全部会话。

新生账户初始密码固定为 `12345678`，只以 PBKDF2 哈希形式保存。学生首次使用初始密码登录时，服务端返回受限会话和 `mustChangePassword=true`。该会话仅能调用：

- `USER_GET_CURRENT`
- `USER_CHANGE_PASSWORD`
- `USER_LOGOUT`

调用其他命令统一返回 `AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED`。改密成功后将 `mustChangePassword` 更新为 `FALSE`，撤销该受限会话并要求重新登录；不得把受限会话升级为普通会话。

## 4. Swing 页面

- `U-01 LoginFrame`：服务器状态、登录标识、密码和登录入口；学生在登录标识栏输入一卡通号。
- `U-02 RegisterDialog`：仅支持教师账户申请，不显示学生或管理员角色选项。
- `U-03 InitialPasswordChangeDialog`：首次登录专用，只允许修改初始密码或登出，成功后返回登录页。
- `U-04 AccountPanel`：本人账户、登录标识、角色、状态和最近登录时间。
- `U-05 ChangePasswordDialog`：旧密码、新密码和确认密码。
- `U-06 UserManagementPanel`：管理员分页、筛选、启用和禁用。
- `U-07 UserRoleDialog`：调整教师或管理员角色；不得把普通账户直接改成学生账户。
- `U-08 SecurityAuditPanel`：按用户、动作、时间和结果筛选。

登录和密码操作不得在 EDT 中等待网络。密码字段提交后立即清空字符数组。`LoginResult.mustChangePassword=true` 时不得创建或显示业务 `MainFrame`。

## 5. 领域类型与 DTO

```java
enum UserRole { STUDENT, TEACHER, ADMIN }
enum AccountStatus { PENDING, ACTIVE, DISABLED, CANCELLED }

record RegisterUserCommand(String loginId, char[] password,
                           UserRole requestedRole)
        implements Serializable {}
record LoginCommand(String loginId, char[] password,
                    String clientInstanceId)
        implements Serializable {}
record ChangePasswordCommand(char[] oldPassword, char[] newPassword)
        implements Serializable {}
record UpdateUserRoleCommand(String userId, UserRole newRole,
                             long expectedVersion)
        implements Serializable {}
record ChangeUserStatusCommand(String userId, AccountStatus newStatus,
                               String reason, long expectedVersion)
        implements Serializable {}
record UserSearchQuery(String keyword, UserRole role,
                       AccountStatus status, int page, int pageSize)
        implements Serializable {}
record LoginResult(String sessionToken, UserView user,
                   Set<String> permissions, boolean mustChangePassword)
        implements Serializable {}
record ProvisionedUserAccount(String userId, String loginId,
                              UserRole role, AccountStatus status)
        implements Serializable {}
```

`loginId` 为 4–32 个大写字母、数字或下划线，写库前统一转为大写并建立唯一索引。学生 `loginId` 必须是学籍模块生成的一卡通号并匹配 `^2[123]3[0-9]{6}$`。密码为 8–64 个字符，必须同时包含字母和数字；固定初始密码仅允许内部学生账户创建接口使用。客户端校验用于提示，服务端必须再次校验。

## 6. 服务接口

```java
public interface UserService {
    UserView register(RegisterUserCommand command);
    LoginResult login(LoginCommand command, ClientContext context);
    void logout(String sessionToken);
    UserView getCurrentUser(String sessionToken);
    void changePassword(String sessionToken, ChangePasswordCommand command);
    PageResult<UserSummary> searchUsers(UserSearchQuery query);
    UserView updateRole(UpdateUserRoleCommand command);
    UserView changeStatus(ChangeUserStatusCommand command);
}

public interface UserQueryPort {
    Optional<UserIdentity> findActiveUser(String userId);
    Optional<UserIdentity> findByUserId(String userId);
    Optional<UserIdentity> findByLoginId(String loginId);
    boolean hasRole(String userId, UserRole role);
}

public interface UserAccountProvisioningPort {
    ProvisionedUserAccount createStudentAccount(
            TransactionContext transaction,
            String campusCardNumber,
            char[] initialPassword);
}

public interface AuthorizationPort {
    UserIdentity requireSession(String sessionToken);
    void requirePermission(String sessionToken, String permissionCode);
}
```

`UserAccountProvisioningPort` 是服务端内部 Port，不注册为 Socket 命令，不接受客户端调用。调用方必须传入已经开启的 `TransactionContext`；实现不得自行提交、回滚或开启嵌套事务。它固定创建 `roleCode=STUDENT`、`accountStatus=ACTIVE`、`mustChangePassword=TRUE` 的账户，并写安全审计。

## 7. 消息合同

| 命令 | 请求 | 响应 | 权限 | 幂等 |
|---|---|---|---|---|
| `USER_REGISTER` | `RegisterUserCommand` | `UserView` | 公开；仅允许教师申请 | 是 |
| `USER_LOGIN` | `LoginCommand` | `LoginResult` | 公开 | 是 |
| `USER_LOGOUT` | `EmptyRequest` | `EmptyResponse` | 已登录/受限会话 | 是 |
| `USER_GET_CURRENT` | `EmptyRequest` | `UserView` | 已登录/受限会话 | 否 |
| `USER_CHANGE_PASSWORD` | `ChangePasswordCommand` | `EmptyResponse` | 已登录/受限会话 | 是 |
| `USER_SEARCH` | `UserSearchQuery` | `PageResult<UserSummary>` | `USER_READ_ALL` | 否 |
| `USER_UPDATE_ROLE` | `UpdateUserRoleCommand` | `UserView` | `USER_ROLE_WRITE` | 是 |
| `USER_CHANGE_STATUS` | `ChangeUserStatusCommand` | `UserView` | `USER_STATUS_WRITE` | 是 |

## 8. 数据库

### 8.1 `tblUser` 用户账户表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `userId` | 用户内部编号 | `VARCHAR(36)` | 主键；UUID |
| `loginId` | 登录标识 | `VARCHAR(32)` | 非空；转为大写后唯一；学生保存一卡通号 |
| `passwordHash` | 密码哈希 | `VARCHAR(256)` | 非空；PBKDF2 结果，不保存明文 |
| `passwordSalt` | 密码随机盐 | `VARCHAR(128)` | 非空；每个账户独立生成 |
| `passwordIterations` | 密码哈希迭代次数 | `LONG` | 非空；大于零 |
| `roleCode` | 基础角色代码 | `VARCHAR(16)` | 非空；外键关联 `tblRole.roleCode` |
| `accountStatus` | 账户状态 | `VARCHAR(16)` | 非空；`PENDING/ACTIVE/DISABLED/CANCELLED` |
| `mustChangePassword` | 是否必须修改初始密码 | `YESNO` | 非空；学生账户创建时为 `TRUE`，改密后为 `FALSE` |
| `failedLoginCount` | 连续登录失败次数 | `LONG` | 非空；默认 `0` |
| `lockedUntil` | 临时锁定截止时间 | `DATETIME` | 可空；未锁定时为空 |
| `lastLoginAt` | 最近成功验证密码时间 | `DATETIME` | 可空；从未登录时为空 |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0`，每次更新加一 |
| `createdAt` | 创建时间 | `DATETIME` | 非空 |
| `updatedAt` | 最后更新时间 | `DATETIME` | 非空 |

索引：`uk_tblUser_loginId` 唯一索引；`idx_tblUser_roleCode`；`idx_tblUser_accountStatus`。

### 8.2 `tblRole` 角色表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `roleCode` | 角色代码 | `VARCHAR(16)` | 主键；`STUDENT/TEACHER/ADMIN` |
| `roleName` | 角色中文名称 | `VARCHAR(32)` | 非空 |

### 8.3 `tblPermission` 权限表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `permissionCode` | 权限代码 | `VARCHAR(64)` | 主键；例如 `USER_READ_ALL` |
| `permissionName` | 权限中文名称 | `VARCHAR(64)` | 非空 |

### 8.4 `tblRolePermission` 角色权限关联表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `roleCode` | 角色代码 | `VARCHAR(16)` | 联合主键；外键关联 `tblRole.roleCode` |
| `permissionCode` | 权限代码 | `VARCHAR(64)` | 联合主键；外键关联 `tblPermission.permissionCode` |

### 8.5 `tblAuditLog` 安全审计日志表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `auditId` | 审计记录编号 | `VARCHAR(36)` | 主键；UUID |
| `userId` | 操作用户编号 | `VARCHAR(36)` | 可空；外键关联 `tblUser.userId`，登录失败时可为空 |
| `actionCode` | 操作代码 | `VARCHAR(64)` | 非空；例如 `USER_LOGIN`、`STUDENT_ACCOUNT_PROVISION` |
| `targetType` | 被操作对象类型 | `VARCHAR(32)` | 非空；例如 `USER` |
| `targetId` | 被操作对象编号 | `VARCHAR(36)` | 可空 |
| `resultCode` | 操作结果代码 | `VARCHAR(64)` | 非空；成功或错误码 |
| `clientAddress` | 客户端网络地址 | `VARCHAR(64)` | 可空 |
| `createdAt` | 操作发生时间 | `DATETIME` | 非空 |

`loginId` 以规范化大写值建立唯一索引。PBKDF2 使用每账户随机盐，迭代次数保存在账户记录中。任何日志和审计记录不得保存初始密码或提交的密码字符数组。

## 9. 事务与并发

- 公开注册锁键：`LOGIN_ID:<normalizedLoginId>`；唯一索引作为第二道保护。
- 学生账户创建由学籍模块先取得编号序列锁，再通过同一事务调用内部 Port；用户模块仅补充取得 `LOGIN_ID:<campusCardNumber>` 锁，不改变既定锁顺序。
- 登录失败计数锁键：`USER:<userId>`，事务中重新读取并更新。
- 状态和角色修改使用 `rowVersion`，旧版本返回并发冲突。
- 会话保存在并发映射中；撤销用户全部会话时锁定 `USER:<userId>`。
- 修改密码在同一事务中验证旧密码、写入新哈希、清除 `mustChangePassword` 并记录审计；提交后撤销该用户的其他会话。首次改密还必须撤销当前受限会话。

## 10. 错误码

`AUTH_INVALID_CREDENTIALS`、`AUTH_ACCOUNT_PENDING`、`AUTH_ACCOUNT_DISABLED`、`AUTH_ACCOUNT_LOCKED`、`AUTH_SESSION_EXPIRED`、`AUTH_PASSWORD_POLICY_VIOLATION`、`AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED`、`AUTH_STUDENT_SELF_REGISTRATION_DISABLED`、`USER_LOGIN_ID_EXISTS`、`USER_LAST_ADMIN_PROTECTED`、`USER_ROLE_CONFLICT`。

登录失败统一返回凭据无效，避免泄露登录标识是否存在；账户待启用、禁用和锁定仅在密码验证成功后返回明确状态。受限会话调用禁止命令时返回 `AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED`。

## 11. 日志与审计

注册、学生账户内部创建、登录成功/失败、登出、首次改密、普通密码修改、角色修改、启用、禁用和注销均写审计。不得记录密码、哈希、盐、完整会话令牌或完整 DTO。

## 12. 测试与验收

- 20 个相同 `loginId` 并发创建只能成功一个。
- 公开注册请求 `STUDENT` 必须返回 `AUTH_STUDENT_SELF_REGISTRATION_DISABLED`，且不得写入账户。
- 内部 Port 使用一卡通 `213242478` 时创建 `ACTIVE/STUDENT` 账户，`loginId=213242478` 且 `mustChangePassword=TRUE`。
- 初始密码登录只能访问本人信息、改密和登出；访问任一业务命令返回 `AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED`。
- 首次改密后 `mustChangePassword=FALSE`，旧初始密码和受限会话均失效，使用新密码重新登录后获得正常权限。
- 连续五次失败后锁定，15 分钟后恢复；成功验证密码清零失败数。
- 密码修改后旧密码失效，其他会话被撤销。
- 禁用用户后已有会话不能调用任何受保护命令。
- 学生不能查询账户列表或修改角色。
- 两个管理员用同一旧版本修改账户时只有一个成功。
- 审计日志不包含初始密码、普通密码、盐和完整令牌。

## 13. 文件边界

```text
vcampus-common/.../user/{command,query,view,UserRole,AccountStatus}
vcampus-client/.../user/{ui,service}
vcampus-server/.../user/{handler,service,repository,domain,validation}
vcampus-server/.../session
vcampus-server/.../security
vcampus-server/src/test/.../user
```

本模块不得修改学籍、选课、图书馆和商城 Repository。对外发布 `UserQueryPort`、`AuthorizationPort` 和仅供服务端调用的 `UserAccountProvisioningPort`。用户模块不得生成一卡通号、学号或直接创建学生档案。

## 14. 下游实现任务

1. 建立 `loginId`、`mustChangePassword` 字段、角色权限种子数据和 Repository 集成测试。
2. 以测试驱动实现密码策略、PBKDF2、非学生注册和 `loginId` 并发唯一性。
3. 实现 `UserAccountProvisioningPort`，验证共享事务边界和整体回滚。
4. 实现普通会话、受限会话、首次强制改密、登录锁定、注销和账户状态撤销。
5. 实现管理员查询、角色/状态修改和审计。
6. 实现八条消息 Handler 与权限测试；内部账户创建不得暴露 Socket Handler。
7. 实现八个 Swing 页面和异步客户端服务。
8. 运行单元、Access 集成、Socket 端到端和并发测试。
