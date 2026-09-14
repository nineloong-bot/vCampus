# 订单与库存接口

新版命令前缀 `SHOP2_ORDER_`，包括 QUOTE、CHECKOUT、BUYER_LIST、SELLER_LIST、VALIDATE、PAY、CANCEL、REFUND_REQUEST、REFUND_APPROVE、REFUND_REJECT、SHIP、RECEIVE。请求身份来自服务端会话，写入使用 Message.requestId 持久化幂等回执。重复请求必须保留原请求编号和内容；调整付款后再次确认应使用新编号。

结算先 QUOTE 并展示当前数量及价格，经买家确认后 CHECKOUT。付款前 VALIDATE；PAY 在同一事务内再次检查，若新出现失效或超时，提交调整并返回提示，等待下一次确认，不扣款。库存、资金、订单状态与回执使用运行时共用 TransactionManager。店铺停业调用 cancelPendingForShop 参与原事务。OrderExpiryJob 在构造时补处理超时，此后每分钟处理一次，关闭运行时时 close。

状态为 PENDING_PAYMENT、PAID、SHIPPED、COMPLETED、REFUND_PENDING、REFUNDED、CANCELLED；列表筛选 ALL 包含全部，CLOSED 包含退款审核中、已退款和已取消。卖家响应仅有公开昵称缺省值“校园用户”。

旧版订单保留在原表，通过 LEGACY_ 状态前缀以只读快照展示。旧模拟付款没有新版待结算账户，不能自动建立资金或重复支付、退款、收货结算。列表提示“历史版本订单仅供查询，未迁移资金不得重复支付或结算”。正式迁移需要独立核实旧款项与库存；本模块不制造待结算余额。新版全量演示数据应通过新版订单流程生成。

新增的 053_shop_orders.sql 仅创建订单状态、明细有效性、请求回执、库存流水及订单历史扩展表；原 tblOrderGroup、tblOrder、tblOrderItem、tblProductSku 和 tblProduct 为权威业务数据。
