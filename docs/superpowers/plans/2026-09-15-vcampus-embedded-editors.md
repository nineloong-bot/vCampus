# vCampus 全系统嵌入式编辑工作区 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将登录后所有持续性新增、编辑、录入、申请与审核表单迁移到默认隐藏的页面内工作区，清除业务主标题下的解释小字，并把教学班课程/教师选择改为可靠的自动补全。

**Architecture:** 在客户端核心层增加与业务无关的 `EmbeddedEditorHost`、编辑器生命周期协议和自动补全控件；各业务模块把现有弹窗内容拆成普通面板，继续调用本模块已有 gateway/service。短确认、错误警告、文件选择、模拟支付、只读详情和登录安全流程保留弹窗。实施从最新 `origin/main` 的隔离工作树开始，避免修改当前工作树中的运行时 Access 数据库和服务端 JAR。

**Tech Stack:** Java 21、Swing、Maven、JUnit 5、AssertJ、Microsoft Access/UCanAccess（本计划不修改数据库）

**Spec:** `docs/superpowers/specs/2026-09-15-vcampus-embedded-editors-design.md`

## Global Constraints

- 开工前必须阅读仓库根目录 `AGENTS.md`，并使用 `superpowers:using-git-worktrees` 从最新 `origin/main` 创建 `codex/embedded-editor-workspaces` 隔离工作树。
- 当前 `/Users/je1ghtxyun/code/java-summer-course` 中的 `vcampus-distribution/data/vCampus.accdb` 和 `vcampus-distribution/lib/vCampusServer.jar` 是用户运行产生的未提交改动；不得暂存、还原、复制覆盖或带入新工作树。
- 在隔离工作树中 cherry-pick 设计提交 `26d8ee6`，随后按本计划逐项执行；每个行为变更先写失败测试。
- 新 Java 文件不得超过 200 个物理行。修改已有超长类时必须拆分职责，不得通过压缩格式规避限制。
- 不改服务端协议、Access schema/seed、权限模型、幂等键、乐观锁或业务校验；跨模块访问仍经现有 port/gateway。
- 正常态静态说明文字删除；字段标签、字段级帮助、校验错误、加载/空结果/失败/版本冲突/提交中状态保留。
- 每个任务完成后运行列出的聚焦测试并提交；不要提交数据库、服务端 JAR、日志、`target/` 或 IDE 文件。

---

## Task 1: 建立最新远端基线与迁移清单测试

**Files:**
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/editor/LegacyEditorSurfaceInventoryTest.java`
- Modify: `docs/superpowers/specs/2026-09-15-vcampus-embedded-editors-design.md`（仅当 cherry-pick 产生路径调整）

- [ ] **Step 1: 创建隔离工作树并带入设计文档**

  在当前仓库执行：

  ```bash
  git fetch origin
  git worktree add ../java-summer-course-embedded -b codex/embedded-editor-workspaces origin/main
  cd ../java-summer-course-embedded
  git cherry-pick 26d8ee6
  ```

  预期：新工作树基于 `b795aa6` 或更新的 `origin/main`；原工作树两个未提交运行产物不出现在 `git status --short` 中。

- [ ] **Step 2: 写失败的遗留录入弹窗清单测试**

  `LegacyEditorSurfaceInventoryTest` 扫描 `vcampus-client/src/main/java`，仅把以下弹窗列为最终允许项：

  ```java
  private static final Set<String> ALLOWED_DIALOGS = Set.of(
      "OfferingDetailDialog.java",
      "ChangeDetailDialog.java",
      "ApplicationDetailDialog.java",
      "SimulatedCashierDialog.java",
      "ChangePasswordDialog.java",
      "InitialPasswordChangeDialog.java",
      "LogoutConfirmationDialog.java",
      "SessionReplacementWarningDialog.java",
      "StudentPasswordResetConfirmationDialog.java",
      "TeacherPasswordResetConfirmationDialog.java"
  );
  ```

  测试还扫描 `JOptionPane.showInputDialog`、`showOptionDialog` 和 `showConfirmDialog`：任何 `showInputDialog`/`showOptionDialog` 均失败；`showConfirmDialog` 的第二参数是 `JPanel`、`JScrollPane` 或业务表单变量时失败；纯字符串确认继续允许。失败信息必须列出文件路径和行号。

- [ ] **Step 3: 运行测试确认它因现有录入弹窗失败**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=LegacyEditorSurfaceInventoryTest test
  ```

  预期：FAIL，并至少报告 `CourseEditorDialog.java`、`TermEditorDialog.java`、`OfferingEditorDialog.java`、`UserRoleDialog.java` 和图书管理中的表单确认框。

