package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.shell.ModulePlaceholderPage;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.ui.MyStudentProfilePanel;
import edu.seu.vcampus.client.student.ui.OrganizationManagementPanel;
import edu.seu.vcampus.client.student.ui.StudentModulePageFactory;
import edu.seu.vcampus.client.student.ui.StudentSearchPanel;
import edu.seu.vcampus.client.student.ui.StudentProfileReviewPanel;
import edu.seu.vcampus.client.student.majortransfer.ui.MajorTransferAdminPanel;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;

class StudentModulePageFactoryTest {
    private final ArrayList<ClientConnection> connections = new ArrayList<>();

    @AfterEach
    void closeConnections() {
        connections.forEach(ClientConnection::close);
    }

    @Test
    void signedInStudentWithLiveDependenciesReceivesProfileNamedStudentProfile()
            throws Exception {
        StudentClientService students = students(new AtomicInteger(), new CountDownLatch(1));
        JPanel page = onEdt(() -> StudentModulePageFactory.create(
                user(UserRole.STUDENT), students, connection()));

        JTabbedPane tabs = findTabbedPane(page);
        assertThat(tabs.getTabCount()).isEqualTo(4);
        assertThat(tabs.getComponentAt(0)).isInstanceOf(MyStudentProfilePanel.class);
        assertThat(tabs.getTitleAt(1)).isEqualTo("培养方案");
        assertThat(tabs.getTitleAt(2)).isEqualTo("成绩单");
        assertThat(tabs.getTitleAt(3)).isEqualTo("转专业申请");
    }

    @Test
    void teacherReceivesTabbedPanelWithSearchOnly() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch requestStarted = new CountDownLatch(1);
        StudentClientService students = students(requests, requestStarted);

        JPanel page = onEdt(() -> StudentModulePageFactory.create(
                user(UserRole.TEACHER), students, connection()));

