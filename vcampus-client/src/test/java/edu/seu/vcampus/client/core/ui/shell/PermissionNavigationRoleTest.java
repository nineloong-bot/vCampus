package edu.seu.vcampus.client.core.ui.shell;

import edu.seu.vcampus.common.user.UserRole;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionNavigationRoleTest {
    @Test
    void superAdminSeesOnlyAccountManagementMenu() {
        PermissionNavigation nav = new PermissionNavigation(id -> { }, UserRole.SUPER_ADMIN);
        List<String> items = buttonNames(nav);
        assertThat(items).containsExactly("navigation.account");
        assertThat(buttonTexts(nav)).containsExactly("账户设置");
        assertThat(PermissionNavigation.defaultPageFor(UserRole.SUPER_ADMIN)).isEqualTo("account");
    }

    @Test
    void libraryAdminSeesOnlyLibraryAndAccountMenus() {
        PermissionNavigation nav = new PermissionNavigation(id -> { }, UserRole.LIBRARY_ADMIN);
        List<String> items = buttonNames(nav);
        assertThat(items).containsExactly("navigation.library", "navigation.account");
        assertThat(buttonTexts(nav)).containsExactly("图书借阅", "账户设置");
        assertThat(PermissionNavigation.defaultPageFor(UserRole.LIBRARY_ADMIN)).isEqualTo("library");
    }

    @Test
    void otherSpecializedAdministratorsSeeOnlyTheirModuleAndAccount() {
        assertRoleNavigation(UserRole.STUDENT_ADMIN, List.of("navigation.student", "navigation.account"), "student");
        assertRoleNavigation(UserRole.COLLEGE_ADMIN, List.of("navigation.student", "navigation.account"), "student");
        assertRoleNavigation(UserRole.COURSE_ADMIN, List.of("navigation.course", "navigation.account"), "course");
        assertRoleNavigation(UserRole.SHOP_ADMIN, List.of("navigation.shop", "navigation.account"), "shop");
        assertRoleNavigation(UserRole.USER_ADMIN, List.of("navigation.account"), "account");
    }

    @Test
    void studentAndTeacherRetainAllFiveMenus() {
        List<String> allFive = List.of(
                "navigation.student", "navigation.course",
                "navigation.library", "navigation.shop", "navigation.account");
        assertRoleNavigation(UserRole.STUDENT, allFive, "student");
        assertRoleNavigation(UserRole.TEACHER, allFive, "student");
    }

    @Test
    void selectByIdSelectsCorrectButtonInFilteredNavigation() {
        List<String> selected = new ArrayList<>();
        PermissionNavigation nav = new PermissionNavigation(selected::add, UserRole.LIBRARY_ADMIN);

        nav.selectById("account");
        AbstractButton accountBtn = (AbstractButton) nav.getComponent(1);
        AbstractButton libraryBtn = (AbstractButton) nav.getComponent(0);

        assertThat(accountBtn.isSelected()).isTrue();
        assertThat(libraryBtn.isSelected()).isFalse();
    }

    private static void assertRoleNavigation(UserRole role, List<String> expectedButtons, String expectedDefault) {
        PermissionNavigation nav = new PermissionNavigation(id -> { }, role);
        assertThat(buttonNames(nav)).containsExactlyElementsOf(expectedButtons);
        assertThat(PermissionNavigation.defaultPageFor(role)).isEqualTo(expectedDefault);
    }

    private static List<String> buttonNames(PermissionNavigation nav) {
        return Arrays.stream(nav.getComponents())
                .filter(AbstractButton.class::isInstance)
                .map(AbstractButton.class::cast)
                .map(AbstractButton::getName)
                .toList();
    }

    private static List<String> buttonTexts(PermissionNavigation nav) {
        return Arrays.stream(nav.getComponents())
                .filter(AbstractButton.class::isInstance)
                .map(AbstractButton.class::cast)
                .map(AbstractButton::getText)
                .toList();
    }
}
