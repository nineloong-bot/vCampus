package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.course.service.CourseTransport;
import edu.seu.vcampus.client.course.ui.CourseUiComposition;
import edu.seu.vcampus.client.course.ui.CourseWorkspacePanel;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.LoginFrame;
import edu.seu.vcampus.client.user.ui.UserUiCoordinator;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticatedCourseShellTest {
    private static final List<String> GLOBAL_MODULES = List.of(
            "学籍档案", "课程中心", "图书借阅", "校园商城", "账户设置");

    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @ParameterizedTest
    @MethodSource("roleTabs")
    void everyAuthenticatedRoleKeepsFiveGlobalModulesAndOwnsOnlyItsCourseTabs(
            UserRole role, List<String> expectedTabs) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        UserClientService users = usersFor(role);
        ClientConnection connection = connected();
        UserUiCoordinator coordinator = new UserUiCoordinator(
                users, failingCourses("COMMON_FORBIDDEN"), connection);

        SwingUtilities.invokeAndWait(coordinator::start);
        submitLogin(showing(LoginFrame.class));
        awaitEdt(() -> showingWindows(MainFrame.class).size() == 1);
        MainFrame shell = showing(MainFrame.class);
        SwingUtilities.invokeAndWait(() -> component(
                shell, "navigation.course", AbstractButton.class).doClick());

        assertThat(buttonTexts(shell.navigation())).containsExactlyElementsOf(GLOBAL_MODULES);
        CourseWorkspacePanel workspace = component(shell.content(), "page.course", CourseWorkspacePanel.class);
        assertThat(workspace.isVisible()).isTrue();
        JTabbedPane tabs = descendants(workspace).stream()
                .filter(JTabbedPane.class::isInstance).map(JTabbedPane.class::cast)
                .findFirst().orElseThrow();
        assertThat(IntStream.range(0, tabs.getTabCount()).mapToObj(tabs::getTitleAt))
                .containsExactlyElementsOf(expectedTabs);
        assertThat(String.join(" ", labelTexts(shell.header())))
                .contains("S20260001", roleLabel(role)).doesNotContain("session-token-must-not-appear");
    }

    @Test
    void hiddenTeacherTabDoesNotIssueRequestsUntilSelected() throws Exception {
        List<String> commands = new CopyOnWriteArrayList<>();
        CourseClientService courses = successfulTeacherCourses(commands);
        CourseWorkspacePanel workspace = onEdt(
                () -> new CourseUiComposition(courses).workspaceFor(UserRole.TEACHER));

        awaitEdt(() -> commands.contains("COURSE_SEARCH_OFFERINGS"));
        assertThat(commands).containsExactly("COURSE_GET_CURRENT_TERM", "COURSE_SEARCH_OFFERINGS");
        assertThat(commands).doesNotContain("COURSE_GET_MY_SCHEDULE", "COURSE_TERM_LIST");

        JTabbedPane tabs = descendants(workspace).stream()
                .filter(JTabbedPane.class::isInstance).map(JTabbedPane.class::cast)
                .findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(1));
        awaitEdt(() -> commands.contains("COURSE_GET_MY_SCHEDULE"));

        assertThat(commands).contains("COURSE_GET_MY_SCHEDULE", "COURSE_TERM_LIST");
        assertThat(commands).doesNotContain("COURSE_GET_MY_ENROLLMENTS", "COURSE_CATALOG_SEARCH",
                "COURSE_ADJUSTMENT_AUDIT_SEARCH");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AUTH_SESSION_EXPIRED", "AUTH_ACCOUNT_DISABLED", "AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED"
    })
    void authoritativeCourseAuthenticationFailuresClearSessionAndReturnToOneLogin(String code)
            throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        UserClientService users = usersFor(UserRole.TEACHER);
        ClientConnection connection = connected();
        List<String> commands = new CopyOnWriteArrayList<>();
        CourseClientService courses = authenticationOnSecondOfferingSearch(code, commands);
        UserUiCoordinator coordinator = new UserUiCoordinator(users, courses, connection);

        SwingUtilities.invokeAndWait(coordinator::start);
        submitLogin(showing(LoginFrame.class));
        awaitEdt(() -> showingWindows(MainFrame.class).size() == 1);
        MainFrame shell = showing(MainFrame.class);
        awaitEdt(() -> commands.contains("COURSE_SEARCH_OFFERINGS"));
        SwingUtilities.invokeAndWait(() -> button(shell.content(), "查询教学班").doClick());
        awaitEdt(() -> showingWindows(LoginFrame.class).size() == 1);

        verify(users, times(1)).clearSession();
        assertThat(shell.isDisplayable()).isFalse();
        assertThat(showingWindows(LoginFrame.class)).hasSize(1);
        assertThat(text(showing(LoginFrame.class))).contains("登录状态已失效，请重新登录")
                .doesNotContain(code, "session-token-must-not-appear");

        courses.searchOfferings(new edu.seu.vcampus.common.course.OfferingSearchQuery(
                "term", null, null, false, 0, 20)).exceptionally(ignored -> null).join();
        flushEdt();
        verify(users, times(1)).clearSession();
        assertThat(showingWindows(LoginFrame.class)).hasSize(1);
    }

    @Test
    void nonAuthenticationCourseFailureKeepsAuthenticatedShellOpen() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        UserClientService users = usersFor(UserRole.TEACHER);
        UserUiCoordinator coordinator = new UserUiCoordinator(
                users, failingCourses("COURSE_TERM_CLOSED"), connected());

        SwingUtilities.invokeAndWait(coordinator::start);
        submitLogin(showing(LoginFrame.class));
        awaitEdt(() -> showingWindows(MainFrame.class).size() == 1);
        MainFrame shell = showing(MainFrame.class);
        flushEdt();

        verify(users, never()).clearSession();
        assertThat(shell.isDisplayable()).isTrue();
        assertThat(showingWindows(LoginFrame.class)).isEmpty();
    }

    static Stream<Arguments> roleTabs() {
        return Stream.of(
                Arguments.of(UserRole.STUDENT, List.of("选课", "我的选课", "我的课表")),
                Arguments.of(UserRole.TEACHER, List.of("教学班查询", "教师课表")),
                Arguments.of(UserRole.ADMIN, List.of("学期管理", "选课阶段", "课程目录", "教学班管理", "修读结果导入", "选退记录")));
    }

    private static UserClientService usersFor(UserRole role) {
        UserClientService users = mock(UserClientService.class);
        LoginResult result = loginResult(role);
        doReturn(CompletableFuture.completedFuture(result))
                .when(users).login(anyString(), any(char[].class));
        doReturn(CompletableFuture.completedFuture(result.user())).when(users).getCurrentUser();
        return users;
    }

    private static ClientConnection connected() {
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        return connection;
    }

    private static CourseClientService failingCourses(String code) {
        return new CourseClientService(new CourseTransport() {
            @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                return CompletableFuture.completedFuture(ResponseBody.failure(code, "rejected", null));
            }
        });
    }

    private static CourseClientService successfulTeacherCourses(List<String> commands) {
        return new CourseClientService(new CourseTransport() {
            @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                commands.add(command);
                return switch (command) {
                    case "COURSE_GET_CURRENT_TERM" -> success(term());
                    case "COURSE_SEARCH_OFFERINGS" -> success(
                            new edu.seu.vcampus.common.paging.PageResult<edu.seu.vcampus.common.course.OfferingSummary>(
                                    List.of(), 0, 20, 0));
                    case "COURSE_GET_MY_SCHEDULE" -> success(new ArrayList<>());
                    default -> CompletableFuture.completedFuture(
                            ResponseBody.failure("COMMON_FORBIDDEN", "rejected", null));
                };
            }
        });
    }

    private static CourseClientService authenticationOnSecondOfferingSearch(
            String code, List<String> commands) {
        java.util.concurrent.atomic.AtomicInteger offeringSearches = new java.util.concurrent.atomic.AtomicInteger();
        return new CourseClientService(new CourseTransport() {
            @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                commands.add(command);
                if ("COURSE_GET_CURRENT_TERM".equals(command)) return success(term());
                if ("COURSE_SEARCH_OFFERINGS".equals(command)) {
                    if (offeringSearches.incrementAndGet() > 1) {
                        return CompletableFuture.completedFuture(ResponseBody.failure(code, "rejected", null));
                    }
                    return success(new edu.seu.vcampus.common.paging.PageResult<edu.seu.vcampus.common.course.OfferingSummary>(
                            List.of(), 0, 20, 0));
                }
                return CompletableFuture.completedFuture(
                        ResponseBody.failure("COMMON_FORBIDDEN", "rejected", null));
            }
        });
    }

    private static edu.seu.vcampus.common.course.TermView term() {
        return new edu.seu.vcampus.common.course.TermView(
                "term", "2026-1", "学期", java.time.LocalDate.of(2026, 9, 1),
                java.time.LocalDate.of(2027, 1, 1), java.time.Instant.EPOCH,
                java.time.Instant.EPOCH, java.time.Instant.EPOCH, java.time.Instant.EPOCH,
                "ACTIVE", 0, java.time.Instant.EPOCH, java.time.Instant.EPOCH);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> CompletableFuture<ResponseBody<T>> success(Serializable value) {
        return (CompletableFuture<ResponseBody<T>>) (CompletableFuture<?>)
                CompletableFuture.completedFuture(ResponseBody.success(value));
    }

    private static LoginResult loginResult(UserRole role) {
        return new LoginResult("session-token-must-not-appear", user(role), Set.of(), false);
    }

    private static UserView user(UserRole role) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 30, 12, 0);
        return new UserView("student-1", "S20260001", role, ACTIVE,
                false, now, 1, now, now);
    }

    private static String roleLabel(UserRole role) {
        return switch (role) {
            case STUDENT -> "学生";
            case TEACHER -> "教师";
            case ADMIN -> "管理员";
        };
    }

    private static void submitLogin(LoginFrame frame) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            component(frame, "login.loginId", JTextField.class).setText("S20260001");
            component(frame, "login.password", JPasswordField.class).setText("Password7");
            component(frame, "login.submit", AbstractButton.class).doClick();
        });
    }

    private static List<String> buttonTexts(Container root) {
        return Arrays.stream(root.getComponents()).filter(AbstractButton.class::isInstance)
                .map(AbstractButton.class::cast).map(AbstractButton::getText).toList();
    }

    private static List<String> labelTexts(Container root) {
        return descendants(root).stream().filter(JLabel.class::isInstance)
                .map(JLabel.class::cast).map(JLabel::getText).toList();
    }

    private static List<Component> descendants(Container root) {
        List<Component> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            found.add(child);
            if (child instanceof Container nested) found.addAll(descendants(nested));
        }
        return found;
    }

    private static JButton button(Container root, String text) {
        return descendants(root).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(candidate -> text.equals(candidate.getText())).findFirst().orElseThrow();
    }

    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        return descendants(root).stream().filter(type::isInstance).map(type::cast)
                .filter(candidate -> name.equals(candidate.getName())).findFirst().orElseThrow();
    }

    private static String text(Container root) {
        return descendants(root).stream().map(component -> {
            if (component instanceof JLabel label) return label.getText();
            if (component instanceof AbstractButton button) return button.getText();
            return "";
        }).reduce("", (left, right) -> left + " " + right);
    }

    private static <T extends Window> T showing(Class<T> type) {
        return showingWindows(type).stream().findFirst().orElseThrow();
    }

    private static <T extends Window> List<T> showingWindows(Class<T> type) {
        return Arrays.stream(Window.getWindows()).filter(type::isInstance).map(type::cast)
                .filter(Window::isShowing).toList();
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> supplier) throws Exception {
        java.util.concurrent.atomic.AtomicReference<T> value = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                value.set(supplier.call());
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        return value.get();
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void awaitEdt(java.util.function.BooleanSupplier condition) throws Exception {
        for (int attempt = 0; attempt < 100 && !condition.getAsBoolean(); attempt++) {
            flushEdt();
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
