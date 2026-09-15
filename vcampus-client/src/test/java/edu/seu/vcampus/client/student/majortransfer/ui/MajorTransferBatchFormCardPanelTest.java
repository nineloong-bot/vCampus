package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MajorTransferBatchFormCardPanelTest {
    @Test
    void statusSelectorUsesChineseLabels() throws Exception {
        onEdt(() -> {
            MajorTransferBatchFormCardPanel card = new MajorTransferBatchFormCardPanel();
            JComboBox<?> combo = component(card, "major-transfer.batch-state", JComboBox.class);
            Component rendered = render(combo, MajorTransferBatchStatus.OPEN);
            assertThat(((JLabel) rendered).getText()).isEqualTo("开放报名");
        });
    }

    @Test
    void invalidPublicitySequenceIsRejectedBeforeSending() throws Exception {
        onEdt(() -> {
            MajorTransferBatchFormCardPanel card = new MajorTransferBatchFormCardPanel();
            field(card, "major-transfer.batch-name").setText("测试批次");
            field(card, "major-transfer.batch-application-start").setText("2026-09-01 08:00");
            field(card, "major-transfer.batch-application-end").setText("2026-09-02 08:00");
            field(card, "major-transfer.batch-publicity-start").setText("2026-09-01 12:00");
            field(card, "major-transfer.batch-publicity-end").setText("2026-09-03 12:00");
            assertThatThrownBy(card::buildCommand).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("公示时间必须在报名结束后");
        });
    }

    @Test
    void editingDoesNotShowRedundantGuidanceText() throws Exception {
        onEdt(() -> {
            MajorTransferBatchFormCardPanel card = new MajorTransferBatchFormCardPanel();
            Instant start = Instant.parse("2026-09-01T00:00:00Z");
            card.loadBatch(new edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchView(
                    "batch", "测试批次", MajorTransferBatchStatus.OPEN,
                    start, start.plusSeconds(3600), null, null, null, 1));
            JLabel feedback = component(card, "major-transfer.batch-feedback", JLabel.class);
            assertThat(feedback.getText()).isBlank();
        });
    }

    @Test
    void busyStateLocksEveryEditableControl() throws Exception {
        onEdt(() -> {
            MajorTransferBatchFormCardPanel card = new MajorTransferBatchFormCardPanel();
            card.setBusy(true);
            assertThat(component(card, "major-transfer.batch-state", JComboBox.class).isEnabled())
                    .isFalse();
            assertThat(field(card, "major-transfer.batch-name").isEnabled()).isFalse();
            assertThat(field(card, "major-transfer.batch-application-start").isEnabled()).isFalse();
            assertThat(component(card, "saveBatchButton", Component.class).isEnabled()).isFalse();
            assertThat(component(card, "major-transfer.batch-reset", Component.class).isEnabled())
                    .isFalse();
        });
    }

    private static JTextField field(Container root, String name) {
        return component(root, name, JTextField.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Component render(JComboBox<?> combo, Object value) {
        return ((ListCellRenderer) combo.getRenderer()).getListCellRendererComponent(
                new JList<>(), value, 0, false, false);
    }

    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container container) {
                T found = find(container, name, type);
                if (found != null) return found;
            }
        }
        throw new AssertionError("未找到组件: " + name);
    }

    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container container) {
                T found = find(container, name, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void onEdt(Runnable task) throws Exception {
        SwingUtilities.invokeAndWait(task);
    }
}
