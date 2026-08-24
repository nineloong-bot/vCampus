# 虚拟校园学籍管理模块设计

## 1. 目标与范围

本模块维护院系、专业、班级、学生档案、联系方式和学籍状态，并向选课模块提供资格查询。模块不维护课程、选课记录或成绩；学籍模块不依赖选课模块。

## 2. 角色权限

| 用例 | 学生 | 教师 | 管理员 |
|---|---:|---:|---:|
| 查看本人档案 | 是 | 否 | 是 |
| 修改本人联系方式 | 是 | 否 | 是 |
| 查询学生只读摘要 | 否 | 是 | 是 |
| 新建档案、转班、变更状态 | 否 | 否 | 是 |
| 维护院系/专业/班级 | 否 | 否 | 是 |

教师查询只返回学号、姓名、班级、专业和学籍状态，不返回电话等非必要联系方式。

## 3. 学籍状态

`ACTIVE`、`SUSPENDED`、`GRADUATED`、`WITHDRAWN`。只有 `ACTIVE` 具备选课资格。已毕业、休学和退学档案保留历史，不物理删除。

## 4. Swing 页面

- `S-01 MyStudentProfilePanel`
- `S-02 StudentSearchPanel`
- `S-03 StudentDetailPanel`
- `S-04 CreateStudentDialog`
- `S-05 UpdateContactDialog`
- `S-06 EnrollmentChangeDialog`
- `S-07 OrganizationManagementPanel`

查询支持学号/姓名关键词、院系、专业、班级、状态和分页。编辑页面必须显示当前 `rowVersion`，并发冲突后提示刷新。

## 5. DTO

```java
enum StudentStatus { ACTIVE, SUSPENDED, GRADUATED, WITHDRAWN }

record CreateStudentCommand(String userId, String studentNumber,
        String studentName, String gender, String email, String phone,
        String classId, LocalDate enrollmentDate)
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

## 6. 服务接口

```java
public interface StudentService {
    StudentView createStudent(CreateStudentCommand command);
    StudentView getCurrentStudent(String sessionToken);
    StudentView getStudent(String studentId);
    PageResult<StudentSummary> searchStudents(StudentSearchQuery query);
    StudentView updateContact(UpdateStudentContactCommand command);
    StudentView updateEnrollment(UpdateStudentEnrollmentCommand command);
    StudentView changeStatus(ChangeStudentStatusCommand command);
}

public interface StudentQueryPort {
    StudentIdentity findByUserId(String userId);
    StudentEligibility getEnrollmentEligibility(String userId);
    boolean existsActiveStudent(String studentId);
}
```

`StudentQueryPort` 是选课模块的唯一学籍入口，不返回联系方式或完整档案。

## 7. 消息合同

| 命令 | 请求 | 响应 | 权限 |
|---|---|---|---|
| `STUDENT_CREATE` | `CreateStudentCommand` | `StudentView` | `STUDENT_WRITE` |
| `STUDENT_GET_CURRENT` | `EmptyRequest` | `StudentView` | 学生 |
| `STUDENT_GET` | `EntityIdRequest` | `StudentView` | 教师/管理员 |
| `STUDENT_SEARCH` | `StudentSearchQuery` | `PageResult<StudentSummary>` | 教师/管理员 |
| `STUDENT_UPDATE_CONTACT` | `UpdateStudentContactCommand` | `StudentView` | 本人/管理员 |
| `STUDENT_UPDATE_ENROLLMENT` | `UpdateStudentEnrollmentCommand` | `StudentView` | 管理员 |
| `STUDENT_CHANGE_STATUS` | `ChangeStudentStatusCommand` | `StudentView` | 管理员 |
| `STUDENT_LIST_DEPARTMENTS` | `ActiveOnlyQuery` | `List<DepartmentView>` | 已登录 |
| `STUDENT_LIST_MAJORS` | `ParentIdQuery` | `List<MajorView>` | 已登录 |
| `STUDENT_LIST_CLASSES` | `ParentIdQuery` | `List<ClassView>` | 已登录 |

所有写命令要求幂等并携带 `expectedVersion`，创建命令通过 `requestId` 去重。

## 8. 数据库

```text
tblDepartment(departmentId CHAR(36) PK, departmentCode VARCHAR(16) UNIQUE,
              departmentName VARCHAR(64), isActive YESNO, rowVersion INTEGER)
