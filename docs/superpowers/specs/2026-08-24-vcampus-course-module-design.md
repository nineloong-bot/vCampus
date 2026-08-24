# 虚拟校园选课系统模块设计

## 1. 目标与边界

本模块管理学期、课程目录、教学班、上课时间、普通选课、开课后的退改补、课表和重修选课。模块不录入、保存或展示具体成绩；仅保存外部导入的 `PASSED`/`FAILED` 修读结果，以判断重修资格。

## 2. 权限

| 用例 | 学生 | 教师 | 管理员 |
|---|---:|---:|---:|
| 查询教学班、查看课表 | 是 | 是 | 是 |
| 普通选课、退改补、重修 | 是 | 否 | 否 |
| 查看本人教学安排 | 否 | 是 | 是 |
| 维护学期、课程和教学班 | 否 | 否 | 是 |
| 导入通过/不通过结果 | 否 | 否 | 是 |
| 查看退改补日志 | 否 | 否 | 是 |

## 3. 时间阶段

`tblTerm` 配置 `enrollmentStartAt`、`enrollmentEndAt`、`adjustmentStartAt`、`adjustmentEndAt`。正常选课只在第一个时间窗开放；退、改、补只在第二个时间窗开放并在校验通过后立即生效。时间窗之外只允许查询。

## 4. 业务规则

- 学籍必须为 `ACTIVE`。
- 同一学生同一学期不能同时选择同一课程的多个教学班。
- 教学班 `enrolledCount` 必须小于 `capacity`。
- 星期相同、周次重叠且节次区间相交视为时间冲突。
- 补选重新执行全部普通选课校验。
- 退课释放名额并保留 `DROPPED` 历史记录。
- 改选在单一事务中加入目标教学班并退出原教学班；目标失败时原记录不变。
- 重修要求同一课程至少存在一条 `FAILED` 历史结果；`PASSED` 或无历史均不允许。
- 重修仍需检查容量、时间冲突、重复选择和学籍资格。

## 5. Swing 页面

`C-01 OfferingSearchPanel`、`C-02 OfferingDetailDialog`、`C-03 MyEnrollmentPanel`、`C-04 MySchedulePanel`、`C-05 AdjustmentPanel`、`C-06 RetakePanel`、`C-07 TermManagementPanel`、`C-08 CourseCatalogPanel`、`C-09 OfferingManagementPanel`、`C-10 OutcomeImportPanel`、`C-11 AdjustmentAuditPanel`。

退改补页显示当前阶段和时间窗。改选确认框并列显示原/目标教学班、课程、时间、容量与冲突结果。

## 6. DTO

```java
enum EnrollmentType { NORMAL, LATE_ADD, RETAKE }
enum EnrollmentStatus { ACTIVE, DROPPED }
enum CourseOutcome { PASSED, FAILED }
enum AdjustmentType { ADD, DROP, CHANGE }

record EnrollCommand(String offeringId) implements Serializable {}
record LateAddCommand(String offeringId) implements Serializable {}
record DropCommand(String enrollmentId, long expectedVersion)
        implements Serializable {}
record ChangeOfferingCommand(String sourceEnrollmentId,
        String targetOfferingId, long expectedVersion)
        implements Serializable {}
record RetakeCommand(String offeringId) implements Serializable {}
record OfferingSearchQuery(String termId, String keyword,
        String dayOfWeek, Boolean availableOnly, int page, int pageSize)
        implements Serializable {}
record RetakeEligibility(String courseId, boolean eligible,
        List<String> failedAttemptIds, String reason)
        implements Serializable {}
```

## 7. 服务接口

