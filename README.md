# vCampus 虚拟校园

Java 21 校园管理应用，包含统一登录与账户管理、学籍档案、选课、图书借阅及校园商城。客户端采用 Swing，服务端使用 Socket 通信与 Access 数据库。

## 运行当前发行版

安装 Java 21 或更高版本，进入 `vcampus-distribution/scripts`：

1. 运行 `start-server-with-data.bat`，等待服务端监听 8888 端口。
2. 运行 `start-client.bat`。

账号初始值及密码初始化说明见 [测试账号说明](vcampus-distribution/scripts/测试账号与密码.txt)。

## 全模块批量测试数据

提供 1,000 名学生、50 名教师、120 门课程、240 个教学班、500 种图书、30 家店铺及相关选课、借阅和交易记录。

在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File vcampus-database/demo/full-test-data/build-package.ps1
```

脚本使用仓库中的发行 JAR 和 SQL 快照生成独立测试包，默认端口 18888，统一密码 `Test12345`。详见 [全模块测试数据](vcampus-database/demo/full-test-data/README.md)。

## 源码构建

需要 JDK 21 与 Maven：

```powershell
mvn clean verify
```

发行文件输出到 `vcampus-distribution/lib`。已知验证问题见 [密码显示功能验证记录](docs/testing/2026-09-07-password-visibility-manual-test.md)。

## 项目目录

| 目录 | 内容 |
| --- | --- |
| `vcampus-common` | 公共协议、命令和数据类型 |
| `vcampus-client` | 客户端界面与通信 |
| `vcampus-server` | 业务服务、权限与持久化 |
| `vcampus-database` | 数据库结构、基础数据与批量测试工具 |
| `vcampus-distribution` | 可运行 JAR、配置和启动脚本 |
| `docs/testing` | 手动测试说明与问题记录 |
| `docs/superpowers` | 设计和实施计划 |
| `docs/archive` | 历史说明 |

最新问题清单见 [统一版本测试问题记录](docs/testing/2026-09-04-vcampus-unified-test-findings.md)。
