# vCampus 商店模块功能图

本文档描述 vCampus 多商户商城的主要业务流程、角色权限、跨店订单结构和核心状态变化。Shop 模块依赖 Foundation 提供的鉴权、消息通信、事务和资源锁能力，但不负责实现这些公共基础设施。

## 1. 买家购物主流程

```mermaid
flowchart TD
    A["进入校园商城"] --> B["浏览首页 / 搜索商品"]
    B --> C["价格筛选与商品排序"]
    C --> D["查看商品详情"]
    D --> E["进入店铺主页"]
    E --> D
    D --> F["选择可售 SKU"]
    F --> G["加入购物车"]
    G --> H["选择需要结算的商品"]
    H --> I{"服务端校验"}

    I -->|"商品失效"| I1["提示商品不可购买"]
    I -->|"库存不足"| I2["提示库存不足"]
    I -->|"价格发生变化"| I3{"接受最新价格？"}
    I3 -->|"否"| G
    I3 -->|"是"| J
    I -->|"校验通过"| J["创建跨店订单组"]

    J --> K["按店铺拆分子订单"]
    K --> L["预留 SKU 库存 15 分钟"]
    L --> M["打开模拟收银台"]
    M --> N{"支付结果"}

    N -->|"支付失败"| O["记录失败尝试<br/>支付单仍为 PENDING"]
    O --> M
    N -->|"用户取消"| P["取消支付并释放预留库存"]
    N -->|"超过预留时间"| Q["支付过期并释放预留库存"]
    N -->|"支付成功"| R["扣减实际库存<br/>更新商品销量"]
    R --> S["全部子订单变为 PAID"]
    S --> T["店主备货 PREPARING"]
    T --> U["店主发货 SHIPPED"]
    U --> V["买家确认收货 COMPLETED"]
    V --> W{"全部子订单已完成？"}
    W -->|"否"| U
    W -->|"是"| X["订单组变为 COMPLETED"]
```

客户端负责展示页面和发起操作，商品状态、实时价格、可售库存及操作权限均由服务端重新校验。结算时不会立即扣减库存，而是先预留 15 分钟；只有支付成功后才正式扣减实际库存。

## 2. 商城角色与功能

```mermaid
flowchart LR
    User["vCampus 用户账户"]

    User --> Buyer["买家能力<br/>学生 / 教师"]
    User --> Apply["提交店主申请"]
    Apply --> Review{"管理员审核"}
    Review -->|"驳回"| Draft["修改申请并重新提交"]
    Draft --> Apply
    Review -->|"通过"| Seller["增加店主能力"]

    Buyer --> B1["浏览和搜索商品"]
    Buyer --> B2["购物车与跨店结算"]
    Buyer --> B3["模拟支付"]
    Buyer --> B4["查看订单与确认收货"]

    Seller --> S1["维护店铺资料"]
    Seller --> S2["管理自己的商品和 SKU"]
    Seller --> S3["管理库存"]
    Seller --> S4["处理自己的店铺订单"]

    Admin["管理员"] --> A1["审核店主申请"]
    Admin --> A2["停用 / 恢复店铺"]
    Admin --> A3["查询平台订单"]
    Admin --> A4["查询模拟支付记录"]

    Foundation["Foundation 公共能力"]
    Foundation -.->|"鉴权、消息通信、事务、锁"| Buyer
    Foundation -.->|"提供公共接口"| Seller
    Foundation -.->|"提供公共接口"| Admin
```

- 学生和教师天然拥有买家能力。
- 店主不是新的基础角色，而是管理员审批后附加的业务能力。
- 店主只能操作自己的店铺、商品和订单。
- Shop 模块只调用 Foundation 提供的公共接口，不实现 Foundation 本身。

## 3. 跨店订单拆分

```mermaid
flowchart TB
    Cart["用户购物车"]

    Cart --> Item1["商品 A × 2<br/>店铺甲"]
    Cart --> Item2["商品 B × 1<br/>店铺甲"]
    Cart --> Item3["商品 C × 3<br/>店铺乙"]

    Item1 --> Checkout["一次统一结算"]
    Item2 --> Checkout
    Item3 --> Checkout

    Checkout --> Group["OrderGroup<br/>订单组"]

    Group --> OrderA["Order：店铺甲子订单"]
    Group --> OrderB["Order：店铺乙子订单"]

    OrderA --> A1["OrderItem：商品 A 快照"]
    OrderA --> A2["OrderItem：商品 B 快照"]
    OrderB --> B1["OrderItem：商品 C 快照"]

    Group --> Payment["Payment<br/>聚合支付单"]
    Payment --> Attempt1["PaymentAttempt 1<br/>失败"]
    Payment --> Attempt2["PaymentAttempt 2<br/>成功"]

    Payment -->|"成功后统一更新"| OrderA
    Payment -->|"成功后统一更新"| OrderB
```

