package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.common.protocol.ResponseBody;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the freshman-admission editor shell. */
class FreshmanAdmissionPanelTest {
    @Test
    void isWideEditorWithDedicatedComponentName() {
        StudentRequestClient requests = new StudentRequestClient() {
            @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                return new CompletableFuture<>();
            }
        };

        var panel = new FreshmanAdmissionPanel(new StudentClientService(requests, Duration.ofSeconds(1)),
                ignored -> { }, () -> { });

        assertThat(panel.size()).isEqualTo(EditorSize.WIDE);
        assertThat(panel.component().getName()).isEqualTo("student.freshman-admission.editor");
        assertThat(panel.isDirty()).isFalse();
    }
}
