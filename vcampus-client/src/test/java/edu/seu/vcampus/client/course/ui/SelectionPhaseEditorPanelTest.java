package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.common.course.*;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionPhaseEditorPanelTest {
    @Test
    void savesTitleBeforeStatusUsingReturnedVersion() throws Exception {
        AtomicReference<UpdateSelectionPhaseCommand> update = new AtomicReference<>();
        AtomicReference<ChangeSelectionPhaseStatusCommand> status = new AtomicReference<>();
        CourseUiGateway gateway = new CourseUiGateway() {
            @Override public CompletableFuture<SelectionPhaseView> updateSelectionPhase(
                    UpdateSelectionPhaseCommand command) {
                update.set(command);
                return CompletableFuture.completedFuture(view(command.displayTitle(), "DRAFT", 8));
            }
            @Override public CompletableFuture<SelectionPhaseView> changeSelectionPhaseStatus(
                    ChangeSelectionPhaseStatusCommand command) {
                status.set(command);
                return CompletableFuture.completedFuture(view("新标题", command.targetStatus(), 9));
            }
        };
        SelectionPhaseEditorPanel editor = new SelectionPhaseEditorPanel(gateway,
                List.of(new TermView("term-1", "2026-AUTUMN", "秋季",
                        java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2027, 1, 1),
                        null, null, null, null, "ACTIVE", 0, Instant.EPOCH, Instant.EPOCH)),
                view("旧标题", "DRAFT", 7), () -> { }, () -> { });
        editor.onOpened();

        SwingUtilities.invokeAndWait(() -> {
            field(editor.component(), JTextField.class, "学生端标题").setText("新标题");
            field(editor.component(), JComboBox.class, "阶段状态").setSelectedIndex(2);
            button(editor.component(), "保存阶段").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(update.get().expectedVersion()).isEqualTo(7);
        assertThat(status.get().expectedVersion()).isEqualTo(8);
        assertThat(status.get().targetStatus()).isEqualTo("OPEN");
    }

    private static SelectionPhaseView view(String title, String status, long version) {
        return new SelectionPhaseView("phase-1", "term-1", "ENROLLMENT", title,
                status, version, Instant.EPOCH, Instant.EPOCH);
    }

    private static JButton button(Container root, String text) {
        return components(root, JButton.class).stream()
                .filter(value -> text.equals(value.getText())).findFirst().orElseThrow();
    }

    private static <T extends JComponent> T field(Container root, Class<T> type, String name) {
        return components(root, type).stream().filter(value ->
                name.equals(value.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
    }

    private static <T extends Component> java.util.ArrayList<T> components(Container root, Class<T> type) {
        java.util.ArrayList<T> result = new java.util.ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container nested) result.addAll(components(nested, type));
        }
        return result;
    }
}
