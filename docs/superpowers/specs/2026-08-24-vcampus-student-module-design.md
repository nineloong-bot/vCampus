# 虚拟校园学籍管理模块设计

## 1. 目标与范围

本模块维护院系、专业、班级、学生档案、联系方式和学籍状态，负责管理员录取新生时生成一卡通号和学号，并向选课模块提供资格查询。一次录取必须原子完成编号分配、学生用户创建、学生档案创建和审计。

本模块不维护课程、选课记录或成绩；学籍模块不依赖选课模块。一卡通号同时是学生登录标识，但账户、密码和会话仍由用户模块维护。

## 2. 角色权限

| 用例 | 学生 | 教师 | 管理员 |
|---|---:|---:|---:|
| 查看本人档案和一卡通号 | 是 | 否 | 是 |
| 修改本人联系方式 | 是 | 否 | 是 |
| 查询学生只读摘要 | 否 | 是 | 是 |
| 录取新生、转班、变更状态 | 否 | 否 | 是 |
| 维护院系/专业/班级 | 否 | 否 | 是 |

教师查询只返回一卡通号、学号、姓名、班级、专业和学籍状态，不返回电话等非必要联系方式。学生档案和登录账户由管理员在新生录取事务中统一创建。

## 3. 学籍状态与学生类型

学籍状态为 `ACTIVE`、`SUSPENDED`、`GRADUATED`、`WITHDRAWN`。只有 `ACTIVE` 具备选课资格。已毕业、休学和退学档案保留历史，不物理删除。

学生类型为 `UNDERGRADUATE`、`MASTER`、`DOCTORATE`，分别映射到一卡通号中的类型位 `1`、`2`、`3`。类型建档后不得直接修改；确需纠错时由管理员执行专用数据修复流程并保留审计，不在普通页面开放。

## 4. 编号规则

### 4.1 一卡通号

一卡通号固定为 9 位数字，格式为 `2T3YYNNNN`，校验表达式为 `^2[123]3[0-9]{6}$`。

| 位置 | 长度 | 含义 | 规则 |
|---|---:|---|---|
| `2` | 1 | 固定首位 | 必须为 `2` |
| `T` | 1 | 学生类型 | `1` 本科生、`2` 硕士生、`3` 博士生 |
| `3` | 1 | 固定第三位 | 必须为 `3` |
| `YY` | 2 | 入学年份 | 四位年份的后两位，支持 2000–2099 |
| `NNNN` | 4 | 录取顺序号 | 全校统一连续序号，`0001–9999` |

例：`213242478` 表示本科生、2024 级、全局录取顺序 2478。全局顺序在本科、硕士、博士以及不同入学年份之间共用，不按类型或年份重置。

### 4.2 学号

学号固定为 8 个字符，格式为 `PPPYYCSS`，校验表达式为 `^[0-9A-Z]{3}[0-9]{5}$`。

| 位置 | 长度 | 含义 | 规则 |
|---|---:|---|---|
| `PPP` | 3 | 专业代码 | 三个大写字母或数字，匹配 `^[0-9A-Z]{3}$` |
| `YY` | 2 | 入学年份 | 四位年份的后两位，支持 2000–2099 |
| `C` | 1 | 班号 | `1–9`，同专业、同年级内唯一 |
| `SS` | 2 | 班内顺序号 | 每个专业、年级、班级独立从 `01` 到 `99` |

普通计算机专业代码可为 `090`，计算机拔尖班可为 `09J`。例：`09024110` 表示专业 `090`、2024 级、1 班、班内 10 号；`09J24110` 表示专业 `09J` 的对应学生。

成功提交的一卡通号和学号永久占用，不得回收或复用。事务回滚的分配不视为成功占用，因此序列值必须随事务一起回滚。转班不自动更改已生成的学号；如以后增加管理员学号纠错功能，也不得修改一卡通号、`tblUser.loginId` 或学生登录方式。

## 5. Swing 页面

- `S-01 MyStudentProfilePanel`：显示一卡通号、学号、个人档案和联系方式。
- `S-02 StudentSearchPanel`：按一卡通号/学号/姓名、组织、状态分页查询。
- `S-03 StudentDetailPanel`：显示档案、编号拆解和学籍变更历史。
- `S-04 StudentAdmissionDialog`：管理员录取新生；选择类型、专业、年级、班级后预览编号规则，编号只在提交事务中正式生成。
- `S-05 UpdateContactDialog`
- `S-06 EnrollmentChangeDialog`
- `S-07 OrganizationManagementPanel`：维护院系、三字符专业代码、年级和 1–9 班号。

