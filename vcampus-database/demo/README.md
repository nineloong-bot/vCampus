# Shop-only Demo

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
