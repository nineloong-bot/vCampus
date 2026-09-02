package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.shop.service.ShopClientService;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnifiedMainFrameTest {
    @Test
    void authenticatedAdministratorReceivesEveryRealModuleInOneShell() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        Duration timeout = Duration.ofSeconds(1);
        UserClientService users = new UserClientService(connection, "client", timeout);
        MainFrame[] frame = new MainFrame[1];

        SwingUtilities.invokeAndWait(() -> frame[0] = new MainFrame(admin(), connection,
                new StudentClientService(connection, timeout),
                new CourseClientService(connection),
                new LibraryClientService(connection, timeout),
                new ShopClientService(connection, timeout), users,
                Set.of("LIBRARY_ADMIN"), () -> { }));

        try {
            assertThat(frame[0].registeredPageIds())
                    .containsExactlyInAnyOrder("student", "course", "library", "shop", "account");
            assertThat(frame[0].pageNavigator().page("course").getClass().getSimpleName())
                    .isEqualTo("CourseWorkspacePanel");
            assertThat(frame[0].pageNavigator().page("library").getClass().getSimpleName())
                    .isEqualTo("LibraryWorkspacePanel");
            assertThat(frame[0].pageNavigator().page("shop").getComponentCount()).isPositive();
        } finally {
            SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }

    private static UserView admin() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 12, 0);
        return new UserView("admin", "ADMIN001", UserRole.ADMIN, AccountStatus.ACTIVE,
                false, now, 0, now, now);
    }
}
