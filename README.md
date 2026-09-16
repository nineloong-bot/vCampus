# vCampus 虚拟校园

Java 21 校园管理应用，包含统一登录与账户管理、学籍档案、选课、图书借阅及校园商城。客户端采用 Swing，服务端使用 Socket 通信与 Access 数据库。

## 运行当前发行版

安装 Java 21 或更高版本，进入 `vcampus-distribution/scripts`：

1. 运行 `start-server-with-data.bat`，等待服务端监听 8888 端口。
2. 运行 `start-client.bat`。

默认发行数据库包含 120 名学生、24 名教师、8 个管理账号以及培养方案、课程、图书和商城场景。超级管理员 `ADMIN`，教师 `T001`，学生从 `213240001` 起，初始密码均为 `123456`。学生首次登录必须改密。

完整账号和逾期场景见 [账号清单](vcampus-database/demo/full-test-data/tools/账号清单.md)。

## 发行数据库

只保留计算机科学与工程学院和数学学院，共 5 个专业、15 套八学期培养方案、104 门课程和 208 个教学班。初始选课为空，仅开放一个学期。

数据库重建、组织绑定、成绩来源和测试账号以
[当前数据契约](docs/current-data-contract.md) 为准；历史方案和测试记录不作为数据规范。

在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File vcampus-database/demo/full-test-data/build-package.ps1
```

脚本使用仓库中的发行 JAR 和 SQL 快照生成独立测试包，默认端口 18888，统一密码 `123456`。详见 [全模块测试数据](vcampus-database/demo/full-test-data/README.md)。

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
