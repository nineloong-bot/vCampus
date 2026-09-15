package edu.seu.vcampus.client.student.majortransfer;
import edu.seu.vcampus.client.student.majortransfer.ui.MajorTransferBatchManagementPanel;
import edu.seu.vcampus.client.student.majortransfer.ui.MajorTransferBatchFormCardPanel;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchView;
import edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferBatchCommand;
import org.junit.jupiter.api.Test;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import java.awt.Window;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MajorTransferBatchWorkspaceTest {
    @Test
    void refreshKeepsTheSelectedBatchInTheWorkspace() throws Exception {
        BatchClient backend = new BatchClient(List.of(batch("one", "第一批", 1),
                batch("two", "第二批", 2)));
        MajorTransferBatchManagementPanel panel = onEdt(() ->
                new MajorTransferBatchManagementPanel(service(backend)));
        invokeRefresh(panel);
        awaitSize(panel, 2);
        onEdt(() -> batchList(panel).setSelectedIndex(1));
        invokeRefresh(panel);
        awaitStatus(panel, "批次列表已更新");
        assertThat(onEdt(() -> batchList(panel).getSelectedValue().batchId())).isEqualTo("two");
    }

    @Test
    void successfulCreateSelectsTheSavedBatchForFurtherEditing() throws Exception {
        MajorTransferBatchView saved = batch("created", "新批次", 1);
        BatchClient backend = new BatchClient(List.of());
        backend.saved = saved;
        MajorTransferBatchManagementPanel panel = onEdt(() ->
                new MajorTransferBatchManagementPanel(service(backend)));
        setBatchName(panel, "新批次");
        invokeSave(panel);
        awaitSize(panel, 1);
        awaitStatus(panel, "批次列表已更新");
        assertThat(onEdt(() -> batchList(panel).getSelectedValue().batchId()))
                .isEqualTo("created");
    }

    @Test
    void batchListUsesChineseStatusLabels() throws Exception {
        MajorTransferBatchManagementPanel panel = onEdt(() ->
                new MajorTransferBatchManagementPanel(service(new BatchClient(List.of()))));
        JList<MajorTransferBatchView> list = batchList(panel);
        MajorTransferBatchView value = batch("open", "春季批次", 1);
        String text = onEdt(() -> ((javax.swing.JLabel) list.getCellRenderer()
                .getListCellRendererComponent(list, value, 0, false, false)).getText());
        assertThat(text).isEqualTo("春季批次 · 开放报名");
    }
    @Test
    void selectingEditsInlineAndCreateReturnsTheSameWorkspaceToNewMode() throws Exception {
        BatchClient backend = new BatchClient(List.of(batch("edit", "编辑批次", 7)));
        MajorTransferBatchManagementPanel panel = onEdt(() -> new MajorTransferBatchManagementPanel(service(backend)));
        invokeRefresh(panel);
        awaitSize(panel, 1);
        onEdt(() -> {
            batchList(panel).setSelectedIndex(0);
            JButton editButton = findButton(panel, "major-transfer.batch-edit");
            editButton.doClick();
            SaveMajorTransferBatchCommand command = formCard(panel).buildCommand();
            assertThat(command.batchId()).isEqualTo("edit");
            assertThat(command.expectedVersion()).isEqualTo(7);
            long dialogs = visibleDialogs();
            button(panel, "createButton").doClick();
            assertThat(batchList(panel).isSelectionEmpty()).isTrue();
            assertThat(nameField(panel).getText()).isBlank();
            assertThat(visibleDialogs()).isEqualTo(dialogs);
        });
    }
    private static long visibleDialogs() {
        return java.util.Arrays.stream(Window.getWindows())
                .filter(window -> window instanceof JDialog && window.isShowing()).count();
    }
    private static StudentClientService service(StudentRequestClient client) {
        return new StudentClientService(client, Duration.ofSeconds(1));
    }
    private static MajorTransferBatchView batch(String id, String name, long version) {
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        return new MajorTransferBatchView(id, name, MajorTransferBatchStatus.OPEN,
                now, now.plusSeconds(3600), null, null, null, version);
    }
    @SuppressWarnings("unchecked")
    private static JList<MajorTransferBatchView> batchList(
            MajorTransferBatchManagementPanel panel) throws Exception {
        Field field = MajorTransferBatchManagementPanel.class.getDeclaredField("batches");
        field.setAccessible(true);
        return (JList<MajorTransferBatchView>) field.get(panel);
    }
    private static void invokeRefresh(MajorTransferBatchManagementPanel panel) throws Exception {
        Method method = MajorTransferBatchManagementPanel.class.getDeclaredMethod("refresh");
        method.setAccessible(true);
        onEdt(() -> method.invoke(panel));
    }
    private static void invokeSave(MajorTransferBatchManagementPanel panel) throws Exception {
        Method method = MajorTransferBatchManagementPanel.class.getDeclaredMethod("saveBatch");
        method.setAccessible(true);
        onEdt(() -> method.invoke(panel));
    }
    private static void setBatchName(MajorTransferBatchManagementPanel panel, String value)
            throws Exception {
        onEdt(() -> nameField(panel).setText(value));
    }
    private static MajorTransferBatchFormCardPanel formCard(
            MajorTransferBatchManagementPanel panel) throws Exception {
        Field field = MajorTransferBatchManagementPanel.class.getDeclaredField("formCard");
        field.setAccessible(true);
        return (MajorTransferBatchFormCardPanel) field.get(panel);
    }

    private static JTextField nameField(MajorTransferBatchManagementPanel panel) throws Exception {
        Field field = formCard(panel).getClass().getDeclaredField("nameField");
        field.setAccessible(true);
        return (JTextField) field.get(formCard(panel));
    }

    private static JButton button(MajorTransferBatchManagementPanel panel, String fieldName)
            throws Exception {
        Field field = MajorTransferBatchManagementPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (JButton) field.get(panel);
    }

    private static JButton findButton(java.awt.Container root, String name) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JButton button && name.equals(button.getName())) return button;
            if (child instanceof java.awt.Container nested) {
                JButton found = findButton(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void awaitSize(MajorTransferBatchManagementPanel panel, int size)
            throws Exception {
        await(() -> onEdt(() -> ((DefaultListModel<?>) batchList(panel).getModel()).size() == size));
    }

    private static void awaitStatus(MajorTransferBatchManagementPanel panel, String text)
            throws Exception {
        Field field = MajorTransferBatchManagementPanel.class.getDeclaredField("status");
        field.setAccessible(true);
        await(() -> onEdt(() -> ((javax.swing.JLabel) field.get(panel)).getText().contains(text)));
    }

    private static void await(CheckedBoolean condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.get() && System.nanoTime() < deadline) {
            Thread.sleep(10);
            SwingUtilities.invokeAndWait(() -> { });
        }
        assertThat(condition.get()).isTrue();
    }

    private static <T> T onEdt(CheckedSupplier<T> supplier) throws Exception {
        Object[] result = new Object[1];
        Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try { result[0] = supplier.get(); } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }

    private static void onEdt(CheckedRunnable runnable) throws Exception {
        onEdt(() -> { runnable.run(); return null; });
    }

    private interface CheckedBoolean { boolean get() throws Exception; }
    private interface CheckedSupplier<T> { T get() throws Exception; }
    private interface CheckedRunnable { void run() throws Exception; }

    private static final class BatchClient implements StudentRequestClient {
        private final List<MajorTransferBatchView> batches = new ArrayList<>();
        private MajorTransferBatchView saved;

        private BatchClient(List<MajorTransferBatchView> initial) { batches.addAll(initial); }

        @Override @SuppressWarnings("unchecked")
        public <T extends Serializable> java.util.concurrent.CompletableFuture<ResponseBody<T>> send(
                String command, Serializable body, Duration timeout) {
            if ("MAJOR_TRANSFER_SAVE_BATCH".equals(command)) {
                batches.clear();
                batches.add(saved);
                return (java.util.concurrent.CompletableFuture<ResponseBody<T>>) (Object)
                        java.util.concurrent.CompletableFuture.completedFuture(ResponseBody.success(saved));
            }
            return (java.util.concurrent.CompletableFuture<ResponseBody<T>>) (Object)
                    java.util.concurrent.CompletableFuture.completedFuture(
                            ResponseBody.success(new ArrayList<>(batches)));
        }
    }
}
