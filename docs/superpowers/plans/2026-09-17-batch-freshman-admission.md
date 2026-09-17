# 新生 CSV 批量录取与自动分班 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有学生录取链路中实现固定 CSV 新生批量录取、预览校验、按专业自动分班和原子提交。

**Architecture:** 复用现有 `StudentAdmissionCoordinator`、组织结构仓储、学号生成器和客户端学生管理页面。新增纯函数式 CSV/分班领域组件与不可变预览 DTO；服务端负责最终校验、权限、锁和 Access 单事务，客户端只负责选择文件、展示预览和确认提交。

**Tech Stack:** JDK 21、Maven、多模块 Java、Access/UCanAccess、现有 socket protocol 与 Swing 客户端。

**Spec:** `docs/superpowers/specs/2026-09-17-batch-freshman-admission-design.md`

## Global Constraints

- 使用 JDK 21，并从仓库根目录使用 Maven 验证。
- Microsoft Access（UCanAccess）是唯一支持数据库；不引入 MySQL 或远程数据库。
- 专业必须通过现有组织端口访问，且必须验证专业隶属 CSV 指定学院。
- 任一错误整批回滚；不得向客户端暴露 SQL、数据库路径、堆栈或内部异常细节。
- 新 Java 文件不超过 200 行；公共类型、构造器和方法添加有效 JavaDoc。
- 写操作保留请求去重，并在同一事务、同一锁顺序中重新检查可变规则。
- 每项功能先写失败自动化测试，再实现最小生产代码。

---

### Task 1: 固定 CSV 解析与边界校验

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/FreshmanAdmissionRow.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/FreshmanAdmissionCsv.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/FreshmanAdmissionValidationError.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/student/FreshmanAdmissionCsvTest.java`

**Interfaces:** `FreshmanAdmissionCsv.parse(String csv)` 返回不可变行列表；`validate(...)` 返回按行、按字段的错误列表。只接受表头 `姓名,性别,身份证,学院,专业`，性别只接受 `男`/`女`，保留身份证前导字符并拒绝重复。

- [ ] 写测试覆盖正确表头、UTF-8 BOM、空字段、错误列数、非法性别、身份证校验码错误、CSV 内重复身份证和逗号引号字段。
- [ ] 运行 `mvn -pl vcampus-common -Dtest=FreshmanAdmissionCsvTest test`，确认新测试失败。
- [ ] 实现无外部数据库依赖的解析器、字段错误 DTO 和身份证校验；使用 `List.copyOf` 等保证结果不可变。
- [ ] 重跑同一聚焦测试并确认通过。
- [ ] `git add` 相关文件并提交 `feat: validate freshman admission csv`。

### Task 2: 可重复的按专业分班算法

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/FreshmanClassAssignment.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/FreshmanClassAssigner.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/student/FreshmanClassAssignerTest.java`

**Interfaces:** `FreshmanClassAssigner.assign(List<FreshmanAdmissionRow>, int enrollmentYear)` 返回每行对应的 `FreshmanClassAssignment`；按 `majorId`/专业键分组，班级数为 `max(1, ceil(n / 35))`，输入按身份证稳定排序，返回班号、班名、男女人数和学生映射。

- [ ] 先写测试：35 人单班、36 人两班、两个专业不混班、班级人数差不超过 1、性别分布可达范围内最均衡、同输入顺序扰动仍得相同映射。
- [ ] 运行聚焦测试确认失败。
- [ ] 实现容量约束、按专业独立分组、确定性排序和“比例偏差→人数→班号”比较器；不得依赖数据库或 UI。
- [ ] 重跑测试确认通过，并检查文件物理行数不超过 200。
- [ ] 提交 `feat: add deterministic freshman class assignment`。

### Task 3: 公共预览/提交协议与服务端组织校验

**Files:**
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/BatchImportCommand.java`
- Create/Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/BatchAdmissionPreview.java`
- Create/Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/student/BatchImportResult.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentAdmissionCoordinator.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/StudentAdmissionCoordinatorTest.java`

**Interfaces:** 增加预览入口 `previewBatchAdmission(String csv, int enrollmentYear, RequestContext)` 与确认入口 `commitBatchAdmission(BatchAdmissionPreview preview, RequestContext)`；预览 DTO 携带原始行、专业/学院解析结果、班级统计和稳定学生映射。保留现有接口兼容调用方，必要时将旧 `BatchImportCommand` 适配到新流程。

- [ ] 先写集成测试验证专业不存在、专业不属于学院、学院管理员越权、数据库已有身份证和预览无持久化副作用均失败。
- [ ] 运行服务端聚焦测试确认失败。
- [ ] 在事务内从组织仓储解析学院和专业，明确比较 `departmentId`；将客户端错误映射为安全的业务错误码。
- [ ] 实现预览服务与确认 DTO 的序列化，避免携带 SQL、路径和异常详情。
- [ ] 重跑服务端测试确认通过。
- [ ] 提交 `feat: add freshman admission preview protocol`。

### Task 4: 原子录取、班级创建与最终学号生成

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentAdmissionCoordinator.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/AccessOrganizationRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/student/repository/StudentRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/student/service/StudentAdmissionCoordinatorTest.java`

**Interfaces:** 提交阶段按既有锁顺序锁定组织/班级编号/学号流水号；在 `TransactionManager.inTransaction` 中创建或复用目标班级，确认最终班级后调用 `StudentNumberGenerator.next(...)`，创建账号、学生、培养方案关联和 admission change。

- [ ] 先写失败测试：36 人拆班落库、跨专业不混班、账号/学生/班级数量一致、中途注入异常后所有表和流水号恢复、重复 requestId 不重复录取。
- [ ] 运行聚焦测试确认失败。
- [ ] 实现最小事务流程，避免先生成再删除学号；所有数据库可变规则在写入事务内二次检查。
- [ ] 用安全异常映射替换数据库异常直接传播，并确认已有手工录取行为不回归。
- [ ] 重跑测试确认通过。
- [ ] 提交 `feat: commit freshman admission atomically`。

### Task 5: 客户端 CSV 预览与确认 UI

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/BatchStudentCsv.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/BatchClassAssignmentPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/service/StudentClientService.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/ui/BatchStudentCsvTest.java`

**Interfaces:** 文件选择后调用预览 API，显示错误行或按专业/班级统计表；只有无错误预览才启用“确认录取”，确认后展示成功统计和生成学号。客户端不自行决定学院专业关系或最终班级。

- [ ] 先写客户端解析/按钮状态测试：错误预览不可提交、专业分组展示、确认结果展示。
- [ ] 运行聚焦测试确认失败。
- [ ] 接入现有学生页面和网络服务，保留用户取消、编码错误和服务端错误提示。
- [ ] 重跑客户端测试并检查 UI 不泄露内部异常文本。
- [ ] 提交 `feat: add freshman admission preview ui`。

### Task 6: 全量验证与交付检查

**Files:**
- Modify only if verification exposes defects; otherwise no source changes.
- Test: all affected module tests and repository-wide Maven tests.

- [ ] 运行 `mvn -pl vcampus-common -Dtest=FreshmanAdmissionCsvTest,FreshmanClassAssignerTest test`。
- [ ] 运行服务端和客户端受影响测试，确认数据库测试使用临时 Access 数据库。
- [ ] 从仓库根目录运行 `mvn test`，必要时按 AGENTS.md 为 loopback socket 测试申请权限。
- [ ] 运行 `git diff --check`、检查新 Java 文件物理行数、确认无 target/cache/log/数据库锁文件进入 diff。
- [ ] 汇总测试证据、未解决问题和建议的后续集成方式。