- [ ] **Step 4: 提交清单测试**

  ```bash
  git add vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/editor/LegacyEditorSurfaceInventoryTest.java
  git commit -m "test: inventory legacy editor dialogs"
  ```

---

## Task 2: 实现统一页面内工作区

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/editor/EditorPlacement.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/editor/EditorSize.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/editor/EmbeddedEditor.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/editor/DiscardChangesConfirmation.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/editor/EmbeddedEditorHost.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/editor/EmbeddedEditorHostTest.java`

- [ ] **Step 1: 写默认隐藏、布局和关闭语义的失败测试**

  测试用一个记录生命周期次数的假编辑器断言：

  ```java
  assertThat(host.isEditorOpen()).isFalse();
  assertThat(host.getComponentCount()).isEqualTo(1);
  host.showEditor(compactEditor);
  assertThat(host.currentPlacement()).isEqualTo(EditorPlacement.RIGHT);
  host.setAvailableWidthForTest(900);
  assertThat(host.currentPlacement()).isEqualTo(EditorPlacement.BOTTOM);
  assertThat(host.requestClose()).isFalse(); // dirty + confirmation rejects
  assertThat(host.completeAndClose()).isTrue();
  assertThat(host.isEditorOpen()).isFalse();
  ```

  另测 `WIDE` 在任何宽度均为 `BOTTOM`，替换脏编辑器必须确认，关闭后只剩列表组件且列表实例未被替换。

- [ ] **Step 2: 运行测试确认编译失败**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=EmbeddedEditorHostTest test
  ```

  预期：FAIL，缺少 `core.ui.editor` 类型。

- [ ] **Step 3: 实现最小生命周期协议**

  `EditorSize` 声明编辑器偏好 `COMPACT`、`WIDE`，`EditorPlacement` 声明 host 解析后的 `RIGHT`、`BOTTOM`；两个 enum 均保持在 30 行以内。

  `EmbeddedEditor` 公共接口必须有 JavaDoc，最小签名：

  ```java
  public interface EmbeddedEditor {
      JComponent component();
      EditorSize size();
      boolean isDirty();
      default void onOpened() { }
      default void onClosed() { }
  }
  ```

  `EmbeddedEditorHost` 使用 `BorderLayout`：关闭态只挂载列表；打开态挂载 `JSplitPane`。`COMPACT` 在可用宽度至少 1180px 且列表/表单最小宽度可满足时为右侧，否则为下侧；`WIDE` 总在下侧。组件 resize 时只切换 split orientation，不重新创建表单。

- [ ] **Step 4: 实现统一放弃确认入口和异步代际号**

  `DiscardChangesConfirmation.confirm(Component owner)` 由页面注入；生产默认实现使用简短 `JOptionPane.YES_NO_OPTION`。Host 每次打开/关闭递增 generation，并提供：

  ```java
  public long generation();
  public boolean isCurrent(long candidate);
  ```

  后续异步加载回调在写 UI 前检查 generation。

