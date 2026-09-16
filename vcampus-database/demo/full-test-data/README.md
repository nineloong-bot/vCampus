# vCampus 发布数据库重建工具

本目录是发行数据库的唯一规模化数据生成入口，数据基准时间为 2026-09-16 12:00。结构来自 `vcampus-database/schema`，角色权限来自 `seed/010_roles_permissions.sql`，其余业务数据全部由 `tools/generate.py` 生成。

## 数据范围

- 2 个学院、5 个专业、15 个班、120 名学生，学生一卡通严格覆盖 `213240001-213240040`、`213250001-213250040`、`213260001-213260040`。
- 8 个管理员账号、24 个教师账号；全部初始密码为 `123456`，学生首次登录必须改密。
- 15 套八学期培养方案、104 门课程，每门课程 2 个教学班；只开放 1 个学期，初始选课和选退记录为空。
- 5 条数学学院转入计算机科学与工程学院的已提交申请。
- 12 种图书、36 册馆藏、2 个明确逾期罚款账号。
- 6 家店铺、72 件商品、20 个店铺订单，以及购物车、资质、举报处置和共享钱包场景。

精确数量见 [counts.tsv](tools/counts.tsv)，登录及逾期账号见 [账号清单](tools/账号清单.md)，机器可读场景见 [scenarios.json](tools/scenarios.json)。

## 重建与验证

在仓库根目录执行：

```text
python3 vcampus-database/demo/full-test-data/tools/generate.py vcampus-database/demo/full-test-data/tools
mvn -pl vcampus-server -am -DskipTests package
```

随后使用临时目录运行 `BuildDataset.java`，再用 `ValidateDataset.java` 校验。构建器拒绝覆盖已存在文件，验证通过后才能替换 `vcampus-distribution/data/vCampus.accdb`。

Windows 独立包可运行：

```powershell
powershell -ExecutionPolicy Bypass -File vcampus-database/demo/full-test-data/build-package.ps1
```
