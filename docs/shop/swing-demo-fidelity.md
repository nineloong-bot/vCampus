# Swing 展示层对照验收（2026-09-14）

以 `prototypes/shop-buyer` 为设计依据，商城 Swing 展示层采用统一字体、绿色主题、圆角卡片、居中遮罩弹层和固定操作区。

## 已落实

- 买家首页：品牌导航、商品卡片、多选提示、底部分页。
- 我的：余额卡片与纵向功能入口；钱包、充值、开店申请及申请状态页面统一排版。
- 商品详情和购物车：规格与数量控件、右侧勾选、全选、固定结算区；长列表在内容区域滚动。
- 店主和管理员：侧栏分区、列表管理、表单弹层；商品编辑、导入和订单页面统一结构。
- 弹层支持关闭和 Escape，保留异步请求完成后的状态恢复，并限制弹层焦点循环。

继续使用现有 Socket 服务和真实业务数据。截图中的商品、金额随测试数据而变化。系统文件选择器及通用确认对话框仍使用 Swing 原生窗口。

## 验证

执行 `mvn '-Dtest=edu.seu.vcampus.client.**' '-Dsurefire.failIfNoSpecifiedTests=false' package javadoc:aggregate`：613 项客户端测试，0 失败、0 错误，18 项跳过；构建和 JavaDoc 成功。

验收测试通过真实 Socket 与独立 Access 测试库，串联订单和资金流程，并将实际 Swing 组件渲染为截图。检查了常规尺寸和较矮窗口中的“我的”页面，以及多行购物车、数量控件和表单滚动。此次统计是客户端回归，不代表重新运行全部服务端测试。

截图保存在 [对照目录](../ui-review/swing-demo-fidelity/)：

- [我的](../ui-review/swing-demo-fidelity/my.png)
- [买家首页](../ui-review/swing-demo-fidelity/buyer.png)
- [购物车](../ui-review/swing-demo-fidelity/cart.png)
- [钱包](../ui-review/swing-demo-fidelity/wallet.png)
- [店主商品管理](../ui-review/swing-demo-fidelity/seller-products.png)
- [管理员店铺管理](../ui-review/swing-demo-fidelity/admin-shops.png)

新版客户端位于 `vcampus-distribution/lib/vCampusClient.jar`。退出旧客户端后，运行 `vcampus-distribution/scripts/start-client.bat` 查看。

细节回归：店铺举报按钮按内容宽度显示；数量框使用单层输入边框；搜索切换商品和店铺时保持按钮尺寸。`ControlSizingTest` 覆盖这些布局约束，相关21项回归通过。
