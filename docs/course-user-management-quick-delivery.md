# 选课与用户管理快速交付 Demo

> 状态：当前快速 Demo（2026-09-02）。用于团队联调，不是所有模块合并后的最终验收包；完整回归和最终发行复核按后文延期执行。

## 获取与运行

分支为 `course-user-management`，本机独立 worktree 为：

```text
/private/tmp/java-summer-course-course-user-integration
```

如果主仓库提示 `already used by worktree`，不要在原目录强行切换；直接进入上述目录运行，或在团队机器重新 clone 后 checkout 远端分支。该限制来自一个分支不能同时被两个 worktree 检出。

要求 Java 21 或更高版本。先启动服务端，再启动客户端，客户端不负责数据初始化：

```bash
cd /private/tmp/java-summer-course-course-user-integration
vcampus-distribution/scripts/start-server-with-data.sh
```

看到服务端监听端口后，在第二个终端执行：

```bash
cd /private/tmp/java-summer-course-course-user-integration
vcampus-distribution/scripts/start-client.sh
```

Windows 依次使用 `start-server-with-data.bat`、`start-client.bat`。

## Demo 账号

所有账号的初始密码均为 `DemoPassword7`：

| 角色 | 账号 | 初始登录行为 |
| --- | --- | --- |
| 学生 | `DEMO_STUDENT` | 进入学生课程中心 |
| 教师 | `DEMO_TEACHER` | 进入教师课程中心 |
| 管理员 | `DEMO_ADMIN` | 首次登录强制改密；改密成功后回到登录页，使用新密码重新登录 |

## 当前可走查能力

- 课程中心作为一个全局模块嵌入共享 vCampus 外壳；内部标签按角色显示：学生为“教学班查询、我的选课、我的课表、退改补、重修”，教师为“教学班查询、教师课表”，管理员为“学期管理、课程目录、教学班管理、修读结果导入、选退记录”。
- 学生可从“教学班查询”选开放教学班，在“我的选课”确认后立即退选；确认成功后保留退选历史并释放名额。
- 补选和改选只在退改补调整窗口开放；正常选课窗口和两个窗口之外都不能通过这两个操作绕过服务端规则。退选在正常选课窗口和调整窗口均可用。
- 管理员课程、学期和教学班录入使用结构化控件：学期/课程/教师通过选择项加载，容量和状态有控件，课程表时间逐行编辑；正常流程不要求原始 ID、逗号分隔 schedule 或手工日期时间字符串。

## 重置数据

先停止服务端，再执行：

```bash
vcampus-distribution/scripts/reset-data.sh
```

Windows 使用 `reset-data.bat`。脚本只针对 `data/course-user-demo.accdb`，删除前要求输入 `y` 或 `Y` 确认；其他输入取消，不会触碰 `data/vCampus.accdb`。下次启动服务端会恢复 Demo 账号、初始密码和种子数据。管理员改过的密码也会随重置恢复为 `DemoPassword7`。

## 快速 Demo 的最小验证与证据

以下是本轮快速 Demo 的聚焦门禁（Java 21），不是最终全量测试：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl vcampus-server,vcampus-client -am \
  -Dtest=IntegratedDemoServerMainTest,DistributionDemoScriptsTest,CourseDemoNetworkTest,LoginCourseSocketIntegrationTest,CourseUiTest,UserClientServiceTask6Test \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期输出 `BUILD SUCCESS`，覆盖 Demo 账号/角色和种子幂等性、三组发行入口、真实登录选课 socket 及课程 UI。当前快速 Demo 文档不虚构固定 `Tests run` 总数；完整 `mvn clean test`、全模块合并后的三角色人工流程和最终发行 JAR 复核，延后到所有模块合并后执行。

视觉证据见 [统一课程中心 Demo 与测试指南](course-user-management-demo-and-test-guide.md) 及 [UI review manifest](ui-review/manifest.md)，截图路径为：

- `docs/ui-review/course/integrated-login.png`
- `docs/ui-review/course/integrated-student-course.png`
- `docs/ui-review/course/integrated-admin-selection-phase.png`
- `docs/ui-review/course/integrated-admin-offering-editor.png`

## 仍需最终验收处理

- 全模块合并后执行完整测试、三角色真实人工流程和发行 JAR 复核。
- 组合根目前仍使用 `TemporaryUserStudentGateway` 将活动学生账号临时映射为选课 `studentId`；正式学籍模块提供 `StudentQueryPort` 并完成 ID 迁移后再替换。
