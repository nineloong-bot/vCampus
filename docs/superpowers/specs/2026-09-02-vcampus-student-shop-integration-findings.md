# vCampus 学籍与商城联合集成问题记录

**记录日期：** 2026-09-02  
**检查分支：** `SHOP`  
**检查提交：** `97e68af`（将 `origin/nineloong@ae90971` 合并到 `SHOP@4632002`）  
**检查范围：** 正式登录入口、公共主界面、学籍模块、商城模块、标准数据库、权限与会话适配、启动和自动化测试。

## 1. 当前结论

本次 Git 合并没有产生文件级冲突，但“代码同时存在”不等于“模块已经在正式入口完成组合”。当前仓库同时包含 User、Student、Shop 的实现和数据库表结构，其中正式应用入口只接通 User 与 Student；Shop 仍主要通过专用 Demo 入口运行。标准数据库仅包含 User 与 Student 的种子数据，Shop 业务表为空。

因此，在完成正式服务端、正式客户端、权限和联合种子数据接线之前，不能把当前状态认定为“登录 + 学籍 + 商城”的完整联合版本。

## 2. 已确认问题

### INTEGRATION-001：正式登录流程与商城 Demo 登录流程是两套入口

**现象：**

- 标准客户端脚本启动 `ClientMain`，由 `UserUiCoordinator` 打开公共 `LoginFrame`。
- 商城 Demo 脚本启动 `ShopAuthDemoClientMain`，自行组织登录成功、会话失效和商城安装流程。
- 用户看到的登录与登录后页面取决于启动了哪一个入口，而不是 Git 自动从两个分支中选择某个界面。

**形成原因：**

- `origin/nineloong` 修改了标准入口相关的 `ClientMain`、`UserUiCoordinator` 和 `MainFrame`，以接入学籍模块。
- `SHOP` 将商城接在 `ShopAuthDemoClientMain` 与 `ShopUiInstaller` 上，没有将商城服务和安装流程接入标准 `ClientMain`。
- 两侧没有修改同一组接线代码，合并时没有冲突，Git 也无法推断产品层面的组合意图。

**影响：** 当前无法仅通过一次标准启动保证进入“指定登录界面 + 真实学籍 + 真实商城”的统一客户端。

**待确认：** 最终保留的登录界面版本、首次改密流程、退出流程和会话失效后的返回行为。

### INTEGRATION-002：标准客户端只接通学籍，商城入口仍是占位页

**证据：**

- `ClientMain` 只创建 `UserClientService` 和 `StudentClientService`。
- `MainFrame` 通过 `StudentModulePageFactory` 注册真实学籍页面。
- `MainFrame` 对 `shop` 注册的仍是 `ModulePlaceholderPage`。
- `ShopUiInstaller` 已提供向公共 `MainFrame` 安装商城的扩展点，但标准登录成功流程没有调用它。

**影响：** 使用 `start-client.bat` 可以进入学籍模块，但不能据此证明真实商城已在同一客户端可用。

**预留接口判断：** 客户端已经留出可复用的 `ShopUiInstaller` 和 `MainFrame` 命名组件扩展点，具备对接基础；缺少正式入口的依赖创建、安装与生命周期接线。

### INTEGRATION-003：标准服务端只注册 User 与 Student，未注册 Shop handlers

**证据：**

- `ServerMain` 创建并注册 `UserHandlers`。
- `ServerMain` 创建 `StudentHandlers` 并调用 `register(router)`。
- `ServerMain` 没有创建 Shop repository、service、handler，也没有把 Buyer、Seller、Admin Shop handlers 注册到同一 `MessageRouter`。
- Shop Demo 的 `ShopAuthDemoRuntime` 已包含一套完整商城运行时组装，但尚未提取或复用于正式 `ServerMain`。

**影响：** 即使客户端安装真实商城页面，标准服务端也无法处理相应的 `SHOP_*` 命令。

**预留接口判断：** 服务端已有 `FoundationShopUserAdapter`，可以把统一会话转换为商城用户；商城 handlers 也按注册器形式存在。接口基础已经具备，缺少正式运行时组合。

### INTEGRATION-004：标准数据库与商城 Demo 数据库相互独立

**标准数据库：** `vcampus-distribution/data/vCampus.accdb`

- 由 `vcampus-database/schema` 和 `vcampus-database/seed` 初始化。
- 包含 User、Student、Shop 表结构。
- 包含 1 名管理员、1 名教师、12 名学生，以及院系、专业、班级和学籍资料。
- 不包含商城店铺、商品种类、购物车、订单、支付和开店申请种子数据。

**商城 Demo 数据库：** `vcampus-database/demo/vcampus-shop-auth-demo.accdb`

- 由商城 Demo 初始化流程维护。
- 包含 `DEMO_ADMIN`、`DEMO_BUYER`、`DEMO_OTHER_BUYER`、`DEMO_TEACHER` 等固定账号和商城业务数据。
- 不等同于标准联合数据库。

**影响：** 用标准账号登录时商城表为空；用商城 Demo 数据库和入口时又不能据此验证标准学籍集成。当前没有一份可同时支持两个模块人工验收的联合种子数据库。

### INTEGRATION-005：学籍与商城采用不同的管理员授权判定

**统一 User 层已有能力：**

- 登录结果 `LoginResult` 同时返回 `UserView` 和权限集合。
- `SessionRegistry` 保存用户身份、权限集合和首次改密受限状态。
- `AuthorizationService.requirePermission` 可以对任意权限码执行统一校验。

**学籍模块：**

