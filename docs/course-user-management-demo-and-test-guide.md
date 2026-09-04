# 统一课程中心 Demo 与测试指南

本文记录 `course-user-management` 分支当前的快速 Demo 用法。Demo 使用生产组合、真实登录会话和服务端权限校验，数据文件只由服务端管理；它不是生产部署说明。

## 代码位置与运行前提

分支为 `course-user-management`，本机独立 worktree 为：

```text
/private/tmp/java-summer-course-course-user-integration
```

如果主 checkout 提示 `already used by worktree`，不要在主目录强行切换该分支：一个分支不能同时被两个 worktree 检出。直接进入上面的 worktree，或在团队机器重新 clone 后 checkout 该分支即可。

需要 Java 21 或更高版本。macOS 可先确认：

```bash
java -version
```

## 启动顺序

先启动服务端，再启动客户端；客户端不创建、复制或重置数据库。两个终端分别执行：

```bash
cd /private/tmp/java-summer-course-course-user-integration
vcampus-distribution/scripts/start-server-with-data.sh
```

看到服务端监听端口后，在第二个终端执行：

```bash
cd /private/tmp/java-summer-course-course-user-integration
vcampus-distribution/scripts/start-client.sh
```

Windows 使用同名 `.bat` 文件：`start-server-with-data.bat` 先执行，`start-client.bat` 后执行。两个启动脚本都要求 Java 21；服务端使用 `config/server-with-data.properties`，客户端使用 `config/client.properties`。

## Demo 账号

三种角色共用初始密码 `DemoPassword7`：

| 角色 | 登录账号 | 初始密码 | 初始登录行为 |
| --- | --- | --- | --- |
| 学生 | `DEMO_STUDENT` | `DemoPassword7` | 直接进入课程中心 |
| 教师 | `DEMO_TEACHER` | `DemoPassword7` | 直接进入课程中心 |
| 管理员 | `DEMO_ADMIN` | `DemoPassword7` | 必须先修改密码；成功后返回登录页，再用新密码重新登录 |

管理员首次改密是受限登录流程，不会在改密完成前创建可操作的课程工作区。若要重新使用初始密码，请停止服务端并按下节重置 Demo 数据。

## 登录后可见的课程标签

课程中心是左侧全局导航中的一个模块，课程页面属于模块内标签，不会扩展全局导航。按角色创建的标签如下：

| 角色 | 课程中心内标签 |
| --- | --- |
| 学生 | 教学班查询、我的选课、我的课表、退改补、重修 |
| 教师 | 教学班查询、教师课表 |
| 管理员 | 学期管理、课程目录、教学班管理、修读结果导入、选退记录 |

## 三角色手工走查

### 学生：选课后立即退选

1. 使用 `DEMO_STUDENT` / `DemoPassword7` 登录，进入“课程中心”。
2. 在“教学班查询”中选择开放的未选教学班，提交选课。
3. 打开“我的选课”，确认新增记录为活动状态。
4. 选中该记录，点击“退选所选课程”，在确认框确认。成功后页面刷新，记录保留为退选历史，名额立即释放。
5. 打开“我的课表”确认课表随选退变化刷新。

Demo 初始数据还带有一个活动选课记录；可先查看它，再选择一个未选教学班完成上述“选课 → 立即退选”流程。服务端以当前学期时间窗和会话权限为准，客户端隐藏标签不构成安全边界。

### 退改补：补选与改选只在调整窗口

“退改补”标签中的补选（late add）和改选（change offering）只在退改补调整窗口开放时可提交。正常选课窗口不能通过这两个操作绕过规则；两个窗口之外服务端都会拒绝。退选本身在正常选课窗口和调整窗口都允许，学期关闭或两个窗口都关闭时拒绝。

### 教师

使用 `DEMO_TEACHER` / `DemoPassword7` 登录，确认只能看到“教学班查询”和“教师课表”，并能看到演示教师负责的教学班与课表。学生或管理员写操作不因客户端页面隐藏而获得授权。