```java
public interface CourseService {
    CourseView createCourse(CreateCourseCommand command);
    CourseView updateCourse(UpdateCourseCommand command);
    OfferingView createOffering(CreateOfferingCommand command);
    OfferingView updateOffering(UpdateOfferingCommand command);
    PageResult<OfferingSummary> searchOfferings(OfferingSearchQuery query);
    EnrollmentView enroll(String sessionToken, EnrollCommand command);
    EnrollmentView addDuringAdjustment(String sessionToken,
                                        LateAddCommand command);
    void dropDuringAdjustment(String sessionToken, DropCommand command);
    EnrollmentView changeDuringAdjustment(String sessionToken,
                                           ChangeOfferingCommand command);
    EnrollmentView enrollRetake(String sessionToken, RetakeCommand command);
    List<ScheduleItem> getCurrentSchedule(String sessionToken);
    List<EnrollmentView> getCurrentEnrollments(String sessionToken);
    RetakeEligibility checkRetakeEligibility(String sessionToken,
                                             String courseId);
    void importCourseOutcomes(ImportCourseOutcomesCommand command);
}

public interface CourseQueryPort {
    boolean hasActiveEnrollment(String studentId);
    List<CourseSummary> findCoursesByStudent(String studentId);
}
```

## 8. 消息合同

| 命令 | 请求 | 响应 | 权限 |
|---|---|---|---|
| `COURSE_SEARCH_OFFERINGS` | `OfferingSearchQuery` | 分页教学班 | 已登录 |
| `COURSE_ENROLL` | `EnrollCommand` | `EnrollmentView` | 学生 |
| `COURSE_ADJUSTMENT_ADD` | `LateAddCommand` | `EnrollmentView` | 学生 |
| `COURSE_ADJUSTMENT_DROP` | `DropCommand` | `EmptyResponse` | 学生本人 |
| `COURSE_ADJUSTMENT_CHANGE` | `ChangeOfferingCommand` | `EnrollmentView` | 学生本人 |
| `COURSE_RETAKE_CHECK` | `EntityIdRequest` | `RetakeEligibility` | 学生 |
| `COURSE_RETAKE_ENROLL` | `RetakeCommand` | `EnrollmentView` | 学生 |
| `COURSE_GET_MY_SCHEDULE` | `EmptyRequest` | 课表 | 学生/教师 |
| `COURSE_GET_MY_ENROLLMENTS` | `EmptyRequest` | 选课列表 | 学生 |
| `COURSE_IMPORT_OUTCOMES` | `ImportCourseOutcomesCommand` | 导入结果 | 管理员 |

课程、学期、教学班维护另有 `COURSE_CREATE`、`COURSE_UPDATE`、`COURSE_CREATE_OFFERING`、`COURSE_UPDATE_OFFERING`，均要求管理员权限和幂等。

## 9. 数据库

### 9.1 `tblTerm` 学期表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `termId` | 学期内部编号 | `VARCHAR(36)` | 主键；UUID |
| `termCode` | 学期代码 | `VARCHAR(24)` | 非空；唯一，例如 `2026-2027-1` |
| `termName` | 学期名称 | `VARCHAR(64)` | 非空 |
| `startDate` | 开课日期 | `DATETIME` | 非空 |
| `endDate` | 结课日期 | `DATETIME` | 非空；晚于 `startDate` |
| `enrollmentStartAt` | 正常选课开始时间 | `DATETIME` | 非空 |
| `enrollmentEndAt` | 正常选课结束时间 | `DATETIME` | 非空；晚于正常选课开始时间 |
| `adjustmentStartAt` | 退改补开始时间 | `DATETIME` | 非空；可设置在开课之后 |
| `adjustmentEndAt` | 退改补结束时间 | `DATETIME` | 非空；晚于退改补开始时间 |
| `termStatus` | 学期状态 | `VARCHAR(16)` | 非空；`PLANNED/ACTIVE/CLOSED` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

### 9.2 `tblCourse` 课程目录表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `courseId` | 课程内部编号 | `VARCHAR(36)` | 主键；UUID |
| `courseCode` | 课程代码 | `VARCHAR(24)` | 非空；唯一 |
| `courseName` | 课程名称 | `VARCHAR(128)` | 非空 |
| `credit` | 课程学分 | `DECIMAL(4,1)` | 非空；大于 `0` |
| `totalHours` | 总学时 | `LONG` | 非空；大于 `0` |
| `description` | 课程简介 | `LONGTEXT` | 可空 |
| `isActive` | 是否启用 | `YESNO` | 非空；默认 `TRUE` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