- 使用 `StudentAuthorizationPort` 将统一会话投影为 `StudentPrincipal`。
- 管理操作主要检查 `STUDENT_WRITE`；部分读取同时允许 `ADMIN` 角色或该权限。
- 当前 seed 已将 `STUDENT_WRITE` 授予 `ADMIN`。

**商城模块：**

- `FoundationShopUserAdapter` 将基础角色直接映射为 `STUDENT`、`TEACHER` 或 `ADMINISTRATOR`。
- 商城管理员能力当前主要依据 `UserRole.ADMIN` 映射，而不是独立的 `SHOP_ADMIN` 权限。
- 普通学生和教师被映射为可购买、可申请开店的商城用户；店主资格由商城审批产生，不改变基础角色。

**差异与风险：**

- 当前唯一管理员能够获得学籍管理权限，也能在商城适配器中被识别为管理员，但两者的授权依据不同。
- 如果未来需要让某个管理员只管理一个模块，或对权限进行审计和最小授权，角色直判无法表达该差异。
- 若仅新增 `SHOP_ADMIN` seed 而不调整商城服务端检查，新增权限不会实际生效。

**待确认决策：** 商城管理是否继续绑定所有 `ADMIN`，还是新增 `SHOP_ADMIN` 并由统一 `AuthorizationService` 强制校验。推荐后者，同时让默认总管理员拥有 `STUDENT_WRITE` 与 `SHOP_ADMIN`。

### INTEGRATION-006：联合测试数据缺少教师与跨模块业务场景

**当前数据：**

- 只有 1 个教师登录账号 `TEACHER01`。
- 学籍主体数据全部是学生，没有覆盖多教师状态或教师参与商城的差异场景。
- Shop 标准表为空。

**需要补充的数据类型：**

- 普通教师、待审核开店教师、已获批店主教师。
- 普通学生、学生店主、存在购物车和订单的学生。
- 正常营业与暂停营业店铺。
- 待处理、已通过、已驳回、草稿开店申请。
- 草稿、已下架、已上架商品，以及多商品种类、库存和价格差异。
- 部分勾选购物车、待支付、已支付、已完成订单。
- 同一个总管理员同时操作学籍管理与商城管理的验收数据。

**待确认：** 教师和店铺数量、是否需要模块专属管理员、订单历史规模及可重复初始化规则。

### INTEGRATION-007：标准启动依赖数据库文件，但缺少首次初始化脚本

**现象：**

- `start-server.bat` 要求 `vcampus-distribution/data/vCampus.accdb` 已存在。
- Maven 打包会生成服务端和客户端 JAR，但不会生成标准数据库文件。
- 在数据库缺失时，服务端以“database.path 指向的 Access 数据库不存在”退出。
- `DatabaseInitializer` 可以生成数据库，但当前需要人工执行命令。

**影响：** 新检出的联合版本不能直接通过标准启动脚本完成首次运行，容易被误判为代码启动失败。

**待设计：** 提供明确、可重复且不会静默覆盖人工测试数据的初始化或重置脚本。

### INTEGRATION-008：合并后存在学籍客户端测试失败

**全量验证：** `mvn verify`

- Common：21 项通过。
- Server：256 项通过。
- Client：308 项中 1 项失败、1 项错误、7 项明确跳过。

**失败内容：**

- `AdminStudentProfilePanelTest.teacherViewerKeepsLimitedDetailWithoutSensitiveFieldsOrEditing`：教师只读页面不创建身份证件号组件，但测试仍直接读取该组件。
- `StudentSearchPanelTest.emptyResultsShowMessage`：全量运行时出现异步响应顺序干扰；单独运行相关测试时该项通过，同时全量日志出现异步返回类型错位异常。

**当前决定：** 用户要求暂不修改。它们不阻止跳过测试打包和服务端启动，但联合版本在修复并通过全量回归前不能标记为完整验收通过。

## 3. 联合数据设计草案（尚未确认）

建议标准联合数据库至少包含以下角色和业务关系：

| 人员/主体 | 数量 | 主要状态与用途 |
| --- | ---: | --- |
| 总管理员 | 1 | 同时拥有学籍与商城管理权限，验证跨模块导航和授权 |
| 普通教师 | 1 | 可查询允许查看的学籍信息，可购物，无开店申请 |
| 待审核教师 | 1 | 有待处理开店申请，供管理员审核 |
| 教师店主 | 1 | 拥有正常营业店铺、商品和卖家订单 |
| 普通学生 | 8–12 | 保留现有多院系、多专业、多状态学籍数据 |
| 学生店主 | 2 | 分别对应正常营业和暂停营业店铺 |
| 店铺 | 3 | 教师店铺、学生正常店铺、学生暂停店铺 |
| 商品 | 每店 3–5 个 | 覆盖草稿、已下架、已上架 |
| 商品种类 | 每商品 1–3 个 | 覆盖颜色、规格、价格和库存差异 |
| 购物车与订单 | 每类至少 1 组 | 覆盖部分选择、待支付、已支付、已完成 |

所有测试账号应使用明确标注的本地测试密码，固定 UUID 和稳定业务编号；初始化结果必须可重复，且不得与真实人员或真实支付数据混用。

## 4. 后续设计检查点

1. 确认最终登录界面和唯一正式客户端入口。
2. 确认商城管理员使用独立权限码还是继续绑定 `ADMIN` 角色。
3. 确认联合测试数据规模和教师、学生店主分布。
4. 设计正式 `ServerMain` 的 Shop 运行时装配。
5. 设计正式 `ClientMain` 的 Shop service、页面安装与统一会话生命周期。
6. 设计联合数据库初始化、重置及人工测试说明。
7. 修复已记录的学籍测试问题并执行全量回归。
8. 完成人工验收后，再决定是否提交和推送联合版本。
