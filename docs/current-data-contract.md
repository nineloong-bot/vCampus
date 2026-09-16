# vCampus 当前数据契约

本文档描述当前发布数据的唯一有效约定。历史实现计划和测试记录不得作为重建数据库的输入。

## 数据源与发布包

- `vcampus-database/schema` 与 `vcampus-database/seed` 是数据库结构和基础数据的唯一来源。
- 批量演示数据由 `vcampus-database/demo/full-test-data/tools` 生成。
- 数据库必须先在临时目录重建并通过 `ValidateDataset` 与
  `ValidateOrganizationMigration`，再替换发布包中的 `data/vCampus.accdb`。
- `vcampus-distribution` 和兼容发布目录 `vCampus-release` 使用同一份已验证数据库和同版 JAR。
- 禁止从历史文档、旧工作树或运行后被修改的 `.accdb` 反向生成 seed。

## 学期与培养方案

- 只保留 `AUTUMN` 和 `SPRING` 两个学季，不恢复 `SUMMER`。
- 四年培养方案固定为八个学期位置：`(academicYearNo - 1) * 2 + seasonOrdinal`。
- 培养方案以 `tblTrainingPlan`、`tblTrainingPlanCourse` 和
  `tblTrainingPlanPrerequisite` 为准。
- 课程模块不得复制培养方案数据；课程可选范围必须通过学籍模块提供的端口读取。
- 重修资格和课程通过状态只读取 `tblStudentGrade`，不使用 `tblCourseAttempt`。

## 组织、学生与管理员

- 学院、专业、班级和培养方案必须绑定规范 ID；迁移完成后删除旧学院、旧专业和旧培养方案。
- 学院管理员只绑定规范学院 ID，其查询范围由该绑定决定。
- 学生通过班级、专业关联到一个当前学院，不额外维护第二份学院归属。
- 入学年份由一卡通号第 4 至第 5 位换算；例如 `21324...` 对应 2024 年入学。

## 测试账号

- 当前演示和批量测试账号的初始密码统一为 `123456`。
- 密码以 PBKDF2 哈希存储；文档不得保存内部哈希实现细节。
- 是否要求首次修改密码由账号的 `mustChangePassword` 字段决定，不改变初始密码约定。
