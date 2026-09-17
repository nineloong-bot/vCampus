# 学院级转专业终审与生效设计

## 目标

一个全校转专业批次可以包含多个目标学院。每个目标学院独立完成终审、回退和生效；某学院生效后只结束该学院在该批次中的转专业工作，不影响其他学院继续处理。学生申请状态机仍沿用 `ASSESSED -> PENDING_EFFECTIVE -> EFFECTIVE`，不增加学生状态。

本次同时修复批次可重新开放、回退无客户端入口、终审没有提前准备、就绪协议无法表达待生效状态，以及终审和生效共用含义失真的结果类型等问题。

## 数据模型

新增 `tblMajorTransferBatchCollege`，以 `(batchId, targetDepartmentId)` 唯一标识一个学院在全校批次中的执行状态。字段包括学院状态、乐观锁版本、终审人和时间、生效人和时间、创建及更新时间。

学院状态为：

- `PROCESSING`：学院仍在处理申请，可以执行终审。
- `REVIEWED`：学院已完成终审，可以撤销终审或生效。
- `EFFECTIVE`：学院已生效，为不可逆终态。

新增 `tblMajorTransferPreparedTransfer`，每份待生效申请最多一条准备记录，保存目标学院、目标专业、目标班级、目标年级、学生基准版本、申请版本和规划时间。准备记录不是正式学籍变更，只是生效计划快照。

`tblMajorTransferBatch` 只管理全校报名生命周期。合法状态转换为 `DRAFT -> OPEN -> CLOSED`；`CLOSED` 不可重新开放或修改报名时间。旧 `EFFECTIVE` 批次在数据重建时转换为 `CLOSED`，并为对应学院生成 `EFFECTIVE` 状态记录。

## 学院状态初始化

学院保存本学院第一个招生选项时，在同一事务中确保存在对应的批次学院状态记录。已有数据重建时，根据招生选项的 `targetDepartmentId` 为每个批次生成学院记录。

已生效学院不能新增、修改或重新启用招生选项。学生保存草稿和提交时，在事务中确认目标学院状态不是 `EFFECTIVE`。

## 终审

终审只处理当前认证学院的招生选项和申请。服务在固定资源锁顺序和同一 Access 事务中：

1. 锁定全校批次、批次学院状态、申请和学生。
2. 要求全校批次为 `CLOSED`，学院状态为 `PROCESSING`，学院版本匹配。
3. 确认该学院不存在 `SUBMITTED`、`SOURCE_APPROVED`、`QUALIFIED`、`EXECUTION_FAILED` 等未处理正式申请。
4. 对所有 `ASSESSED` 申请重新检查学生正式学籍、目标专业、目标年级、培养方案和可用班级。
5. 执行确定性的均衡分班规划，写入准备记录。
6. 将 `ASSESSED` 申请改为 `PENDING_EFFECTIVE`，写入终审审核记录。
7. 将学院状态改为 `REVIEWED`。

任一申请无法准备时整个学院终审回滚。全部申请均为 `REJECTED` 或 `CANCELLED` 时允许零人终审，学院仍进入 `REVIEWED`。

## 回退

回退只接受 `REVIEWED` 学院。服务删除准备记录，将该学院的 `PENDING_EFFECTIVE` 申请恢复为 `ASSESSED`，并把学院状态恢复为 `PROCESSING`。

既有终审审核记录不可删除。回退追加不可变的终审撤销审计记录，用于保留操作者、时间和原因。`EFFECTIVE` 学院不可回退。

## 生效

生效只接受 `REVIEWED` 学院。服务重新检查该学院没有未处理申请，并验证准备快照中的申请版本、学生版本、目标专业、目标班级、班级容量和培养方案仍然有效。

校验通过后，在同一事务中按准备记录正式分配学号、修改学生学籍、协调选课、写入学籍异动与执行审核，将申请改为 `EFFECTIVE`，最后将学院状态改为 `EFFECTIVE`。任何一步失败时整个学院回滚。

学院状态的 `REVIEWED -> EFFECTIVE` 乐观锁更新保证生效只能成功一次。重复生效返回 `TRANSFER_COLLEGE_ALREADY_EFFECTIVE`。

## 协议和界面

新增 `MajorTransferCollegeReadinessView`，字段包括批次、目标学院、学院状态、`assessed`、`pendingEffective`、`rejected`、`cancelled`、`unresolved`、是否可终审、是否可回退、是否可生效、阻塞原因和学院版本。

终审返回 `MajorTransferBatchReviewResult`，表达准备申请数量和学院状态。生效返回 `MajorTransferBatchEffectResult`，表达实际生效人数、退课数量和学院状态。回退返回独立结果，不再复用含义模糊的 `effectiveStudents` 字段。

学院工作台按钮规则：

- `PROCESSING`：显示“批次终审”。
- `REVIEWED`：显示“撤销终审”和“生效”。
- `EFFECTIVE`：按钮全部禁用，显示“本学院转专业已结束”。

学生端在 `PENDING_EFFECTIVE` 时继续显示最后一步进行中，仅在 `EFFECTIVE` 时显示完成。

## 授权、并发和错误

学院范围始终由服务端从认证主体解析。批次操作只查询和修改招生选项目标学院等于当前学院的数据，不接受客户端提供可信学院编号。

写命令继续经过请求去重。锁顺序为批次、批次学院、申请 ID、学生 ID、学号序列；业务规则和学院范围在同一事务内重新检查。

主要业务错误为：

- `TRANSFER_COLLEGE_HAS_UNRESOLVED_APPLICATIONS`
- `TRANSFER_COLLEGE_NOT_REVIEWED`
- `TRANSFER_COLLEGE_ALREADY_EFFECTIVE`
- `TRANSFER_PREPARATION_STALE`
- `TRANSFER_COLLEGE_REVIEW_NOT_REVERSIBLE`

## 数据重建

更新 schema、seed、完整测试数据生成器和数据库校验器。在临时路径重建 Access 数据库并验证后，才替换 `vcampus-distribution/data/vCampus.accdb`。生成 SQL、统计快照、发行数据库和用户说明必须保持同一数据版本。

## 测试

测试覆盖：同一批次多学院独立操作、学院范围隔离、未处理申请只阻止对应学院、终审准备但不修改学籍、准备失败整体回滚、回退保留审计、准备快照过期、生效原子性和一次性、批次关闭不可重新开放、已生效学院禁止招生和申请、零人终审生效、readiness 字段和客户端按钮组合、Handler 权限与请求去重，以及数据库重建后的结构和数据一致性。
