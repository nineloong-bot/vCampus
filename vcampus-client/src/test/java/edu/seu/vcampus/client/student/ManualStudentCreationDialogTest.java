package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.ManualStudentCreationDialog;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ManualStudentCreationDialogTest {
    @AfterEach void closeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose));
    }

    @Test
    void formContainsRequiredIdentityFieldsButNoContactInputs() throws Exception {
        var requests = new RecordingClient();
        ManualStudentCreationDialog dialog = dialog(requests);

        assertThat(find(dialog, "student.manual.department", JLabel.class).getText()).contains("计算机学院");
        assertThat(find(dialog, "student.manual.major", JLabel.class).getText()).contains("软件工程");
        assertThat(find(dialog, "student.manual.class", JLabel.class).getText()).contains("软工2401");
        assertThat(find(dialog, "student.manual.campusCardNumber", JTextField.class).getToolTipText())
                .contains("213240099");
        assertThat(find(dialog, "student.manual.idDocumentNumber", JTextField.class)).isNotNull();
        assertThat(findOrNull(dialog, "student.manual.email")).isNull();
        assertThat(findOrNull(dialog, "student.manual.phone")).isNull();
    }

    @Test
    void validFormSendsSeparateManualCommandWithNullContactData() throws Exception {
        var requests = new RecordingClient();
        ManualStudentCreationDialog dialog = dialog(requests);
        SwingUtilities.invokeAndWait(() -> {
            text(dialog, "campusCardNumber").setText("213240099");
            text(dialog, "studentNumber").setText("09024199");
            text(dialog, "studentName").setText("李雷");
            combo(dialog, "gender").setSelectedItem("男");
            combo(dialog, "studentType").setSelectedItem("本科生");
            combo(dialog, "idDocumentType").setSelectedItem("居民身份证");
            text(dialog, "idDocumentNumber").setText("110105200009030011");
            text(dialog, "birthDate").setText("2000-09-03");
            find(dialog, "student.manual.submit", JButton.class).doClick();
        });

        requests.sent.get(2, TimeUnit.SECONDS);
        assertThat(requests.command).isEqualTo("STUDENT_CREATE_MANUAL");
        assertThat(requests.body).isInstanceOf(CreateStudentManualCommand.class);
        CreateStudentManualCommand command = (CreateStudentManualCommand) requests.body;
        assertThat(command.classId()).isEqualTo("class-1");
        assertThat(command.enrollmentDate()).isEqualTo(java.time.LocalDate.of(2024, 9, 1));
    }

    private static ManualStudentCreationDialog dialog(RecordingClient requests) throws Exception {
        var result = new ManualStudentCreationDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            result[0] = new ManualStudentCreationDialog(null,
                    new StudentClientService(requests, Duration.ofSeconds(1)),
                    new DepartmentView("department-1", "CS", "计算机学院", true, 0),
                    new MajorView("major-1", "department-1", "090", "软件工程", true, 0),
                    new ClassView("class-1", "major-1", "090-24-1", "软工2401", 2024, 1, true, 0));
            result[0].addNotify();
        });
        return result[0];
    }

    private static JTextField text(Container root, String key) {
        return find(root, "student.manual." + key, JTextField.class);
    }
    @SuppressWarnings("unchecked")
    private static JComboBox<String> combo(Container root, String key) {
        return (JComboBox<String>) find(root, "student.manual." + key, JComboBox.class);
    }
    private static Component findOrNull(Container root, String name) {
        if (name.equals(root.getName())) return root;
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) {
                Component found = findOrNull(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        return type.cast(findOrNull(root, name));
    }

    private static final class RecordingClient implements StudentRequestClient {
        private final CompletableFuture<Void> sent = new CompletableFuture<>();
        private volatile String command;
        private volatile Serializable body;
        @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                String command, Serializable body, Duration timeout) {
            this.command = command;
            this.body = body;
            sent.complete(null);
            return new CompletableFuture<>();
        }
    }
}