编辑页面必须显示当前 `rowVersion`，并发冲突后提示刷新。录取页面不得让管理员手工填写一卡通号、学号、`userId` 或初始密码；成功页一次性显示一卡通号、学号和“初始密码为 12345678，请首次登录后修改”的提示。

## 6. DTO

```java
enum StudentStatus { ACTIVE, SUSPENDED, GRADUATED, WITHDRAWN }
enum StudentType { UNDERGRADUATE, MASTER, DOCTORATE }

record CreateStudentAdmissionCommand(
        String studentName, String gender, String email, String phone,
        String majorId, String classId, int enrollmentYear,
        StudentType studentType) implements Serializable {}
record StudentAdmissionResult(
        StudentView student, String campusCardNumber,
        String studentNumber, boolean mustChangePassword)
        implements Serializable {}
record UpdateStudentContactCommand(String studentId, String email,
        String phone, long expectedVersion) implements Serializable {}
record UpdateStudentEnrollmentCommand(String studentId, String classId,
        LocalDate effectiveDate, String reason, long expectedVersion)
        implements Serializable {}
record ChangeStudentStatusCommand(String studentId, StudentStatus status,
        LocalDate effectiveDate, String reason, long expectedVersion)
        implements Serializable {}
record StudentSearchQuery(String keyword, String departmentId,
        String majorId, String classId, StudentStatus status,
        int page, int pageSize) implements Serializable {}
record StudentEligibility(String studentId, StudentStatus status,
        boolean eligible, String reason) implements Serializable {}
```

`CreateStudentAdmissionCommand.majorId` 必须与 `classId` 所属专业一致，`enrollmentYear` 必须与班级入学年份一致。服务端不得信任客户端预览编号。`StudentView` 和 `StudentSummary` 中的一卡通号来自 `UserQueryPort.findByUserId` 的只读身份查询，不在 `tblStudent` 重复存储；按一卡通号搜索时先调用 `findByLoginId` 定位 `userId`。

## 7. 服务与编号接口

```java
public interface StudentAdmissionService {
    StudentAdmissionResult admit(CreateStudentAdmissionCommand command,
                                   RequestContext requestContext);
}

public interface StudentService {
    StudentView getCurrentStudent(String sessionToken);
    StudentView getStudent(String studentId);
    PageResult<StudentSummary> searchStudents(StudentSearchQuery query);
    StudentView updateContact(UpdateStudentContactCommand command);
    StudentView updateEnrollment(UpdateStudentEnrollmentCommand command);
    StudentView changeStatus(ChangeStudentStatusCommand command);
}

public interface CampusCardNumberGenerator {
    String next(TransactionContext transaction,
                StudentType studentType, int enrollmentYear);
}

public interface StudentNumberGenerator {
    String next(TransactionContext transaction,
                String majorCode, int enrollmentYear, int classNumber);
}

public interface StudentQueryPort {
    StudentIdentity findByUserId(String userId);
    StudentEligibility getEnrollmentEligibility(String userId);
    boolean existsActiveStudent(String studentId);
}
```

`StudentQueryPort` 是选课模块的唯一学籍入口，不返回联系方式或完整档案。两个生成器只能在已开启的录取事务内调用，不得缓存未提交序号。

## 8. 消息合同

| 命令 | 请求 | 响应 | 权限 |
|---|---|---|---|
| `STUDENT_CREATE` | `CreateStudentAdmissionCommand` | `StudentAdmissionResult` | `STUDENT_WRITE` |
| `STUDENT_GET_CURRENT` | `EmptyRequest` | `StudentView` | 学生 |
| `STUDENT_GET` | `EntityIdRequest` | `StudentView` | 教师/管理员 |
| `STUDENT_SEARCH` | `StudentSearchQuery` | `PageResult<StudentSummary>` | 教师/管理员 |
| `STUDENT_UPDATE_CONTACT` | `UpdateStudentContactCommand` | `StudentView` | 本人/管理员 |
| `STUDENT_UPDATE_ENROLLMENT` | `UpdateStudentEnrollmentCommand` | `StudentView` | 管理员 |
| `STUDENT_CHANGE_STATUS` | `ChangeStudentStatusCommand` | `StudentView` | 管理员 |
| `STUDENT_LIST_DEPARTMENTS` | `ActiveOnlyQuery` | `List<DepartmentView>` | 已登录 |
| `STUDENT_LIST_MAJORS` | `ParentIdQuery` | `List<MajorView>` | 已登录 |
| `STUDENT_LIST_CLASSES` | `ParentIdQuery` | `List<ClassView>` | 已登录 |

