package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Dimension;

import static org.assertj.core.api.Assertions.assertThat;

class MainFrameLayoutTest {
    @Test
    void shellFollowsReviewedStructureAndNavigationOrder() throws Exception {
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new MainFrame(
                new UserView("demo-user", "ADMIN", UserRole.ADMIN)));

        try {
            MainFrame frame = holder[0];
            assertThat(frame.getSize()).isEqualTo(new Dimension(1280, 800));
            assertThat(frame.getMinimumSize()).isEqualTo(new Dimension(1024, 680));
            assertThat(frame.navigationLabels()).containsExactly(
                    "学籍档案", "课程中心", "图书借阅", "校园商城", "账户设置");
            assertThat(frame.navigationLabels()).doesNotContain("总览", "账户管理");
            assertThat(frame.currentPageTitle()).isEqualTo("学籍档案");

            SwingUtilities.invokeAndWait(() -> frame.showPage("course"));
            assertThat(frame.currentPageTitle()).isEqualTo("课程中心");
        } finally {
            SwingUtilities.invokeAndWait(holder[0]::dispose);
        }
    }
}
