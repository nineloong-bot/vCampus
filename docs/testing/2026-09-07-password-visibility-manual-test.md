# vCampus 密码显示按钮手动测试

**记录日期：** 2026-09-07\
**测试对象：** `sTeven44` 工作区整合后的统一客户端\
**更新来源：** `E:\summer-school\vCampus\download\user-management\vCampus-feat-user-management`\
**测试范围：** 登录、首次修改密码、账户修改密码、申请教师账户，共 9 个密码输入框。

## 启动

1. 关闭之前打开的客户端；如果旧服务端仍在运行，先在其窗口按 `Ctrl+C` 停止。
2. 打开 `E:\summer-school\vCampus\.worktrees\shop-auth-demo\vcampus-distribution\scripts`。
3. 双击 `start-server-with-data.bat`，等待服务端监听 `8888`，保持窗口开启。
4. 双击 `start-client.bat`，打开本次编译的客户端。

测试沿用当前数据库和账号密码。账号曾修改密码时，请使用修改后的密码。确认运行的是上述整合目录中的程序。

## 1. 登录密码显示与隐藏

1. 在登录页密码框输入一段测试文字，例如 `EyeTest123`，暂不提交。
2. 确认默认显示掩码，右侧有眼睛图标；鼠标悬停提示“显示密码”。
3. 点击眼睛：应显示刚才输入的完整文字，图标变化，悬停提示“隐藏密码”。
4. 在显示状态继续输入、删除字符，再次点击眼睛：应恢复掩码，输入内容保持不变。
5. 连续切换数次，确认输入框、按钮无挤压或遮挡，页面没有新增错误提示。
6. 清除测试文字，输入有效账号和当前密码，在显示状态下登录，确认能进入统一主界面。
7. 退出登录后，确认新登录页面的密码框恢复默认隐藏；再以隐藏状态登录一次。

## 2. 账户修改密码

1. 登录后进入“账户设置”，点击“修改密码”。
2. 检查旧密码、新密码、确认密码三个输入框均有眼睛按钮，且默认隐藏。
3. 分别输入文字，只点击其中一个眼睛，确认另外两个输入框保持原状态。
4. 检查每个输入框均可独立显示和隐藏，文字不被清空或改写。
5. 用测试账号填写正确旧密码及一致的新密码并提交；新密码需为 8–64 位且包含字母和数字。
6. 确认修改成功后可以用新密码重新登录。记录测试账号的新密码，供后续测试使用。

## 3. 首次修改密码

1. 使用尚未修改初始密码的测试账号登录，进入首次改密弹窗。
2. 检查旧密码、新密码、确认密码三个输入框均默认隐藏，并能独立切换。
3. 完成首次改密，再使用新密码登录，确认原有流程正常。

若当前账号都已完成首次改密，本项留待有合适测试账号时执行；无需为测试按钮重置整个数据库。

## 4. 申请教师账户

1. 在登录页点击“申请教师账户”。
2. 检查密码和确认密码两个输入框均默认隐藏，并各自带有眼睛按钮。
3. 分别输入文字，独立切换显示和隐藏，确认另一个输入框不受影响。
4. 关闭弹窗并重新打开，确认两个输入框重新默认隐藏。

本项检查按钮即可，无需提交新账户申请。

## 5. 键盘与回归检查

- 使用 `Tab` 移动至眼睛按钮，按空格键，确认能切换显示状态且不会触发表单提交；检查焦点是否容易辨认。
- 登录后确认学籍、选课、图书馆、商城等原有模块入口仍可进入。
- 记录异常所在页面、输入框、操作顺序、实际结果与预期结果。

## 本次整合文件

- 新增共用组件 `PasswordFieldWithVisibilityToggle.java`。
- 更新 `LoginFrame.java`、`InitialPasswordChangeDialog.java`、`ChangePasswordDialog.java`、`TeacherAccountApplicationDialog.java`。
- 导入 `PasswordVisibilityToggleUiTest.java`，覆盖 9 个输入框的默认状态、独立切换、密码内容和提示文字。

## 构建命令

在整合项目根目录执行：

```powershell
mvn -o '-Dmaven.repo.local=C:\Users\35593\.m2\repository' clean verify
```

发行文件输出至 `vcampus-distribution\lib\vCampusClient.jar` 和 `vcampus-distribution\lib\vCampusServer.jar`。

## 本次验证结果

- 导入前，新增按钮测试因找不到眼睛按钮失败；导入后 3 项按钮测试全部通过。
- 共用模块全量测试：43 项通过。
- 服务端全量测试：507 项，503 项通过、2 项失败、2 项跳过。
- 客户端首次全量测试：550 项，532 项通过、18 项跳过。
- 清理构建缓存后的客户端全量复测：550 项，531 项通过、1 项失败、18 项跳过。
- 最终用户模块验证：52 项全部通过，并成功生成客户端和服务端发行 JAR；已检查客户端 JAR 包含新组件。
- 导入的 6 个源码与测试文件均与下载版本一致。原有问题记录、数据库及受保护的未提交修改通过哈希核对，内容未变。

最终成功打包命令（使用清理后重新编译的源码，运行全部客户端用户模块测试）：

```powershell
mvn -o '-Dmaven.repo.local=C:\Users\35593\.m2\repository' '-Dtest=edu/seu/vcampus/client/user/**' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

全量验证发现以下本次未改动文件中的问题，尚未达到全项目测试全部通过：

| 测试 | 现象与定位 |
| --- | --- |
| `CourseRepositoryTest.storesAndOptimisticallyUpdatesSelectionPhase` | 保存后时间戳为微秒精度，测试使用含纳秒部分的时间戳做精确相等断言，导致失败。 |
| `DemoDistributionAccountsTest.distributionDatabaseContainsTheVerifiedUnifiedCampusAccounts` | 当前保留数据库中的密码未通过测试所预设初始密码的校验。 |
| `StudentSearchPanelTest.searchResultsRenderIntoTable` | 测试替身按单一队列提供部门列表和搜索结果，而两项请求异步调度，可能取到对方响应，出现类型转换异常及表格等待超时；首次通过，清理后复测失败。 |

上述问题已保留证据，未在本次密码显示功能整合中修改。
