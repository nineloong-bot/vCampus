# 选课与用户管理快速交付候选版

> 状态：可运行的团队联调候选版（2026-09-02），不是最终验收版。为满足紧急交付，本次保留关键功能测试、编译打包和真实服务端启动冒烟，但没有等待教学班录入优化、完整回归和三角色全流程人工验收结束。

## 获取与运行

分支为 `course-user-management`，本机独立 worktree 为：

```text
/private/tmp/java-summer-course-course-user-integration
```

如果主仓库提示 `already used by worktree`，不要在原目录强行切换；直接进入上述目录运行，或在团队机器重新 clone 后 checkout 远端分支。

要求 Java 21。先启动服务端，再启动客户端：

```bash
cd /private/tmp/java-summer-course-course-user-integration
vcampus-distribution/scripts/start-server-with-data.sh
vcampus-distribution/scripts/start-client.sh
```

Windows 使用同名 `.bat` 文件。数据只属于服务端；客户端是三种角色共用的同一个入口。

## Demo 账号

| 角色 | 账号 | 密码 | 说明 |
| --- | --- | --- | --- |
| 管理员 | `ADMIN` | `Admin1234` | 首次登录会要求修改密码 |
| 学生 | `213000001` | `Student1234` | 可进入学生选课工作区 |
| 教师 | `TEACHER_DEMO` | `Teacher1234` | 可进入教学班查询和教师课表 |

需要恢复固定密码和初始数据时，先停止服务端，再运行 `vcampus-distribution/scripts/reset-data.sh`。

## 本候选版已经具备

- 统一登录成功后进入 vCampus 主界面，选课模块嵌入统一左侧导航；
- 登录会话在客户端和服务端共同控制学生、教师、管理员权限；
- 学生在选课阶段和退改选阶段都可退选，补选/改选仍只允许在退改选阶段；
- “我的选课”支持立即退课，并处理重复提交、页面切换与历史退课行；
- 管理员课程的学分/学时以及学期日期、时间、状态已改为结构化控件并提供明确校验；
- 客户端和带数据服务端 JAR 已使用 Java 21 重新构建；真实服务端已成功创建 Demo 数据并监听 `8888`。

## 已知未完成项

- 教学班管理还未完成最终的“学期/课程/教师下拉搜索 + 多行结构化上课时间编辑器”；当前界面可用，但部分字段仍需手动输入 ID 或按现有格式录入。
- 学期日期步进器的显示使用上海时区；在系统默认时区存在夏令时的极端日期附近，步进一天仍需后续加强专门的时区模型。
- 本次紧急包执行了成功的 Java 21 `mvn -DskipTests package`、关键选退课/UI 聚焦测试和真实服务端启动冒烟；没有在最后一次打包后执行完整 `mvn clean test`，也尚未完成三角色全流程人工截图验收。
- 管理员首次修改密码会改变当前 Demo 数据库中的密码；需要重新演示固定账号时请重置数据。

完整架构、角色页面和运行说明见 [course-runtime-integration.md](course-runtime-integration.md)。后续开发应继续既有计划的 Task 7–9，不应把本文件的“候选版”状态误标为最终验收完成。
