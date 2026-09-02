# vCampus 虚拟校园本地测试包

本目录包含统一登录、学籍、课程、图书馆和校园商城。服务端统一监听 `8888`，所有模块共享同一登录会话与 Access 数据库。

## 启动

要求 Windows 与 JDK 21 或更高版本。进入 `vcampus-distribution/scripts`：

1. 双击 `start-server-with-data.bat`，看到“监听端口 8888”后保持窗口开启。
2. 双击 `start-client.bat`。
3. 停止时先关闭客户端，再在服务端窗口按 `Ctrl+C`。

也可在 PowerShell 中运行：

```powershell
cd E:\summer-school\vCampus\.worktrees\shop-auth-demo\vcampus-distribution\scripts
.\start-server-with-data.bat
# 另开一个 PowerShell
.\start-client.bat
```

## 测试账号

| 用途 | 账号 | 初始密码 | 说明 |
|---|---|---|---|
| 综合管理员 | `ADMIN` | `admin123` | 首次登录必须修改密码，然后重新登录；拥有用户、学籍和图书馆管理权限，并可审核商城申请 |
| 教师 | `TEACHER01` | `admin123` | 测试教师课程功能与普通图书检索 |
| 普通学生/买家 | `213230001` | `admin123` | 有完整学籍，购物车含两种颜色中性笔，并有待支付和已支付订单 |
| 店主 | `SHOPOWNER` | `admin123` | 已开店；商品含草稿、下架、上架及黑/红两种商品种类 |
| 申请草稿 | `SHOPDRAFT` | `admin123` | 开店申请为草稿 |
| 待审核申请 | `SHOPPENDING` | `admin123` | 开店申请待管理员审核 |

详细步骤见 [统一人工测试指南](../docs/testing/2026-09-02-vcampus-unified-manual-test-guide.md)。

## 重置与构建

`reset-data.bat` 会在确认后只删除 `data/vCampus.accdb`；再次启动服务端会按 `database/schema` 与 `database/seed` 重建测试库。

从源码更新 JAR：

```powershell
mvn -pl vcampus-server,vcampus-client -am package
```

发布 JAR 的主类分别是 `edu.seu.vcampus.server.bootstrap.ServerMain` 与 `edu.seu.vcampus.client.ClientMain`。
