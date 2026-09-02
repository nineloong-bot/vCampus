# 用户登录与选课运行时集成

## 生产组合与数据库连接

`ApplicationRuntime` 是用户模块和选课模块唯一的生产组合根。一次服务端启动只创建并共享：

- 一个 `MessageRouter`，同时注册用户命令和选课命令；
- 一个指向同一 Access 数据库的 `ConnectionProvider`；
- 一个应用级 `ResourceLockManager`，由用户服务和 `CourseComposition` 共用；
- 一个用户模块 `SessionRegistry`，登录、登出、用户授权和选课授权都以其中的实时会话为准。

会话空闲过期时间由 `config/server-with-data.properties` 的 `session.timeoutMinutes` 控制；发行启动会把该值传入
`ApplicationRuntime`，未显式配置组合参数的兼容入口仍使用 30 分钟默认值。

带数据服务端入口使用 `ApplicationRuntime` 的路由创建生产 `SocketServer`。客户端也只创建一个
`ClientConnection`，`UserClientService` 与 `CourseClientService` 共用该连接；登录成功后写入的会话令牌
因此会自动附在后续选课请求上。客户端页面过滤只是减少误操作，服务端角色检查始终是安全边界。

UCanAccess JDBC URL 必须保持安全默认值，**禁止设置
`immediatelyReleaseResources=true`**。UCanAccess 5.1.3 在一个线程打开连接、另一个线程立即释放驱动资源时，
可能在驱动级全局资源簿记中死锁。生产 URL 形如：

```text
jdbc:ucanaccess:///absolute/path/to/vcampus.accdb
```

带数据发行配置把 `database.path`（Access 文件）和 `database.resourceRoot`（`schema/`、`seed/` 所在目录）分开解析。
只有显式设置 `database.createIfMissing=true` 时才会创建缺失的数据库及其父目录；已有数据库永远不会被覆盖。
发行配置使用 `data/course-user-demo.accdb` 和 `database/` 资源根，因此全新解压后可以初始化带演示数据的独立数据库。

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

## 可直接运行的带数据 Demo

需要立即交付团队联调时，先阅读 [选课与用户管理快速交付候选版](course-user-management-quick-delivery.md)。该文档明确记录当前可运行能力、固定账号以及尚未完成的最终验收项。

> **以下账号和固定密码只用于本地演示。不要把 Demo 数据库、账号或密码用于部署；正式上线前必须删除
> `data/course-user-demo.accdb`，并通过正式建号流程初始化账户。**

Demo 使用独立的 `data/course-user-demo.accdb`，调用生产 `ApplicationRuntime`、生产登录会话、路由、角色校验和
选课服务。它不会使用旧课程 Demo 的明文 token，也不会向正式 `010_roles_permissions.sql` 添加学生或教师账号。
带数据服务端入口在任何建库或写入前都会校验数据库文件名必须为 `course-user-demo.accdb`；如果传入指向
`data/vCampus.accdb` 的其他配置，会直接拒绝，避免向非 Demo 数据库写入固定演示账号。

数据文件、初始化和演示种子都只属于服务端。客户端不携带数据，因此始终共用同一个
`start-client`，不再提供容易误解的 `integrated-demo-client` 或旧的模拟 token 选课 Demo 入口。

先在第一个终端启动服务端，再在第二个终端启动客户端：

```bash
vcampus-distribution/scripts/start-server-with-data.sh
vcampus-distribution/scripts/start-client.sh
```

Windows 使用对应的 `.bat` 文件。登录账号如下：

| 角色 | 登录账号 | 初始密码 | 说明 |
| --- | --- | --- | --- |
| 管理员 | `ADMIN` | `Admin1234` | 首次登录强制修改密码；改密后返回登录页，使用新密码重新登录 |
| 学生 | `213000001` | `Student1234` | 直接进入学生五个选课页面，临时映射 `studentId=userId=213000001` |
| 教师 | `TEACHER_DEMO` | `Teacher1234` | 直接进入教学班查询和教师课表 |

首次启动会幂等创建一个当前开放的演示学期、`MATH101 高等数学（带数据演示）` 和指派给
`teacher-demo-001` 的 `Demo-01` 教学班。建议按以下顺序验收：

1. 学生登录，查询 `MATH101`，选择 `Demo-01`，再到“我的选课”和“我的课表”确认结果；
2. 教师登录，在教学班查询和教师课表中确认同一教学班；
3. 管理员登录完成首次改密，重新登录后检查学期、课程目录和教学班管理页面；
4. 用学生账号尝试管理员功能时，服务端必须拒绝，不能只依赖页面隐藏。

要恢复初始账号、密码和空选课记录，先停止 Demo 服务端，再运行：

```bash
vcampus-distribution/scripts/reset-data.sh
```

该脚本只删除精确的 `data/course-user-demo.accdb`，不会触碰 `data/vCampus.accdb`。Windows 使用
`reset-data.bat`。

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

# 运行唯一发行入口：带数据服务端 + 共用客户端
vcampus-distribution/scripts/start-server-with-data.sh
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