所有写命令必须幂等。`STUDENT_CREATE` 只通过全局唯一的 `requestId` 去重；同一请求重放必须返回第一次提交的 `StudentAdmissionResult`，不得再次分配编号。

## 9. 数据库

### 9.1 `tblDepartment` 院系表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `departmentId` | 院系内部编号 | `VARCHAR(36)` | 主键；UUID |
| `departmentCode` | 院系代码 | `VARCHAR(16)` | 非空；唯一 |
| `departmentName` | 院系名称 | `VARCHAR(64)` | 非空 |
| `isActive` | 是否启用 | `YESNO` | 非空；默认 `TRUE` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

### 9.2 `tblMajor` 专业表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `majorId` | 专业内部编号 | `VARCHAR(36)` | 主键；UUID |
| `departmentId` | 所属院系编号 | `VARCHAR(36)` | 非空；外键关联 `tblDepartment.departmentId` |
| `majorCode` | 三字符专业代码 | `VARCHAR(3)` | 非空；唯一；匹配 `^[0-9A-Z]{3}$`，如 `090`、`09J` |
| `majorName` | 专业名称 | `VARCHAR(64)` | 非空 |
| `isActive` | 是否启用 | `YESNO` | 非空；默认 `TRUE` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

索引：`uk_tblMajor_majorCode` 唯一索引；`idx_tblMajor_departmentId`。

### 9.3 `tblClass` 班级表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `classId` | 班级内部编号 | `VARCHAR(36)` | 主键；UUID |
| `majorId` | 所属专业编号 | `VARCHAR(36)` | 非空；外键关联 `tblMajor.majorId` |
| `classCode` | 班级展示代码 | `VARCHAR(24)` | 非空；唯一 |
| `className` | 班级名称 | `VARCHAR(64)` | 非空 |
| `enrollmentYear` | 四位入学年份 | `LONG` | 非空；`2000–2099` |
| `classNumber` | 班号 | `LONG` | 非空；`1–9`；同专业同年级内唯一 |
| `isActive` | 是否启用 | `YESNO` | 非空；默认 `TRUE` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

索引：`uk_tblClass_major_year_number` 唯一复合索引（`majorId, enrollmentYear, classNumber`）；`idx_tblClass_majorId`；`idx_tblClass_enrollmentYear`。

### 9.4 `tblStudent` 学生档案表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `studentId` | 学生内部编号 | `VARCHAR(36)` | 主键；UUID |
| `userId` | 绑定的用户编号 | `VARCHAR(36)` | 非空；唯一；逻辑外键关联 `tblUser.userId` |
| `studentNumber` | 学号 | `VARCHAR(8)` | 非空；唯一；匹配 `^[0-9A-Z]{3}[0-9]{5}$` |
| `studentType` | 学生类型 | `VARCHAR(16)` | 非空；`UNDERGRADUATE/MASTER/DOCTORATE` |
| `studentName` | 学生姓名 | `VARCHAR(64)` | 非空 |
| `gender` | 性别 | `VARCHAR(16)` | 非空；受控枚举值 |
| `email` | 电子邮箱 | `VARCHAR(128)` | 可空；非空时校验邮箱格式 |
| `phone` | 联系电话 | `VARCHAR(32)` | 可空 |
| `classId` | 所属班级编号 | `VARCHAR(36)` | 非空；外键关联 `tblClass.classId` |
| `enrollmentDate` | 录取建档日期 | `DATETIME` | 非空；由服务端取录取事务当日 |
| `studentStatus` | 学籍状态 | `VARCHAR(16)` | 非空；初始为 `ACTIVE` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |
| `createdAt` | 档案创建时间 | `DATETIME` | 非空 |
| `updatedAt` | 最后更新时间 | `DATETIME` | 非空 |

索引：`uk_tblStudent_userId`、`uk_tblStudent_studentNumber` 唯一索引；`idx_tblStudent_classId`；`idx_tblStudent_studentStatus`。

一卡通号不在本表重复保存，以用户模块 `tblUser.loginId` 为权威值；学生视图通过 `userId` 和 `UserQueryPort` 组装。

