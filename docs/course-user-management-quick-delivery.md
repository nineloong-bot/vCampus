# 选课与用户管理快速交付 Demo

> 状态：全模块合并版本。用于团队联调和最终发行验证。

## 获取与运行

要求 Java 21 或更高版本。先启动服务端，再启动客户端，客户端不负责数据初始化：

```bash
vcampus-distribution/scripts/start-server-with-data.sh
```

看到服务端监听端口后，在第二个终端执行：

```bash
vcampus-distribution/scripts/start-client.sh
```

Windows 依次使用 `start-server-with-data.bat`、`start-client.bat`。

## Demo 账号

学生、教师和管理员均使用统一数据库中的演示账号：

| 角色 | 账号 | 初始密码 | 初始登录行为 |
| --- | --- | --- | --- |
| 学生 | `213242478` | `12345678` | 首次登录改密后重新登录，进入学生课程中心 |
| 教师 | `DEMO_TEACHER` | `Teacher123456` | 进入教师课程中心 |
| 超级管理员 | `DEMO_ADMIN` | `admin123456` | 进入课程管理页面 |

## 当前可走查能力

- 课程中心作为一个全局模块嵌入共享 vCampus 外壳；内部标签按角色显示：学生为“教学班查询、我的选课、我的课表、退改补、重修”，教师为“教学班查询、教师课表”，管理员为“学期管理、选课阶段、课程目录、教学班管理、修读结果导入、选退记录”。
- 学生可从“教学班查询”选开放教学班，在“我的选课”确认后立即退选；确认成功后保留退选历史并释放名额。
- 补选和改选只在退改补调整窗口开放；正常选课窗口和两个窗口之外都不能通过这两个操作绕过服务端规则。退选在正常选课窗口和调整窗口均可用。
- 管理员课程、学期和教学班录入使用结构化控件：学期/课程/教师通过选择项加载，容量和状态有控件，课程表时间逐行编辑；正常流程不要求原始 ID、逗号分隔 schedule 或手工日期时间字符串。

## 重置数据

先停止服务端，再执行：

```bash
vcampus-distribution/scripts/reset-data.sh
```

Windows 使用 `reset-data.bat`。脚本只针对 `data/vCampus.accdb`，删除前要求输入 `y` 或 `Y` 确认；其他输入取消。下次启动服务端会从统一 schema/seed 恢复全模块 Demo 账号、初始密码和种子数据。

## 快速 Demo 的最小验证与证据

最终合并版本使用 Java 21 执行全量门禁：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean verify
```

预期输出 `BUILD SUCCESS`，覆盖 Demo 账号/角色、统一种子幂等性、三组发行入口、真实登录选课 socket 及全模块 UI。

视觉证据见 [统一课程中心 Demo 与测试指南](course-user-management-demo-and-test-guide.md) 及 [UI review manifest](ui-review/manifest.md)，截图路径为：

- `docs/ui-review/course/integrated-login.png`
- `docs/ui-review/course/integrated-student-course.png`
- `docs/ui-review/course/integrated-admin-selection-phase.png`
- `docs/ui-review/course/integrated-admin-offering-editor.png`

组合根已接入正式学籍 `StudentQueryPort`，选课流程使用真实 `studentId`，不再依赖用户 ID 与学生 ID 相同的临时假设。
