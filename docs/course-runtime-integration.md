# 用户登录与选课运行时集成

## 生产组合与数据库连接

`ApplicationRuntime` 是用户、学籍、选课、图书和商城模块的统一生产组合根。一次服务端启动只创建并共享：

- 一个 `MessageRouter`，统一注册用户、学籍、选课、图书、商城与平台治理命令；
- 一个指向同一 Access 数据库的 `ConnectionProvider`；
- 一个应用级 `ResourceLockManager`，由所有需要并发协调的模块共用；
- 一个用户模块 `SessionRegistry`，所有模块的登录态与业务授权都以其中的实时会话为准。

会话空闲过期时间由 `config/server-with-data.properties` 的 `session.timeoutMinutes` 控制；发行启动会把该值传入
`ApplicationRuntime`，未显式配置组合参数的兼容入口仍使用 30 分钟默认值。

带数据服务端入口使用 `ApplicationRuntime` 的路由创建生产 `SocketServer`。客户端也只创建一个
`ClientConnection`，`UserClientService` 与 `CourseClientService` 共用该连接；登录成功后写入的会话令牌
因此会自动附在后续选课请求上。客户端页面过滤只是减少误操作，服务端角色检查始终是安全边界。

UCanAccess JDBC URL 必须保持安全默认值，**禁止设置
`immediatelyReleaseResources=true`**。UCanAccess 5.1.3 在一个线程打开连接、另一个线程立即释放驱动资源时，
可能在驱动级全局资源簿记中死锁。生产 URL 形如：

当前 UCanAccess 5.1.3 还无法可靠持久化培养方案表的外键和四个辅助查询索引；三组业务唯一约束已由
Access 数据库直接强制，引用完整性也由服务层校验。受支持的生产部署必须由单个 `ApplicationRuntime`
独占写入数据库，不支持多个服务进程同时写入或绕过服务端直接修改 Access 文件。

```text
jdbc:ucanaccess:///absolute/path/to/vcampus.accdb
```

带数据发行配置把 `database.path`（Access 文件）和 `database.resourceRoot`（`schema/`、`seed/` 所在目录）分开解析。
只有显式设置 `database.createIfMissing=true` 时才会创建缺失的数据库及其父目录；已有数据库永远不会被覆盖。
发行配置使用 `data/vCampus.accdb` 和 `database/` 资源根，因此全新解压后可以初始化全模块演示数据库。

## 登录、页面与会话生命周期

连接服务端后先显示 `LoginFrame`。普通登录成功后进入按角色创建的选课主窗口，页面集合精确如下：

| 角色 | 页面键 | 用户可见功能 |
| --- | --- | --- |
| `STUDENT` | `course.offerings`、`course.enrollments`、`course.schedule`、`course.adjustment`、`course.retake` | 教学班查询、我的选课、我的课表、退改补、重修 |
| `TEACHER` | `course.offerings`、`course.schedule` | 教学班查询、教师课表 |
| `SUPER_ADMIN`、`COURSE_ADMIN`、兼容角色 `ADMIN` | `course.terms`、`course.selection-phases`、`course.catalog`、`course.offering-admin`、`course.outcome-import`、`course.adjustment-audit` | 学期管理、选课阶段、课程目录、教学班管理、修读结果导入、选退记录 |

`mustChangePassword=true` 的首次改密受限会话不创建选课主窗口，只能修改密码或登出；任何选课命令都在服务端以
`AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED` 拒绝。密码修改成功或登出后清除内存令牌并回到登录。

运行中的选课请求若返回以下任一认证失败，客户端统一关闭角色化选课窗口、清除内存令牌并重新打开登录页：

- `AUTH_SESSION_EXPIRED`；
- `AUTH_ACCOUNT_DISABLED`；
- `AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED`。

登出后连接上的令牌已经清空，再发出的选课请求同样稳定返回 `AUTH_SESSION_EXPIRED`，不会被误报为普通角色越权。

## 学籍标识接入

选课模块已经通过正式学籍域 `StudentQueryPort` 查询资格和学生标识，不再假设
`studentId == userId`。组合代码保持端口边界：