### 9.5 `tblNumberSequence` 编号序列表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `sequenceKey` | 序列业务键 | `VARCHAR(64)` | 主键；全局键或班级学号键 |
| `currentValue` | 最近已提交序号 | `LONG` | 非空；初始 `0` |
| `maxValue` | 最大允许序号 | `LONG` | 非空；一卡通为 `9999`，班内学号为 `99` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0`，每次分配加一 |
| `updatedAt` | 最近分配时间 | `DATETIME` | 非空 |

序列键固定为：

- 一卡通：`CAMPUS_CARD_GLOBAL`
- 学号：`STUDENT_NUMBER:<majorCode>:<YY>:<classNumber>`，例如 `STUDENT_NUMBER:090:24:1`

全局一卡通种子行随数据库初始化创建。班级学号序列可在创建班级时写入，也可由录取事务以“若不存在则插入 `currentValue=0`”的方式创建；并发创建时仍须持有对应资源锁并处理主键冲突。

### 9.6 `tblStudentChange` 学籍变更记录表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `changeId` | 变更记录编号 | `VARCHAR(36)` | 主键；UUID |
| `studentId` | 学生内部编号 | `VARCHAR(36)` | 非空；外键关联 `tblStudent.studentId` |
| `changeType` | 变更类型 | `VARCHAR(24)` | 非空；`ADMISSION/CLASS_CHANGE/STATUS_CHANGE` |
| `oldValue` | 变更前内容 | `LONGTEXT` | 可空；保存结构化文本快照 |
| `newValue` | 变更后内容 | `LONGTEXT` | 非空；保存结构化文本快照 |
| `reason` | 变更原因 | `VARCHAR(256)` | 非空 |
| `operatorUserId` | 操作人用户编号 | `VARCHAR(36)` | 非空；逻辑外键关联 `tblUser.userId` |
| `effectiveDate` | 变更生效日期 | `DATETIME` | 非空 |
| `createdAt` | 记录创建时间 | `DATETIME` | 非空 |

班级必须属于专业，专业必须属于院系。停用上级组织前必须确认不存在仍启用的下级或活动学生。

## 10. 新生录取事务

`StudentAdmissionCoordinator` 必须按以下顺序执行：

1. 根据 `requestId` 检查幂等记录；已完成则直接返回原结果。
2. 校验学生类型、专业代码、班级、入学年份及组织启用状态。
3. 依次取得 `NUMBER_SEQUENCE:CAMPUS_CARD_GLOBAL` 和班级学号序列锁；所有实现使用相同顺序。
4. 开启一个数据库事务并重新读取两条序列，校验 `currentValue < maxValue`。
5. 两条序列各加一并写回，生成一卡通号和学号；`YY` 使用 `enrollmentYear % 100` 并补足两位，序号左侧补零。
6. 调用 `UserAccountProvisioningPort.createStudentAccount(transaction, campusCardNumber, "12345678".toCharArray())`。
7. 使用返回的 `userId` 写入 `tblStudent`，并写入 `ADMISSION` 学籍变更记录和请求幂等结果。
8. 提交事务后返回 `StudentAdmissionResult(..., mustChangePassword=true)`。

任一步失败必须回滚序列表、用户账户、学生档案、两类审计和幂等完成状态。用户模块不得嵌套开启事务。已进入事务的处理不允许生成新的 `requestId` 重试。

## 11. 其他跨模块规则

- 一个用户账户最多绑定一个学生档案，一个学生档案固定绑定一个一卡通登录账户。
- `tblUser.loginId` 是一卡通号权威值；`tblStudent.studentNumber` 是学号权威值，二者互不替代。
- 选课模块调用 `getEnrollmentEligibility`；学籍模块从不调用选课模块。
- 注销账户由上层协调器检查学籍状态，本模块只提供查询结果。
- 学生更换班级、修正学号或变更学籍状态都不得改变一卡通号和登录标识。

## 12. 并发与容量

- 录取锁顺序固定为全局一卡通序列、班级学号序列、生成后的一卡通 `LOGIN_ID`；禁止反向获取。
- 序列更新使用资源锁、同一 Access 事务和 `rowVersion` 三重保护。
- `CAMPUS_CARD_GLOBAL.currentValue=9999` 时返回 `STUDENT_CAMPUS_CARD_SEQUENCE_EXHAUSTED`。
- 班级序列 `currentValue=99` 时返回 `STUDENT_CLASS_SEQUENCE_EXHAUSTED`。
- 联系方式、转班和状态更新锁键为 `STUDENT:<studentId>`，并校验 `rowVersion`。
- 转班/状态更新与 `tblStudentChange` 在同一事务提交；禁止静默覆盖。

## 13. 错误码

`STUDENT_NOT_FOUND`、`STUDENT_CLASS_INACTIVE`、`STUDENT_ORGANIZATION_MISMATCH`、`STUDENT_STATUS_TRANSITION_INVALID`、`STUDENT_NOT_ACTIVE`、`STUDENT_MAJOR_CODE_INVALID`、`STUDENT_CLASS_NUMBER_INVALID`、`STUDENT_ENROLLMENT_YEAR_INVALID`、`STUDENT_CAMPUS_CARD_SEQUENCE_EXHAUSTED`、`STUDENT_CLASS_SEQUENCE_EXHAUSTED`、`STUDENT_NUMBER_GENERATION_FAILED`、`STUDENT_USER_ALREADY_BOUND`。

格式或组织校验失败不得启动编号分配。唯一索引或序列版本冲突在有限次内部重读后仍失败时返回 `STUDENT_NUMBER_GENERATION_FAILED`，不得向客户端泄露 SQL 异常。

## 14. 测试与验收

### 14.1 编号单元测试

- 本科生、2024 年、全局序号 2478 生成 `213242478`。
- 硕士生、2024 年、全局序号 1 生成 `223240001`。
- 博士生、2025 年、全局序号 2 生成 `233250002`。
- 专业 `090`、2024 年、1 班、班内序号 10 生成 `09024110`。
- 专业 `09J`、2024 年、1 班、班内序号 10 生成 `09J24110`。
- 专业代码、年份、班号或序号越界时返回对应错误，不截断、不循环。

### 14.2 事务与并发测试

- 20 个同班并发录取全部得到唯一、无间断的一卡通号和学号，数据库中账户、档案和审计一一对应。
- 本科、硕士、博士以及不同入学年份并发录取仍共享同一个一卡通全局序列。
- 不同班级的学号序列彼此独立，每班首次成功录取均以 `01` 结尾。
- 在用户账户创建后或学生档案写入后注入异常，事务回滚且再次录取复用未提交序号。
- 已成功提交的编号在注销、退学或重试时均不得复用。
- 全局序号 9999、班内序号 99 的最后一个分配成功，下一次分别返回容量错误。
- 相同 `requestId` 重放返回完全相同的 `StudentAdmissionResult`，序列不再次增加。

### 14.3 权限与业务测试

- 管理员录取后，学生可使用一卡通号和初始密码登录。
- 初始密码会话仅能改密、查看本人和登出，改密后必须重新登录。
- 转班或后续学号纠错不得改变一卡通号、`tblUser.loginId` 或登录行为。
- 班级不属于指定专业、年份不一致或班级停用时拒绝录取且不消耗编号。
- 旧版本更新返回 `COMMON_CONCURRENT_MODIFICATION`。
- 学生只能修改本人邮箱和电话，不能修改班级与状态。
- 教师查询结果不包含非必要联系方式。
- `SUSPENDED`、`GRADUATED`、`WITHDRAWN` 的资格查询均返回不可选课。
- 学籍变化和审计记录同成同败。

## 15. 文件边界

```text
vcampus-common/.../student/{command,query,view,StudentStatus,StudentType}
vcampus-client/.../student/{ui,service}
vcampus-server/.../student/{handler,service,repository,domain,validation,numbering}
vcampus-server/src/test/.../student
```

本模块可消费 `UserAccountProvisioningPort` 和 `UserQueryPort`，不得依赖用户 Repository、课程包或读取 `tblEnrollment`。用户模块不得写 `tblNumberSequence` 或 `tblStudent`。

## 16. 下游实现任务

1. 实现组织结构表、三字符专业代码、班号复合唯一约束及 Repository 集成测试。
2. 以测试驱动实现两种编号格式化器和所有边界样例。
3. 实现 `tblNumberSequence` Repository、固定锁顺序、容量检查及 20 线程并发测试。
4. 实现 `StudentAdmissionCoordinator`，通过共享事务调用 `UserAccountProvisioningPort`，覆盖各步骤故障注入和回滚。
5. 实现联系方式、转班、状态变更和学籍审计事务。
6. 冻结并发布 `StudentQueryPort` 与选课资格测试。
7. 实现十条 Handler、幂等、权限及序列化测试。
8. 实现七个 Swing 页面和公共组织下拉组件。
9. 完成 Access 集成、Socket 端到端、首次登录和并发验收。
