package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.course.service.CourseClientException;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.RetakeCommand;
import edu.seu.vcampus.common.course.RetakeEligibility;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.course.AdjustmentAuditQuery;
import edu.seu.vcampus.common.course.AdjustmentAuditView;
import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.course.CourseView;
import edu.seu.vcampus.common.course.TermView;
import edu.seu.vcampus.common.course.ImportCourseOutcomesCommand;
import edu.seu.vcampus.common.course.CourseOutcome;
import edu.seu.vcampus.common.course.CreateCourseCommand;
import edu.seu.vcampus.common.course.UpdateCourseCommand;
import edu.seu.vcampus.common.course.CreateTermCommand;
import edu.seu.vcampus.common.course.UpdateTermCommand;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.UpdateOfferingCommand;
import edu.seu.vcampus.common.course.OfferingView;
import edu.seu.vcampus.common.course.TermPhaseView;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.protocol.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.JButton;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseUiTest {
    @Test
    void courseGatewaySearchesOnlyActiveTeachersThroughTheSharedUserClient() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        PageResult<UserSummary> result = new PageResult<>(List.of(), 0, 100, 0);
        AtomicReference<Boolean> socketCalledOnEdt = new AtomicReference<>();
        doAnswer(invocation -> {
            socketCalledOnEdt.set(SwingUtilities.isEventDispatchThread());
            return CompletableFuture.completedFuture(ResponseBody.success(result));
        })
                .when(connection).send(eq("USER_SEARCH"), any(UserSearchQuery.class), any(Duration.class));
        UserClientService users = new UserClientService(connection, "course-ui-test", Duration.ofSeconds(2));
        CourseClientGateway gateway = new CourseClientGateway(mock(CourseClientService.class), users);

        onEdt(() -> gateway.searchTeachers("TEA")).join();

        verify(connection).send("USER_SEARCH", new UserSearchQuery(
                "TEA", UserRole.TEACHER, AccountStatus.ACTIVE, 0, 100), Duration.ofSeconds(2));
        assertThat(socketCalledOnEdt.get()).isFalse();
    }

    @Test
    void previewGatewaySuppliesTeacherChoicesWithoutALiveServer() {
        assertThat(CourseUiGateway.preview().searchTeachers("").join().items())
                .hasSizeGreaterThanOrEqualTo(2)
                .allSatisfy(teacher -> {
                    assertThat(teacher.role()).isEqualTo(UserRole.TEACHER);
                    assertThat(teacher.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
                });
    }

    @Test
    void previewOfferingTeacherIdsResolveToHumanReadableLoginLabels() {
        CourseUiGateway preview = CourseUiGateway.preview();
        OfferingSummary offering = preview.searchOfferings(
                new OfferingSearchQuery("2026-autumn", "", null, false, 0, 20)).join().items().get(0);

        assertThat(preview.resolveTeacher(offering.teacherUserId()).join().map(UserSummary::loginId))
                .contains("zhang.teacher");
    }

    @Test
    void courseGatewayResolvesTeacherByIdAcrossActiveTeacherPages() {
        UserClientService users = mock(UserClientService.class);
        CourseClientGateway gateway = new CourseClientGateway(mock(CourseClientService.class), users);
        List<UserSummary> firstHundred = IntStream.range(0, 100)
                .mapToObj(index -> teacher("teacher-" + index, "teacher.login." + index))
                .toList();
        UserSummary target = teacher("teacher-target", "human.readable.login");
        UserSearchQuery firstQuery = new UserSearchQuery(null, UserRole.TEACHER, AccountStatus.ACTIVE, 0, 100);
        UserSearchQuery secondQuery = new UserSearchQuery(null, UserRole.TEACHER, AccountStatus.ACTIVE, 1, 100);
        when(users.searchUsers(firstQuery)).thenReturn(
                CompletableFuture.completedFuture(new PageResult<>(firstHundred, 0, 100, 101)));
        when(users.searchUsers(secondQuery)).thenReturn(
                CompletableFuture.completedFuture(new PageResult<>(List.of(target), 1, 100, 101)));

        assertThat(gateway.resolveTeacher("teacher-target").join())
                .contains(target);
        verify(users).searchUsers(firstQuery);
        verify(users).searchUsers(secondQuery);
    }

    @Test
    void scheduleEditorCreatesStructuredRowsAndMapsThemWithoutCsvParsing() throws Exception {
        OfferingScheduleEditorPanel editor = onEdt(OfferingScheduleEditorPanel::new);
        SwingUtilities.invokeAndWait(() -> button(editor, "添加上课时间").doClick());

        assertThat(descendants(editor).stream().filter(JComboBox.class::isInstance)).isNotEmpty();
        assertThat(descendants(editor).stream().filter(JSpinner.class::isInstance)).hasSizeGreaterThanOrEqualTo(4);
        assertThat(((SpinnerNumberModel) component(editor, "第 1 行起始节次", JSpinner.class).getModel()).getMinimum())
                .isEqualTo(1);
        assertThat(((SpinnerNumberModel) component(editor, "第 1 行结束节次", JSpinner.class).getModel()).getMaximum())
                .isEqualTo(14);
        assertThat(((SpinnerNumberModel) component(editor, "第 1 行起始周", JSpinner.class).getModel()).getMinimum())
                .isEqualTo(1);
        assertThat(((SpinnerNumberModel) component(editor, "第 1 行结束周", JSpinner.class).getModel()).getMaximum())
                .isEqualTo(30);
        assertThat(editor.scheduleInputs()).containsExactly(
                new CreateOfferingCommand.ScheduleInput("MONDAY", 1, 2, 1, 16, "待定"));
    }

    @ParameterizedTest
    @CsvSource({
            "第 1 行起始节次,3,第 1 行结束节次,2,第 1 行：结束节次不能早于起始节次",
            "第 1 行起始周,17,第 1 行结束周,16,第 1 行：结束周不能早于起始周"
    })
    void scheduleEditorIdentifiesInvalidRowOrdering(
            String firstName, int firstValue, String secondName, int secondValue, String message) throws Exception {
        OfferingScheduleEditorPanel editor = onEdt(OfferingScheduleEditorPanel::new);
        SwingUtilities.invokeAndWait(() -> {
            button(editor, "添加上课时间").doClick();
            component(editor, firstName, JSpinner.class).setValue(firstValue);
            component(editor, secondName, JSpinner.class).setValue(secondValue);
        });

        assertThatThrownBy(editor::scheduleInputs)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    @Test
    void scheduleEditorRejectsBlankRoomAndRemovalOfTheFinalRowPrecisely() throws Exception {
        OfferingScheduleEditorPanel editor = onEdt(OfferingScheduleEditorPanel::new);
        SwingUtilities.invokeAndWait(() -> {
            button(editor, "添加上课时间").doClick();
            textField(editor, "第 1 行教室").setText("   ");
        });
        assertThatThrownBy(editor::scheduleInputs)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("第 1 行：请输入教室");

        SwingUtilities.invokeAndWait(() -> button(editor, "删除第 1 行").doClick());
        assertThatThrownBy(editor::scheduleInputs)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请至少添加一行上课时间");
    }

    @ParameterizedTest
    @CsvSource({"第 1 行起始节次,0", "第 1 行结束节次,15", "第 1 行起始周,0", "第 1 行结束周,31"})
    void scheduleEditorEnforcesPeriodAndWeekBoundsWhenValuesAreCommitted(String name, int value)
            throws Exception {
        OfferingScheduleEditorPanel editor = onEdt(OfferingScheduleEditorPanel::new);
        SwingUtilities.invokeAndWait(() -> button(editor, "添加上课时间").doClick());

        assertThatThrownBy(() -> component(editor, name, JSpinner.class).setValue(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletingThirdScheduleRowKeepsVisibleRowsAndSubmittedRowsInSync() throws Exception {
        OfferingScheduleEditorPanel editor = onEdt(OfferingScheduleEditorPanel::new);
        SwingUtilities.invokeAndWait(() -> {
            button(editor, "添加上课时间").doClick();
            button(editor, "添加上课时间").doClick();
            button(editor, "添加上课时间").doClick();
            textField(editor, "第 1 行教室").setText("教室一");
            textField(editor, "第 2 行教室").setText("教室二");
            textField(editor, "第 3 行教室").setText("教室三");
            button(editor, "删除第 3 行").doClick();
        });

        assertThat(textField(editor, "第 1 行教室").getText()).isEqualTo("教室一");
        assertThat(textField(editor, "第 2 行教室").getText()).isEqualTo("教室二");
        assertThat(component(editor, "第 3 行教室", JTextField.class)).isNull();
        assertThat(buttons(editor)).contains("删除第 1 行", "删除第 2 行")
                .doesNotContain("删除第 3 行");
        assertThat(editor.scheduleInputs()).extracting(CreateOfferingCommand.ScheduleInput::classroom)
                .containsExactly("教室一", "教室二");
    }

    @ParameterizedTest
    @MethodSource("roleTabs")
    void workspaceOwnsRoleFilteredInternalTabs(UserRole role, List<String> expected) throws Exception {
        CourseWorkspacePanel workspace = onEdt(
                () -> new CourseWorkspacePanel(CourseUiGateway.preview(), role));
        JTabbedPane tabs = descendants(workspace).stream()
                .filter(JTabbedPane.class::isInstance).map(JTabbedPane.class::cast)
                .findFirst().orElseThrow();
        assertThat(IntStream.range(0, tabs.getTabCount()).mapToObj(tabs::getTitleAt))
                .containsExactlyElementsOf(expected);
        assertThat(workspace.getName()).isEqualTo("page.course");
    }

    static Stream<Arguments> roleTabs() {
        return Stream.of(
                Arguments.of(UserRole.STUDENT, List.of("选课", "我的选课", "我的课表")),
                Arguments.of(UserRole.TEACHER, List.of("教学班查询", "教师课表")),
                Arguments.of(UserRole.ADMIN, List.of("学期管理", "选课阶段", "课程目录", "教学班管理", "修读结果导入", "选退记录")));
    }

    @Test
    void unifiedStudentSelectionUsesPhaseTitleAndPerCourseActions() throws Exception {
        StudentCourseSelectionPanel panel = onEdt(() -> new StudentCourseSelectionPanel(CourseUiGateway.preview()));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(panel)).contains("2026-2027秋季学期退改补选课", "共 3 门课程");
        assertThat(buttons(panel)).contains("取消选课", "补选课程");
        assertThat(buttons(panel)).doesNotContain("选择教学班");
    }

    @Test
    void unifiedSelectionRequiresAnExplicitTeachingClassChoice() throws Exception {
        StudentCourseSelectionPanel panel = onEdt(() -> new StudentCourseSelectionPanel(CourseUiGateway.preview()));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        JButton add = button(panel, "补选课程");
        assertThat(add.isEnabled()).isFalse();
        JRadioButton option = descendants(panel).stream().filter(JRadioButton.class::isInstance)
                .map(JRadioButton.class::cast).filter(AbstractButton::isEnabled).findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(option::doClick);
        assertThat(add.isEnabled()).isTrue();
    }

    @Test
    void myEnrollmentsIsReadOnlyBecauseDropsBelongToUnifiedSelection() throws Exception {
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(CourseUiGateway.preview()));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(buttons(panel)).doesNotContain("退选所选课程");
    }

    @Test
    void phaseManagementKeepsEveryStateControlInsideTheToolbarAtDesktopWidth() throws Exception {
        SelectionPhaseManagementPanel panel = onEdt(() -> new SelectionPhaseManagementPanel(CourseUiGateway.preview()));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(1100, 650);
            layoutTree(panel);
        });

        for (String text : List.of("新建阶段", "应用状态", "保存标题", "刷新")) {
            JButton control = button(panel, text);
            assertThat(control.getHeight()).as(text).isPositive();
            assertThat(control.getY() + control.getHeight()).as(text)
                    .isLessThanOrEqualTo(control.getParent().getHeight());
        }
    }

    @Test
    void queryPageUsesReviewedTemplateAndSharedTokens() throws Exception {
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(CourseUiGateway.preview()));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(panel.getLayout()).isInstanceOf(BorderLayout.class);
        assertThat(panel.getBackground()).isEqualTo(UiColors.BACKGROUND_PAGE);
        assertThat(panel.getBorder().getBorderInsets(panel).top).isEqualTo(UiSpacing.PAGE_PADDING);
        assertThat(labels(panel)).contains("课程中心  /  教学班查询", "教学班查询", "共 3 条");
        assertThat(buttons(panel)).contains("查询教学班", "重置条件", "上一页", "下一页");
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();
        assertThat(table.getRowHeight()).isEqualTo(UiDimensions.TABLE_ROW_HEIGHT);
        assertThat(table.getShowVerticalLines()).isFalse();
        assertThat(table.getTableHeader().getBackground()).isEqualTo(UiColors.BACKGROUND_SUBTLE);
        assertThat(table.getValueAt(0, 4).toString()).startsWith("星期一");
        assertThat(descendants(panel)).anyMatch(JScrollPane.class::isInstance);
    }

    @Test
    void constrainedTableCellsExposeTheirFullTextAsATooltip() throws Exception {
        JTable table = onEdt(() -> AbstractCoursePanel.table(
                new Object[][]{{"08-20 08:00 至 09-01 00:00"}},
                new Object[]{"正常选课窗口"}));

        Component rendered = onEdt(() -> table.getCellRenderer(0, 0)
                .getTableCellRendererComponent(table, table.getValueAt(0, 0), false, false, 0, 0));

        assertThat(rendered).isInstanceOf(JComponent.class);
        assertThat(((JComponent) rendered).getToolTipText())
                .isEqualTo("08-20 08:00 至 09-01 00:00");
    }

    @Test
    void offeringSearchUsesResolvedTermIdAndSelectedWeekday() throws Exception {
        AtomicReference<OfferingSearchQuery> submitted = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<String> currentTermId() {
                return CompletableFuture.completedFuture("term-real-uuid");
            }
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                submitted.set(query);
                return CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 20, 0));
            }
        };
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> {
            descendants(panel).stream().filter(JComboBox.class::isInstance).map(JComboBox.class::cast)
                    .filter(combo -> "上课日期".equals(combo.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow().setSelectedIndex(2);
            descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "查询教学班".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get().termId()).isEqualTo("term-real-uuid");
        assertThat(submitted.get().dayOfWeek()).isEqualTo("TUESDAY");
    }

    @ParameterizedTest
    @CsvSource({"星期六,SATURDAY", "星期日,SUNDAY"})
    void offeringSearchSupportsWeekendFilters(String selectedLabel, String expectedDay) throws Exception {
        AtomicReference<OfferingSearchQuery> submitted = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                submitted.set(query);
                return CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 20, 0));
            }
        };
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> {
            descendants(panel).stream().filter(JComboBox.class::isInstance).map(JComboBox.class::cast)
                    .filter(combo -> "上课日期".equals(combo.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow().setSelectedItem(selectedLabel);
            descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "查询教学班".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get().dayOfWeek()).isEqualTo(expectedDay);
    }

    @Test
    void offeringSearchNavigatesRealServerPagesAndDisablesTheLastNextButton() throws Exception {
        List<OfferingSearchQuery> queries = new ArrayList<>();
        OfferingSummary item = CourseUiGateway.preview()
                .searchOfferings(new OfferingSearchQuery("2026-autumn", "", null, true, 0, 20))
                .join().items().getFirst();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(CourseUiGateway.preview()) {
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                queries.add(query);
                return CompletableFuture.completedFuture(new PageResult<>(List.of(item), query.page(), 20, 25));
            }
        };
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> descendants(panel).stream().filter(JButton.class::isInstance)
                .map(JButton.class::cast).filter(button -> "下一页".equals(button.getText()))
                .findFirst().orElseThrow().doClick());
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(queries).extracting(OfferingSearchQuery::page).containsExactly(0, 1);
        assertThat(labels(panel)).contains("第 2 / 2 页");
        JButton next = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "下一页".equals(button.getText())).findFirst().orElseThrow();
        assertThat(next.isEnabled()).isFalse();
    }

    @Test
    void failedOfferingRefreshKeepsPreviouslyLoadedRows() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                if (calls.incrementAndGet() == 1) return base.searchOfferings(query);
                return CompletableFuture.failedFuture(
                        new CourseClientException("COMMON_NETWORK_ERROR", "network", null, true));
            }
        };
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();
        assertThat(table.getRowCount()).isEqualTo(3);

        SwingUtilities.invokeAndWait(() -> descendants(panel).stream().filter(JButton.class::isInstance)
                .map(JButton.class::cast).filter(button -> "查询教学班".equals(button.getText()))
                .findFirst().orElseThrow().doClick());
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(table.getRowCount()).isEqualTo(3);
        assertThat(labels(panel)).contains("共 3 条");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.DISCONNECTED);
    }

    @Test
    void failedEnrollmentRefreshKeepsPreviouslyLoadedRows() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                if (calls.incrementAndGet() == 1) return base.currentEnrollments();
                return CompletableFuture.failedFuture(new CourseClientException(
                        "COMMON_NETWORK_ERROR", "network", null, true));
            }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> descendants(panel).stream().filter(JButton.class::isInstance)
                .map(JButton.class::cast).filter(button -> "刷新选课".equals(button.getText()))
                .findFirst().orElseThrow().doClick());
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(labels(panel)).contains("共 1 条");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.DISCONNECTED);
    }

    @Test
    void failedRetakeRefreshKeepsPreviouslyLoadedRows() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                if (calls.incrementAndGet() == 1) return base.searchOfferings(query);
                return CompletableFuture.failedFuture(new CourseClientException(
                        "COMMON_NETWORK_ERROR", "network", null, true));
            }
        };
        RetakePanel panel = onEdt(() -> new RetakePanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> descendants(panel).stream().filter(JButton.class::isInstance)
                .map(JButton.class::cast).filter(button -> "刷新教学班".equals(button.getText()))
                .findFirst().orElseThrow().doClick());
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(table.getRowCount()).isEqualTo(3);
        assertThat(labels(panel)).contains("共 3 个可选教学班");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.DISCONNECTED);
    }

    @Test
    void failedTermRefreshKeepsPreviouslyLoadedRows() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<TermView>> listTerms() {
                if (calls.incrementAndGet() == 1) return base.listTerms();
                return CompletableFuture.failedFuture(new CourseClientException(
                        "COMMON_NETWORK_ERROR", "network", null, true));
            }
        };
        TermManagementPanel panel = onEdt(() -> new TermManagementPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> descendants(panel).stream().filter(JButton.class::isInstance)
                .map(JButton.class::cast).filter(button -> "刷新学期".equals(button.getText()))
                .findFirst().orElseThrow().doClick());
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.DISCONNECTED);
    }

    @Test
    void everyPagedAdministrationListCanReachItsSecondServerPage() throws Exception {
        List<CourseCatalogQuery> catalogQueries = new ArrayList<>();
        List<AdjustmentAuditQuery> auditQueries = new ArrayList<>();
        List<OfferingSearchQuery> offeringQueries = new ArrayList<>();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(CourseUiGateway.preview()) {
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                catalogQueries.add(query);
                return CompletableFuture.completedFuture(new PageResult<>(List.of(), query.page(), 50, 101));
            }
            @Override public CompletableFuture<PageResult<AdjustmentAuditView>> searchAdjustmentAudits(
                    AdjustmentAuditQuery query) {
                auditQueries.add(query);
                return CompletableFuture.completedFuture(new PageResult<>(List.of(), query.page(), 50, 101));
            }
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                offeringQueries.add(query);
                return CompletableFuture.completedFuture(new PageResult<>(List.of(), query.page(), 50, 101));
            }
        };
        CourseCatalogPanel catalog = onEdt(() -> new CourseCatalogPanel(gateway));
        AdjustmentAuditPanel audits = onEdt(() -> new AdjustmentAuditPanel(gateway));
        OfferingManagementPanel offerings = onEdt(() -> new OfferingManagementPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        for (Container page : List.of(catalog, audits, offerings)) {
            SwingUtilities.invokeAndWait(() -> descendants(page).stream().filter(JButton.class::isInstance)
                    .map(JButton.class::cast).filter(button -> "下一页".equals(button.getText()))
                    .findFirst().orElseThrow().doClick());
        }
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(catalogQueries).extracting(CourseCatalogQuery::page).containsExactly(0, 1);
        assertThat(auditQueries).extracting(AdjustmentAuditQuery::page).containsExactly(0, 1);
        assertThat(offeringQueries).extracting(OfferingSearchQuery::page).containsExactly(0, 1);
    }

    @Test
    void scheduleUsesAWeeklyGridInsteadOfAPlainEnrollmentTable() throws Exception {
        MySchedulePanel panel = onEdt(() -> new MySchedulePanel(CourseUiGateway.preview()));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(panel)).contains("我的课表", "星期一", "星期二", "星期三", "星期四", "星期五");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
        assertThat(descendants(panel).stream().filter(JPanel.class::isInstance)
                .map(JPanel.class::cast).anyMatch(p -> p.getLayout() instanceof java.awt.GridBagLayout)).isTrue();
    }

    @Test
    void scheduleGridIncludesWeekendAndActualHighPeriods() throws Exception {
        ScheduleItem saturday = new ScheduleItem("s-weekend", "o-weekend", "ART901", "周末艺术",
                "周末班", "teacher-1", "SATURDAY", 9, 10, 2, 18, "艺术楼-9");
        MySchedulePanel panel = onEdt(() -> new MySchedulePanel(
                scheduleGateway(CompletableFuture.completedFuture(List.of(saturday)))));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(panel)).contains("星期六", "星期日", "第 9 节", "第 10 节");
        assertThat(labels(panel)).anyMatch(text -> text.contains("周末艺术") && text.contains("第2–18周"));
        assertThat(labels(panel)).anyMatch(text -> text.contains("周末艺术（续）"));
    }

    @Test
    void scheduleGridKeepsCoursesSharingATimeSlotInDifferentWeeks() throws Exception {
        List<ScheduleItem> splitWeeks = List.of(
                new ScheduleItem("s-first", "o-first", "CS301", "编译原理", "01班", "teacher-1",
                        "TUESDAY", 3, 4, 1, 8, "教一-101"),
                new ScheduleItem("s-second", "o-second", "CS302", "操作系统", "02班", "teacher-2",
                        "TUESDAY", 3, 4, 9, 16, "教一-102"));
        MySchedulePanel panel = onEdt(() -> new MySchedulePanel(
                scheduleGateway(CompletableFuture.completedFuture(splitWeeks))));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(panel)).anyMatch(text -> text.contains("编译原理") && text.contains("第1–8周")
                && text.contains("操作系统") && text.contains("第9–16周"));
    }

    @Test
    void scheduleSummaryUsesTheResolvedCurrentTermInsteadOfAHardcodedSemester() throws Exception {
        ScheduleItem item = new ScheduleItem("s1", "o1", "CS301", "编译原理", "01班", "teacher-1",
                "MONDAY", 1, 2, 2, 18, "教一-101");
        MySchedulePanel panel = onEdt(() -> new MySchedulePanel(
                scheduleGateway(CompletableFuture.completedFuture(List.of(item)))));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(panel)).contains("2026—2027学年秋季学期 · 第 2–18 周");
        assertThat(labels(panel)).noneMatch(text -> text.contains("2026–2027 学年秋季学期"));
    }

    @Test
    void scheduleShowsLoadingThenEmptyInsteadOfAnUnexplainedBlankGrid() throws Exception {
        CompletableFuture<List<ScheduleItem>> request = new CompletableFuture<>();
        MySchedulePanel panel = onEdt(() -> new MySchedulePanel(scheduleGateway(request)));

        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.LOADING);
        request.complete(List.of());
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.EMPTY);
        assertThat(labels(panel)).anyMatch(text -> text.contains("本学期还没有课程安排"));
    }

    @Test
    void scheduleShowsDisconnectedStateWhenLoadingFails() throws Exception {
        CompletableFuture<List<ScheduleItem>> failed = CompletableFuture.failedFuture(
                new CourseClientException("COMMON_NETWORK_ERROR", "socket details", null, true));
        MySchedulePanel panel = onEdt(() -> new MySchedulePanel(scheduleGateway(failed)));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.DISCONNECTED);
        assertThat(labels(panel)).anyMatch(text -> text.contains("无法加载课表") && text.contains("检查连接"));
        assertThat(labels(panel)).noneMatch(text -> text.contains("socket details"));
    }

    @Test
    void publishesAllElevenCourseSurfaces() throws Exception {
        List<String> names = List.of("OfferingSearchPanel", "OfferingDetailDialog", "MyEnrollmentPanel",
                "MySchedulePanel", "AdjustmentPanel", "RetakePanel", "TermManagementPanel",
                "CourseCatalogPanel", "OfferingManagementPanel", "OutcomeImportPanel", "AdjustmentAuditPanel");
        for (String name : names) {
            assertThat(Class.forName(getClass().getPackageName() + "." + name)).isNotNull();
        }
    }

    @Test
    void compositionCreatesIndependentRoleWorkspacesUnderTheCoursePageId() throws Exception {
        CourseUiComposition composition = onEdt(() -> new CourseUiComposition(CourseUiGateway.preview()));

        CourseWorkspacePanel first = onEdt(() -> composition.workspaceFor(UserRole.STUDENT));
        CourseWorkspacePanel second = onEdt(() -> composition.workspaceFor(UserRole.STUDENT));

        assertThat(first).isNotSameAs(second);
        assertThat(first.getName()).isEqualTo("page.course");
        assertThat(second.getName()).isEqualTo("page.course");
    }

    @Test
    void everyInteractiveCoursePageControlHasAnAccessibleNameAndKeyboardFocus() throws Exception {
        CourseUiComposition composition = onEdt(() -> new CourseUiComposition(CourseUiGateway.preview()));
        List<CourseWorkspacePanel> workspaces = onEdt(() -> List.of(
                composition.workspaceFor(UserRole.STUDENT),
                composition.workspaceFor(UserRole.ADMIN)));
        List<String> missingNames = new ArrayList<>();
        List<String> inaccessibleByKeyboard = new ArrayList<>();

        for (CourseWorkspacePanel workspace : workspaces) {
            JTabbedPane tabs = descendants(workspace).stream()
                    .filter(JTabbedPane.class::isInstance).map(JTabbedPane.class::cast)
                    .findFirst().orElseThrow();
            for (int index = 0; index < tabs.getTabCount(); index++) {
                int selected = index;
                SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(selected));
            }
        }
        SwingUtilities.invokeAndWait(() -> { });

        onEdt(() -> {
            workspaces.forEach(workspace -> descendants(workspace).stream()
                    .filter(CourseUiTest::isInteractiveControl)
                    .forEach(component -> {
                        String name = component.getAccessibleContext().getAccessibleName();
                        String description = component.getClass().getSimpleName();
                        if (name == null || name.isBlank()) missingNames.add(description);
                        if (!component.isFocusable()) inaccessibleByKeyboard.add(description + ":" + name);
                    }));
            return null;
        });

        assertThat(missingNames).as("interactive controls without accessible names").isEmpty();
        assertThat(inaccessibleByKeyboard).as("interactive controls excluded from keyboard focus").isEmpty();
    }

    @Test
    void queryShowsLoadingEmptyAndDisconnectedStatesWithActionableChineseText() throws Exception {
        CompletableFuture<PageResult<OfferingSummary>> request = new CompletableFuture<>();
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(gateway(request)));
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.LOADING);

        request.complete(new PageResult<>(List.of(), 0, 20, 0));
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.EMPTY);
        assertThat(labels(panel)).anyMatch(text -> text.contains("未找到教学班"));

        CompletableFuture<PageResult<OfferingSummary>> failed = CompletableFuture.failedFuture(
                new CourseClientException("COMMON_NETWORK_ERROR", "socket details", null, true));
        OfferingSearchPanel disconnected = onEdt(() -> new OfferingSearchPanel(gateway(failed)));
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(disconnected.viewState()).isEqualTo(AbstractCoursePanel.ViewState.DISCONNECTED);
        assertThat(labels(disconnected)).anyMatch(text -> text.contains("连接已断开"));
        assertThat(labels(disconnected)).noneMatch(text -> text.contains("socket details"));
    }

    @Test
    void selectedOfferingCanBeEnrolledAndSuccessRemainsVisible() throws Exception {
        AtomicReference<EnrollCommand> submitted = new AtomicReference<>();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                return CourseUiGateway.preview().searchOfferings(query);
            }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new EnrollmentView(
                        "e1", command.offeringId(), "s1", "NORMAL", "ACTIVE", Instant.now(), null, 0));
            }
        };
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();
        JButton enroll = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "选择教学班".equals(button.getText())).findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> { table.setRowSelectionInterval(0, 0); enroll.doClick(); });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get().offeringId()).isEqualTo("o1");
        assertThat(labels(panel)).anyMatch(text -> text.contains("课程已选上"));
    }

    @Test
    void myEnrollmentsLoadsRealGatewayRowsAsynchronously() throws Exception {
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return base.searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(List.of(new EnrollmentView(
                        "e1", "offering-real-1", "student-real", "NORMAL", "ACTIVE", Instant.parse("2026-08-28T08:00:00Z"), null, 2)));
            }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return base.enroll(command); }
            @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return base.getTermPhase(termId);
            }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();

        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("offering-real-1");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    @Disabled("Obsolete: drop mutations moved to the unified selection page")
    void myEnrollmentsDropsTheSelectedActiveRowAndRefreshesAuthoritativeData() throws Exception {
        AtomicReference<DropCommand> submitted = new AtomicReference<>();
        AtomicReference<String> confirmedLabel = new AtomicReference<>();
        AtomicInteger refreshCalls = new AtomicInteger();
        AtomicInteger invalidations = new AtomicInteger();
        EnrollmentView active = new EnrollmentView(
                "enrollment-1", "offering-1", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 7);
        CourseUiGateway gateway = new DelegatingCourseUiGateway(
                enrollmentGateway(List.of(active), "ENROLLMENT", "ACTIVE")) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                refreshCalls.incrementAndGet();
                return CompletableFuture.completedFuture(List.of(active));
            }

            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(EmptyResponse.INSTANCE);
            }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(gateway,
                (owner, courseLabel) -> {
                    confirmedLabel.set(courseLabel);
                    return true;
                }, invalidations::incrementAndGet));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = enrollmentTable(panel);

        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        assertThat(button(panel, "退选所选课程").isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(() -> button(panel, "退选所选课程").doClick());
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new DropCommand("enrollment-1", 7));
        assertThat(confirmedLabel.get()).contains("offering-1");
        assertThat(refreshCalls).hasValue(2);
        assertThat(invalidations).hasValue(1);
        assertThat(labels(panel)).anyMatch(text -> text.contains("正常选课开放")
                && text.contains("选课或退课请前往“选课”页"));
    }

    @Test
    @Disabled("Obsolete: drop mutations moved to the unified selection page")
    void myEnrollmentsMapsASortedViewRowBackToItsEnrollment() throws Exception {
        AtomicReference<DropCommand> submitted = new AtomicReference<>();
        EnrollmentView first = new EnrollmentView(
                "enrollment-1", "offering-a", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 7);
        EnrollmentView second = new EnrollmentView(
                "enrollment-2", "offering-z", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-29T08:00:00Z"), null, 11);
        CourseUiGateway gateway = new DelegatingCourseUiGateway(
                enrollmentGateway(List.of(first, second), "ENROLLMENT", "ACTIVE")) {
            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(EmptyResponse.INSTANCE);
            }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(gateway, (owner, label) -> true, () -> { }));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = enrollmentTable(panel);

        SwingUtilities.invokeAndWait(() -> {
            table.getRowSorter().toggleSortOrder(0);
            table.getRowSorter().toggleSortOrder(0);
            table.setRowSelectionInterval(0, 0);
            button(panel, "退选所选课程").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new DropCommand("enrollment-2", 11));
    }

    @ParameterizedTest
    @Disabled("Obsolete: the enrollment list is now read-only")
    @CsvSource({
            "ENROLLMENT,ACTIVE,true",
            "ADJUSTMENT,ACTIVE,true",
            "READ_ONLY,ACTIVE,false",
            "CLOSED,CLOSED,false"
    })
    void myEnrollmentDropAvailabilityFollowsTheAuthoritativePhase(
            String phase, String termStatus, boolean expectedEnabled) throws Exception {
        EnrollmentView active = new EnrollmentView(
                "enrollment-1", "offering-1", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 7);
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(
                enrollmentGateway(List.of(active), phase, termStatus), (owner, label) -> true, () -> { }));
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> enrollmentTable(panel).setRowSelectionInterval(0, 0));

        assertThat(button(panel, "退选所选课程").isEnabled()).isEqualTo(expectedEnabled);
    }

    @Test
    @Disabled("Obsolete: the enrollment list is now read-only")
    void myEnrollmentDropRequiresAnActiveSelectedRow() throws Exception {
        EnrollmentView dropped = new EnrollmentView(
                "enrollment-1", "offering-1", "student-1", "NORMAL", "DROPPED",
                Instant.parse("2026-08-28T08:00:00Z"), Instant.parse("2026-08-29T08:00:00Z"), 8);
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(
                enrollmentGateway(List.of(dropped), "ENROLLMENT", "ACTIVE"),
                (owner, label) -> true, () -> { }));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(button(panel, "退选所选课程").isEnabled()).isFalse();
        SwingUtilities.invokeAndWait(() -> enrollmentTable(panel).setRowSelectionInterval(0, 0));
        assertThat(button(panel, "退选所选课程").isEnabled()).isFalse();
    }

    @Test
    @Disabled("Obsolete: the enrollment list is now read-only")
    void myEnrollmentDropStaysDisabledWithoutASelection() throws Exception {
        EnrollmentView active = new EnrollmentView(
                "enrollment-1", "offering-1", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 7);
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(
                enrollmentGateway(List.of(active), "ENROLLMENT", "ACTIVE"),
                (owner, label) -> true, () -> { }));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(enrollmentTable(panel).getSelectedRow()).isEqualTo(-1);
        assertThat(button(panel, "退选所选课程").isEnabled()).isFalse();
    }

    @Test
    @Disabled("Obsolete: drop confirmation is covered by the unified selection page")
    void myEnrollmentDropStopsWhenConfirmationIsRejected() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        EnrollmentView active = new EnrollmentView(
                "enrollment-1", "offering-1", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 7);
        CourseUiGateway gateway = new DelegatingCourseUiGateway(
                enrollmentGateway(List.of(active), "ENROLLMENT", "ACTIVE")) {
            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) {
                submissions.incrementAndGet();
                return CompletableFuture.completedFuture(EmptyResponse.INSTANCE);
            }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(
                gateway, (owner, label) -> false, () -> { }));
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> {
            enrollmentTable(panel).setRowSelectionInterval(0, 0);
            button(panel, "退选所选课程").doClick();
        });

        assertThat(submissions).hasValue(0);
        assertThat(button(panel, "退选所选课程").isEnabled()).isTrue();
    }

    @Test
    @Disabled("Obsolete: drop failure handling is covered by the unified selection page")
    void failedImmediateDropKeepsRowsAndRestoresTheAction() throws Exception {
        EnrollmentView active = new EnrollmentView(
                "enrollment-1", "offering-1", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 7);
        CourseUiGateway gateway = new DelegatingCourseUiGateway(
                enrollmentGateway(List.of(active), "ADJUSTMENT", "ACTIVE")) {
            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) {
                return CompletableFuture.failedFuture(
                        new CourseClientException("COURSE_DROP_FAILED", "服务端拒绝退选", null, false));
            }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(
                gateway, (owner, label) -> true, () -> { }));
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> {
            enrollmentTable(panel).setRowSelectionInterval(0, 0);
            button(panel, "退选所选课程").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(enrollmentTable(panel).getRowCount()).isEqualTo(1);
        assertThat(button(panel, "退选所选课程").isEnabled()).isTrue();
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.ERROR);
        assertThat(labels(panel)).contains("服务端拒绝退选");
    }

    @Test
    @Disabled("Obsolete: drop mutations moved to the unified selection page")
    void refreshDuringPendingImmediateDropCannotSupersedeTheMutation() throws Exception {
        CompletableFuture<EmptyResponse> pendingDrop = new CompletableFuture<>();
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger refreshCalls = new AtomicInteger();
        AtomicInteger invalidations = new AtomicInteger();
        EnrollmentView active = new EnrollmentView(
                "enrollment-1", "offering-1", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 7);
        CourseUiGateway gateway = new DelegatingCourseUiGateway(
                enrollmentGateway(List.of(active), "ENROLLMENT", "ACTIVE")) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                refreshCalls.incrementAndGet();
                return CompletableFuture.completedFuture(List.of(active));
            }

            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) {
                submissions.incrementAndGet();
                return pendingDrop;
            }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(
                gateway, (owner, label) -> true, invalidations::incrementAndGet));
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> {
            enrollmentTable(panel).setRowSelectionInterval(0, 0);
            button(panel, "退选所选课程").doClick();
            button(panel, "刷新选课").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            enrollmentTable(panel).setRowSelectionInterval(0, 0);
            button(panel, "退选所选课程").doClick();
        });

        assertThat(submissions).hasValue(1);
        assertThat(button(panel, "退选所选课程").isEnabled()).isFalse();
        pendingDrop.complete(EmptyResponse.INSTANCE);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(invalidations).hasValue(1);
        assertThat(refreshCalls).hasValue(3);
    }

    @Test
    @Disabled("Obsolete: drop mutations moved to the unified selection page")
    void hiddenEnrollmentPageStillInvalidatesAfterSuccessfulDropAndRefreshesWhenShown() throws Exception {
        CompletableFuture<EmptyResponse> pendingDrop = new CompletableFuture<>();
        AtomicInteger refreshCalls = new AtomicInteger();
        AtomicInteger invalidations = new AtomicInteger();
        EnrollmentView active = new EnrollmentView(
                "enrollment-1", "offering-1", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 7);
        CourseUiGateway gateway = new DelegatingCourseUiGateway(
                enrollmentGateway(List.of(active), "ENROLLMENT", "ACTIVE")) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                refreshCalls.incrementAndGet();
                return CompletableFuture.completedFuture(List.of(active));
            }

            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) { return pendingDrop; }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(
                gateway, (owner, label) -> true, invalidations::incrementAndGet));
        JPanel cards = onEdt(() -> {
            JPanel host = new JPanel(new CardLayout());
            host.add(panel, "course");
            host.add(new JPanel(), "other");
            ((CardLayout) host.getLayout()).show(host, "course");
            return host;
        });
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> {
            enrollmentTable(panel).setRowSelectionInterval(0, 0);
            button(panel, "退选所选课程").doClick();
            ((CardLayout) cards.getLayout()).show(cards, "other");
        });
        pendingDrop.complete(EmptyResponse.INSTANCE);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(panel.isVisible()).isFalse();
        assertThat(invalidations).hasValue(1);
        assertThat(refreshCalls).hasValue(1);
        SwingUtilities.invokeAndWait(() -> ((CardLayout) cards.getLayout()).show(cards, "course"));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(refreshCalls).hasValue(2);
    }

    @Test
    @Disabled("Obsolete: drop mutations moved to the unified selection page")
    void removedEnrollmentPageIgnoresALateDropCompletion() throws Exception {
        CompletableFuture<EmptyResponse> request = new CompletableFuture<>();
        AtomicInteger refreshCalls = new AtomicInteger();
        AtomicInteger invalidations = new AtomicInteger();
        EnrollmentView active = new EnrollmentView(
                "enrollment-1", "offering-1", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 7);
        CourseUiGateway gateway = new DelegatingCourseUiGateway(
                enrollmentGateway(List.of(active), "ENROLLMENT", "ACTIVE")) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                refreshCalls.incrementAndGet();
                return CompletableFuture.completedFuture(List.of(active));
            }

            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) { return request; }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(
                gateway, (owner, label) -> true, invalidations::incrementAndGet));
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> {
            enrollmentTable(panel).setRowSelectionInterval(0, 0);
            button(panel, "退选所选课程").doClick();
        });
        assertThat(button(panel, "退选所选课程").isEnabled()).isFalse();
        SwingUtilities.invokeAndWait(panel::removeNotify);
        request.complete(EmptyResponse.INSTANCE);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(refreshCalls).hasValue(1);
        assertThat(invalidations).hasValue(0);
    }

    @Test
    @Disabled("Obsolete: mutation invalidation now originates from the unified selection page")
    void enrollmentChangesRefreshLoadedTabsLazilyAndDoNotConstructHiddenTabs() throws Exception {
        AtomicInteger enrollmentLoads = new AtomicInteger();
        AtomicInteger scheduleLoads = new AtomicInteger();
        EnrollmentView active = new EnrollmentView(
                "enrollment-1", "o1", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 7);
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                enrollmentLoads.incrementAndGet();
                return CompletableFuture.completedFuture(List.of(active));
            }

            @Override public CompletableFuture<List<ScheduleItem>> currentSchedule() {
                scheduleLoads.incrementAndGet();
                return base.currentSchedule();
            }

            @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return enrollmentPhase(termId, "ENROLLMENT", "ACTIVE");
            }

            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) {
                return CompletableFuture.completedFuture(EmptyResponse.INSTANCE);
            }
        };
        CourseWorkspacePanel workspace = onEdt(() -> new CourseWorkspacePanel(
                gateway, UserRole.STUDENT, (owner, label) -> true));
        JTabbedPane tabs = descendants(workspace).stream().filter(JTabbedPane.class::isInstance)
                .map(JTabbedPane.class::cast).findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(2));
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(scheduleLoads).hasValue(1);
        SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(1));
        SwingUtilities.invokeAndWait(() -> { });
        MyEnrollmentPanel enrollments = (MyEnrollmentPanel) tabs.getComponentAt(1);
        SwingUtilities.invokeAndWait(() -> {
            enrollmentTable(enrollments).setRowSelectionInterval(0, 0);
            button(enrollments, "退选所选课程").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(enrollmentLoads).hasValue(2);
        assertThat(scheduleLoads).hasValue(1);
        assertThat(tabs.getTabCount()).isEqualTo(3);
        SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(2));
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(scheduleLoads.get()).isGreaterThan(1);
    }

    @Test
    void removedEnrollmentPageIgnoresLateAsyncResults() throws Exception {
        CompletableFuture<List<EnrollmentView>> request = new CompletableFuture<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return request; }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(gateway));
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(panel::removeNotify);

        request.complete(List.of(new EnrollmentView(
                "late", "offering-late", "student-late", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 0)));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(table.getRowCount()).isZero();
    }

    @Test
    void cardLayoutPageIgnoresAsyncResultsAfterNavigationHidesIt() throws Exception {
        CompletableFuture<List<EnrollmentView>> request = new CompletableFuture<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return request; }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(gateway));
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();
        JPanel cards = onEdt(() -> {
            JPanel host = new JPanel(new CardLayout());
            host.add(panel, "course");
            host.add(new JPanel(), "other");
            ((CardLayout) host.getLayout()).show(host, "course");
            ((CardLayout) host.getLayout()).show(host, "other");
            return host;
        });

        request.complete(List.of(new EnrollmentView(
                "late", "offering-late", "student-late", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 0)));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(cards.isAncestorOf(panel)).isTrue();
        assertThat(panel.isVisible()).isFalse();
        assertThat(table.getRowCount()).isZero();
    }

    @Test
    void cardLayoutPageReloadsAutomaticallyAfterItIsShownAgain() throws Exception {
        CompletableFuture<List<EnrollmentView>> first = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return calls.incrementAndGet() == 1 ? first : base.currentEnrollments();
            }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(gateway));
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();
        JPanel cards = onEdt(() -> {
            JPanel host = new JPanel(new CardLayout());
            host.add(panel, "course");
            host.add(new JPanel(), "other");
            CardLayout layout = (CardLayout) host.getLayout();
            layout.show(host, "other");
            layout.show(host, "course");
            return host;
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(cards.isAncestorOf(panel)).isTrue();
        assertThat(panel.isVisible()).isTrue();
        assertThat(calls).hasValue(2);
        assertThat(table.getRowCount()).isEqualTo(1);
    }

    @Test
    void adjustmentChangeUsesSelectedEnrollmentTargetAndExpectedVersion() throws Exception {
        AtomicReference<ChangeOfferingCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return base.searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(List.of(new EnrollmentView(
                        "enrollment-source", "o1", "s1", "NORMAL", "ACTIVE", Instant.now(), null, 7)));
            }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return base.enroll(command); }
            @Override public CompletableFuture<EnrollmentView> lateAdd(LateAddCommand command) { return base.enroll(new EnrollCommand(command.offeringId())); }
            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) { return CompletableFuture.completedFuture(EmptyResponse.INSTANCE); }
            @Override public CompletableFuture<EnrollmentView> change(ChangeOfferingCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new EnrollmentView(
                        "enrollment-source", command.targetOfferingId(), "s1", "NORMAL", "ACTIVE", Instant.now(), null, 8));
            }
            @Override public CompletableFuture<RetakeEligibility> checkRetake(String courseId) { return CompletableFuture.completedFuture(new RetakeEligibility(courseId, false, List.of(), "")); }
            @Override public CompletableFuture<EnrollmentView> enrollRetake(RetakeCommand command) { return base.enroll(new EnrollCommand(command.offeringId())); }
            @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return CompletableFuture.completedFuture(new TermPhaseView(termId, "ACTIVE", "ADJUSTMENT",
                        Instant.parse("2026-09-03T00:00:00Z"), Instant.parse("2026-08-20T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-09-08T16:00:00Z")));
            }
        };
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(gateway,
                (owner, source, target, conflict, request, onSuccess) -> {
                    request.get().join();
                    onSuccess.run();
                }));
        SwingUtilities.invokeAndWait(() -> { });
        List<JTable> tables = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).toList();
        JButton change = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "确认改选".equals(button.getText())).findFirst().orElseThrow();

        assertThat(tables.get(0).getColumnName(0)).isEqualTo("课程");
        assertThat(tables.get(0).getValueAt(0, 0)).isEqualTo("高等数学");
        assertThat(tables.get(0).getValueAt(0, 1)).isEqualTo("01班");
        assertThat(tables.get(0).getValueAt(0, 2)).isEqualTo("正常选课");
        assertThat(tables.get(0).getValueAt(0, 3)).isEqualTo("有效");
        assertThat(labels(panel)).anyMatch(text -> text.contains("退改补开放") && text.contains("09-01") && text.contains("09-09"));

        SwingUtilities.invokeAndWait(() -> {
            tables.get(0).setRowSelectionInterval(0, 0);
            tables.get(1).setRowSelectionInterval(1, 1);
            change.doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new ChangeOfferingCommand("enrollment-source", "o2", 7));
        assertThat(labels(panel)).anyMatch(text -> text.contains("改选成功"));
    }

    @Test
    void adjustmentShowsAnActionableEmptyStateWhenNothingCanBeAdjusted() throws Exception {
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<String> currentTermId() { return base.currentTermId(); }
            @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return base.getTermPhase(termId);
            }
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<List<ScheduleItem>> currentSchedule() {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                return CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 100, 0));
            }
        };
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.EMPTY);
        assertThat(labels(panel)).anyMatch(text -> text.contains("没有可调整的选课或教学班"));
    }

    @Test
    void adjustmentActionsStayDisabledOutsideTheAdjustmentPhase() throws Exception {
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return CompletableFuture.completedFuture(new TermPhaseView(termId, "ACTIVE", "ENROLLMENT",
                        Instant.parse("2026-08-25T00:00:00Z"), Instant.parse("2026-08-20T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-09-08T16:00:00Z")));
            }
        };
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        for (String text : List.of("补选所选", "退选所选", "确认改选")) {
            JButton action = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
            assertThat(action.isEnabled()).as(text).isFalse();
        }
        assertThat(labels(panel)).anyMatch(text -> text.contains("服务端阶段：正常选课开放"));
    }

    @Test
    void adjustmentDropAndChangeStayDisabledWithoutAnEnrollmentSelection() throws Exception {
        AtomicInteger drops = new AtomicInteger();
        AtomicInteger changes = new AtomicInteger();
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(
                adjustmentSelectionGateway(drops, changes),
                (owner, source, target, conflict, request, onSuccess) -> request.get()));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(button(panel, "补选所选").isEnabled()).isTrue();
        assertThat(button(panel, "退选所选").isEnabled()).isFalse();
        assertThat(button(panel, "确认改选").isEnabled()).isFalse();
        fireAction(button(panel, "退选所选"));
        fireAction(button(panel, "确认改选"));
        assertThat(drops).hasValue(0);
        assertThat(changes).hasValue(0);
    }

    @Test
    void adjustmentDroppedSelectionCannotSubmitDropOrChange() throws Exception {
        AtomicInteger drops = new AtomicInteger();
        AtomicInteger changes = new AtomicInteger();
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(
                adjustmentSelectionGateway(drops, changes),
                (owner, source, target, conflict, request, onSuccess) -> request.get()));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        JTable enrollments = table(panel, "当前选课记录");
        JTable offerings = table(panel, "可调整教学班");

        SwingUtilities.invokeAndWait(() -> {
            enrollments.setRowSelectionInterval(0, 0);
            offerings.setRowSelectionInterval(1, 1);
        });

        assertThat(button(panel, "退选所选").isEnabled()).isFalse();
        assertThat(button(panel, "确认改选").isEnabled()).isFalse();
        fireAction(button(panel, "退选所选"));
        fireAction(button(panel, "确认改选"));
        assertThat(drops).hasValue(0);
        assertThat(changes).hasValue(0);
    }

    @Test
    void adjustmentActiveSelectionCanSubmitDropAndChange() throws Exception {
        AtomicInteger drops = new AtomicInteger();
        AtomicInteger changes = new AtomicInteger();
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(
                adjustmentSelectionGateway(drops, changes),
                (owner, source, target, conflict, request, onSuccess) -> request.get()));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        JTable enrollments = table(panel, "当前选课记录");
        JTable offerings = table(panel, "可调整教学班");

        SwingUtilities.invokeAndWait(() -> {
            enrollments.setRowSelectionInterval(1, 1);
            offerings.setRowSelectionInterval(1, 1);
        });

        assertThat(button(panel, "退选所选").isEnabled()).isTrue();
        assertThat(button(panel, "确认改选").isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(() -> button(panel, "确认改选").doClick());
        assertThat(changes).hasValue(1);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> button(panel, "退选所选").doClick());
        assertThat(drops).hasValue(1);
    }

    @Test
    void adjustmentSelectionChangesCannotReenableOrResubmitWhileDropIsPending() throws Exception {
        CompletableFuture<EmptyResponse> firstDrop = new CompletableFuture<>();
        AtomicInteger drops = new AtomicInteger();
        AtomicInteger changes = new AtomicInteger();
        AtomicInteger lateAdds = new AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        EnrollmentView active = new EnrollmentView(
                "active-enrollment", "o1", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-29T08:00:00Z"), null, 3);
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(List.of(active));
            }

            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) {
                return drops.incrementAndGet() == 1 ? firstDrop : new CompletableFuture<>();
            }

            @Override public CompletableFuture<EnrollmentView> lateAdd(LateAddCommand command) {
                lateAdds.incrementAndGet();
                return new CompletableFuture<>();
            }

            @Override public CompletableFuture<EnrollmentView> change(ChangeOfferingCommand command) {
                changes.incrementAndGet();
                return new CompletableFuture<>();
            }
        };
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(
                gateway, (owner, source, target, conflict, request, onSuccess) -> request.get()));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        JTable enrollments = table(panel, "当前选课记录");
        JTable offerings = table(panel, "可调整教学班");
        JButton add = button(panel, "补选所选");
        JButton drop = button(panel, "退选所选");
        JButton change = button(panel, "确认改选");

        SwingUtilities.invokeAndWait(() -> {
            enrollments.setRowSelectionInterval(0, 0);
            offerings.setRowSelectionInterval(1, 1);
            drop.doClick();
            enrollments.clearSelection();
            enrollments.setRowSelectionInterval(0, 0);
            offerings.clearSelection();
            offerings.setRowSelectionInterval(1, 1);
        });

        assertThat(drops).hasValue(1);
        assertThat(List.of(add.isEnabled(), drop.isEnabled(), change.isEnabled()))
                .containsExactly(false, false, false);
        fireAction(add);
        fireAction(drop);
        fireAction(change);
        assertThat(drops).hasValue(1);
        assertThat(changes).hasValue(0);
        assertThat(lateAdds).hasValue(0);

        firstDrop.completeExceptionally(new CourseClientException(
                "COURSE_DROP_FAILED", "退选失败", null, false));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(List.of(add.isEnabled(), drop.isEnabled(), change.isEnabled()))
                .containsExactly(true, true, true);
        SwingUtilities.invokeAndWait(drop::doClick);
        assertThat(drops).hasValue(2);
    }

    @Test
    void failedAdjustmentLateAddRestoresItsAction() throws Exception {
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<EnrollmentView> lateAdd(LateAddCommand command) {
                return CompletableFuture.failedFuture(new CourseClientException(
                        "COURSE_LATE_ADD_FAILED", "补选失败", null, false));
            }
        };
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> {
            table(panel, "可调整教学班").setRowSelectionInterval(0, 0);
            button(panel, "补选所选").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(button(panel, "补选所选").isEnabled()).isTrue();
        assertThat(labels(panel)).contains("补选失败");
    }

    @Test
    void adjustmentPageProvidesAnExplicitRefreshAction() throws Exception {
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(CourseUiGateway.preview()));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(buttons(panel)).contains("刷新调整数据");
    }

    @Test
    void failedAdjustmentRefreshKeepsPreviouslyLoadedRows() throws Exception {
        AtomicInteger enrollmentCalls = new AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                if (enrollmentCalls.incrementAndGet() == 1) return base.currentEnrollments();
                return CompletableFuture.failedFuture(new CourseClientException(
                        "COMMON_NETWORK_ERROR", "network", null, true));
            }
            @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return base.getTermPhase(termId);
            }
        };
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        JTable enrollments = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast)
                .filter(table -> "当前选课记录".equals(table.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JTable offerings = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast)
                .filter(table -> "可调整教学班".equals(table.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> descendants(panel).stream().filter(JButton.class::isInstance)
                .map(JButton.class::cast).filter(button -> "刷新调整数据".equals(button.getText()))
                .findFirst().orElseThrow().doClick());
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(enrollments.getRowCount()).isEqualTo(1);
        assertThat(offerings.getRowCount()).isEqualTo(3);
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.DISCONNECTED);
    }

    @Test
    void successfulLateAddRefreshesAuthoritativeAdjustmentData() throws Exception {
        AtomicInteger enrollmentLoads = new AtomicInteger();
        AtomicInteger offeringLoads = new AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                enrollmentLoads.incrementAndGet();
                return base.currentEnrollments();
            }
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                offeringLoads.incrementAndGet();
                return base.searchOfferings(query);
            }
            @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return base.getTermPhase(termId);
            }
            @Override public CompletableFuture<EnrollmentView> lateAdd(LateAddCommand command) {
                return base.lateAdd(command);
            }
        };
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        JTable offerings = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast)
                .filter(table -> "可调整教学班".equals(table.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JButton add = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "补选所选".equals(button.getText())).findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> { offerings.setRowSelectionInterval(1, 1); add.doClick(); });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(enrollmentLoads.get()).isEqualTo(2);
        assertThat(offeringLoads.get()).isEqualTo(2);
    }

    @Test
    void successfulAtomicChangeRefreshesAuthoritativeAdjustmentData() throws Exception {
        AtomicInteger enrollmentLoads = new AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                enrollmentLoads.incrementAndGet();
                return base.currentEnrollments();
            }
            @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return base.getTermPhase(termId);
            }
            @Override public CompletableFuture<EnrollmentView> change(ChangeOfferingCommand command) {
                return base.change(command);
            }
        };
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(gateway,
                (owner, source, target, conflict, request, onSuccess) -> {
                    request.get().join();
                    onSuccess.run();
                }));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        JTable enrollments = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast)
                .filter(table -> "当前选课记录".equals(table.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JTable offerings = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast)
                .filter(table -> "可调整教学班".equals(table.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JButton change = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "确认改选".equals(button.getText())).findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> {
            enrollments.setRowSelectionInterval(0, 0);
            offerings.setRowSelectionInterval(1, 1);
            change.doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(enrollmentLoads.get()).isEqualTo(2);
    }

    @Test
    void retakeRequiresEligibilityCheckBeforeSubmittingSelectedOffering() throws Exception {
        AtomicReference<String> checkedCourse = new AtomicReference<>();
        AtomicReference<RetakeCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return base.searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return base.enroll(command); }
            @Override public CompletableFuture<RetakeEligibility> checkRetake(String courseId) {
                checkedCourse.set(courseId);
                return CompletableFuture.completedFuture(new RetakeEligibility(courseId, true, List.of("failed-1"), "FAILED_ATTEMPT"));
            }
            @Override public CompletableFuture<EnrollmentView> enrollRetake(RetakeCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new EnrollmentView(
                        "retake-1", command.offeringId(), "s1", "RETAKE", "ACTIVE", Instant.now(), null, 0));
            }
        };
        RetakePanel panel = onEdt(() -> new RetakePanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();
        JButton check = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "检查重修资格".equals(button.getText())).findFirst().orElseThrow();
        JButton enroll = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "确认重修".equals(button.getText())).findFirst().orElseThrow();

        assertThat(enroll.isEnabled()).isFalse();
        SwingUtilities.invokeAndWait(() -> { table.setRowSelectionInterval(0, 0); check.doClick(); });
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(checkedCourse.get()).isEqualTo("c1");
        assertThat(enroll.isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(enroll::doClick);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new RetakeCommand("o1"));
        assertThat(labels(panel)).anyMatch(text -> text.contains("重修选课成功"));
    }

    @Test
    void adjustmentAuditLoadsLiveRowsAndUsesAdministrativeQuery() throws Exception {
        AtomicReference<AdjustmentAuditQuery> captured = new AtomicReference<>();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<PageResult<AdjustmentAuditView>> searchAdjustmentAudits(AdjustmentAuditQuery query) {
                captured.set(query);
                return CompletableFuture.completedFuture(new PageResult<>(List.of(new AdjustmentAuditView(
                        "a1", "20260001", "CHANGE", "math-01", "math-02", "SUCCEEDED", null,
                        Instant.parse("2026-08-28T08:30:00Z"))), 0, 50, 1));
            }
        };

        AdjustmentAuditPanel panel = onEdt(() -> new AdjustmentAuditPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();

        assertThat(captured.get()).isEqualTo(new AdjustmentAuditQuery(null, null, null, null, 0, 50));
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 1)).isEqualTo("20260001");
        assertThat(table.getValueAt(0, 2)).isEqualTo("改选");
        assertThat(table.getValueAt(0, 5)).isEqualTo("成功");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    void courseCatalogLoadsLiveRowsWithCatalogQuery() throws Exception {
        AtomicReference<CourseCatalogQuery> captured = new AtomicReference<>();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                captured.set(query);
                return CompletableFuture.completedFuture(new PageResult<>(List.of(new CourseView(
                        "c1", "MATH101", "高等数学", new BigDecimal("5.0"), 80, "理工科基础课程", true, 3,
                        Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-28T00:00:00Z"))), 0, 50, 1));
            }
        };

        CourseCatalogPanel panel = onEdt(() -> new CourseCatalogPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();

        assertThat(captured.get()).isEqualTo(new CourseCatalogQuery("", null, 0, 50));
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("MATH101");
        assertThat(table.getValueAt(0, 4)).isEqualTo("启用");
        assertThat(buttons(panel)).contains("新建课程", "编辑所选");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    void courseCatalogKeepsTheLatestQueryWhenAnOlderResponseArrivesLate() throws Exception {
        CompletableFuture<PageResult<CourseView>> older = new CompletableFuture<>();
        CourseView newestCourse = courseView("new", "NEW101", "新查询课程");
        CourseView olderCourse = courseView("old", "OLD101", "旧查询课程");
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                if (calls.getAndIncrement() == 0) return older;
                return CompletableFuture.completedFuture(new PageResult<>(List.of(newestCourse), 0, 50, 1));
            }
        };
        CourseCatalogPanel panel = onEdt(() -> new CourseCatalogPanel(gateway));
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(() -> descendants(panel).stream().filter(JButton.class::isInstance)
                .map(JButton.class::cast).filter(button -> "查询课程".equals(button.getText()))
                .findFirst().orElseThrow().doClick());
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(table.getValueAt(0, 0)).isEqualTo("NEW101");

        older.complete(new PageResult<>(List.of(olderCourse), 0, 50, 1));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(table.getValueAt(0, 0)).isEqualTo("NEW101");
    }

    @Test
    void termManagementLoadsLiveWindowsAndChineseStatus() throws Exception {
        AtomicReference<Boolean> requested = new AtomicReference<>(false);
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<List<TermView>> listTerms() {
                requested.set(true);
                return CompletableFuture.completedFuture(List.of(new TermView(
                        "t1", "2026-2027-1", "2026—2027学年秋季学期", LocalDate.parse("2026-09-01"), LocalDate.parse("2027-01-15"),
                        Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-31T16:00:00Z"),
                        Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-08T16:00:00Z"), "ACTIVE", 4,
                        Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-28T00:00:00Z"))));
            }
        };

        TermManagementPanel panel = onEdt(() -> new TermManagementPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();

        assertThat(requested.get()).isTrue();
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("2026-2027-1");
        assertThat(table.getValueAt(0, 4)).isEqualTo("进行中");
        assertThat(table.getValueAt(0, 5)).isEqualTo("v4");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    void offeringManagementLoadsLiveOfferingRows() throws Exception {
        AtomicReference<OfferingSearchQuery> captured = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                captured.set(query);
                return base.searchOfferings(query);
            }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
        };

        OfferingManagementPanel panel = onEdt(() -> new OfferingManagementPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();

        assertThat(captured.get()).isEqualTo(new OfferingSearchQuery("2026-autumn", "", null, false, 0, 50));
        assertThat(table.getRowCount()).isEqualTo(3);
        assertThat(table.getValueAt(0, 0)).isEqualTo("MATH101");
        assertThat(table.getValueAt(0, 6)).isEqualTo("开放");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    void outcomeImportParsesPassFailRowsAndSubmitsTypedCommand() throws Exception {
        AtomicReference<ImportCourseOutcomesCommand> submitted = new AtomicReference<>();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<EmptyResponse> importOutcomes(ImportCourseOutcomesCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(EmptyResponse.INSTANCE);
            }
        };
        OutcomeImportPanel panel = onEdt(() -> new OutcomeImportPanel(gateway));
        JTextArea input = descendants(panel).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).findFirst().orElseThrow();
        JButton submit = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "导入课程结果".equals(button.getText())).findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> {
            input.setText("student-1,course-1,term-1,FAILED,registrar-2026-001");
            submit.doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry("student-1", "course-1", "term-1", CourseOutcome.FAILED, "registrar-2026-001"))));
        assertThat(labels(panel)).anyMatch(text -> text.contains("已导入 1 条"));
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    void administrativeFilterFieldsHaveVisibleChineseLabels() throws Exception {
        CourseUiGateway gateway = CourseUiGateway.preview();
        CourseCatalogPanel catalog = onEdt(() -> new CourseCatalogPanel(gateway));
        OfferingManagementPanel offerings = onEdt(() -> new OfferingManagementPanel(gateway));
        AdjustmentAuditPanel audits = onEdt(() -> new AdjustmentAuditPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(catalog)).contains("课程关键字");
        assertThat(labels(offerings)).contains("学期编号", "课程或教学班");
        assertThat(labels(audits)).contains("学生编号", "学期编号", "操作类型", "操作结果");
    }

    @Test
    void overlongAdministrativeIdsShowValidationInsteadOfThrowingOnEdt() throws Exception {
        AdjustmentAuditPanel audits = onEdt(() -> new AdjustmentAuditPanel(CourseUiGateway.preview()));
        OfferingManagementPanel offerings = onEdt(() -> new OfferingManagementPanel(CourseUiGateway.preview()));
        SwingUtilities.invokeAndWait(() -> { });
        String overlong = "x".repeat(37);

        SwingUtilities.invokeAndWait(() -> {
            textField(audits, "学生编号").setText(overlong);
            descendants(audits).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "查询审计记录".equals(button.getText())).findFirst().orElseThrow().doClick();
            textField(offerings, "学期编号").setText(overlong);
            descendants(offerings).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "查询教学班".equals(button.getText())).findFirst().orElseThrow().doClick();
        });

        assertThat(audits.viewState()).isEqualTo(AbstractCoursePanel.ViewState.ERROR);
        assertThat(offerings.viewState()).isEqualTo(AbstractCoursePanel.ViewState.ERROR);
        assertThat(labels(audits)).anyMatch(text -> text.contains("超过 36"));
        assertThat(labels(offerings)).anyMatch(text -> text.contains("超过 36"));
    }

    @Test
    void courseAndTermEditorsUseStructuredControlsWithLocalizedStatusChoices() throws Exception {
        CourseEditorDialog course = onEdt(() -> new CourseEditorDialog(
                null, CourseUiGateway.preview(), null, () -> { }));
        TermEditorDialog term = onEdt(() -> new TermEditorDialog(
                null, CourseUiGateway.preview(), null, () -> { }));

        JSpinner credit = component(course, "学分", JSpinner.class);
        JSpinner hours = component(course, "总学时", JSpinner.class);
        assertThat(credit).isNotNull();
        assertThat(hours).isNotNull();
        SpinnerNumberModel creditModel = (SpinnerNumberModel) credit.getModel();
        SpinnerNumberModel hoursModel = (SpinnerNumberModel) hours.getModel();
        assertThat(List.of(creditModel.getValue(), creditModel.getMinimum(), creditModel.getMaximum(),
                creditModel.getStepSize())).containsExactly(new BigDecimal("1.0"), new BigDecimal("0.5"),
                        new BigDecimal("20.0"), new BigDecimal("0.5"));
        assertThat(List.of(hoursModel.getValue(), hoursModel.getMinimum(), hoursModel.getMaximum(),
                hoursModel.getStepSize())).containsExactly(32, 1, 1000, 1);
        assertThat(component(term, "开学日期", JSpinner.class)).isNotNull();
        assertThat(component(term, "结束日期", JSpinner.class)).isNotNull();
        assertThat(component(term, "选课开始", JSpinner.class)).isNull();
        assertThat(component(term, "退改补开始", JSpinner.class)).isNull();
        assertThat(Stream.concat(descendants(course).stream(), descendants(term).stream())
                .filter(JTextField.class::isInstance).map(JTextField.class::cast)
                .map(field -> field.getAccessibleContext().getAccessibleName()))
                .doesNotContain("学分", "总学时", "开学日期", "结束日期", "选课开始", "选课结束",
                        "退改补开始", "退改补结束");
        JComboBox<?> status = component(term, "学期状态", JComboBox.class);
        assertThat(IntStream.range(0, status.getItemCount()).mapToObj(index -> status.getItemAt(index).toString()))
                .containsExactly("计划中", "进行中", "已关闭");

        SwingUtilities.invokeAndWait(() -> {
            course.dispose();
            term.dispose();
        });
    }

    @Test
    void creditSpinnerStepsByHalfWithoutJdkNumericTypeFailures() throws Exception {
        CourseEditorDialog dialog = onEdt(() -> new CourseEditorDialog(
                null, CourseUiGateway.preview(), null, () -> { }));
        JSpinner credit = component(dialog, "学分", JSpinner.class);

        Object[] steps = onEdt(() -> {
            credit.setValue(new BigDecimal("1.0"));
            Object next = spinnerStep(credit, true);
            Object previous = spinnerStep(credit, false);
            credit.setValue(new BigDecimal("20.0"));
            Object maximumNext = spinnerStep(credit, true);
            credit.setValue(new BigDecimal("0.5"));
            Object minimumPrevious = spinnerStep(credit, false);
            return new Object[]{next, previous, maximumNext, minimumPrevious};
        });

        assertThat(steps).containsExactly(new BigDecimal("1.5"), new BigDecimal("0.5"), null, null);
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @ParameterizedTest
    @CsvSource({
            "开学日期,结束日期,结束日期必须晚于开学日期"
    })
    void termEditorIdentifiesTheInvalidOrdering(String startName, String endName, String expectedMessage)
            throws Exception {
        AtomicReference<CreateTermCommand> submitted = new AtomicReference<>();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(CourseUiGateway.preview()) {
            @Override public CompletableFuture<TermView> createTerm(CreateTermCommand command) {
                submitted.set(command);
                return CompletableFuture.failedFuture(new AssertionError("invalid ordering was submitted"));
            }
        };
        TermEditorDialog dialog = onEdt(() -> new TermEditorDialog(null, gateway, null, () -> { }));

        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "学期代码").setText("2027-2028-1");
            textField(dialog, "学期名称").setText("2027—2028学年秋季学期");
            setTemporalValue(dialog, "开学日期", "2027-09-01", "2027-08-31T16:00:00Z");
            setTemporalValue(dialog, "结束日期", "2028-01-15", "2028-01-14T16:00:00Z");
            Component start = namedComponent(dialog, startName);
            Component end = namedComponent(dialog, endName);
            if (start instanceof JSpinner startSpinner && end instanceof JSpinner endSpinner) {
                endSpinner.setValue(startSpinner.getValue());
            } else {
                ((JTextField) end).setText(((JTextField) start).getText());
            }
            button(dialog, "创建学期").doClick();
        });

        assertThat(submitted.get()).isNull();
        assertThat(labels(dialog)).contains(expectedMessage);
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void courseEditorSubmitsTypedCreateCommandFromVisibleFields() throws Exception {
        AtomicReference<CreateCourseCommand> submitted = new AtomicReference<>();
        AtomicReference<Boolean> saved = new AtomicReference<>(false);
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<CourseView> createCourse(CreateCourseCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new CourseView(
                        "c-new", command.courseCode(), command.courseName(), command.credit(), command.totalHours(),
                        command.description(), command.active(), 0, Instant.now(), Instant.now()));
            }
        };
        CourseEditorDialog dialog = onEdt(() -> new CourseEditorDialog(null, gateway, null, () -> saved.set(true)));

        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "课程代码").setText(" SE101 ");
            textField(dialog, "课程名称").setText(" 软件工程导论 ");
            component(dialog, "学分", JSpinner.class).setValue(new BigDecimal("4.5"));
            component(dialog, "总学时", JSpinner.class).setValue(72);
            descendants(dialog).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).findFirst().orElseThrow()
                    .setText(" 软件工程基础课程 ");
            descendants(dialog).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast).findFirst().orElseThrow()
                    .setSelected(true);
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "创建课程".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new CreateCourseCommand(
                "SE101", "软件工程导论", new BigDecimal("4.5"), 72, "软件工程基础课程", true));
        assertThat(saved.get()).isTrue();
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void disposedCourseEditorIgnoresALateSaveResult() throws Exception {
        CompletableFuture<CourseView> pending = new CompletableFuture<>();
        AtomicReference<Boolean> saved = new AtomicReference<>(false);
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<CourseView> createCourse(CreateCourseCommand command) {
                return pending;
            }
        };
        CourseEditorDialog dialog = onEdt(() -> new CourseEditorDialog(null, gateway, null, () -> saved.set(true)));
        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "课程代码").setText("LATE101");
            textField(dialog, "课程名称").setText("迟到响应测试");
            component(dialog, "学分", JSpinner.class).setValue(new BigDecimal("2.0"));
            component(dialog, "总学时", JSpinner.class).setValue(32);
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "创建课程".equals(button.getText())).findFirst().orElseThrow().doClick();
            dialog.dispose();
        });

        pending.complete(courseView("late", "LATE101", "迟到响应测试"));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(saved.get()).isFalse();
    }

    @Test
    void courseEditorPreservesIdAndVersionWhenUpdating() throws Exception {
        AtomicReference<UpdateCourseCommand> submitted = new AtomicReference<>();
        CourseView existing = new CourseView("course-7", "SE101", "软件工程导论", new BigDecimal("4.5"), 72,
                "原简介", true, 7, Instant.now(), Instant.now());
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<CourseView> updateCourse(UpdateCourseCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(existing);
            }
        };
        CourseEditorDialog dialog = onEdt(() -> new CourseEditorDialog(null, gateway, existing, () -> { }));

        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "课程名称").setText("软件工程基础");
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "保存修改".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new UpdateCourseCommand(
                "course-7", "SE101", "软件工程基础", new BigDecimal("4.5"), 72, "原简介", true, 7));
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void termEditorSubmitsCompleteTypedCreateCommand() throws Exception {
        AtomicReference<CreateTermCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = gateway(CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 20, 0)));
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<TermView> createTerm(CreateTermCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new TermView("term-new", command.termCode(), command.termName(),
                        command.startDate(), command.endDate(), command.enrollmentStartAt(), command.enrollmentEndAt(),
                        command.adjustmentStartAt(), command.adjustmentEndAt(), command.termStatus(), 0, Instant.now(), Instant.now()));
            }
        };
        TermEditorDialog dialog = onEdt(() -> new TermEditorDialog(null, gateway, null, () -> { }));

        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "学期代码").setText(" 2027-2028-1 ");
            textField(dialog, "学期名称").setText(" 2027—2028学年秋季学期 ");
            setTemporalValue(dialog, "开学日期", "2027-09-01", "2027-08-31T16:00:00Z");
            setTemporalValue(dialog, "结束日期", "2028-01-15", "2028-01-14T16:00:00Z");
            descendants(dialog).stream().filter(JComboBox.class::isInstance).map(JComboBox.class::cast)
                    .findFirst().orElseThrow().setSelectedIndex(1);
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "创建学期".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new CreateTermCommand("2027-2028-1", "2027—2028学年秋季学期",
                LocalDate.parse("2027-09-01"), LocalDate.parse("2028-01-15"),
                Instant.parse("2027-08-29T16:00:00Z"), Instant.parse("2027-08-30T16:00:00Z"),
                Instant.parse("2027-08-31T16:00:00Z"), Instant.parse("2027-09-01T16:00:00Z"), "ACTIVE"));
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void termEditorPreservesIdentityAndOptimisticVersionWhenUpdating() throws Exception {
        TermView existing = CourseUiGateway.preview().listTerms().join().get(0);
        AtomicReference<UpdateTermCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = gateway(CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 20, 0)));
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<TermView> updateTerm(UpdateTermCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(existing);
            }
        };
        TermEditorDialog dialog = onEdt(() -> new TermEditorDialog(null, gateway, existing, () -> { }));

        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "学期名称").setText("秋季学期（调整）");
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "保存修改".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new UpdateTermCommand(existing.termId(), existing.termCode(),
                "秋季学期（调整）", existing.startDate(), existing.endDate(),
                existing.enrollmentStartAt(), existing.enrollmentEndAt(), existing.adjustmentStartAt(),
                existing.adjustmentEndAt(), existing.termStatus(), existing.rowVersion()));
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void offeringEditorUsesLocalizedReferenceChoicesAndSubmitsTheirIds() throws Exception {
        AtomicReference<CreateOfferingCommand> submitted = new AtomicReference<>();
        AtomicReference<CourseCatalogQuery> catalogQuery = new AtomicReference<>();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(CourseUiGateway.preview()) {
            @Override public CompletableFuture<OfferingView> createOffering(CreateOfferingCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new OfferingView("new-offering", command.termId(),
                        command.courseId(), command.teacherUserId(), command.className(), command.capacity(), 0,
                        command.offeringStatus(), 0, Instant.now(), Instant.now(), List.of()));
            }
            @Override public CompletableFuture<PageResult<UserSummary>> searchTeachers(String keyword) {
                return CourseUiGateway.preview().searchTeachers(keyword);
            }
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                catalogQuery.set(query);
                return CourseUiGateway.preview().searchCatalog(query);
            }
        };
        OfferingEditorDialog dialog = onEdt(() -> new OfferingEditorDialog(null, gateway, null, () -> { }));
        flushEdt(4);

        JComboBox<?> terms = component(dialog, "学期", JComboBox.class);
        JComboBox<?> courses = component(dialog, "课程", JComboBox.class);
        JComboBox<?> teachers = component(dialog, "教师", JComboBox.class);
        JComboBox<?> status = component(dialog, "教学班状态", JComboBox.class);
        assertThat(terms.getSelectedItem().toString()).contains("2026—2027学年秋季学期");
        assertThat(courses.getItemAt(0).toString()).contains("MATH101", "高等数学");
        assertThat(teachers.getItemAt(0).toString()).contains("zhang.teacher");
        assertThat(status.getItemAt(0).toString()).isEqualTo("草稿");
        assertThat(catalogQuery.get()).isEqualTo(new CourseCatalogQuery("", true, 0, 100));
        assertThat(descendants(dialog).stream().filter(JTextField.class::isInstance).map(JTextField.class::cast)
                .map(field -> field.getAccessibleContext().getAccessibleName()))
                .doesNotContain("学期编号", "课程编号", "教师用户编号", "容量", "上课安排");

        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "教学班名称").setText("  软件工程 01 班  ");
            component(dialog, "容量", JSpinner.class).setValue(48);
            button(dialog, "创建教学班").doClick();
        });
        flushEdt(2);

        assertThat(submitted.get()).isEqualTo(new CreateOfferingCommand(
                "2026-autumn", "c1", "teacher-zhang", "软件工程 01 班", 48, "DRAFT",
                List.of(new CreateOfferingCommand.ScheduleInput("MONDAY", 1, 2, 1, 16, "待定"))));
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void offeringEditorKeepsSaveDisabledUntilReferencesLoadAndDefaultsToCurrentTerm() throws Exception {
        CompletableFuture<List<TermView>> terms = new CompletableFuture<>();
        CompletableFuture<String> currentTerm = new CompletableFuture<>();
        CompletableFuture<PageResult<CourseView>> courses = new CompletableFuture<>();
        CompletableFuture<PageResult<UserSummary>> teachers = new CompletableFuture<>();
        CourseUiGateway gateway = referenceGateway(terms, currentTerm, courses, teachers);
        OfferingEditorDialog dialog = onEdt(() -> new OfferingEditorDialog(null, gateway, null, () -> { }));

        assertThat(button(dialog, "创建教学班").isEnabled()).isFalse();
        assertThat(labels(dialog)).contains("正在加载学期、课程和教师，请稍候…");

        TermView first = term("term-first", "第一学期");
        TermView current = term("term-current", "当前学期");
        terms.complete(List.of(first, current));
        currentTerm.complete("term-current");
        courses.complete(new PageResult<>(List.of(courseView("course-current", "CS301", "编译原理")), 0, 100, 1));
        teachers.complete(new PageResult<>(List.of(teacher("teacher-current", "compiler.teacher")), 0, 100, 1));
        flushEdt(5);

        assertThat(button(dialog, "创建教学班").isEnabled()).isTrue();
        OfferingReferenceChoice selected = (OfferingReferenceChoice) component(dialog, "学期", JComboBox.class)
                .getSelectedItem();
        assertThat(selected.id()).isEqualTo("term-current");
        assertThat(selected.toString()).contains("当前学期");
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void offeringEditorCanRetryOneFailedReferenceLoad() throws Exception {
        AtomicInteger courseSearches = new AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                if (courseSearches.incrementAndGet() == 1) {
                    return CompletableFuture.failedFuture(new CourseClientException(
                            "COMMON_NETWORK_ERROR", "网络暂时不可用", null, true));
                }
                return base.searchCatalog(query);
            }
            @Override public CompletableFuture<PageResult<UserSummary>> searchTeachers(String keyword) {
                return base.searchTeachers(keyword);
            }
        };
        OfferingEditorDialog dialog = onEdt(() -> new OfferingEditorDialog(null, gateway, null, () -> { }));
        flushEdt(5);

        assertThat(button(dialog, "创建教学班").isEnabled()).isFalse();
        assertThat(labels(dialog)).contains("参考数据加载失败，请重试");
        SwingUtilities.invokeAndWait(() -> button(dialog, "重试加载").doClick());
        flushEdt(5);

        assertThat(courseSearches.get()).isEqualTo(2);
        assertThat(button(dialog, "创建教学班").isEnabled()).isTrue();
        assertThat(labels(dialog)).contains("参考数据已就绪");
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void disposedOfferingEditorIgnoresLateReferenceResponses() throws Exception {
        CompletableFuture<List<TermView>> terms = new CompletableFuture<>();
        CourseUiGateway gateway = referenceGateway(terms, CompletableFuture.completedFuture("late-term"),
                CompletableFuture.completedFuture(new PageResult<>(List.of(courseView("c", "C1", "课程")), 0, 100, 1)),
                CompletableFuture.completedFuture(new PageResult<>(List.of(teacher("t", "teacher")), 0, 100, 1)));
        OfferingEditorDialog dialog = onEdt(() -> new OfferingEditorDialog(null, gateway, null, () -> { }));
        JComboBox<?> termChoice = component(dialog, "学期", JComboBox.class);

        SwingUtilities.invokeAndWait(dialog::dispose);
        terms.complete(List.of(term("late-term", "不应出现")));
        flushEdt(3);

        assertThat(termChoice.getItemCount()).isZero();
        assertThat(button(dialog, "创建教学班").isEnabled()).isFalse();
    }

    @Test
    void offeringEditorPreservesExistingReferencesSchedulesCapacityFloorAndVersion() throws Exception {
        ScheduleItem monday = new ScheduleItem("s1", "offering-7", "SE101", "软件工程", "01班",
                "teacher-legacy", "MONDAY", 1, 2, 1, 16, "教一-101");
        ScheduleItem thursday = new ScheduleItem("s2", "offering-7", "SE101", "软件工程", "01班",
                "teacher-legacy", "THURSDAY", 5, 6, 2, 15, "教二-301");
        OfferingSummary existing = new OfferingSummary("offering-7", "term-legacy", "course-legacy",
                "SE101", "软件工程", "teacher-legacy", "01班", 40, 28, "OPEN", 7,
                List.of(monday, thursday));
        AtomicReference<UpdateOfferingCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<TermView>> listTerms() {
                return CompletableFuture.completedFuture(List.of(term("other-term", "其他学期")));
            }
            @Override public CompletableFuture<String> currentTermId() {
                return CompletableFuture.completedFuture("other-term");
            }
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                return CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 100, 0));
            }
            @Override public CompletableFuture<PageResult<UserSummary>> searchTeachers(String keyword) {
                return CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 100, 0));
            }
            @Override public CompletableFuture<Optional<UserSummary>> resolveTeacher(String userId) {
                return CompletableFuture.completedFuture(Optional.of(teacher(userId, "legacy.teacher")));
            }
            @Override public CompletableFuture<OfferingView> updateOffering(UpdateOfferingCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new OfferingView(command.offeringId(), command.termId(),
                        command.courseId(), command.teacherUserId(), command.className(), command.capacity(), 28,
                        command.offeringStatus(), command.expectedVersion() + 1, Instant.now(), Instant.now(), List.of()));
            }
        };
        OfferingEditorDialog dialog = onEdt(() -> new OfferingEditorDialog(null, gateway, existing, () -> { }));
        flushEdt(5);

        assertThat(((OfferingReferenceChoice) component(dialog, "学期", JComboBox.class).getSelectedItem()).id())
                .isEqualTo("term-legacy");
        assertThat(((OfferingReferenceChoice) component(dialog, "课程", JComboBox.class).getSelectedItem()).id())
                .isEqualTo("course-legacy");
        assertThat(((OfferingReferenceChoice) component(dialog, "教师", JComboBox.class).getSelectedItem()).id())
                .isEqualTo("teacher-legacy");
        SpinnerNumberModel capacity = (SpinnerNumberModel) component(dialog, "容量", JSpinner.class).getModel();
        assertThat(capacity.getMinimum()).isEqualTo(28);
        assertThatThrownBy(() -> capacity.setValue(27)).isInstanceOf(IllegalArgumentException.class);
        assertThat(component(dialog, "第 2 行星期", JComboBox.class).getSelectedItem().toString()).isEqualTo("周四");

        SwingUtilities.invokeAndWait(() -> button(dialog, "保存修改").doClick());
        flushEdt(2);
        assertThat(submitted.get()).isEqualTo(new UpdateOfferingCommand(
                "offering-7", "term-legacy", "course-legacy", "teacher-legacy", "01班", 40, "OPEN", 7,
                List.of(new CreateOfferingCommand.ScheduleInput("MONDAY", 1, 2, 1, 16, "教一-101"),
                        new CreateOfferingCommand.ScheduleInput("THURSDAY", 5, 6, 2, 15, "教二-301"))));
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void offeringEditorKeepsChangedCourseAndTeacherSelectionsAcrossReferenceSearches() throws Exception {
        ScheduleItem schedule = new ScheduleItem("s1", "offering-edit", "C1", "课程一", "01班",
                "teacher-1", "MONDAY", 1, 2, 1, 16, "教室一");
        OfferingSummary existing = new OfferingSummary("offering-edit", "2026-autumn", "course-1",
                "C1", "课程一", "teacher-1", "01班", 40, 5, "OPEN", 9, List.of(schedule));
        CourseView courseOne = courseView("course-1", "C1", "课程一");
        CourseView courseTwo = courseView("course-2", "C2", "课程二");
        UserSummary teacherOne = teacher("teacher-1", "teacher.one");
        UserSummary teacherTwo = teacher("teacher-2", "teacher.two");
        AtomicReference<UpdateOfferingCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                return CompletableFuture.completedFuture(new PageResult<>(List.of(courseOne, courseTwo), 0, 100, 2));
            }
            @Override public CompletableFuture<PageResult<UserSummary>> searchTeachers(String keyword) {
                return CompletableFuture.completedFuture(new PageResult<>(List.of(teacherOne, teacherTwo), 0, 100, 2));
            }
            @Override public CompletableFuture<OfferingView> updateOffering(UpdateOfferingCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new OfferingView(command.offeringId(), command.termId(),
                        command.courseId(), command.teacherUserId(), command.className(), command.capacity(), 5,
                        command.offeringStatus(), command.expectedVersion() + 1, Instant.now(), Instant.now(), List.of()));
            }
        };
        OfferingEditorDialog dialog = onEdt(() -> new OfferingEditorDialog(null, gateway, existing, () -> { }));
        flushEdt(5);

        SwingUtilities.invokeAndWait(() -> {
            selectChoice(component(dialog, "课程", JComboBox.class), "course-2");
            textField(dialog, "教师关键字").setText("teacher");
            button(dialog, "查询教师").doClick();
        });
        flushEdt(5);
        assertThat(((OfferingReferenceChoice) component(dialog, "课程", JComboBox.class).getSelectedItem()).id())
                .isEqualTo("course-2");

        SwingUtilities.invokeAndWait(() -> {
            selectChoice(component(dialog, "教师", JComboBox.class), "teacher-2");
            textField(dialog, "课程关键字").setText("C");
            button(dialog, "查询课程").doClick();
        });
        flushEdt(5);
        assertThat(((OfferingReferenceChoice) component(dialog, "教师", JComboBox.class).getSelectedItem()).id())
                .isEqualTo("teacher-2");

        SwingUtilities.invokeAndWait(() -> button(dialog, "保存修改").doClick());
        flushEdt(2);
        assertThat(submitted.get().courseId()).isEqualTo("course-2");
        assertThat(submitted.get().teacherUserId()).isEqualTo("teacher-2");
        assertThat(submitted.get().expectedVersion()).isEqualTo(9);
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void offeringEditorResolvesOutOfPageExistingTeacherToLoginLabelAndSubmitsUserId() throws Exception {
        ScheduleItem schedule = new ScheduleItem("s1", "offering-teacher", "C1", "课程一", "01班",
                "teacher-target", "MONDAY", 1, 2, 1, 16, "教室一");
        OfferingSummary existing = new OfferingSummary("offering-teacher", "2026-autumn", "course-1",
                "C1", "课程一", "teacher-target", "01班", 40, 5, "OPEN", 12, List.of(schedule));
        UserSummary resolved = teacher("teacher-target", "human.readable.login");
        AtomicReference<UpdateOfferingCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                return CompletableFuture.completedFuture(new PageResult<>(
                        List.of(courseView("course-1", "C1", "课程一")), 0, 100, 1));
            }
            @Override public CompletableFuture<PageResult<UserSummary>> searchTeachers(String keyword) {
                return CompletableFuture.completedFuture(new PageResult<>(
                        List.of(teacher("teacher-page-1", "first.page.teacher")), 0, 100, 101));
            }
            @Override public CompletableFuture<Optional<UserSummary>> resolveTeacher(String userId) {
                return CompletableFuture.completedFuture(Optional.of(resolved));
            }
            @Override public CompletableFuture<OfferingView> updateOffering(UpdateOfferingCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new OfferingView(command.offeringId(), command.termId(),
                        command.courseId(), command.teacherUserId(), command.className(), command.capacity(), 5,
                        command.offeringStatus(), command.expectedVersion() + 1, Instant.now(), Instant.now(), List.of()));
            }
        };
        OfferingEditorDialog dialog = onEdt(() -> new OfferingEditorDialog(null, gateway, existing, () -> { }));
        flushEdt(5);

        OfferingReferenceChoice selected = (OfferingReferenceChoice) component(dialog, "教师", JComboBox.class)
                .getSelectedItem();
        assertThat(selected.id()).isEqualTo("teacher-target");
        assertThat(selected.toString()).isEqualTo("human.readable.login");
        assertThat(selected.toString()).isNotEqualTo("teacher-target");

        SwingUtilities.invokeAndWait(() -> button(dialog, "保存修改").doClick());
        flushEdt(2);
        assertThat(submitted.get().teacherUserId()).isEqualTo("teacher-target");
        assertThat(submitted.get().expectedVersion()).isEqualTo(12);
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @ParameterizedTest
    @MethodSource("offeringSaveFailures")
    void offeringEditorShowsSafeSpecificSaveFailures(CourseClientException failure, String expected) throws Exception {
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                return base.searchCatalog(query);
            }
            @Override public CompletableFuture<PageResult<UserSummary>> searchTeachers(String keyword) {
                return base.searchTeachers(keyword);
            }
            @Override public CompletableFuture<OfferingView> createOffering(CreateOfferingCommand command) {
                return CompletableFuture.failedFuture(failure);
            }
        };
        OfferingEditorDialog dialog = onEdt(() -> new OfferingEditorDialog(null, gateway, null, () -> { }));
        flushEdt(4);
        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "教学班名称").setText("错误消息测试班");
            button(dialog, "创建教学班").doClick();
        });
        flushEdt(3);

        assertThat(labels(dialog)).contains(expected);
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    static Stream<Arguments> offeringSaveFailures() {
        return Stream.of(
                Arguments.of(new CourseClientException("COMMON_CONCURRENT_MODIFICATION", "stale", null, false),
                        "教学班已被其他管理员修改，请刷新并核对最新记录后重试"),
                Arguments.of(new CourseClientException("COURSE_UNEXPECTED", "可安全显示的失败", "trace-7", false),
                        "保存失败：可安全显示的失败（跟踪编号：trace-7）"));
    }

    @Test
    void offeringChangeDialogShowsRequiredComparisonAndConfirmsExplicitly() throws Exception {
        List<OfferingSummary> offerings = CourseUiGateway.preview()
                .searchOfferings(new OfferingSearchQuery("2026-autumn", "", null, true, 0, 20)).join().items();
        OfferingSummary source = offerings.get(0);
        ScheduleItem targetSchedule = new ScheduleItem("s1b", "o1b", source.courseCode(), source.courseName(),
                "02班", "赵老师", "TUESDAY", 3, 4, 1, 16, "教一-203");
        OfferingSummary target = new OfferingSummary("o1b", source.termId(), source.courseId(), source.courseCode(),
                source.courseName(), "赵老师", "02班", 40, 31, "OPEN", 0, List.of(targetSchedule));
        AtomicReference<Boolean> confirmed = new AtomicReference<>(false);
        OfferingDetailDialog dialog = onEdt(() -> new OfferingDetailDialog(
                null, source, target, "未发现时间冲突（服务端提交时将再次校验）",
                () -> confirmed.set(true)));

        assertThat(labels(dialog)).contains("原教学班", "目标教学班", "高等数学 · 01班", "高等数学 · 02班",
                "容量：28 / 40", "未发现时间冲突（服务端提交时将再次校验）");
        SwingUtilities.invokeAndWait(() -> descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "确认改选".equals(button.getText())).findFirst().orElseThrow().doClick());

        assertThat(confirmed.get()).isTrue();
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void offeringChangeDialogStaysOpenAndExplainsServerRejection() throws Exception {
        OfferingSummary source = CourseUiGateway.preview()
                .searchOfferings(new OfferingSearchQuery("2026-autumn", "", null, true, 0, 20)).join().items().get(0);
        ScheduleItem targetSchedule = new ScheduleItem("s1b", "o1b", source.courseCode(), source.courseName(),
                "02班", "赵老师", "TUESDAY", 3, 4, 1, 16, "教一-203");
        OfferingSummary target = new OfferingSummary("o1b", source.termId(), source.courseId(), source.courseCode(),
                source.courseName(), "赵老师", "02班", 40, 40, "OPEN", 0, List.of(targetSchedule));
        OfferingDetailDialog dialog = onEdt(() -> new OfferingDetailDialog(
                null, source, target, "未发现时间冲突（服务端提交时将再次校验）",
                () -> CompletableFuture.failedFuture(new CourseClientException(
                        "COURSE_OFFERING_FULL", "internal capacity detail", null, false)), () -> { }));

        SwingUtilities.invokeAndWait(() -> {
            dialog.setModal(false);
            dialog.setVisible(true);
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "确认改选".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(dialog.isShowing()).isTrue();
        assertThat(labels(dialog)).contains("教学班容量已满，请选择其他教学班");
        assertThat(descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "确认改选".equals(button.getText())).findFirst().orElseThrow().isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    private static CourseUiGateway gateway(CompletableFuture<PageResult<OfferingSummary>> offerings) {
        return new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return offerings; }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
        };
    }

    private static CourseUiGateway referenceGateway(
            CompletableFuture<List<TermView>> terms,
            CompletableFuture<String> currentTerm,
            CompletableFuture<PageResult<CourseView>> courses,
            CompletableFuture<PageResult<UserSummary>> teachers) {
        return new DelegatingCourseUiGateway(CourseUiGateway.preview()) {
            @Override public CompletableFuture<List<TermView>> listTerms() { return terms; }
            @Override public CompletableFuture<String> currentTermId() { return currentTerm; }
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                return courses;
            }
            @Override public CompletableFuture<PageResult<UserSummary>> searchTeachers(String keyword) {
                return teachers;
            }
        };
    }

    private static TermView term(String id, String name) {
        return new TermView(id, id + "-code", name, LocalDate.parse("2026-09-01"),
                LocalDate.parse("2027-01-15"), Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-31T16:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-08T16:00:00Z"), "ACTIVE", 0,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-27T00:00:00Z"));
    }

    private static UserSummary teacher(String id, String loginId) {
        return new UserSummary(id, loginId, UserRole.TEACHER, AccountStatus.ACTIVE,
                LocalDateTime.parse("2026-08-27T09:00:00"), 0);
    }

    private static void selectChoice(JComboBox<?> combo, String id) {
        for (int index = 0; index < combo.getItemCount(); index++) {
            Object value = combo.getItemAt(index);
            if (value instanceof OfferingReferenceChoice choice && id.equals(choice.id())) {
                combo.setSelectedIndex(index);
                return;
            }
        }
        throw new AssertionError("Missing offering reference choice: " + id);
    }

    private static void flushEdt(int rounds) throws Exception {
        for (int round = 0; round < rounds; round++) SwingUtilities.invokeAndWait(() -> { });
    }

    private static CourseUiGateway scheduleGateway(CompletableFuture<List<ScheduleItem>> schedule) {
        CourseUiGateway base = CourseUiGateway.preview();
        return new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<ScheduleItem>> currentSchedule() { return schedule; }
        };
    }

    private static CourseView courseView(String id, String code, String name) {
        return new CourseView(id, code, name, new BigDecimal("2.0"), 32, "测试课程", true, 0,
                Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-28T00:00:00Z"));
    }

    private static List<Component> descendants(Container root) {
        List<Component> all = new ArrayList<>();
        for (Component child : root.getComponents()) {
            all.add(child);
            if (child instanceof Container nested) all.addAll(descendants(nested));
        }
        return all;
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    private static boolean isInteractiveControl(Component component) {
        if (component instanceof JButton && component.getParent() instanceof JComboBox<?>) return false;
        return component instanceof JButton || component instanceof JTextField
                || component instanceof JTextArea || component instanceof JComboBox<?>
                || component instanceof JCheckBox || component instanceof JTable;
    }

    private static List<String> labels(Container root) {
        return descendants(root).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
                .map(JLabel::getText).toList();
    }

    private static List<String> buttons(Container root) {
        return descendants(root).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .map(JButton::getText).toList();
    }

    private static JButton button(Container root, String text) {
        return descendants(root).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(candidate -> text.equals(candidate.getText())).findFirst().orElseThrow();
    }

    private static <T extends Component> T component(Container root, String accessibleName, Class<T> type) {
        return descendants(root).stream().filter(type::isInstance).map(type::cast)
                .filter(candidate -> accessibleName.equals(candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElse(null);
    }

    private static Component namedComponent(Container root, String accessibleName) {
        return descendants(root).stream()
                .filter(candidate -> candidate instanceof JTextField || candidate instanceof JSpinner)
                .filter(candidate -> candidate.getAccessibleContext() != null
                        && accessibleName.equals(candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static void setTemporalValue(Container root, String accessibleName, String text, String instant) {
        Component component = namedComponent(root, accessibleName);
        if (component instanceof JSpinner spinner) {
            spinner.setValue(Date.from(Instant.parse(instant)));
        } else {
            ((JTextField) component).setText(text);
        }
    }

    private static Object spinnerStep(JSpinner spinner, boolean next) {
        try {
            return next ? spinner.getNextValue() : spinner.getPreviousValue();
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private static JTable enrollmentTable(Container root) {
        return descendants(root).stream().filter(JTable.class::isInstance).map(JTable.class::cast)
                .filter(table -> "我的选课记录".equals(table.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Container root, String accessibleName) {
        return descendants(root).stream().filter(JTable.class::isInstance).map(JTable.class::cast)
                .filter(candidate -> accessibleName.equals(candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static void fireAction(JButton button) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (java.awt.event.ActionListener listener : button.getActionListeners()) {
                listener.actionPerformed(new java.awt.event.ActionEvent(
                        button, java.awt.event.ActionEvent.ACTION_PERFORMED, button.getActionCommand()));
            }
        });
    }

    private static CourseUiGateway adjustmentSelectionGateway(
            AtomicInteger drops, AtomicInteger changes) {
        CourseUiGateway base = CourseUiGateway.preview();
        EnrollmentView dropped = new EnrollmentView(
                "dropped-enrollment", "o1", "student-1", "NORMAL", "DROPPED",
                Instant.parse("2026-08-27T08:00:00Z"), Instant.parse("2026-08-28T08:00:00Z"), 8);
        EnrollmentView active = new EnrollmentView(
                "active-enrollment", "o1", "student-1", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-29T08:00:00Z"), null, 3);
        return new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(List.of(dropped, active));
            }

            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) {
                drops.incrementAndGet();
                return new CompletableFuture<>();
            }

            @Override public CompletableFuture<EnrollmentView> change(ChangeOfferingCommand command) {
                changes.incrementAndGet();
                return CompletableFuture.completedFuture(active);
            }
        };
    }

    private static CourseUiGateway enrollmentGateway(
            List<EnrollmentView> enrollments, String phase, String termStatus) {
        CourseUiGateway base = CourseUiGateway.preview();
        return new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(enrollments);
            }

            @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return enrollmentPhase(termId, phase, termStatus);
            }
        };
    }

    private static CompletableFuture<TermPhaseView> enrollmentPhase(
            String termId, String phase, String termStatus) {
        return CompletableFuture.completedFuture(new TermPhaseView(
                termId, termStatus, phase, Instant.parse("2026-08-25T00:00:00Z"),
                Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-31T16:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-08T16:00:00Z")));
    }

    private static JTextField textField(Container root, String accessibleName) {
        return descendants(root).stream().filter(JTextField.class::isInstance).map(JTextField.class::cast)
                .filter(field -> accessibleName.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private abstract static class DelegatingCourseUiGateway implements CourseUiGateway {
        private final CourseUiGateway delegate;

        private DelegatingCourseUiGateway(CourseUiGateway delegate) { this.delegate = delegate; }
        public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
            return delegate.searchOfferings(query);
        }
        public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return delegate.currentEnrollments(); }
        public CompletableFuture<List<ScheduleItem>> currentSchedule() { return delegate.currentSchedule(); }
        public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return delegate.enroll(command); }
        @Override public CompletableFuture<String> currentTermId() { return delegate.currentTermId(); }
        @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
            return delegate.getTermPhase(termId);
        }
        @Override public CompletableFuture<List<TermView>> listTerms() { return delegate.listTerms(); }
        @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
            return delegate.searchCatalog(query);
        }
        @Override public CompletableFuture<PageResult<UserSummary>> searchTeachers(String keyword) {
            return delegate.searchTeachers(keyword);
        }
        @Override public CompletableFuture<Optional<UserSummary>> resolveTeacher(String userId) {
            return delegate.resolveTeacher(userId);
        }
        @Override public CompletableFuture<OfferingView> createOffering(CreateOfferingCommand command) {
            return delegate.createOffering(command);
        }
        @Override public CompletableFuture<OfferingView> updateOffering(UpdateOfferingCommand command) {
            return delegate.updateOffering(command);
        }
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> supplier) throws Exception {
        java.util.concurrent.atomic.AtomicReference<T> value = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { value.set(supplier.call()); } catch (Exception error) { throw new RuntimeException(error); }
        });
        return value.get();
    }
}