### 管理员：结构化录入

使用 `DEMO_ADMIN` / `DemoPassword7` 登录；首次登录先修改密码。新密码必须为 8–64 位并同时包含字母和数字（例如本地走查可使用 `DemoChanged8`），确认修改成功返回登录页后，必须用这个新密码重新登录；旧的 `DemoPassword7` 已失效。

1. 在“学期管理”创建或编辑学期，使用日期/时间控件填写开学、结束、正常选课和退改补窗口，检查状态和时间先后校验。
2. 在“课程目录”创建或编辑课程，填写课程代码、名称、学分和学时。
3. 在“教学班管理”新建或编辑教学班：从已加载的学期、课程、教师选择项中选择引用对象，填写容量和中文状态。
4. 点击“添加上课时间”逐行填写星期、起止节次、起止周次和教室；按需删除行。确认至少一行且每行顺序有效后保存。
5. 在“选退记录”检查选课和退选记录。编辑已有教学班时，容量不能低于当前已选人数；版本冲突应先刷新并复核最新记录。

正常录入不要求手填 term/course/teacher 原始 ID、逗号分隔的 schedule，或手工拼接日期时间字符串。批量 Excel/CSV 导入不属于本轮教学班录入流程。

## 重置 Demo 数据

重置只针对服务端的精确路径 `vcampus-distribution/data/course-user-demo.accdb`，不会触碰其他数据库。先停止服务端，再执行：

```bash
vcampus-distribution/scripts/reset-data.sh
```

脚本会先显示确认提示；只有输入 `y` 或 `Y` 才删除文件，其他输入（包括直接回车）都会取消。Windows 执行 `reset-data.bat`，同样需要输入 `y` 确认。删除后下次执行 `start-server-with-data` 会重新创建当前 Demo 数据、账号和初始密码。

## 当前快速 Demo 的最小验证范围

以下命令是 Task 8 快速 Demo 的聚焦门禁，均应在 Java 21 下执行：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

mvn -pl vcampus-server,vcampus-client -am \
  -Dtest=IntegratedDemoServerMainTest,DistributionDemoScriptsTest,CourseDemoNetworkTest,LoginCourseSocketIntegrationTest,CourseUiTest,UserClientServiceTask6Test \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

聚焦结果应为 `BUILD SUCCESS`；服务端 Demo 测试应证明三个 `DEMO_*` 账号、角色、活动选课和多个开放教学班可幂等初始化，发行脚本测试应证明仅存在三组入口：`start-client.{sh,bat}`、`start-server-with-data.{sh,bat}`、`reset-data.{sh,bat}`。本节是当前快速 Demo 的证据范围，不代替最终全量回归，也不在未实际运行时虚构固定 `Tests run` 总数。

完整 `mvn clean test`、全模块合并后的三角色人工验收和最终发行包复核，延后到所有模块合并后执行。快速 Demo 期间可用真实启动冒烟确认服务端先监听、客户端随后连接；停止时先关客户端，再在服务端终端按 `Ctrl+C`。

## 当前视觉证据

Task 8 生成的截图及索引位于 `docs/ui-review/`：

- [统一登录](ui-review/course/integrated-login.png)
- [学生课程中心](ui-review/course/integrated-student-course.png)
- [管理员选课阶段](ui-review/course/integrated-admin-selection-phase.png)
- [管理员教学班结构化编辑器](ui-review/course/integrated-admin-offering-editor.png)
- [UI review manifest](ui-review/manifest.md)

截图按支持的窗口尺寸生成；具体状态与审阅记录以 manifest 为准。

## 仍需在最终验收补齐的事项

- 当前交付是快速 Demo；全模块合并后仍需执行完整 `mvn clean test`、真实三角色全流程和最终发行 JAR 复核。
- 组合根目前仍使用 `TemporaryUserStudentGateway` 将活动学生账号临时映射为选课 `studentId`；正式学籍模块提供 `StudentQueryPort` 并完成 ID 迁移后再替换该 adapter。
