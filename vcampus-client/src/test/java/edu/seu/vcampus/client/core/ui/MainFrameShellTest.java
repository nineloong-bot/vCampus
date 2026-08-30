package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MainFrameShellTest {
    private static final String[] PAGE_IDS = {
            "student", "course", "library", "shop", "account"
    };
    private static final String[] TITLES = {
            "学籍档案", "课程中心", "图书借阅", "校园商城", "账户设置"
    };

    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Frame.getFrames())
                .forEach(Frame::dispose));
    }

    @Test
    void shellShowsIdentityAndFiveOrderedNavigationEntries() throws Exception {
        MainFrame[] frame = new MainFrame[1];
        ClientConnection connection = connected();
        SwingUtilities.invokeAndWait(() -> frame[0] = new MainFrame(
                user(), connection));

        assertThat(frame[0].getSize().width).isEqualTo(1280);
        assertThat(frame[0].getSize().height).isEqualTo(800);
        assertThat(frame[0].getMinimumSize().width).isEqualTo(1024);
        assertThat(frame[0].getMinimumSize().height).isEqualTo(680);
        assertThat(component(frame[0], "header.brand", JLabel.class).getText())
                .isEqualTo("vCampus · 虚拟校园");
        assertThat(component(frame[0], "identity.summary", JLabel.class).getText())
                .isEqualTo("DEMO_ADMIN · 管理员");
        assertThat(component(frame[0], "status.message", JLabel.class).getText())
                .isEqualTo("就绪");
        assertThat(component(frame[0], "status.date", JLabel.class).getText())
                .matches("\\d{4}年\\d{2}月\\d{2}日");
        assertThat(component(frame[0], "connection.status", JLabel.class).getForeground())
                .isEqualTo(UiColors.TEXT_ON_PRIMARY);
        List<String> navigationOrder = Arrays.stream(frame[0].navigation().getComponents())
                .filter(AbstractButton.class::isInstance)
                .map(AbstractButton.class::cast)
                .map(AbstractButton::getText)
                .toList();
        assertThat(navigationOrder).containsExactly(TITLES);
        for (int index = 0; index < PAGE_IDS.length; index++) {
            AbstractButton item = component(frame[0], "navigation." + PAGE_IDS[index],
                    AbstractButton.class);
            assertThat(item.getText()).isEqualTo(TITLES[index]);
            assertThat(item.getAccessibleContext().getAccessibleName()).isNotBlank();
            assertThat(item.isSelected()).isEqualTo(index == 0);
        }
    }

    @Test
    void navigationUsesCompactFullWidthAcademicRowsWithoutPlatformButtonChrome()
            throws Exception {
        MainFrame[] frame = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> frame[0] = new MainFrame(user(), connected()));
        JPanel navigation = frame[0].navigation();

        assertThat(navigation.getPreferredSize().width)
                .isEqualTo(UiDimensions.NAVIGATION_WIDTH);
        assertThat(navigation.getBackground()).isEqualTo(UiColors.BACKGROUND_NAV);
        assertThat(navigation.getBorder().getBorderInsets(navigation))
                .isEqualTo(new Insets(0, 0, 0, 0));
        assertThat(navigation.getComponentCount()).isEqualTo(PAGE_IDS.length);

        for (int index = 0; index < PAGE_IDS.length; index++) {
            AbstractButton item = component(navigation, "navigation." + PAGE_IDS[index],
                    AbstractButton.class);
            assertThat(item.getPreferredSize())
                    .isEqualTo(new java.awt.Dimension(UiDimensions.NAVIGATION_WIDTH,
                            UiDimensions.CONTROL_HEIGHT));
            assertThat(item.getMaximumSize())
                    .isEqualTo(item.getPreferredSize());
            assertThat(item.getHorizontalAlignment()).isEqualTo(AbstractButton.LEFT);
            assertThat(item.getMargin()).isEqualTo(new Insets(0, 0, 0, 0));
            assertThat(item.getBorder()).isInstanceOf(EmptyBorder.class);
            assertThat(item.isFocusPainted()).isFalse();
            assertThat(item.isOpaque()).isTrue();
            assertThat(item.isContentAreaFilled()).isTrue();
            assertThat(item.getBackground()).isEqualTo(index == 0
                    ? UiColors.PRIMARY : UiColors.BACKGROUND_NAV);
            assertThat(item.getForeground()).isEqualTo(index == 0
                    ? UiColors.TEXT_ON_PRIMARY : UiColors.TEXT_PRIMARY);
        }
    }

    @Test
    void navigationFocusAndSelectionRemainVisibleAndSynchronized() throws Exception {
        MainFrame[] frame = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> frame[0] = new MainFrame(user(), connected()));
        AbstractButton first = component(frame[0], "navigation.student", AbstractButton.class);
        AbstractButton course = component(frame[0], "navigation.course", AbstractButton.class);

        SwingUtilities.invokeAndWait(() -> {
            course.doClick();
            FocusEvent gained = new FocusEvent(course, FocusEvent.FOCUS_GAINED);
            for (FocusListener listener : course.getFocusListeners()) {
                listener.focusGained(gained);
            }
        });

        assertThat(course.isSelected()).isTrue();
        assertThat(course.getBackground()).isEqualTo(UiColors.PRIMARY);
        assertThat(course.getForeground()).isEqualTo(UiColors.TEXT_ON_PRIMARY);
        assertThat(first.isSelected()).isFalse();
        assertThat(first.getBackground()).isEqualTo(UiColors.BACKGROUND_NAV);
        assertThat(first.getForeground()).isEqualTo(UiColors.TEXT_PRIMARY);
        assertThat(course.getBorder()).isInstanceOf(CompoundBorder.class);
        CompoundBorder focusBorder = (CompoundBorder) course.getBorder();
        assertThat(focusBorder.getOutsideBorder()).isSameAs(UiBorders.FOCUS);
        assertThat(component(frame[0], "page.course", JPanel.class).isVisible()).isTrue();
    }

    @Test
    void everyNavigationItemSwitchesToItsStructuredPlaceholder() throws Exception {
        MainFrame[] frame = new MainFrame[1];
        ClientConnection connection = connected();
        SwingUtilities.invokeAndWait(() -> frame[0] = new MainFrame(
                user(), connection));

        for (int index = 0; index < PAGE_IDS.length; index++) {
            int current = index;
            SwingUtilities.invokeAndWait(() -> component(frame[0],
                    "navigation." + PAGE_IDS[current], AbstractButton.class).doClick());
            JPanel page = component(frame[0], "page." + PAGE_IDS[index], JPanel.class);
            assertThat(page.isVisible()).isTrue();
            assertThat(component(page, "page.breadcrumb", JLabel.class).getText())
                    .isEqualTo("虚拟校园 / " + TITLES[index]);
            assertThat(component(page, "page.title", JLabel.class).getText())
                    .isEqualTo(TITLES[index]);
            assertThat(component(page, "page.description", JLabel.class).getText())
                    .isNotBlank();
            assertThat(component(page, "page.status", JLabel.class).getText())
                    .isEqualTo("功能建设中");
            assertThat(page.isFocusable()).isTrue();
            assertThat(page.getAccessibleContext().getAccessibleName()).contains(TITLES[index]);
            assertThat(component(frame[0], "navigation." + PAGE_IDS[index],
                    AbstractButton.class).isSelected()).isTrue();
        }
    }

    private static UserView user() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 12, 0);
        return new UserView("demo", "DEMO_ADMIN", ADMIN, ACTIVE,
                false, now, 0, now, now);
    }

    private static ClientConnection connected() {
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        return connection;
    }

    private static <T extends Component> T component(
            Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                try {
                    return component(nested, name, type);
                } catch (IllegalArgumentException ignored) {
                    // Search remaining siblings.
                }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    private static String text(Container root) {
        StringBuilder result = new StringBuilder();
        for (Component child : root.getComponents()) {
            if (child instanceof javax.swing.JLabel label) result.append(label.getText()).append(' ');
            if (child instanceof AbstractButton button) result.append(button.getText()).append(' ');
            if (child instanceof Container nested) result.append(text(nested));
        }
        return result.toString();
    }
}
