# 统一课程中心 Demo 与测试指南

本文记录合并后统一版本的课程中心 Demo 用法。Demo 使用生产组合、真实登录会话和服务端权限校验，数据文件只由服务端管理；它不是生产部署说明。

## 代码位置与运行前提

需要 Java 21 或更高版本。macOS 可先确认：

```bash
java -version
```

## 启动顺序

先启动服务端，再启动客户端；客户端不创建、复制或重置数据库。两个终端分别执行：

```bash
vcampus-distribution/scripts/start-server-with-data.sh
```

看到服务端监听端口后，在第二个终端执行：

```bash
vcampus-distribution/scripts/start-client.sh
```

Windows 使用同名 `.bat` 文件：`start-server-with-data.bat` 先执行，`start-client.bat` 后执行。两个启动脚本都要求 Java 21；服务端使用 `config/server-with-data.properties`，客户端使用 `config/client.properties`。

## Demo 账号

学生、教师和管理员均来自统一数据库的演示种子：

| 角色 | 登录账号 | 初始密码 | 初始登录行为 |
| --- | --- | --- | --- |
| 学生 | `213242478` | `12345678` | 首次登录必须先设置新密码，然后重新登录 |
| 教师 | `DEMO_TEACHER` | `Teacher123456` | 直接进入课程中心 |
| 超级管理员 | `DEMO_ADMIN` | `admin123456` | 直接进入课程管理页面 |

管理员与教师凭据以统一用户管理种子为准。若要恢复初始数据，请停止服务端并按下节重置 Demo 数据。

## 登录后可见的课程标签

课程中心是左侧全局导航中的一个模块，课程页面属于模块内标签，不会扩展全局导航。按角色创建的标签如下：

| 角色 | 课程中心内标签 |
| --- | --- |
| 学生 | 教学班查询、我的选课、我的课表、退改补、重修 |
| 教师 | 教学班查询、教师课表 |
| 管理员 | 学期管理、选课阶段、课程目录、教学班管理、修读结果导入、选退记录 |

## 三角色手工走查

### 学生：选课后立即退选

1. 使用 `213242478` / `12345678` 首次登录，按提示设置新密码后重新登录，进入“课程中心”。
2. 在“教学班查询”中选择开放的未选教学班，提交选课。
3. 打开“我的选课”，确认新增记录为活动状态。
4. 选中该记录，点击“退选所选课程”，在确认框确认。成功后页面刷新，记录保留为退选历史，名额立即释放。
5. 打开“我的课表”确认课表随选退变化刷新。

Demo 初始数据还带有一个活动选课记录；可先查看它，再选择一个未选教学班完成上述“选课 → 立即退选”流程。服务端以当前学期时间窗和会话权限为准，客户端隐藏标签不构成安全边界。

### 退改补：补选与改选只在调整窗口

“退改补”标签中的补选（late add）和改选（change offering）只在退改补调整窗口开放时可提交。正常选课窗口不能通过这两个操作绕过规则；两个窗口之外服务端都会拒绝。退选本身在正常选课窗口和调整窗口都允许，学期关闭或两个窗口都关闭时拒绝。

### 教师

使用 `DEMO_TEACHER` / `Teacher123456` 登录，确认只能看到“教学班查询”和“教师课表”，并能看到演示教师负责的教学班与课表。学生或管理员写操作不因客户端页面隐藏而获得授权。

### 管理员：结构化录入

使用 `DEMO_ADMIN` / `admin123456` 登录。该账号来自统一用户管理模块，角色为超级管理员，并在课程模块中映射为课程管理员。

1. 在“学期管理”创建或编辑学期，使用日期/时间控件填写开学、结束、正常选课和退改补窗口，检查状态和时间先后校验。
2. 在“课程目录”创建或编辑课程，填写课程代码、名称、学分和学时。
3. 在“教学班管理”新建或编辑教学班：从已加载的学期、课程、教师选择项中选择引用对象，填写容量和中文状态。
4. 点击“添加上课时间”逐行填写星期、起止节次、起止周次和教室；按需删除行。确认至少一行且每行顺序有效后保存。
5. 在“选退记录”检查选课和退选记录。编辑已有教学班时，容量不能低于当前已选人数；版本冲突应先刷新并复核最新记录。

正常录入不要求手填 term/course/teacher 原始 ID、逗号分隔的 schedule，或手工拼接日期时间字符串。批量 Excel/CSV 导入不属于本轮教学班录入流程。

## 重置 Demo 数据

重置只针对服务端的精确路径 `vcampus-distribution/data/vCampus.accdb`，不会触碰其他数据库。先停止服务端，再执行：

```bash
vcampus-distribution/scripts/reset-data.sh
```

脚本会先显示确认提示；只有输入 `y` 或 `Y` 才删除文件，其他输入（包括直接回车）都会取消。Windows 执行 `reset-data.bat`，同样需要输入 `y` 确认。删除后下次执行 `start-server-with-data` 会重新创建当前 Demo 数据、账号和初始密码。

## 最终合并版本的验证范围

以下全量门禁应在 Java 21 下执行：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

mvn clean verify
```

结果应为 `BUILD SUCCESS`；测试覆盖上述三个演示账号、角色、活动选课、统一数据初始化、发行脚本、真实登录选课 socket 及全模块回归。运行冒烟测试时先确认服务端监听，再启动客户端；停止时先关客户端，再在服务端终端按 `Ctrl+C`。

## 当前视觉证据

Task 8 生成的截图及索引位于 `docs/ui-review/`：

- [统一登录](ui-review/course/integrated-login.png)
- [学生课程中心](ui-review/course/integrated-student-course.png)
- [管理员选课阶段](ui-review/course/integrated-admin-selection-phase.png)
- [管理员教学班结构化编辑器](ui-review/course/integrated-admin-offering-editor.png)
- [UI review manifest](ui-review/manifest.md)

截图按支持的窗口尺寸生成；具体状态与审阅记录以 manifest 为准。

## 合并状态

当前交付已完成全模块组合、完整自动化回归和最终发行 JAR 复核。选课组合根已使用正式学籍
`StudentQueryPort`，真实 socket 集成测试覆盖彼此独立的 `userId` 与 `studentId`。
