package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.shell.ModulePlaceholderPage;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.JPanel;

/** Selects the student module page that is safe for the authenticated user. */
public final class StudentModulePageFactory {
    private static final String TITLE = "学籍档案";
    private static final String DESCRIPTION = "用于查看和维护校园身份与学籍信息。";

    private StudentModulePageFactory() {
    }

    /** Creates the real profile only for an unrestricted student with live dependencies. */
    public static JPanel create(UserView user, StudentClientService students,
                                ClientConnection connection) {
        if (user != null && user.role() == UserRole.STUDENT && !user.mustChangePassword()
                && students != null && connection != null) {
            return new MyStudentProfilePanel(students, connection);
        }
        return new ModulePlaceholderPage(TITLE, DESCRIPTION);
    }
}
