package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchView;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for the transfer-admission option editor panel. */
class MajorTransferOptionEditorPanelTest {

    @Test
    void gradesFieldIsLockedToFreshmenAndSophomoresAndNonEditable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StudentClientService students = Mockito.mock(StudentClientService.class);
            Mockito.when(students.listDepartments(true))
                    .thenReturn(CompletableFuture.completedFuture(null));
            MajorTransferBatchView batch = new MajorTransferBatchView(
                    "batch-1", "2026秋季", MajorTransferBatchStatus.OPEN,
                    Instant.now(), Instant.now().plusSeconds(3600),
                    null, null, null, 1);

            MajorTransferOptionEditorPanel editor = new MajorTransferOptionEditorPanel(
                    students, batch, () -> {}, () -> {});

            JTextField grades = findTextField(editor.component());
            assertThat(grades).isNotNull();
            assertThat(grades.getText()).isEqualTo("2025,2026");
            assertThat(grades.isEditable()).isFalse();
            assertThat(grades.isFocusable()).isFalse();
            assertThat(grades.getToolTipText()).contains("大一及大二");
            assertThat(editor.isDirty()).isFalse();
        });
    }

    private static JTextField findTextField(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTextField field) {
                return field;
            }
            if (child instanceof Container container) {
                JTextField found = findTextField(container);
                if (found != null) return found;
            }
        }
        return null;
    }
}
