# vCampus 虚拟校园本地测试包

本目录包含统一登录、学籍、课程、图书馆和校园商城。服务端统一监听 `8888`，所有模块共享同一登录会话与 Access 数据库。

## 运行要求

- Windows 系统；
- JDK 21 或更高版本。

## 直接启动

clone 或 pull 仓库后，进入：

```text
vcampus-distribution/scripts
```

1. 双击 `start-server-with-data.bat`。
2. 看到服务端监听 8888 端口后，双击 `start-client.bat`。

需要恢复仓库随附的初始演示数据时，先停止服务端，再运行 `reset-data.bat`。

仓库已随附 `vcampus-distribution/lib/vCampusServer.jar` 和
`vcampus-distribution/lib/vCampusClient.jar`，仅运行演示不需要安装 Maven。

## 从源码重新构建（可选）

需要验证或更新分发 JAR 时，在仓库根目录执行：

```powershell
mvn -pl vcampus-server,vcampus-client -am package
```

打包成功后，Maven 会更新 `vcampus-distribution/lib` 中的两个 JAR。

主要演示账号（当前完整发行库的账号统一密码 `Test12345`）：

- 超级管理员：`DEMO_ADMIN`
- 学籍管理员：`STUDENT_ADMIN`
- 选课管理员：`COURSE_ADMIN`
- 图书管理员：`LIBRARY_ADMIN`
- 商城管理员：`SHOP_ADMIN`
- 用户管理员：`USER_ADMIN`
- 计算机学院管理员：`CS_COLLEGE_ADMIN`
- 数学学院管理员：`MATH_COLLEGE_ADMIN`
- 信息科学与工程学院管理员：`EE_COLLEGE_ADMIN`
- 外国语学院管理员：`FL_COLLEGE_ADMIN`

批量测试管理员为 `TESTADMIN`，教师为 `TESTTEACHER001`～`TESTTEACHER050`，学生为
`213260001`～`213261000`。该密码仅用于演示，不得用于真实环境或真实账户。完整说明见
`../docs/testing/2026-09-12-main运行状态与待讨论问题.md`。

## 数据与日志

- `data/vCampus.accdb` 是全模块演示数据库；
- `logs` 目录包含本机运行日志；
- 不要在 Git 中提交 `logs` 或 `target`；源码更新后应同步更新分发 JAR。

## 停止

先关闭客户端窗口，再回到服务端窗口按 `Ctrl+C` 停止服务。
