package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.shell.ModulePlaceholderPage;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.*;
import java.awt.*;

/** Selects the student module page that is safe for the authenticated user. */
public final class StudentModulePageFactory {
    private static final String TITLE = "学籍档案";
    private static final String DESCRIPTION = "用于查看和维护校园身份与学籍信息。";

    private StudentModulePageFactory() {
    }

    public static JPanel create(UserView user, StudentClientService students,
                                ClientConnection connection) {
        if (user == null || students == null || connection == null) {
            return new ModulePlaceholderPage(TITLE, DESCRIPTION);
        }
        return switch (user.role()) {
            case STUDENT -> createStudentPage(user, students, connection);
            case TEACHER -> createTeacherPage(students, connection);
            case ADMIN -> createAdminPage(students, connection);
        };
    }

    private static JPanel createStudentPage(UserView user, StudentClientService students,
                                            ClientConnection connection) {
        if (user.mustChangePassword()) {
            return new ModulePlaceholderPage(TITLE, DESCRIPTION);
        }
        return new MyStudentProfilePanel(students, connection);
    }

    private static JPanel createTeacherPage(StudentClientService students,
                                            ClientConnection connection) {
        return tabbedPanel(students, connection, false);
    }

    private static JPanel createAdminPage(StudentClientService students,
                                          ClientConnection connection) {
        return tabbedPanel(students, connection, true);
    }

    private static JPanel tabbedPanel(StudentClientService students,
                                      ClientConnection connection, boolean admin) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setName("student.tabs");
        tabs.addTab("学生查询", new StudentSearchPanel(students, connection,
                studentId -> openStudentDetail(students, connection, studentId, admin)));
        if (admin) {
            tabs.addTab("组织管理", new OrganizationManagementPanel(students, connection));
            tabs.addTab("资料审核", new StudentProfileReviewPanel(students, connection));
        }
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setName("student.module");
        wrapper.add(tabs);
        return wrapper;
    }

    private static void openStudentDetail(StudentClientService students,
                                          ClientConnection connection,
                                          String studentId, boolean canEdit) {
        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getFocusedWindow();
        JDialog dialog = new JDialog(owner, "学生详情", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(new StudentDetailPanel(students, connection, studentId, canEdit));
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