### 9.3 `tblCourseOffering` 教学班表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `offeringId` | 教学班内部编号 | `VARCHAR(36)` | 主键；UUID |
| `termId` | 所属学期编号 | `VARCHAR(36)` | 非空；外键关联 `tblTerm.termId` |
| `courseId` | 对应课程编号 | `VARCHAR(36)` | 非空；外键关联 `tblCourse.courseId` |
| `teacherUserId` | 授课教师用户编号 | `VARCHAR(36)` | 非空；外键关联 `tblUser.userId`，账户角色必须为教师 |
| `className` | 教学班名称 | `VARCHAR(64)` | 非空 |
| `capacity` | 容量上限 | `LONG` | 非空；大于 `0` |
| `enrolledCount` | 当前有效选课人数 | `LONG` | 非空；默认 `0`，不得超过容量 |
| `offeringStatus` | 教学班状态 | `VARCHAR(16)` | 非空；`DRAFT/OPEN/CLOSED/CANCELLED` |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

索引：`idx_tblCourseOffering_termId`、`idx_tblCourseOffering_courseId`、`idx_tblCourseOffering_teacherUserId`。

### 9.4 `tblCourseSchedule` 教学班上课时间表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `scheduleId` | 上课安排编号 | `VARCHAR(36)` | 主键；UUID |
| `offeringId` | 教学班编号 | `VARCHAR(36)` | 非空；外键关联 `tblCourseOffering.offeringId` |
| `dayOfWeek` | 星期 | `LONG` | 非空；`1–7` 表示周一至周日 |
| `startPeriod` | 开始节次 | `LONG` | 非空；大于 `0` |
| `endPeriod` | 结束节次 | `LONG` | 非空；不小于开始节次 |
| `startWeek` | 开始周次 | `LONG` | 非空；大于 `0` |
| `endWeek` | 结束周次 | `LONG` | 非空；不小于开始周次 |
| `classroom` | 上课地点 | `VARCHAR(64)` | 非空 |

### 9.5 `tblEnrollment` 学生选课记录表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `enrollmentId` | 选课记录编号 | `VARCHAR(36)` | 主键；UUID |
| `offeringId` | 教学班编号 | `VARCHAR(36)` | 非空；外键关联 `tblCourseOffering.offeringId` |
| `studentId` | 学生内部编号 | `VARCHAR(36)` | 非空；外键关联 `tblStudent.studentId` |
| `enrollmentType` | 选课类型 | `VARCHAR(16)` | 非空；`NORMAL/LATE_ADD/RETAKE` |
| `enrollmentStatus` | 选课状态 | `VARCHAR(16)` | 非空；`ACTIVE/DROPPED` |
| `enrolledAt` | 选入时间 | `DATETIME` | 非空 |
| `droppedAt` | 退课时间 | `DATETIME` | 可空；有效记录为空 |
| `rowVersion` | 乐观锁版本号 | `LONG` | 非空；默认 `0` |

唯一索引：`uk_tblEnrollment_student_offering(studentId, offeringId)`。每名学生对同一教学班只保留一条记录；退课后再次补选该教学班时重新激活原记录，完整操作历史由 `tblEnrollmentAdjustment` 保存。

### 9.6 `tblEnrollmentAdjustment` 退改补操作日志表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `adjustmentId` | 异动记录编号 | `VARCHAR(36)` | 主键；UUID |
| `studentId` | 学生内部编号 | `VARCHAR(36)` | 非空；外键关联 `tblStudent.studentId` |
| `adjustmentType` | 异动类型 | `VARCHAR(16)` | 非空；`ADD/DROP/CHANGE` |
| `sourceOfferingId` | 原教学班编号 | `VARCHAR(36)` | 可空；退课和改选时关联原教学班 |
| `targetOfferingId` | 目标教学班编号 | `VARCHAR(36)` | 可空；补选和改选时关联目标教学班 |
| `operationResult` | 操作结果 | `VARCHAR(16)` | 非空；`SUCCEEDED/FAILED` |
| `failureCode` | 失败错误码 | `VARCHAR(64)` | 可空；成功时为空 |
| `operatedAt` | 操作发生时间 | `DATETIME` | 非空 |

