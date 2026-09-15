package edu.seu.vcampus.client.core.ui.autocomplete;

import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutocompleteSelectionFieldTest {
    @Test
    void debouncesLimitsAndRejectsLateResponses() throws Exception {
        ManualDebouncer debouncer = new ManualDebouncer();
        ControlledLoader loader = new ControlledLoader();
        AutocompleteSelectionField field = new AutocompleteSelectionField(loader, debouncer);

        onEdt(() -> field.inputForTest().setText("数"));
        assertThat(loader.queries).isEmpty();
        assertThat(debouncer.delay).isEqualTo(250);
        debouncer.fire();
        assertThat(loader.queries).containsExactly("数");
        assertThat(loader.limits).containsExactly(8);

        onEdt(() -> field.inputForTest().setText("数据库"));
        debouncer.fire();
        assertThat(loader.queries).containsExactly("数", "数据库");
        loader.responses.get(1).complete(choices("new", 10));
        flushEdt();
        assertThat(field.suggestionsForTest()).hasSize(8);
        assertThat(field.suggestionsForTest().get(0).label()).isEqualTo("new-0");

        loader.responses.get(0).complete(choices("old", 2));
        flushEdt();
        assertThat(field.suggestionsForTest()).hasSize(8);
        assertThat(field.suggestionsForTest().get(0).label()).isEqualTo("new-0");
    }

    @Test
    void keyboardSelectionUsesStableIdAndTypingClearsIt() throws Exception {
        ManualDebouncer debouncer = new ManualDebouncer();
        ControlledLoader loader = new ControlledLoader();
        AutocompleteSelectionField field = new AutocompleteSelectionField(loader, debouncer);
        onEdt(() -> field.inputForTest().setText("高数"));
        debouncer.fire();
        loader.responses.get(0).complete(List.of(
                new AutocompleteChoice("course-1", "高等数学 A", "5 学分"),
                new AutocompleteChoice("course-2", "高等数学 B", "4 学分")));
        flushEdt();

        invoke(field, "autocomplete.down");
        invoke(field, "autocomplete.choose");
        assertThat(field.selectedId()).contains("course-2");
        assertThat(field.inputForTest().getText()).isEqualTo("高等数学 B");
        assertThat(field.requireSelection().id()).isEqualTo("course-2");

        onEdt(() -> field.inputForTest().setText("自由文本"));
        assertThat(field.selectedId()).isEmpty();
        assertThatThrownBy(field::requireSelection)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请选择");
        invoke(field, "autocomplete.dismiss");
        assertThat(field.isSuggestionVisibleForTest()).isFalse();
    }

    @Test
    void explicitSelectionBackfillsIdAndLabel() throws Exception {
        AutocompleteSelectionField field = new AutocompleteSelectionField(
                (query, limit) -> CompletableFuture.completedFuture(List.of()));
        onEdt(() -> field.setSelection("teacher-7", "张老师 · 10007"));

        assertThat(field.selectedId()).contains("teacher-7");
        assertThat(field.inputForTest().getText()).isEqualTo("张老师 · 10007");
    }

    private static void invoke(AutocompleteSelectionField field, String key) throws Exception {
        onEdt(() -> {
            Action action = field.inputForTest().getActionMap().get(key);
            action.actionPerformed(new ActionEvent(field, ActionEvent.ACTION_PERFORMED, key));
        });
    }

    private static List<AutocompleteChoice> choices(String prefix, int count) {
        List<AutocompleteChoice> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(new AutocompleteChoice(prefix + "-id-" + index, prefix + "-" + index, "detail"));
        }
        return result;
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static void flushEdt() throws Exception {
        onEdt(() -> { });
    }

    private static final class ManualDebouncer implements AutocompleteSelectionField.Debouncer {
        private Runnable pending;
        private int delay;
        @Override public void schedule(int delayMillis, Runnable task) {
            delay = delayMillis;
            pending = task;
        }
        @Override public void cancel() { pending = null; }
        private void fire() throws Exception {
            Runnable task = pending;
            pending = null;
            onEdt(task);
        }
    }

    private static final class ControlledLoader implements SuggestionLoader {
        private final List<String> queries = new ArrayList<>();
        private final List<Integer> limits = new ArrayList<>();
        private final List<CompletableFuture<List<AutocompleteChoice>>> responses = new ArrayList<>();
        @Override public CompletableFuture<List<AutocompleteChoice>> load(String query, int limit) {
            queries.add(query);
            limits.add(limit);
            CompletableFuture<List<AutocompleteChoice>> response = new CompletableFuture<>();
            responses.add(response);
            return response;
        }
    }
}
