package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.shell.ModulePlaceholderPage;
import edu.seu.vcampus.client.core.ui.shell.PermissionNavigation;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.ui.StudentModulePageFactory;
import edu.seu.vcampus.common.user.UserView;

/** Helper for installing initial top-level placeholder pages and default selections. */
final class MainFramePageSetup {
    private MainFramePageSetup() { }

    static void registerInitialPages(PageNavigator navigator, PermissionNavigation navigation,
                                     UserView user, ClientConnection connection,
                                     StudentClientService students) {
        navigator.register("student", StudentModulePageFactory.create(user, students, connection));
        navigator.register("course", new ModulePlaceholderPage("课程中心"));
        navigator.register("library", new ModulePlaceholderPage("图书借阅"));
        navigator.register("shop", new ModulePlaceholderPage("校园商城"));
        navigator.register("account", new ModulePlaceholderPage("账户设置"));
        String defaultPage = PermissionNavigation.defaultPageFor(user == null ? null : user.role());
        navigator.show(defaultPage);
        navigation.selectById(defaultPage);
    }
}