- [ ] **Step 5: 运行聚焦测试**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=EmbeddedEditorHostTest test
  ```

  预期：PASS。

- [ ] **Step 6: 检查新文件行数并提交**

  ```bash
  wc -l vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/editor/*.java
  git add vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/editor vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/editor/EmbeddedEditorHostTest.java
  git commit -m "feat: add embedded editor workspace"
  ```

  预期：每个新 Java 文件少于或等于 200 行。

---

## Task 3: 实现课程和教师共用的自动补全控件

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/autocomplete/AutocompleteChoice.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/autocomplete/SuggestionLoader.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/autocomplete/AutocompleteSelectionField.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/autocomplete/SuggestionPopup.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/autocomplete/AutocompleteSelectionFieldTest.java`

- [ ] **Step 1: 写失败的防抖、过期响应和稳定 ID 测试**

  使用可控 scheduler/loader，覆盖：250ms 前不请求；第 251ms 请求；展示最多 8 条；第二次请求先完成后，第一次迟到结果被忽略；键盘上下键和 Enter 选择；Esc 关闭建议；编辑已选文本立即清空 `selectedId`；自由文本 `requireSelection()` 返回校验错误；`setSelection(id, label)` 正确回填。

- [ ] **Step 2: 运行测试确认编译失败**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=AutocompleteSelectionFieldTest test
  ```

- [ ] **Step 3: 实现不可变选项和异步加载端口**

  ```java
  public record AutocompleteChoice(String id, String label, String detail) { }

  @FunctionalInterface
  public interface SuggestionLoader {
      CompletableFuture<List<AutocompleteChoice>> load(String query, int limit);
  }
  ```

  对公开类型、构造器和方法补齐 JavaDoc；构造时验证 `id`/`label` 非空。

- [ ] **Step 4: 实现输入框、建议列表和内联状态**

  `AutocompleteSelectionField` 组合 `JTextField`、`SuggestionPopup` 和下方状态标签。使用可取消 Swing `Timer` 做 250ms 防抖，以递增 request sequence 忽略过期结果；loader 的完成回调切回 EDT。不得使用顶层 `JDialog`，建议层使用字段所在页面内的 `JPopupMenu`。

- [ ] **Step 5: 运行聚焦测试并提交**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=AutocompleteSelectionFieldTest test
  wc -l vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/autocomplete/*.java
  git add vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/autocomplete vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/autocomplete/AutocompleteSelectionFieldTest.java
  git commit -m "feat: add asynchronous autocomplete field"
  ```

---

## Task 4: 迁移课程目录和学期编辑器

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseEditorPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/TermEditorPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/SelectionPhaseEditorPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OutcomeImportEditorPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseCatalogPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/TermManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/SelectionPhaseManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OutcomeImportPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/AbstractCoursePanel.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/CourseEditorDialog.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/TermEditorDialog.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseUiTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseEditorPanelTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/TermEditorPanelTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/CourseAdministrationWorkspaceTest.java`

- [ ] **Step 1: 把原弹窗行为写成面板失败测试**

  断言新建/编辑按钮打开同一 `EmbeddedEditorHost`；课程、学期和选课阶段为 `COMPACT`，批量课程结果导入为 `WIDE`；这些页面默认只显示列表或导入入口且不为表单留空位；取消收起；保存失败保留字段；保存成功刷新原页并收起；版本号和原 command 字段不变。

- [ ] **Step 2: 运行聚焦测试确认失败**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=CourseUiTest,CourseEditorPanelTest,TermEditorPanelTest,CourseAdministrationWorkspaceTest test
  ```

- [ ] **Step 3: 从弹窗提取可复用表单面板**

  将字段初始化、校验、填充和 command 构造移到 `CourseEditorPanel` / `TermEditorPanel`。把 `SelectionPhaseManagementPanel.editor()` 提取为 `SelectionPhaseEditorPanel`，新建与编辑共用它；把 `OutcomeImportPanel` 中永久显示的文本域提取为按“导入课程结果”后出现的 `OutcomeImportEditorPanel`。面板通过 `Consumer`/小型回调报告成功或取消，不拥有窗口、不调用 `setVisible`/`dispose`。删除表单标题下“必填字段……”和日期格式长说明；日期格式错误作为字段校验展示。

- [ ] **Step 4: 页面接入 host 并删除旧弹窗**

  `CourseCatalogPanel`、`TermManagementPanel`、`SelectionPhaseManagementPanel` 和 `OutcomeImportPanel` 各自持有一个 host；列表/筛选组件保持原实例。删除 `CourseCatalogPanel` 底部“新增和短编辑使用统一课程表单……”文案以及 `OutcomeImportPanel` 的“尚未提交导入”正常态文字。`AbstractCoursePanel.heading` 改为只创建面包屑和主标题，不再接受或渲染 description。

- [ ] **Step 5: 运行测试并提交**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=CourseUiTest,CourseEditorPanelTest,TermEditorPanelTest,CourseAdministrationWorkspaceTest test
  git add vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui
  git commit -m "refactor: embed course and term editors"
  ```

---

## Task 5: 迁移教学班编辑并接入自动补全

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingEditorPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingReferenceLoader.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingFormState.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingScheduleEditorPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/AdminEnrollmentControl.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/course/ui/OfferingEditorDialog.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/OfferingEditorRetakeDefaultTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/OfferingAutocompleteTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/course/ui/OfferingManagementWorkspaceTest.java`

- [ ] **Step 1: 写失败的布局、容量和自动补全测试**

  断言编辑器为 `WIDE` 并显示在下侧；普通容量和重修容量分别保留，重修新建默认 5；课程输入按代码/名称查询且最多 8 条，只含启用课程；教师输入按姓名/工号/账号查询，只含在职教师；未选择稳定 ID 时保存被阻止；编辑模式正确回填课程、教师和排课行。管理员“添加重修学生”输入默认隐藏，点击后作为 `COMPACT` 工作区出现，成功后收起并刷新所选教学班。

- [ ] **Step 2: 运行聚焦测试确认失败**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=OfferingEditorRetakeDefaultTest,OfferingAutocompleteTest,OfferingManagementWorkspaceTest test
  ```

- [ ] **Step 3: 为现有 gateway 增加窄查询适配方法**

  `OfferingReferenceLoader` 调用 `CourseUiGateway.searchCatalog(...)`、`searchTeachers(...)` 和 `resolveTeacher(...)`，将结果映射为 `AutocompleteChoice`；不修改已经超长的 `CourseUiGateway`。每次返回前过滤启用课程/在职教师并截断到 8 条，保存仍使用原 course ID 和 teacher user ID。

- [ ] **Step 4: 拆分原 479 行弹窗**

  `OfferingEditorPanel` 只组合字段与子面板；`OfferingFormState` 负责不可变表单快照和 command 构造；`OfferingReferenceLoader` 负责异步参考数据；`OfferingScheduleEditorPanel` 仅维护排课行。移除“课程关键字/查询课程/课程下拉”和“教师关键字/查询教师/教师下拉”。保留学期下拉框、两个独立容量字段、状态和排课行。

- [ ] **Step 5: 接入 host、处理迟到响应并删除旧弹窗**

  `OfferingManagementPanel` 在新建/编辑时生成 host generation；课程、教师和保存回调写 UI 前检查 generation。将 `AdminEnrollmentControl` 从永久输入行改为由“添加重修学生”按钮打开的 compact editor。保存成功刷新当前分页和选中项并收起，失败保留表单。

- [ ] **Step 6: 运行课程模块测试并提交**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest='edu.seu.vcampus.client.course.**' test
  git add vcampus-client/src/main/java/edu/seu/vcampus/client/course vcampus-client/src/test/java/edu/seu/vcampus/client/course
  git commit -m "refactor: embed teaching class editor"
  ```

---

## Task 6: 迁移学生档案、录取与组织管理表单

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/AdminStudentInfoEditPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/EnrollmentChangePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/UpdateContactPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/ManualStudentCreationPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentAdmissionPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/BatchClassAssignmentPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/CollegeAdministratorCreationPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentDetailPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/MyStudentProfilePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentProfileReviewPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/OrganizationManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/CollegeAdministratorManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/PersonalProfileEditPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/AttendanceModeEditPanel.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/AdminStudentInfoEditDialog.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/EnrollmentChangeDialog.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/UpdateContactDialog.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/ManualStudentCreationDialog.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/StudentAdmissionDialog.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/BatchClassAssignmentDialog.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/CollegeAdministratorCreationDialog.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/PersonalProfileEditDialog.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/AttendanceModeEditDialog.java`
- Modify/Rename tests: existing `*DialogTest.java` files under `vcampus-client/src/test/java/edu/seu/vcampus/client/student/`

- [ ] **Step 1: 先把四组弹窗测试改写为面板/host 失败测试**

  分组覆盖：完整档案与联系方式；考勤/学籍变更；资料修改申请审核；手工/批量录取；组织与学院管理员。断言复杂档案、批量录取为 `WIDE`，短申请/管理员创建/填写驳回原因的审核为 `COMPACT`；保存 command、版本号、权限控制和刷新行为与旧测试一致。

- [ ] **Step 2: 运行学生聚焦测试确认失败**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest='*Student*PanelTest,*EnrollmentChange*Test,*Organization*Test,*CollegeAdministrator*Test' test
  ```

- [ ] **Step 3: 将已有普通面板直接接入 host**

  `PersonalProfileEditPanel`、`AttendanceModeEditPanel` 不再由同名 dialog 包装；它们实现/适配 `EmbeddedEditor`，显式报告 dirty、save、cancel。联系方式表单从 `UpdateContactDialog` 提取到 `UpdateContactPanel`。`MyStudentProfilePanel` 删除“已加载”“尚无修改申请”等正常态标签，仅在真实加载、空结果、失败或提交中显示状态；`StudentProfileReviewPanel` 的驳回原因输入改为同页工作区，审核通过的纯确认框保留。

- [ ] **Step 4: 将其余弹窗按表单职责拆成面板**

  从旧 dialog 移出字段、校验、加载和 command 构造；页面负责打开/关闭 host。复杂面板按“字段区/参考数据加载/提交协调”拆文件，确保每个新文件不超过 200 行。保留 `ChangeDetailDialog` 作为只读详情例外。

- [ ] **Step 5: 运行学生档案与组织测试并提交**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest='edu.seu.vcampus.client.student.**' test
  git add vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui vcampus-client/src/test/java/edu/seu/vcampus/client/student
  git commit -m "refactor: embed student record editors"
  ```

---

## Task 7: 迁移培养方案、成绩与转专业工作区

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/TrainingPlanEditorPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/TrainingPlanCourseEditorPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/CrossDisciplineRequestPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/GradeEntryPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/TrainingPlanManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/ui/GradeManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferBatchManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferBatchFormCardPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeProcessingPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeActions.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MyMajorTransferPanel.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/student/majortransfer/ui/MajorTransferCollegeDialogs.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/ui/TrainingPlanWorkspaceTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/ui/NewFeaturesUiTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/student/majortransfer/MajorTransferUiRegressionTest.java`

- [ ] **Step 1: 写失败的复杂工作区回归测试**

  覆盖方案新建/编辑、方案课程、课程库引入、跨学科申请/审核、成绩录入、转专业批次/招生专业/申请/审核。断言它们均使用 `WIDE` 下侧工作区；批次表单初始完全隐藏；点击新建/编辑才出现；保存/取消后收起。

- [ ] **Step 2: 运行聚焦测试确认失败**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=TrainingPlanWorkspaceTest,NewFeaturesUiTest,MajorTransferUiRegressionTest test
  ```

- [ ] **Step 3: 拆分超长培养方案页面**

  `TrainingPlanManagementPanel` 只保留页面编排、列表状态和 host；把方案主表单、课程表单、跨学科申请/审核表单分别移入上述面板。不得复制训练方案数据，仍经现有训练方案 gateway 访问 `tblTrainingPlan*` 对应服务接口。

- [ ] **Step 4: 接入成绩与转专业 host**

  复用 `MajorTransferBatchFormCardPanel`，但从永久并排改为 host 按需显示。将其“请填写…”“正在编辑…”等正常引导改为字段标签/校验；只保留保存中和错误状态。把 `MajorTransferCollegeDialogs` 和 `MajorTransferCollegeActions` 的招生专业、成绩、目标班级及理由输入拆到 processing/application 页面；只读详情继续用现有详情呈现。

- [ ] **Step 5: 运行学生模块测试并提交**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest='edu.seu.vcampus.client.student.**' test
  git add vcampus-client/src/main/java/edu/seu/vcampus/client/student vcampus-client/src/test/java/edu/seu/vcampus/client/student
  git commit -m "refactor: embed academic workflow editors"
  ```

---

## Task 8: 迁移图书管理录入表单

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/BookFormCardPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/BookManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/CopyEditorPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/CopyManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/LoanActionPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/LoanAdminPanel.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/LoanActionDialog.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/library/BookManagementWorkflowTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/library/ui/CopyManagementRefreshTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/library/ui/LibraryEditorWorkspaceTest.java`

- [ ] **Step 1: 写失败的书目、副本和借阅操作工作区测试**

  断言新增/编辑书目和副本不再调用带表单的 `showConfirmDialog`；短表单右侧显示，窄窗口切换到底部；需要持续字段录入的借阅管理操作使用页面工作区；保存后刷新列表，错误时保留输入。

- [ ] **Step 2: 运行聚焦测试确认失败**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=BookManagementWorkflowTest,CopyManagementRefreshTest,LibraryEditorWorkspaceTest test
  ```

- [ ] **Step 3: 复用书目表单并提取副本/借阅面板**

  `BookFormCardPanel` 实现 dirty/lifecycle 适配；`CopyEditorPanel` 和 `LoanActionPanel` 从现有页面/弹窗提取字段和校验。删除 `LoanActionDialog`，保留纯确认和错误提示弹窗。

- [ ] **Step 4: 运行图书模块测试并提交**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest='edu.seu.vcampus.client.library.**' test
  git add vcampus-client/src/main/java/edu/seu/vcampus/client/library vcampus-client/src/test/java/edu/seu/vcampus/client/library
  git commit -m "refactor: embed library editors"
  ```

---

## Task 9: 迁移旧商城卖家与管理员表单

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerApplicationPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductEditorPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ShopProfilePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SkuEditorPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ApplicationReviewPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ShopStatusPanel.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SkuEditorDialog.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SwingSellerApplicationDialog.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SwingProductEditorDialogs.java`
- Modify: seller/admin tests under `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/`

- [ ] **Step 1: 将弹窗测试改写为 host 失败测试**

  覆盖开店申请、商品、SKU、店铺资料、需要输入理由的驳回/状态操作。断言详情仍可只读弹窗，模拟支付仍为例外；有字段的审核动作进入工作区。

- [ ] **Step 2: 运行聚焦测试确认失败**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=SellerApplicationPanelTest,ProductManagementPanelTest,SwingProductEditorDialogsTest,SwingSellerApplicationDialogTest,ApplicationReviewPanelTest,ShopStatusPanelTest test
  ```

- [ ] **Step 3: 复用现有面板，移除 dialog 包装器**

  `SellerApplicationPanel`、`ProductEditorPanel` 接入 host；SKU 提取为 `SkuEditorPanel`；店铺资料和审核理由表单以 `COMPACT` 展开。`ApplicationDetailDialog` 只保留只读详情，把其中的驳回原因输入移到 `ApplicationReviewPanel` 的工作区。删除编辑器自身标题下说明和“参考数据已就绪”等正常态提示。

- [ ] **Step 4: 运行旧商城测试并提交**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest='edu.seu.vcampus.client.shop.ui.**' test
  git add vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui
  git commit -m "refactor: embed shop management editors"
  ```

---

## Task 10: 迁移新版 commerce 与钱包录入表单

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/CommercePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/CommerceStage.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/CommerceForm.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/AccountPages.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/ProductEditorPage.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/ImportPage.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/GovernanceForms.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/GovernanceCasePages.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/GovernancePages.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/GovernanceProducts.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/SellerCatalogPage.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/OrderPages.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/WalletPage.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce/CommerceEditorBridge.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/commerce/CommerceEmbeddedEditorTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/commerce/WalletWorkspaceTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/commerce/CommerceUiTest.java`

- [ ] **Step 1: 写失败的 commerce/wallet 工作区测试**

  覆盖开店申请、商品编辑、目录导入、预置图片选择、举报处理、订单操作理由、治理表单和充值。断言 `ui.modal(...)`、`showInputDialog`、`showOptionDialog` 与带组件的 `showConfirmDialog` 不再承载数据录入，编辑器默认隐藏，复杂商品/导入表单在下侧，短开店申请/充值/治理输入优先右侧；钱包历史仅查询不创建编辑器。

- [ ] **Step 2: 运行聚焦测试确认失败**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=CommerceEmbeddedEditorTest,WalletWorkspaceTest,CommerceUiTest test
  ```

- [ ] **Step 3: 以桥接层接入共享 host**

  `CommerceEditorBridge` 把现有 commerce 页面组件、save/cancel 和 dirty 状态适配到 `EmbeddedEditor`。`CommercePanel`/`CommerceStage` 持有 host，不重写 transport。`AccountPages` 的开店申请、`SellerCatalogPage` 的预置图片选择、`GovernanceForms` 的所有有字段操作、`GovernanceCasePages` 的举报处理和 `OrderPages` 的理由输入接入 bridge；`GovernanceProducts` 的只读资料仍可使用 modal。只读产品详情、一次性结果和模拟支付仍用现有 modal；`CommerceForm` 不再假定处在 modal viewport；迁移完成后删除 `CommercePanel.input(...)`。

- [ ] **Step 4: 拆分超过 200 行或职责混合的 commerce 文件**

  若修改后的 `ProductEditorPage`、`GovernanceForms`、`WalletPage` 超过限制，按字段区、提交协调和页面编排拆成同包内 package-private 小类。保持现有请求 DTO 和错误映射不变。

- [ ] **Step 5: 运行 commerce 测试并提交**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest='edu.seu.vcampus.client.shop.commerce.**' test
  git add vcampus-client/src/main/java/edu/seu/vcampus/client/shop/commerce vcampus-client/src/test/java/edu/seu/vcampus/client/shop/commerce
  git commit -m "refactor: embed commerce and wallet editors"
  ```

---

## Task 11: 迁移用户角色、权限与账户维护表单

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserRoleEditorPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/ModulePermissionManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/AccountPanel.java`
- Delete: `vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserRoleDialog.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/user/ui/UserManagementWorkspaceTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/user/ui/ModulePermissionManagementPanelTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/user/ui/AccountManagementSafetyTest.java`

- [ ] **Step 1: 写失败的用户/权限工作区测试**

  覆盖角色分配、模块权限、用户状态以及需要字段的教师账户维护。断言工作区按需出现、权限不足时入口不可见/不可用、保存携带原版本并刷新当前选择。密码重置确认、首次改密、普通改密、退出和会话替换继续使用安全弹窗。

- [ ] **Step 2: 运行聚焦测试确认失败**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=UserManagementWorkspaceTest,ModulePermissionManagementPanelTest,AccountManagementSafetyTest test
  ```

- [ ] **Step 3: 提取角色面板并接入现有管理页**

  `UserRoleEditorPanel` 复用旧 dialog 的角色列表、版本和保存 command；管理页面持有 `COMPACT` host。已有页面内权限面板不再永久占位，编辑动作触发后才挂载。纯布尔切换若是直接表格操作可保留，但任何需要补充字段后提交的操作必须进入 host。

- [ ] **Step 4: 运行用户模块测试并提交**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest='edu.seu.vcampus.client.user.**' test
  git add vcampus-client/src/main/java/edu/seu/vcampus/client/user vcampus-client/src/test/java/edu/seu/vcampus/client/user
  git commit -m "refactor: embed account permission editors"
  ```

---

## Task 12: 全角色标题说明清理与静态防回归

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/shell/ModulePlaceholderPage.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/library/ui/LibraryDataPanel.java`
- Modify: all login-after business page classes that still render title descriptions
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/BusinessPageCopyPolicyTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/shell/PermissionNavigationRoleTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/UnifiedMainFrameTest.java`

- [ ] **Step 1: 写失败的文字策略测试**

  `BusinessPageCopyPolicyTest` 维护确定的禁止字符串集合并扫描 Swing 组件树/源码，至少包括：

  ```java
  Set.of(
      "按课程查看可选教学班",
      "维护学期名称、教学日期和状态",
      "查看书目信息和馆藏副本",
      "新增和短编辑使用统一课程表单",
      "参考数据已就绪",
      "尚无修改申请",
      "已加载"
  );
  ```

  角色遍历覆盖学生、教师、学院管理员、学籍管理员、课程管理员、图书管理员、商城管理员和系统管理员的所有可达业务页。登录页“校园服务 · 学术生活”必须仍存在。

- [ ] **Step 2: 运行测试确认它列出残留页面**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=BusinessPageCopyPolicyTest,PermissionNavigationRoleTest,UnifiedMainFrameTest test
  ```

- [ ] **Step 3: 清理全部业务页说明文字**

  删除主标题下用途说明、编辑器标题下重复说明、底部实现说明和正常态静态状态。`ModulePlaceholderPage`、`LibraryDataPanel` 及各模块 heading helper 改为只渲染面包屑/标题。不要删除字段标签、字段校验、加载中、空数据、错误、版本冲突或提交中状态。

- [ ] **Step 4: 运行角色遍历和模块 UI 测试**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=BusinessPageCopyPolicyTest,PermissionNavigationRoleTest,UnifiedMainFrameTest,CourseUiTest,LibraryUiTest,ShopUiTest,StudentModulePageFactoryTest test
  ```

- [ ] **Step 5: 提交全局文字清理**

  ```bash
  git add vcampus-client/src/main/java vcampus-client/src/test/java
  git commit -m "style: remove business page helper captions"
  ```

---

## Task 13: 完整验证、发行包与精确差异审阅

**Files:**
- Modify: `vcampus-distribution/lib/vCampusClient.jar`
- Verify only: `vcampus-distribution/data/vCampus.accdb`
- Verify only: `vcampus-distribution/lib/vCampusServer.jar`

- [ ] **Step 1: 运行遗留表面清单测试**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -Dtest=LegacyEditorSurfaceInventoryTest test
  ```

  预期：PASS；只有设计批准的弹窗例外留在清单中，不存在用 `showConfirmDialog` 承载业务表单的调用。

- [ ] **Step 2: 运行全量测试**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test
  ```

  如果 socket 测试因沙箱无法绑定 loopback，按工具要求申请权限后原命令重跑；不得跳过测试。

- [ ] **Step 3: 检查行数、格式和禁止表面**

  ```bash
  find vcampus-client/src/main/java -name '*.java' -print0 | xargs -0 wc -l | sort -nr | head -40
  rg -n 'new (CourseEditorDialog|TermEditorDialog|OfferingEditorDialog|UserRoleDialog|LoanActionDialog)|showConfirmDialog\([^,]+,[[:space:]]*[a-zA-Z]+Form' vcampus-client/src/main/java
  git diff --check origin/main...HEAD
  ```

  预期：本次新增 Java 文件均不超过 200 行；删除的编辑弹窗无实例化；无空白错误。

- [ ] **Step 4: 打包并只更新客户端发行 JAR**

  `vcampus-client/pom.xml` 的 shade 输出已直接指向发行目录，因此执行：

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl vcampus-client -am package
  ```

  预期 Maven 直接更新 `vcampus-distribution/lib/vCampusClient.jar`。不要更新或暂存 `vCampusServer.jar` 与 `vCampus.accdb`。

- [ ] **Step 5: 复核工作树与最终差异**

  ```bash
  git status --short
  git diff --stat origin/main...HEAD
  git diff --name-status origin/main...HEAD
  git diff -- vcampus-distribution/data/vCampus.accdb vcampus-distribution/lib/vCampusServer.jar
  ```

  预期：数据库和服务端 JAR 无差异；只包含源代码、测试、设计/计划文档及客户端 JAR。

- [ ] **Step 6: 提交发行包**

  ```bash
  git add vcampus-distribution/lib/vCampusClient.jar
  git commit -m "build: package embedded editor client"
  ```

- [ ] **Step 7: 使用完成前验证与代码审查技能**

  调用 `superpowers:verification-before-completion`，重新核对最近一次全量测试输出；随后调用 `superpowers:requesting-code-review` 审阅工作区生命周期、过期异步响应、权限、乐观锁、文件行数和弹窗例外。修复审查发现后重跑受影响测试与 `mvn test`。

- [ ] **Step 8: 最终提交状态检查**

  ```bash
  git status --short --branch
  git log --oneline --decorate origin/main..HEAD
  ```

  预期：工作树干净，提交按基础组件、各模块迁移、文字清理和发行包分层可审查。只有用户明确要求时才 push。
