# 转专业按招生专业独立终审设计

## 目标

将现有“批次＋目标学院”终审改为“批次中的招生专业 `optionId`”独立终审、回退和生效。一个学院有多个招生专业时，学院管理员必须分别操作；其中一个专业的状态不影响同学院其他专业。

终审必须有实际待终审申请。目标专业没有任何转入申请，或没有 `ASSESSED` 申请时，客户端禁用终审按钮，服务端也必须拒绝空终审。

## 作用域与授权

客户端只提交 `optionId` 和该专业终审状态的预期版本。服务端从认证主体解析管理员学院，然后在事务内重新加载招生专业，并要求 `option.targetDepartmentId` 等于该学院。

因此：

- 数学学院管理员只能终审数学学院招生专业。
- 计算机学院管理员可分别终审人工智能、计算机科学与技术、软件工程。
- 任何按批次或学院猜测操作目标的路径都不再用于终审写操作。

## 数据模型与迁移

新增 `tblMajorTransferOptionFinalization`，以 `optionId` 为主键，保存专业终审状态、行版本、终审人/时间、生效人/时间及创建/更新时间。状态为 `PROCESSING`、`REVIEWED`、`EFFECTIVE`。`optionId` 已唯一确定批次、目标学院和目标专业，不重复存储这些字段。

新建数据库不再创建 `tblMajorTransferBatchCollege`。现有数据库启动时通过幂等 schema 安装创建新表；旧学院状态表保留但不再读写，避免破坏用户当前 Access 数据。专业状态首次创建时根据申请现状恢复：有 `EFFECTIVE` 申请则为 `EFFECTIVE`，有 `PENDING_EFFECTIVE` 申请则为 `REVIEWED`，否则为 `PROCESSING`。

`tblMajorTransferPreparedTransfer` 继续以申请为唯一准备记录。新增通过申请关联 `optionId` 查询和删除准备记录的仓储方法，禁止回退一个专业时删除同学院其他专业的准备数据。

## 就绪判定与终审

专业就绪判定只统计指定 `optionId` 的正式申请：

1. 全校批次必须为 `CLOSED`。
2. 至少存在一份非草稿转入申请。
3. 不得存在 `SUBMITTED`、`SOURCE_APPROVED`、`QUALIFIED`或其他尚未完成考核的申请。
4. 至少存在一份 `ASSESSED` 申请。全部已驳回或取消时不执行空终审。
5. 专业终审状态必须为 `PROCESSING`，版本必须匹配。

终审在同一事务内按该招生专业的名额和成绩排名，规划目标班级，写入准备记录，将拟录取申请改为 `PENDING_EFFECTIVE`，超出名额的申请改为 `REJECTED`，最后把专业状态改为 `REVIEWED`。

## 回退与生效

回退和生效同样以 `optionId` 为边界。

- 回退只恢复该专业的 `PENDING_EFFECTIVE` 申请，只删除该专业的准备记录，并把状态改回 `PROCESSING`。
- 生效只处理该专业的准备记录和待生效申请，重新检查学籍、版本、班级容量和培养方案，成功后进入 `EFFECTIVE`。
- 专业锁、申请锁和学生锁保证同一专业不能重复终审或生效，不同专业之间不共用状态。

## 协议与客户端

用专业级协议取代学院级协议：

- `MajorTransferOptionReadinessView`
- `FinalizeMajorTransferOptionCommand`
- `EffectiveMajorTransferOptionCommand`
- `RollbackMajorTransferOptionCommand`
- 对应的专业终审、生效和回退结果

学院处理页在终审区增加“终审专业”下拉框，数据只来自当前管理员学院的招生选项。切换专业后单独加载该专业就绪状态，按钮文案改为“终审所选专业”、“生效所选专业”和“回退所选专业”。

没有转入申请时显示“该专业暂无转入申请”，三个按钮均禁用。应用列表仍保留学院的转出和转入审批视图，不因终审专业下拉框而隐藏转出待审申请。

## 学生可选专业

保留同学院转专业能力。学生工作台返回当前批次所有启用、名额大于零且不是学生当前专业的招生选项，不按学院过滤。添加回归测试，要求计算机学院学生同时看到本学院的人工智能/软件工程和数学学院的数学与应用数学。

批次关闭后不允许新建或修改草稿，但客户端必须显示明确原因“报名已截止，不能修改目标专业”，避免被误解为跨学院专业被隐藏。

## 并发、错误和安全

写命令继续经过请求去重。固定锁顺序为批次、招生专业终审状态、申请 ID、学生 ID、学号序列。授权、批次状态、申请状态、专业归属、名额、准备快照和乐观版本均在同一事务内重新检查。

新增业务错误：

- `TRANSFER_OPTION_NO_APPLICATIONS`
- `TRANSFER_OPTION_NO_ASSESSED_APPLICATIONS`
- `TRANSFER_OPTION_HAS_UNRESOLVED_APPLICATIONS`
- `TRANSFER_OPTION_NOT_REVIEWED`
- `TRANSFER_OPTION_ALREADY_EFFECTIVE`
- `TRANSFER_PREPARATION_STALE`

客户响应不包含 SQL、数据库路径或内部异常信息。

## 测试与交付

按 TDD 分层覆盖：

- 同一学院多专业可独立终审、回退和生效。
- 一个专业的终审不修改其他专业的申请或准备记录。
- 数学管理员无法访问计算机招生专业的终审命令。
- 零申请、零 `ASSESSED` 申请、存在未处理申请时均不可终审。
- 学生可选列表同时包含同学院和跨学院专业，排除当前专业、未启用选项和零名额选项。
- 客户端专业选择、就绪提示和按钮状态。
- 旧数据库启动迁移、新数据库重建、生成 SQL、计数快照和发行数据库一致性。

实现时不直接覆盖当前已有未提交修改的 `vcampus-distribution/data/vCampus.accdb`。数据库变更先在临时路径重建和验证，再单独确认如何处理用户当前运行数据。
