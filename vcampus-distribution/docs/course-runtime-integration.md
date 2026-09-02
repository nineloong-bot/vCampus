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
| `ADMIN` | `course.terms`、`course.catalog`、`course.offering-admin`、`course.outcome-import`、`course.adjustment-audit` | 学期管理、课程目录、教学班管理、修读结果导入、选退记录 |

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

需要快速团队联调时，先阅读 [统一课程中心 Demo 与测试指南](course-user-management-demo-and-test-guide.md)；[快速交付说明](course-user-management-quick-delivery.md) 也保留了本轮快速 Demo 的范围和限制。

> **以下账号和固定密码只用于本地演示。不要把 Demo 数据库、账号或密码用于部署；正式上线前必须删除
> `data/course-user-demo.accdb`，并通过正式建号流程初始化账户。**

Demo 使用独立的 `data/course-user-demo.accdb`，调用生产 `ApplicationRuntime`、生产登录会话、路由、角色校验和选课服务。带数据服务端入口在任何建库或写入前都会校验数据库文件名必须为 `course-user-demo.accdb`；数据文件、初始化和演示种子都只属于服务端。

三种角色使用同一个 `start-client` 入口。初始密码统一为 `DemoPassword7`：

| 角色 | 登录账号 | 初始登录行为 |
| --- | --- | --- |
| 学生 | `DEMO_STUDENT` | 直接进入课程中心 |
| 教师 | `DEMO_TEACHER` | 直接进入课程中心 |
| 管理员 | `DEMO_ADMIN` | 首次登录强制修改密码；改密后返回登录页，使用新密码重新登录 |

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

课程中心只占一个全局导航模块，内部标签按角色过滤：学生为“教学班查询、我的选课、我的课表、退改补、重修”；教师为“教学班查询、教师课表”；管理员为“学期管理、课程目录、教学班管理、修读结果导入、选退记录”。

学生可在“教学班查询”选择开放教学班，随后在“我的选课”确认活动记录并立即点击“退选所选课程”；确认后记录保留为退选历史、名额释放，课表在重新选择时刷新。Demo 预置一个活动选课和多个开放教学班，便于完成“选课 → 立即退选”。补选和改选仍只允许在退改补调整窗口，正常选课窗口不能绕过该限制；两个窗口之外服务端拒绝。

管理员必须先完成 `DEMO_ADMIN` 首次改密并重新登录，再从学期、课程、教师选择项录入教学班，使用结构化容量、状态和多行上课时间控件保存。正常流程不要求手填原始 ID、逗号分隔 schedule 或日期时间字符串。容量、时间顺序和乐观锁版本仍由客户端提示并由服务端最终校验。

### 重置

先停止服务端，再运行 `vcampus-distribution/scripts/reset-data.sh`（Windows 使用 `.bat`）。脚本只删除精确的 `data/course-user-demo.accdb`，并在删除前要求输入 `y` 或 `Y`；其他输入取消操作。下次启动服务端会重新创建 Demo 数据、账号和初始密码，不会触碰 `data/vCampus.accdb`。

## 当前快速 Demo 的最小验证

所有 Maven 命令显式使用 JDK 21。快速 Demo 只执行聚焦门禁，不宣称全模块最终验收：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl vcampus-server,vcampus-client -am \
  -Dtest=IntegratedDemoServerMainTest,DistributionDemoScriptsTest,CourseDemoNetworkTest,LoginCourseSocketIntegrationTest,CourseUiTest,UserClientServiceTask6Test \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期为 `BUILD SUCCESS`；测试覆盖账号/角色/种子幂等性、发行入口、真实登录选课 socket 和课程 UI。完整 `mvn clean test`、全模块合并后的三角色人工验收及最终发行 JAR 复核，延后到所有模块合并后执行；本快速 Demo 文档不虚构固定 `Tests run` 总数。

截图索引见 [UI review manifest](ui-review/manifest.md)，Task 8 生成的文件为：[统一登录](ui-review/course/integrated-login.png)、[学生课程中心](ui-review/course/integrated-student-course.png)、[管理员教学班结构化编辑器](ui-review/course/integrated-admin-offering-editor.png)。