```java
StudentQueryPort studentQueries = /* 正式学籍域查询端口 */;
CourseStudentGateway students = CourseRuntimeAdapters.students(
        studentQueries::getEnrollmentEligibility,
        StudentEligibility::studentId,
        eligibility -> eligibility.status().name(),
        StudentEligibility::majorCode,
        StudentEligibility::cohortYear,
        studentQueries::existsActiveStudent);
```

真实 socket 集成测试使用彼此独立的 `userId` 与 `studentId`，覆盖选课、退选和活动学籍校验。

## 可直接运行的带数据 Demo

需要快速团队联调时，先阅读 [统一课程中心 Demo 与测试指南](course-user-management-demo-and-test-guide.md)；[快速交付说明](course-user-management-quick-delivery.md) 也保留了本轮快速 Demo 的范围和限制。

> **以下账号和固定密码只用于本地演示。不要把 Demo 数据库、账号或密码用于部署；正式上线前必须删除
> `data/vCampus.accdb`，并通过正式建号流程初始化账户。**

Demo 使用统一的 `data/vCampus.accdb`，调用生产 `ApplicationRuntime`、生产登录会话、路由和全模块服务。数据文件、初始化和演示种子都只属于服务端。

三种角色使用同一个 `start-client` 入口，教师和管理员复用统一用户管理账号：

| 角色 | 登录账号 | 初始密码 | 初始登录行为 |
| --- | --- | --- | --- |
| 学生 | `213242478` | `12345678` | 首次登录必须先设置新密码，然后重新登录 |
| 教师 | `DEMO_TEACHER` | `Teacher123456` | 直接进入课程中心 |
| 超级管理员 | `DEMO_ADMIN` | `admin123456` | 直接进入课程管理页面 |

### 启动顺序

第一个终端先执行服务端，看到监听端口后，第二个终端再执行客户端：

```bash
vcampus-distribution/scripts/start-server-with-data.sh
```

```bash
vcampus-distribution/scripts/start-client.sh
```

Windows 依次使用 `start-server-with-data.bat`、`start-client.bat`。两个脚本均要求 Java 21 或更高版本。客户端不创建、复制或重置数据库。

### 课程标签和业务走查

课程中心只占一个全局导航模块，内部标签按角色过滤：学生为“教学班查询、我的选课、我的课表、退改补、重修”；教师为“教学班查询、教师课表”；管理员为“学期管理、选课阶段、课程目录、教学班管理、修读结果导入、选退记录”。

学生可在“教学班查询”选择开放教学班，随后在“我的选课”确认活动记录并立即点击“退选所选课程”；确认后记录保留为退选历史、名额释放，课表在重新选择时刷新。Demo 预置一个活动选课和多个开放教学班，便于完成“选课 → 立即退选”。补选和改选仍只允许在退改补调整窗口，正常选课窗口不能绕过该限制；两个窗口之外服务端拒绝。

管理员使用 `DEMO_ADMIN` / `admin123456` 登录，再从学期、课程、教师选择项录入教学班，使用结构化容量、状态和多行上课时间控件保存。正常流程不要求手填原始 ID、逗号分隔 schedule 或日期时间字符串。容量、时间顺序和乐观锁版本仍由客户端提示并由服务端最终校验。

### 重置

先停止服务端，再运行 `vcampus-distribution/scripts/reset-data.sh`（Windows 使用 `.bat`）。脚本只删除精确的 `data/vCampus.accdb`，并在删除前要求输入 `y` 或 `Y`；其他输入取消操作。下次启动服务端会从统一 schema/seed 重新创建全模块 Demo 数据、账号和初始密码。

## 当前快速 Demo 的最小验证

所有 Maven 命令显式使用 JDK 21。最终合并版本执行全量门禁：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean verify
```

预期为 `BUILD SUCCESS`；测试覆盖账号/角色、统一 schema/seed 幂等性、发行入口、真实登录选课 socket 和各模块 UI。

截图索引见 [UI review manifest](ui-review/manifest.md)，Task 8 生成的文件为：[统一登录](ui-review/course/integrated-login.png)、[学生课程中心](ui-review/course/integrated-student-course.png)、[管理员教学班结构化编辑器](ui-review/course/integrated-admin-offering-editor.png)。
