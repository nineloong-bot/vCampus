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

    @Test
    void loadsAndEchoesExistingOptionAndPreservesOptionIdOnSave() throws Exception {
        StudentClientService students = Mockito.mock(StudentClientService.class);
        var dept = new edu.seu.vcampus.common.student.DepartmentView("dept-1", "计算机学院", "01", true, 0);
        var major = new edu.seu.vcampus.common.student.MajorView("major-1", "dept-1", "CS", "计算机科学", "4", true, 0);
        var option = new edu.seu.vcampus.common.student.majortransfer.MajorTransferOptionView(
                "opt-1", "batch-1", "major-1", "dept-1", "计算机科学", "计算机学院",
                "2025,2026", 5, 12, 60.0, 60.0, 70, 30, false, "None", true, 2);

        Mockito.when(students.listTransferOptions("batch-1"))
                .thenReturn(CompletableFuture.completedFuture(edu.seu.vcampus.common.protocol.ResponseBody.success(
                        new java.util.ArrayList<>(java.util.List.of(option)))));
        Mockito.when(students.listDepartments(true))
                .thenReturn(CompletableFuture.completedFuture(edu.seu.vcampus.common.protocol.ResponseBody.success(
                        new java.util.ArrayList<>(java.util.List.of(dept)))));
        Mockito.when(students.listMajors("dept-1"))
                .thenReturn(CompletableFuture.completedFuture(edu.seu.vcampus.common.protocol.ResponseBody.success(
                        new java.util.ArrayList<>(java.util.List.of(major)))));

        var captor = org.mockito.ArgumentCaptor.forClass(
                edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferOptionCommand.class);
        Mockito.when(students.saveTransferOption(captor.capture()))
                .thenReturn(CompletableFuture.completedFuture(edu.seu.vcampus.common.protocol.ResponseBody.success(option)));

        MajorTransferBatchView batch = new MajorTransferBatchView(
                "batch-1", "2026秋季", MajorTransferBatchStatus.OPEN,
                Instant.now(), Instant.now().plusSeconds(3600),
                null, null, null, 1);

        java.util.concurrent.atomic.AtomicReference<MajorTransferOptionEditorPanel> ref =
                new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ref.set(new MajorTransferOptionEditorPanel(
                students, batch, () -> {}, () -> {})));

        for (int i = 0; i < 5; i++) {
            SwingUtilities.invokeAndWait(() -> {});
        }

        SwingUtilities.invokeAndWait(() -> {
            MajorTransferOptionEditorPanel editor = ref.get();
            java.util.List<JSpinner> spinners = findSpinners(editor.component());
            assertThat(spinners).hasSize(3);
            assertThat(spinners.get(0).getValue()).isEqualTo(5);
            assertThat(spinners.get(1).getValue()).isEqualTo(12);
            assertThat(spinners.get(2).getValue()).isEqualTo(70);

            spinners.get(0).setValue(8);
            assertThat(editor.isDirty()).isTrue();

            JButton saveBtn = findButton(editor.component(), "保存");
            assertThat(saveBtn).isNotNull();
            saveBtn.doClick();

            var sent = captor.getValue();
            assertThat(sent).isNotNull();
            assertThat(sent.optionId()).isEqualTo("opt-1");
            assertThat(sent.expectedVersion()).isEqualTo(2);
            assertThat(sent.receiveQuota()).isEqualTo(8);
            assertThat(sent.interviewQuota()).isEqualTo(12);
            assertThat(sent.writtenWeightPct()).isEqualTo(70);
            assertThat(sent.interviewWeightPct()).isEqualTo(30);
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

    private static java.util.List<JSpinner> findSpinners(Container root) {
        java.util.List<JSpinner> result = new java.util.ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JSpinner spinner) {
                result.add(spinner);
            } else if (child instanceof Container container) {
                result.addAll(findSpinners(container));
            }
        }
        return result;
    }

    private static JButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (child instanceof Container container) {
                JButton found = findButton(container, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
