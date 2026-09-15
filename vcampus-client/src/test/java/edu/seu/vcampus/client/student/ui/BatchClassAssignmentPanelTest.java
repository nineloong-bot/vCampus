package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.MajorView;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class BatchClassAssignmentPanelTest {
    @Test void batchAssignmentIsAWideEmbeddedEditor() {
        StudentRequestClient requests = new StudentRequestClient() {
            @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                return new CompletableFuture<>();
            }
        };
        BatchClassAssignmentPanel editor = new BatchClassAssignmentPanel(
                new StudentClientService(requests, Duration.ofSeconds(1)),
                new MajorView("m", "d", "090", "软件工程", null, true, 0),
                List.of(new ClassView("c1", "m", "01", "一班", 2024, 1, true, 0),
                        new ClassView("c2", "m", "02", "二班", 2024, 2, true, 0)),
                ignored -> { }, () -> { });

        assertThat(editor.size()).isEqualTo(EditorSize.WIDE);
        assertThat(editor.component().getName()).isEqualTo("student.batch.editor");
        assertThat(editor.isDirty()).isFalse();
    }
}
