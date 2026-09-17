package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.EntityIdRequest;
import edu.seu.vcampus.common.student.StudentSearchQuery;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentSummary;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.StudentView;
import org.junit.jupiter.api.Test;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class ClassStudentPanelTest {
    @Test
    void loadsTheFourRosterFieldsAndGenderStatisticsForTheSelectedClass() throws Exception {
        Client client = new Client(false);
        ClassStudentPanel panel = panel();
        SwingUtilities.invokeAndWait(() -> panel.load(service(client), "class-1"));

        waitFor(label(panel).getName(), panel, "共 2 人，男 1 人，女 1 人");
        JTable table = component(panel, "student.org.class-students.table", JTable.class);
        assertThat(client.classId).isEqualTo("class-1");
        assertThat(table.getColumnCount()).isEqualTo(4);
        assertThat(table.getValueAt(0, 0)).isEqualTo("张三");
        assertThat(table.getValueAt(0, 1)).isEqualTo("男");
        assertThat(table.getValueAt(0, 2)).isEqualTo("213240001");
        assertThat(table.getValueAt(0, 3)).isEqualTo("20240001");
    }

    @Test
    void clearsTheRosterWhenDetailDataCannotBeLoaded() throws Exception {
        ClassStudentPanel panel = panel();
        SwingUtilities.invokeAndWait(() -> panel.load(service(new Client(true)), "class-1"));

        waitFor("student.org.class-students.statistics", panel, "加载失败");
        assertThat(component(panel, "student.org.class-students.table", JTable.class).getRowCount()).isZero();
    }

    @Test
    void clearRemovesThePreviouslyShownRoster() throws Exception {
        ClassStudentPanel panel = panel();
        SwingUtilities.invokeAndWait(() -> panel.load(service(new Client(false)), "class-1"));
        waitFor("student.org.class-students.statistics", panel, "共 2 人");
        SwingUtilities.invokeAndWait(panel::clear);

        assertThat(label(panel).getText()).isEqualTo("请选择班级查看学生");
        assertThat(component(panel, "student.org.class-students.table", JTable.class).getRowCount()).isZero();
    }

    private static ClassStudentPanel panel() throws Exception {
        ClassStudentPanel[] value = new ClassStudentPanel[1];
        SwingUtilities.invokeAndWait(() -> value[0] = new ClassStudentPanel());
        return value[0];
    }

    private static StudentClientService service(Client client) {
        return new StudentClientService(client, Duration.ofSeconds(1));
    }

    private static void waitFor(String name, Container panel, String text) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (component(panel, name, JLabel.class).getText().contains(text)) return;
            TimeUnit.MILLISECONDS.sleep(10);
        }
        throw new AssertionError("Label did not contain: " + text);
    }

    private static JLabel label(Container panel) {
        return component(panel, "student.org.class-students.statistics", JLabel.class);
    }

    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        if (name.equals(root.getName())) return type.cast(root);
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container nested) {
                try { return component(nested, name, type); }
                catch (AssertionError ignored) { }
            }
        }
        throw new AssertionError("Component not found: " + name);
    }

    private static final class Client implements StudentRequestClient {
        private final boolean detailFails;
        private String classId;

        Client(boolean detailFails) { this.detailFails = detailFails; }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                String command, Serializable body, Duration timeout) {
            ResponseBody<?> response = switch (command) {
                case "STUDENT_SEARCH" -> roster((StudentSearchQuery) body);
                case "STUDENT_GET" -> detail((EntityIdRequest) body);
                default -> ResponseBody.failure("COMMON_INVALID_REQUEST", "请求体类型错误", null);
            };
            return CompletableFuture.completedFuture((ResponseBody) response);
        }

        private ResponseBody<PageResult<StudentSummary>> roster(StudentSearchQuery query) {
            classId = query.classId();
            return ResponseBody.success(new PageResult<>(List.of(
                    new StudentSummary("s1", "213240001", "20240001", "张三", "m1", "class-1", StudentStatus.ACTIVE),
                    new StudentSummary("s2", "213240002", "20240002", "李四", "m1", "class-1", StudentStatus.ACTIVE)), 1, 100, 2));
        }

        private ResponseBody<StudentView> detail(EntityIdRequest request) {
            if (detailFails) return ResponseBody.failure("STUDENT_NOT_FOUND", "学生不存在", null);
            boolean first = "s1".equals(request.entityId());
            return ResponseBody.success(new StudentView(request.entityId(), "u" + request.entityId(),
                    first ? "213240001" : "213240002", first ? "20240001" : "20240002", StudentType.UNDERGRADUATE,
                    first ? "张三" : "李四", first ? "男" : "女", null, null, "m1", "class-1",
                    LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, 0, "计算机学院", "软件工程", "一班"));
        }
    }
}
