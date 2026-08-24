# 虚拟校园用户管理模块设计

## 1. 目标与范围

本模块负责注册、登录、登出、密码修改、账户状态、基础角色、会话撤销和安全审计。模块不维护学生学籍、教师档案、店铺资格或业务资源权限；这些能力通过公开查询接口或对应业务模块管理。

## 2. 角色与权限

| 用例 | 未登录 | 学生 | 教师 | 管理员 |
|---|---:|---:|---:|---:|
| 注册、登录 | 是 | 是 | 是 | 是 |
| 查看本人、修改密码、登出 | 否 | 是 | 是 | 是 |
| 查询全部账户 | 否 | 否 | 否 | 是 |
| 调整角色和状态 | 否 | 否 | 否 | 是 |
| 查看安全审计 | 否 | 否 | 否 | 是 |

基础角色为 `STUDENT`、`TEACHER`、`ADMIN`。学生自助注册后状态为 `PENDING`，管理员启用后才能登录。管理员不得禁用当前唯一的有效管理员账户。

## 3. 状态模型

```text
PENDING → ACTIVE → DISABLED
PENDING → CANCELLED
ACTIVE → CANCELLED
DISABLED → ACTIVE
```

连续五次密码错误后设置 `lockedUntil = now + 15 minutes`。成功登录清零失败次数。禁用、注销和安全重置密码时撤销全部会话。

## 4. Swing 页面

- `U-01 LoginFrame`：服务器状态、用户名、密码、登录和注册入口。
- `U-02 RegisterDialog`：用户名、密码、确认密码和基础身份。
- `U-03 AccountPanel`：本人账户、角色、状态和最近登录时间。
- `U-04 ChangePasswordDialog`：旧密码、新密码和确认密码。
- `U-05 UserManagementPanel`：管理员分页、筛选、启用和禁用。
- `U-06 UserRoleDialog`：调整学生、教师或管理员角色。
- `U-07 SecurityAuditPanel`：按用户、动作、时间和结果筛选。

登录和密码操作不得在 EDT 中等待网络。密码字段提交后立即清空字符数组。

## 5. 领域类型与 DTO

```java
enum UserRole { STUDENT, TEACHER, ADMIN }
enum AccountStatus { PENDING, ACTIVE, DISABLED, CANCELLED }

record RegisterUserCommand(String username, char[] password,
                           UserRole requestedRole)
        implements Serializable {}
record LoginCommand(String username, char[] password,
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
                   Set<String> permissions)
        implements Serializable {}
```

用户名为 4–32 个字母、数字或下划线，大小写不敏感且数据库唯一。密码为 8–64 个字符，必须同时包含字母和数字。客户端校验用于提示，服务端必须再次校验。

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
    Optional<UserIdentity> findByUsername(String username);
    boolean hasRole(String userId, UserRole role);
}

public interface AuthorizationPort {
    UserIdentity requireSession(String sessionToken);
    void requirePermission(String sessionToken, String permissionCode);
}
```

## 7. 消息合同

| 命令 | 请求 | 响应 | 权限 | 幂等 |
|---|---|---|---|---|
| `USER_REGISTER` | `RegisterUserCommand` | `UserView` | 公开 | 是 |
| `USER_LOGIN` | `LoginCommand` | `LoginResult` | 公开 | 是 |
| `USER_LOGOUT` | `EmptyRequest` | `EmptyResponse` | 已登录 | 是 |
| `USER_GET_CURRENT` | `EmptyRequest` | `UserView` | 已登录 | 否 |
| `USER_CHANGE_PASSWORD` | `ChangePasswordCommand` | `EmptyResponse` | 已登录 | 是 |
| `USER_SEARCH` | `UserSearchQuery` | `PageResult<UserSummary>` | `USER_READ_ALL` | 否 |
| `USER_UPDATE_ROLE` | `UpdateUserRoleCommand` | `UserView` | `USER_ROLE_WRITE` | 是 |
| `USER_CHANGE_STATUS` | `ChangeUserStatusCommand` | `UserView` | `USER_STATUS_WRITE` | 是 |

## 8. 数据库

```text
tblUser
- userId CHAR(36) PK
- username VARCHAR(32) NOT NULL UNIQUE
- passwordHash VARCHAR(256) NOT NULL
- passwordSalt VARCHAR(128) NOT NULL
- passwordIterations INTEGER NOT NULL
- roleCode VARCHAR(16) NOT NULL FK tblRole
- accountStatus VARCHAR(16) NOT NULL
- failedLoginCount INTEGER NOT NULL DEFAULT 0
- lockedUntil DATETIME NULL
- lastLoginAt DATETIME NULL
- rowVersion INTEGER NOT NULL DEFAULT 0
- createdAt DATETIME NOT NULL
- updatedAt DATETIME NOT NULL

