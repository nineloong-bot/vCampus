# 全模块批量测试数据

本目录保存可重复构建的合成数据快照、生成工具和手动测试说明。基准日期为 2026-09-07。

## 创建可运行测试包

在仓库根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File vcampus-database/demo/full-test-data/build-package.ps1
```

需要 Java 21，使用仓库已有的两个发行 JAR。默认输出为 `artifacts/vCampus-full-test-data-20260907`，已有同名目录时自动使用时间后缀。也可通过 `-OutputDirectory` 指定新目录。

脚本构建数据库、校验数据并生成 ZIP。包内依次运行 `start-server.bat` 与 `start-client.bat`，连接端口 18888。

**统一密码：`Test12345`**。管理员为 `TESTADMIN`，教师为 `TESTTEACHER001`～`TESTTEACHER050`，学生为 `213260001`～`213261000`。数据包含 10 个专业、2023～2026 四个年级、40 套培养方案、120 门课程和 290 个教学班；教学学期覆盖暑期、秋季和春季。

## 内容

- [完整使用说明](docs/使用说明.md)
- [账号清单](docs/测试账号清单.txt)
- [课程场景](docs/课程测试场景.md)
- [商城场景](docs/商城测试场景.md)
- [验证记录](docs/验证记录.txt)
- `tools/generated.sql`：固定数据快照，可直接导入新库。
- `tools/*.py`：按模块拆分的生成器，使用 Python 3 标准库；修改 `generate.py` 的日期或模块规模后重新生成快照。
- `tools/BuildDataset.java`、`ValidateDataset.java`：创建新库、校验数量及关联。
- `tools/SmokeDataset.java`：在副本上运行真实服务的读写测试，会执行选课、借还书、付款及编号递增。

重新生成 SQL 的命令：

```powershell
python vcampus-database/demo/full-test-data/tools/generate.py vcampus-database/demo/full-test-data/tools
```

日期相关场景会随时间变化：当前有效借阅初始到期日为 2026-09-27，待付款样例库存预留截至 2026-09-14 12:00。新增交易与借阅由程序当前策略计算。
