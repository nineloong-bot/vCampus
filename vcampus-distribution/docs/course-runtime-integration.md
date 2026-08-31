# 用户登录与选课运行时集成

## 生产组合与数据库连接

`ApplicationRuntime` 是用户模块和选课模块唯一的生产组合根。一次服务端启动只创建并共享：

- 一个 `MessageRouter`，同时注册用户命令和选课命令；
- 一个指向同一 Access 数据库的 `ConnectionProvider`；
- 一个应用级 `ResourceLockManager`，由用户服务和 `CourseComposition` 共用；
- 一个用户模块 `SessionRegistry`，登录、登出、用户授权和选课授权都以其中的实时会话为准。

会话空闲过期时间由 `config/server.properties` 的 `session.timeoutMinutes` 控制；生产启动会把该值传入
`ApplicationRuntime`，未显式配置组合参数的兼容入口仍使用 30 分钟默认值。

`ServerMain` 使用 `ApplicationRuntime` 的路由创建生产 `SocketServer`。客户端也只创建一个
`ClientConnection`，`UserClientService` 与 `CourseClientService` 共用该连接；登录成功后写入的会话令牌
因此会自动附在后续选课请求上。客户端页面过滤只是减少误操作，服务端角色检查始终是安全边界。

UCanAccess JDBC URL 必须保持安全默认值，**禁止设置
`immediatelyReleaseResources=true`**。UCanAccess 5.1.3 在一个线程打开连接、另一个线程立即释放驱动资源时，
可能在驱动级全局资源簿记中死锁。生产 URL 形如：

```text
jdbc:ucanaccess:///absolute/path/to/vcampus.accdb
```

## 登录、页面与会话生命周期

连接服务端后先显示 `LoginFrame`。普通登录成功后进入按角色创建的选课主窗口，页面集合精确如下：

| 角色 | 页面键 | 用户可见功能 |
| --- | --- | --- |
| `STUDENT` | `course.offerings`、`course.enrollments`、`course.schedule`、`course.adjustment`、`course.retake` | 教学班查询、我的选课、我的课表、退改补、重修 |
| `TEACHER` | `course.offerings`、`course.schedule` | 教学班查询、教师课表 |
| `ADMIN` | `course.terms`、`course.catalog`、`course.offering-admin`、`course.outcome-import`、`course.adjustment-audit` | 学期管理、课程目录、教学班管理、修读结果导入、退改补审计 |

`mustChangePassword=true` 的首次改密受限会话不创建选课主窗口，只能修改密码或登出；任何选课命令都在服务端以
`AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED` 拒绝。密码修改成功或登出后清除内存令牌并回到登录。

运行中的选课请求若返回以下任一认证失败，客户端统一关闭角色化选课窗口、清除内存令牌并重新打开登录页：

- `AUTH_SESSION_EXPIRED`；
- `AUTH_ACCOUNT_DISABLED`；
- `AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED`。

登出后连接上的令牌已经清空，再发出的选课请求同样稳定返回 `AUTH_SESSION_EXPIRED`，不会被误报为普通角色越权。

## **临时学生标识规则——上线前必须处理**

> **过渡规则：只有能够由 `UserQueryPort.findActiveUser(userId)` 查到、且角色恰为 active
> `STUDENT` 的账户才具备临时选课资格；此时强制 `studentId == userId`，学籍状态临时映射为
> `ACTIVE`。这不表示真实业务数据中的 `userId` 与 `studentId` 天然相同。上线或迁入任何真实选课数据前，
> 必须校验两套 ID，并完成映射或数据迁移。**

当前组合 seam 是：

```java
CourseStudentGateway students = TemporaryUserStudentGateway.create(users);
```

正式学籍域提供 `StudentQueryPort` 后，只替换组合代码，不允许选课领域直接访问用户或学籍仓储。当前
`CourseRuntimeAdapters.students` 的真实签名接收四个函数：资格查询、资格对象到 `studentId` 的映射、
资格对象到状态字符串的映射、活动学生存在性查询。按正式端口组合为：

```java
StudentQueryPort studentQueries = /* 正式学籍域查询端口 */;
CourseStudentGateway students = CourseRuntimeAdapters.students(
        studentQueries::getEnrollmentEligibility,
        StudentEligibility::studentId,
        eligibility -> eligibility.status().name(),
        studentQueries::existsActiveStudent);
```

也就是用基于真实学生域 `StudentQueryPort` 的 `CourseRuntimeAdapters.students(...)` 替换
`TemporaryUserStudentGateway.create(users)`。只有同时满足以下条件才可删除临时 adapter：正式学籍模块已经合并并在
`ApplicationRuntime` 组合；已有用户/学生 ID 已校验并迁移；活动学籍查询覆盖现有数据；真实 socket 集成测试改用彼此独立的
`userId`/`studentId` 并通过。导入修读结果的活动学生校验也必须同时切换，不能留下半套临时规则。

## 运行、测试与打包验证

所有 Maven 命令显式使用 JDK 21：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

# 真实登录→选课 socket 边界（需要允许绑定本机临时端口）
mvn -pl vcampus-client -am \
  -Dtest=LoginCourseSocketIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

# 从干净 target 重建、测试并重建两端发行 JAR
mvn clean verify

# 从发行目录运行
vcampus-distribution/scripts/start-server.sh
vcampus-distribution/scripts/start-client.sh
```

打包后验证当前 HEAD 的代码、schema 和文档均进入发行目录：

```bash
jar tf vcampus-distribution/lib/vCampusServer.jar | rg 'server/(user|course|security|session)/'
jar tf vcampus-distribution/lib/vCampusClient.jar | rg 'client/(user|course)/'
test -f vcampus-distribution/database/schema/010_user.sql
test -f vcampus-distribution/database/schema/030_course.sql
test -f vcampus-distribution/docs/course-runtime-integration.md
rg -n 'userId.*studentId|TemporaryUserStudentGateway|StudentQueryPort' \
  docs/course-runtime-integration.md vcampus-distribution/docs/course-runtime-integration.md
```