tblRole(roleCode PK, roleName)
tblPermission(permissionCode PK, permissionName)
tblRolePermission(roleCode, permissionCode, PK(roleCode, permissionCode))
tblAuditLog(auditId PK, userId NULL, actionCode, targetType,
            targetId NULL, resultCode, clientAddress, createdAt)
```

`username` 以规范化小写值建立唯一索引。PBKDF2 使用每账户随机盐，迭代次数保存在账户记录中，便于以后升级参数。

## 9. 事务与并发

- 注册锁键：`USER_NAME:<normalizedUsername>`；唯一索引作为第二道保护。
- 登录失败计数锁键：`USER:<userId>`，事务中重新读取并更新。
- 状态和角色修改使用 `rowVersion`，旧版本返回并发冲突。
- 会话保存在并发映射中；撤销用户全部会话时锁定 `USER:<userId>`。
- 修改密码在同一事务中验证旧密码、写入新哈希并记录审计；提交后撤销其他会话。

## 10. 错误码

`AUTH_INVALID_CREDENTIALS`、`AUTH_ACCOUNT_PENDING`、`AUTH_ACCOUNT_DISABLED`、`AUTH_ACCOUNT_LOCKED`、`AUTH_SESSION_EXPIRED`、`AUTH_PASSWORD_POLICY_VIOLATION`、`USER_USERNAME_EXISTS`、`USER_LAST_ADMIN_PROTECTED`、`USER_ROLE_CONFLICT`。

登录失败统一返回凭据无效，避免泄露用户名是否存在；账户待启用、禁用和锁定仅在密码验证成功后返回明确状态。

## 11. 日志与审计

注册、登录成功/失败、登出、密码修改、角色修改、启用、禁用和注销均写审计。不得记录密码、哈希、盐、完整会话令牌或完整 DTO。

## 12. 测试与验收

- 20 个相同用户名并发注册只能成功一个。
- 连续五次失败后锁定，15 分钟后恢复；成功登录清零失败数。
- 密码修改后旧密码失效，其他会话被撤销。
- 禁用用户后已有会话不能调用任何受保护命令。
- 学生不能查询账户列表或修改角色。
- 两个管理员用同一旧版本修改账户时只有一个成功。
- 审计日志不包含密码、盐和完整令牌。

## 13. 文件边界

```text
vcampus-common/.../user/{command,query,view,UserRole,AccountStatus}
vcampus-client/.../user/{ui,service}
vcampus-server/.../user/{handler,service,repository,domain,validation}
vcampus-server/.../session
vcampus-server/.../security
vcampus-server/src/test/.../user
```

本模块不得修改学籍、选课、图书馆和商城 Repository。对外只发布 `UserQueryPort` 和 `AuthorizationPort`。

## 14. 下游实现任务

1. 建立用户表、角色权限种子数据和 Repository 集成测试。
2. 以测试驱动实现密码策略、PBKDF2、注册和并发唯一性。
3. 实现会话、登录锁定、注销和账户状态撤销。
4. 实现管理员查询、角色/状态修改和审计。
5. 实现八条消息 Handler 与权限测试。
6. 实现七个 Swing 页面和异步客户端服务。
7. 运行单元、Access 集成、Socket 端到端和并发测试。