跨店订单采用三级结构：

- `OrderGroup`：代表买家的一次统一结算。
- `Order`：按照店铺拆分的子订单，每个店主只能处理自己的子订单。
- `OrderItem`：保存下单时的商品名称、SKU、价格和店铺名称快照。

整个订单组只对应一个聚合支付单。支付失败不会重新创建订单或重复预留库存，只会追加一条支付尝试记录，并允许用户在库存预留期内重试。

## 4. 商城核心状态流转

```mermaid
stateDiagram-v2
    state "店主申请" as Application {
        [*] --> DRAFT
        DRAFT --> PENDING: 申请人提交
        PENDING --> APPROVED: 管理员通过
        PENDING --> REJECTED: 管理员驳回
        REJECTED --> DRAFT: 申请人修改
    }

    state "店铺" as Shop {
        [*] --> ACTIVE: 申请审核通过
        ACTIVE --> SUSPENDED: 管理员停用
        SUSPENDED --> ACTIVE: 管理员恢复
    }

    state "商品" as Product {
        [*] --> PRODUCT_DRAFT
        PRODUCT_DRAFT --> PRODUCT_ACTIVE: 满足上架条件
        PRODUCT_ACTIVE --> PRODUCT_INACTIVE: 店主下架
        PRODUCT_INACTIVE --> PRODUCT_ACTIVE: 重新上架
    }

    state "支付单" as Payment {
        [*] --> PAYMENT_PENDING
        PAYMENT_PENDING --> PAYMENT_PENDING: 支付失败，可重试
        PAYMENT_PENDING --> PAYMENT_SUCCEEDED: 支付成功
        PAYMENT_PENDING --> PAYMENT_CANCELLED: 用户取消
        PAYMENT_PENDING --> PAYMENT_EXPIRED: 预留超时
    }

    state "子订单" as Order {
        [*] --> PENDING_PAYMENT
        PENDING_PAYMENT --> PAID: 支付成功
        PENDING_PAYMENT --> CANCELLED: 待支付时取消
        PAID --> PREPARING: 店主备货
        PREPARING --> SHIPPED: 店主发货
        SHIPPED --> COMPLETED: 买家确认收货
    }
```

关键状态规则：

- 待审核申请不可编辑；被驳回后，申请人修改时回到草稿状态。
- 店铺停用不会撤销已经批准的店主申请。
- 支付失败不会让聚合支付单进入失败终态，而是继续保持 `PENDING`，允许重试。
- 只有支付成功才扣减实际库存；取消或过期只释放预留库存。
- 订单组内全部店铺子订单完成后，订单组才变为 `COMPLETED`。

## 5. 模块边界

```mermaid
flowchart LR
    Client["Shop Swing 页面"] --> ClientService["Shop 客户端服务"]
    ClientService -->|"SHOP_* 消息命令"| Handler["Shop Handler"]
    Handler --> Service["Shop Service"]
    Service --> Repository["Shop Repository"]
    Repository --> Database[("050_shop 数据表")]

    Service -.-> Auth["AuthorizationPort"]
    Service -.-> Transaction["事务接口"]
    Service -.-> Lock["资源锁接口"]
    Service -.-> User["用户身份查询接口"]

    Foundation["Foundation / 其他成员负责"] --> Auth
    Foundation --> Transaction
    Foundation --> Lock
    Foundation --> User
```

Shop 负责人拥有商城相关的 Swing 页面、DTO、Handler、Service、Repository、数据库表、测试和文档。鉴权、Socket 通信、通用事务、资源锁和用户身份查询由公共模块提供，Shop 仅通过公开接口进行调用。

## 6. 设计依据

- `docs/superpowers/specs/2026-08-24-vcampus-shop-module-design.md`
- `docs/superpowers/plans/2026-08-24-vcampus-shop-module.md`
- `docs/superpowers/specs/2026-08-24-vcampus-overall-architecture-design.md`
- `docs/superpowers/plans/2026-08-24-vcampus-foundation.md`
