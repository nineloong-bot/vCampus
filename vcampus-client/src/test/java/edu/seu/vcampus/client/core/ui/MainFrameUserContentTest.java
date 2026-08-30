package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.time.LocalDateTime;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;

class MainFrameUserContentTest {
    @Test
    void userAwareContentKeepsPageNavigatorCardsSelectable() {
        JPanel header = new JPanel(new BorderLayout());
        JPanel content = new JPanel();
        PageNavigator navigator = new PageNavigator(content);
        JPanel course = new JPanel();

        MainFrame.configureLoggedInContent(header, navigator, demoUser());
        navigator.register("course", course);

        assertThat(content.getLayout()).isInstanceOf(CardLayout.class);
        assertThat(content.getComponentCount()).isEqualTo(2);
        ((CardLayout) content.getLayout()).show(content, "course");
        assertThat(course.isVisible()).isTrue();
        assertThat(header.getComponentCount()).isEqualTo(1);
        assertThat(((JLabel) header.getComponent(0)).getText())
                .contains("DEMO_ADMIN", "ADMIN");
    }

    private static UserView demoUser() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 28, 12, 0);
        return new UserView("demo-user", "DEMO_ADMIN", ADMIN, ACTIVE,
                false, now, 0, now, now);
    }
}