tblMajor(majorId CHAR(36) PK, departmentId CHAR(36) FK,
         majorCode VARCHAR(16) UNIQUE, majorName VARCHAR(64), isActive YESNO,
         rowVersion INTEGER)
tblClass(classId CHAR(36) PK, majorId CHAR(36) FK,
         classCode VARCHAR(24) UNIQUE, className VARCHAR(64),
         enrollmentYear INTEGER, isActive YESNO, rowVersion INTEGER)
tblStudent(studentId CHAR(36) PK, userId CHAR(36) UNIQUE FK tblUser,
           studentNumber VARCHAR(24) UNIQUE, studentName VARCHAR(64),
           gender VARCHAR(16), email VARCHAR(128), phone VARCHAR(32),
           classId CHAR(36) FK, enrollmentDate DATETIME,
           studentStatus VARCHAR(16), rowVersion INTEGER,
           createdAt DATETIME, updatedAt DATETIME)
tblStudentChange(changeId CHAR(36) PK, studentId CHAR(36), changeType VARCHAR(24),
                 oldValue LONGTEXT, newValue LONGTEXT, reason VARCHAR(256),
                 operatorUserId CHAR(36), effectiveDate DATETIME, createdAt DATETIME)
```

班级必须属于专业，专业必须属于院系。停用上级组织前必须确认不存在仍启用的下级或活动学生。

## 9. 跨模块规则

- 创建档案前通过 `UserQueryPort.findActiveUser` 确认账户存在且角色为 `STUDENT`。
- 一个账户最多绑定一个学生档案。
- 选课模块调用 `getEnrollmentEligibility`；学籍模块从不调用选课模块。
- 注销账户由上层协调器检查学籍状态，本模块只提供查询结果。

## 10. 事务与并发

- 创建锁键：`STUDENT_NUMBER:<number>` 和 `USER:<userId>`。
- 联系方式、转班和状态更新锁键：`STUDENT:<studentId>`，并校验 `rowVersion`。
- 转班/状态更新与 `tblStudentChange` 审计记录在同一事务提交。
- 学生修改联系方式与管理员修改学籍字段可通过版本冲突检测，禁止静默覆盖。

## 11. 错误码

`STUDENT_NUMBER_EXISTS`、`STUDENT_USER_ALREADY_BOUND`、`STUDENT_USER_NOT_ELIGIBLE`、`STUDENT_NOT_FOUND`、`STUDENT_CLASS_INACTIVE`、`STUDENT_ORGANIZATION_MISMATCH`、`STUDENT_STATUS_TRANSITION_INVALID`、`STUDENT_NOT_ACTIVE`。

## 12. 测试与验收

- 相同学号或相同账户的并发建档只能成功一个。
- 班级不属于指定专业时拒绝建档或转班。
- 旧版本更新返回 `COMMON_CONCURRENT_MODIFICATION`。
- 学生只能修改本人邮箱和电话，不能修改班级与状态。
- 教师查询结果不包含非必要联系方式。
- `SUSPENDED`、`GRADUATED`、`WITHDRAWN` 的资格查询均返回不可选课。
- 学籍变化和审计记录同成同败。

## 13. 文件边界

```text
vcampus-common/.../student/{command,query,view,StudentStatus}
vcampus-client/.../student/{ui,service}
vcampus-server/.../student/{handler,service,repository,domain,validation}
vcampus-server/src/test/.../student
```

本模块可消费 `UserQueryPort`，不得依赖课程包或读取 `tblEnrollment`。

## 14. 下游实现任务

1. 实现组织结构表、Repository 和层级约束测试。
2. 实现学生建档、唯一性和用户绑定校验。
3. 实现联系方式、转班、状态变更和审计事务。
4. 冻结并发布 `StudentQueryPort` 与资格测试。
5. 实现十条 Handler、权限及序列化测试。
6. 实现七个 Swing 页面和公共组织下拉组件。
7. 完成 Access 集成、并发和 Socket 端到端验收。
