package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.course.service.CourseTransport;
import edu.seu.vcampus.client.course.ui.CourseUiComposition;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.time.LocalDateTime;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuthenticatedCourseShellTest {
    @Test
    void selectsExactlyThePagesAuthorizedForEachRole() {
        CourseUiComposition composition = new CourseUiComposition(
                edu.seu.vcampus.client.course.ui.CourseUiGateway.preview());

        assertThat(composition.pagesFor(UserRole.STUDENT).keySet()).containsExactly(
                "course.offerings", "course.enrollments", "course.schedule", "course.adjustment", "course.retake");
        assertThat(composition.pagesFor(UserRole.TEACHER).keySet()).containsExactly(
                "course.offerings", "course.schedule");
        assertThat(composition.pagesFor(UserRole.ADMIN).keySet()).containsExactly(
                "course.terms", "course.catalog", "course.offering-admin",
                "course.outcome-import", "course.adjustment-audit");
    }

    @Test
    void shellShowsOnlyStudentNavigationAndNeverDisplaysTheSessionToken() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        ClientConnection connection = mock(ClientConnection.class);
        CourseClientService courses = failingCourses("COMMON_FORBIDDEN");
        MainFrame[] shell = new MainFrame[1];

        SwingUtilities.invokeAndWait(() -> shell[0] = new MainFrame(
                user(UserRole.STUDENT), courses, connection));

        assertThat(buttonTexts(shell[0].navigation())).containsExactly(
                "教学班查询", "我的选课", "我的课表", "退改补", "重修");
        assertThat(String.join(" ", labelTexts(shell[0].header()))).contains("S20260001", "STUDENT")
                .doesNotContain("session-token-must-not-appear");
        SwingUtilities.invokeAndWait(shell[0]::dispose);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUTH_SESSION_EXPIRED", "AUTH_ACCOUNT_DISABLED", "AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED"})
    void authenticationFailuresFromRealCoursePanelsClearTheTokenCloseTheShellAndReopenLogin(String code)
            throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        ClientConnection connection = mock(ClientConnection.class);
        CourseClientService courses = failingCourses(code);
        AtomicInteger loginRequests = new AtomicInteger();
        MainFrame[] shell = new MainFrame[1];

        SwingUtilities.invokeAndWait(() -> {
            shell[0] = new MainFrame(user(UserRole.TEACHER), courses, connection,
                    loginRequests::incrementAndGet);
            shell[0].setVisible(true);
        });
        awaitEdt(() -> loginRequests.get() == 1);

        verify(connection).setSessionToken(null);
        assertThat(shell[0].isDisplayable()).isFalse();
        assertThat(loginRequests).hasValue(1);
    }

    @Test
    void nonAuthenticationCourseFailuresDoNotLeaveTheCourseShell() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        ClientConnection connection = mock(ClientConnection.class);
        CourseClientService courses = failingCourses("COURSE_TERM_CLOSED");
        AtomicBoolean loginRequested = new AtomicBoolean();
        MainFrame[] shell = new MainFrame[1];

        SwingUtilities.invokeAndWait(() -> {
            shell[0] = new MainFrame(user(UserRole.TEACHER), courses, connection,
                    () -> loginRequested.set(true));
            shell[0].setVisible(true);
        });
        SwingUtilities.invokeAndWait(() -> { });

        verify(connection, never()).setSessionToken(null);
        assertThat(loginRequested).isFalse();
        assertThat(shell[0].isDisplayable()).isTrue();
        SwingUtilities.invokeAndWait(shell[0]::dispose);
    }

    private static CourseClientService failingCourses(String code) {
        return new CourseClientService(new CourseTransport() {
            @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                return CompletableFuture.completedFuture(ResponseBody.failure(code, "rejected", null));
            }
        });
    }

    private static UserView user(UserRole role) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 30, 12, 0);
        return new UserView("student-1", "S20260001", role, ACTIVE,
                false, now, 1, now, now);
    }

    private static List<String> buttonTexts(Container root) {
        return descendants(root).stream().filter(JButton.class::isInstance)
                .map(JButton.class::cast).map(JButton::getText).toList();
    }

    private static List<String> labelTexts(Container root) {
        return descendants(root).stream().filter(JLabel.class::isInstance)
                .map(JLabel.class::cast).map(JLabel::getText).toList();
    }

    private static List<Component> descendants(Container root) {
        List<Component> found = new ArrayList<>();
        for (Component component : root.getComponents()) {
            found.add(component);
            if (component instanceof Container child) {
                found.addAll(descendants(child));
            }
        }
        return found;
    }

    private static void awaitEdt(java.util.function.BooleanSupplier condition) throws Exception {
        for (int attempt = 0; attempt < 50 && !condition.getAsBoolean(); attempt++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