        assertThat(page.getName()).isEqualTo("student.module");
        JTabbedPane tabs = findTabbedPane(page);
        assertThat(tabs).isNotNull();
        assertThat(tabs.getTabCount()).isEqualTo(1);
        assertThat(tabs.getTitleAt(0)).isEqualTo("学生查询");
        assertThat(tabs.getComponentAt(0)).isInstanceOf(StudentSearchPanel.class);
        assertThat(requests).hasValue(0);
    }

    @Test
    void studentAdminReceivesSearchOrganizationAndProfileReviewTabs() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch requestStarted = new CountDownLatch(1);
        StudentClientService students = students(requests, requestStarted);

        JPanel page = onEdt(() -> StudentModulePageFactory.create(
                user(UserRole.STUDENT_ADMIN), students, connection()));

        assertThat(page.getName()).isEqualTo("student.module");
        JTabbedPane tabs = findTabbedPane(page);
        assertThat(tabs).isNotNull();
        assertThat(tabs.getTabCount()).isEqualTo(6);
        assertThat(tabs.getTitleAt(0)).isEqualTo("学生查询");
        assertThat(tabs.getComponentAt(0)).isInstanceOf(StudentSearchPanel.class);
        assertThat(tabs.getTitleAt(1)).isEqualTo("组织管理");
        assertThat(tabs.getComponentAt(1)).isInstanceOf(OrganizationManagementPanel.class);
        assertThat(tabs.getTitleAt(2)).isEqualTo("资料审核");
        assertThat(tabs.getComponentAt(2)).isInstanceOf(StudentProfileReviewPanel.class);
        assertThat(tabs.getTitleAt(3)).isEqualTo("转专业管理");
        assertThat(tabs.getTitleAt(4)).isEqualTo("培养方案管理");
        assertThat(tabs.getTitleAt(5)).isEqualTo("成绩管理");
    }

    @Test
    void collegeAdminReceivesOnlyTheTransferApprovalWorkspace() throws Exception {
        JPanel page = onEdt(() -> StudentModulePageFactory.create(
                user(UserRole.COLLEGE_ADMIN),
                students(new AtomicInteger(), new CountDownLatch(1)), connection()));

        JTabbedPane tabs = findTabbedPane(page);
        assertThat(tabs.getTabCount()).isEqualTo(1);
        assertThat(tabs.getTitleAt(0)).isEqualTo("转专业审批");
        assertThat(tabs.getComponentAt(0)).isInstanceOf(MajorTransferAdminPanel.class);
    }

    @Test
    void missingCompatibilityDependenciesKeepTheStructuredStudentPlaceholder()
            throws Exception {
        JPanel noUser = onEdt(() -> StudentModulePageFactory.create(null, null, null));
        JPanel missingStudentService = onEdt(() -> StudentModulePageFactory.create(
                user(UserRole.STUDENT), null, connection()));
        JPanel missingConnection = onEdt(() -> StudentModulePageFactory.create(
                user(UserRole.STUDENT), students(new AtomicInteger(), new CountDownLatch(1)), null));

        assertThat(noUser).isInstanceOf(ModulePlaceholderPage.class);
        assertThat(noUser.getName()).isEqualTo("page.student");
        assertThat(missingStudentService).isInstanceOf(ModulePlaceholderPage.class);
        assertThat(missingStudentService.getName()).isEqualTo("page.student");
        assertThat(missingConnection).isInstanceOf(ModulePlaceholderPage.class);
        assertThat(missingConnection.getName()).isEqualTo("page.student");
    }

    @Test
    void restrictedStudentKeepsTheStructuredStudentPlaceholder() throws Exception {
        JPanel page = onEdt(() -> StudentModulePageFactory.create(
                user(UserRole.STUDENT, true),
                students(new AtomicInteger(), new CountDownLatch(1)), connection()));

        assertThat(page).isInstanceOf(ModulePlaceholderPage.class);
        assertThat(page.getName()).isEqualTo("page.student");
    }

    @Test
    void profileDoesNotRequestUntilItsSwingLifecycleMakesItDisplayable()
            throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch requestStarted = new CountDownLatch(1);
        StudentClientService students = students(requests, requestStarted);
        MyStudentProfilePanel page = onEdt(() -> (MyStudentProfilePanel) findTabbedPane(
                StudentModulePageFactory.create(user(UserRole.STUDENT), students, connection())).getComponentAt(0));

        // Other tabs (培养方案, 成绩单) fire requests on construction
        int baseline = requests.get();

        onEdt(() -> page.addNotify());
        assertThat(requestStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(requests.get()).isGreaterThan(baseline);
        onEdt(() -> page.removeNotify());
    }

    private static JTabbedPane findTabbedPane(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTabbedPane tabs) return tabs;
            if (c instanceof Container nested) {
                JTabbedPane found = findTabbedPane(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private ClientConnection connection() {
        ClientConnection connection = new ClientConnection("localhost", 1);
        connections.add(connection);
        return connection;
    }

    private static StudentClientService students(AtomicInteger requests, CountDownLatch requestStarted) {
        return new StudentClientService(new edu.seu.vcampus.client.student.service.StudentRequestClient() {
            @Override
            public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                requests.incrementAndGet();
                requestStarted.countDown();
                return CompletableFuture.completedFuture(ResponseBody.failure("DENIED", "denied", null));
            }
        }, Duration.ofSeconds(1));
    }

    private static UserView user(UserRole role) {
        return user(role, false);
    }

    private static UserView user(UserRole role, boolean mustChangePassword) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 30, 9, 0);
        return new UserView("user-1", "student-1", role, ACTIVE, mustChangePassword,
                now, 1, now, now);
    }

    private static <T> T onEdt(ThrowingSupplier<T> supplier) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.complete(supplier.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get();
    }

    private static void onEdt(ThrowingRunnable runnable) throws Exception {
        onEdt(() -> {
            runnable.run();
            return null;
        });
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
