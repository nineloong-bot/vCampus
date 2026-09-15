# Shop-only Demo

## 全量学籍与选课数据

`full-test-data/tools/generate.py` 会生成 8 个学院、16 个专业、2400 名基础学生，
并额外生成 240 名计算机学院大一转专业场景。每个专业/年级培养方案覆盖 12 个学期，
第 1–8 学期均为 5 门、每门 3 学分，共 15 学分；课程目录包含 240 门课程，配套生成
秋季、春季和暑期教学班，可直接用于选课、退改选和重修测试。

## 转专业脏数据清洗

使用 `MajorTransferDataCleaner <database-path> --audit` 查看违规申请数量，确认备份后
使用 `--clean` 在事务中按附件、审核、执行记录、申请的顺序清理。工具不会写入
`tblUser`，清洗前后应运行 `audit/major_transfer_invalid_applications.sql` 验证。

在仓库根目录运行：

```powershell
mvn -pl vcampus-server -am package
java -cp vcampus-distribution/lib/vCampusServer.jar edu.seu.vcampus.server.shop.demo.ShopDemo
```

程序会重建 `vcampus-database/demo/vcampus-shop-demo.accdb`，然后依次执行商品浏览、
购物车、跨店结算和支付宝模拟支付。数据库会保留，可使用 Microsoft Access 查看。

也可以传入自定义数据库和 schema 目录：

```powershell
java -cp vcampus-distribution/lib/vCampusServer.jar edu.seu.vcampus.server.shop.demo.ShopDemo `
  E:\data\shop-demo.accdb vcampus-database\schema
```

注意：每次运行都会删除并重建指定的 Demo 数据库文件。