### 9.7 `tblCourseAttempt` 历史修读结果表

| 字段名称 | 中文含义 | Access 类型 | 约束与说明 |
|---|---|---|---|
| `attemptId` | 修读结果编号 | `VARCHAR(36)` | 主键；UUID |
| `studentId` | 学生内部编号 | `VARCHAR(36)` | 非空；外键关联 `tblStudent.studentId` |
| `courseId` | 课程内部编号 | `VARCHAR(36)` | 非空；外键关联 `tblCourse.courseId` |
| `termId` | 修读学期编号 | `VARCHAR(36)` | 非空；外键关联 `tblTerm.termId` |
| `outcome` | 修读结果 | `VARCHAR(16)` | 非空；仅 `PASSED/FAILED`，不保存具体成绩 |
| `sourceReference` | 外部结果来源标识 | `VARCHAR(128)` | 非空；用于导入去重和追溯 |
| `importedAt` | 导入时间 | `DATETIME` | 非空 |

唯一索引：`uk_tblCourseAttempt_sourceReference`；查询索引：`idx_tblCourseAttempt_student_course`。

唯一约束防止同一学生对同一教学班创建重复记录。`enrolledCount` 是性能字段，维护任务按 `ACTIVE` 记录核对并生成异常报告。

## 10. 跨模块

从会话取得 `userId`，只通过 `StudentQueryPort` 获得 `studentId` 和资格。模块不得读取 `tblStudent`。教师身份通过 `AuthorizationPort` 校验。

## 11. 事务与并发

普通/补选/重修按顺序锁定 `STUDENT:<studentId>`、`OFFERING:<offeringId>`，事务内再次检查资格、时间、重复和容量后插入并增加人数。

改选锁定学生、原教学班和目标教学班，锁键排序后获取。在同一事务中创建目标记录、停用原记录、更新两边人数并写异动日志；任一步失败全部回滚。

20 人竞争最后一个名额最多一人成功。Access 不依赖 `SELECT FOR UPDATE`，应用资源锁、事务和唯一索引共同保证一致性。

## 12. 错误码

`COURSE_ENROLLMENT_NOT_OPEN`、`COURSE_ADJUSTMENT_NOT_OPEN`、`COURSE_STUDENT_INELIGIBLE`、`COURSE_OFFERING_FULL`、`COURSE_DUPLICATE_ENROLLMENT`、`COURSE_SCHEDULE_CONFLICT`、`COURSE_ENROLLMENT_NOT_ACTIVE`、`COURSE_CHANGE_TARGET_INVALID`、`COURSE_RETAKE_NOT_ELIGIBLE`、`COURSE_OUTCOME_IMPORT_INVALID`。

## 13. 测试与验收

- 20 个请求竞争 1 个名额仅 1 个成功，`enrolledCount` 与有效记录一致。
- 相同学生的并发重复选课只成功一次。
- 周次、星期和节次任一不重叠时不误判冲突。
- 退改补时间窗边界精确到服务端时间。
- 改选目标满员或冲突时原课程保持有效。
- 只有存在 `FAILED` 历史才允许重修。
- 导入结果重复执行不产生重复 `CourseAttempt`。
- 非本人不能退改其他学生记录。

## 14. 文件边界

```text
vcampus-common/.../course/{command,query,view,enum}
vcampus-client/.../course/{ui,service}
vcampus-server/.../course/{handler,service,repository,domain,validation}
vcampus-server/src/test/.../course
```

可消费 `StudentQueryPort` 和 `AuthorizationPort`，不得修改其签名或访问学籍 Repository。

## 15. 下游实现任务

1. 学期、课程、教学班、课表表结构与 Repository。
2. 时间冲突判定的纯单元测试和最小实现。
3. 普通选课服务及 20 请求并发测试。
4. 退课、补选和原子改选及回滚测试。
5. 修读结果导入、资格判断和重修选课。
6. 消息 Handler、权限和幂等测试。
7. 十一个 Swing 页面、端到端测试和演示数据。
