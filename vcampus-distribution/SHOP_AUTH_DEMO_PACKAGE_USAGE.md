# vCampus Shop Demo 便携包使用说明

本说明适用于 Windows 单机演示包 `vCampus-Shop-Demo.zip`。接收演示包的组员不需要安装 Maven、Git，也不需要获取项目源码；电脑上需要安装 Java 21 或更高版本。

## 一、生成演示包

开发者在 `SHOP` 分支的仓库根目录打开 PowerShell，运行：

```powershell
.\vcampus-distribution\scripts\build-shop-auth-demo-package.ps1
```

脚本会跳过自动化测试，构建客户端和服务端 JAR，并生成：

```text
target/shop-auth-demo-release/<时间戳>/vCampus-Shop-Demo.zip
```

每次运行都会创建新的时间戳目录，不会覆盖以前生成的演示包。ZIP 中包含运行所需的 JAR、日志配置、Demo 数据库、schema、seed、使用说明和两个启动 BAT，不包含源码、Maven 工程文件或已有运行日志。

## 二、发送演示包

将完整的 `vCampus-Shop-Demo.zip` 发给测试者。不要只发送其中的 BAT 或 JAR，因为程序还需要 `config` 和 `database` 目录。

## 三、安装 Java

测试电脑需要 Java 21 或更高版本。可以在命令提示符中检查：

```bat
java -version
```

如果提示找不到 `java`，请先安装 Java 21，并将 Java 的 `bin` 目录加入系统 `PATH`。

## 四、启动 Demo

1. 将 `vCampus-Shop-Demo.zip` 完整解压到普通文件夹。不要直接在压缩包预览窗口中运行文件。
2. 双击 `启动服务端.bat`。
3. 等待服务端窗口显示启动成功，并保持该窗口开启。
4. 双击 `启动客户端.bat`。
5. 在登录页面输入固定账号。四个账号的统一密码均为 `123456`：

| 登录标识 | 身份 | 演示用途 |
| --- | --- | --- |
| `DEMO_BUYER` | 学生买家 | 浏览、购买、支付、订单、申请开店 |
| `DEMO_OTHER_BUYER` | 学生买家 | 订单隔离、购买新店主商品 |
| `DEMO_TEACHER` | 教师申请人 | 查看驳回原因、编辑并重新提交 |
| `DEMO_ADMIN` | 管理员 | 审核申请、管理店铺、维护商品/SKU |

客户端默认连接本机 `127.0.0.1:19090`。

## 五、固定演示数据

每次启动服务端都会重建 5 家营业店铺和 100 件在售商品：文具 10、图书 30、生活用品 45、药品 5、其他 10。商品/SKU、库存、销量和订单均为确定性数据。

本版不升级图片数据库：`coverImageUrl` 保持空值，商品卡使用“暂无图片”占位，发布包不携带默认商品图片。

## 六、关闭 Demo

先关闭客户端窗口，再关闭服务端命令窗口。下次启动服务端时，Demo 数据会恢复为固定测试数据。

## 七、目录说明

```text
vCampus-Shop-Demo/
├─ 启动服务端.bat
├─ 启动客户端.bat
├─ 使用说明.txt
├─ lib/
├─ config/
├─ database/
└─ logs/             首次运行后自动创建
```

## 八、常见问题

### Address already in use

本机已经有程序占用 `19090` 端口，最常见的原因是之前启动的 Shop Demo 服务端仍在运行。关闭旧服务端窗口后重新双击 `启动服务端.bat`。

### 客户端无法连接

确认服务端已经启动且窗口没有退出；启动顺序必须是先服务端、后客户端。

### Java 版本错误

确认 `java -version` 显示的主版本不低于 21，并确认双击 BAT 时使用的 `PATH` 已包含正确的 Java。

### 查看日志

程序首次运行后会在解压目录下创建 `logs` 文件夹。服务端运行日志位于 `logs/server.log`，Shop 业务日志位于 `logs/business.log`。

## 九、联机测试说明

便携包中的客户端 BAT 默认连接本机地址，适合单机验收。需要通过 Tailscale 连接另一台电脑上的服务端时，请按照 [`SHOP_AUTH_DEMO_TEAM_TESTING.md`](../vcampus-database/demo/SHOP_AUTH_DEMO_TEAM_TESTING.md) 中的联机测试方式从源码启动客户端，并传入服务端地址。
