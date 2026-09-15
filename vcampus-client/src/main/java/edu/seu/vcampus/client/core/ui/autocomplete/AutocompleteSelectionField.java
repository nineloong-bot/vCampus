package edu.seu.vcampus.client.core.ui.autocomplete;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Text field that resolves a visible label to a stable server-side identifier. */
public final class AutocompleteSelectionField extends JPanel {
    private static final int LIMIT = 8;
    private static final int DEBOUNCE_MILLIS = 250;
    private final SuggestionLoader loader;
    private final Debouncer debouncer;
    private final JTextField input = new JTextField();
    private final JLabel status = new JLabel(" ");
    private final SuggestionPopup suggestions = new SuggestionPopup();
    private AutocompleteChoice selection;
    private long requestSequence;
    private boolean programmaticChange;

    /** Creates a field using a 250 millisecond Swing debounce timer. */
    public AutocompleteSelectionField(SuggestionLoader loader) {
        this(loader, new SwingDebouncer());
    }

    AutocompleteSelectionField(SuggestionLoader loader, Debouncer debouncer) {
        super(new BorderLayout(0, 3));
        this.loader = Objects.requireNonNull(loader, "loader");
        this.debouncer = Objects.requireNonNull(debouncer, "debouncer");
        add(input, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
        });
        bindKeys();
    }

    /** Returns the durable identifier chosen from the current suggestions. */
    public Optional<String> selectedId() {
        return Optional.ofNullable(selection).map(AutocompleteChoice::id);
    }

    /** Returns the selected choice or raises a boundary validation error. */
    public AutocompleteChoice requireSelection() {
        if (selection == null) throw new IllegalArgumentException("请选择匹配结果中的一项");
        return selection;
    }

    /** Backfills an existing durable identifier and its current visible label. */
    public void setSelection(String id, String label) {
        selection = new AutocompleteChoice(id, label, "");
        programmaticChange = true;
        try {
            input.setText(label);
        } finally {
            programmaticChange = false;
        }
        debouncer.cancel();
        suggestions.dismiss();
        status.setText(" ");
    }

    /** Returns the editable text component for labels and accessibility metadata. */
    public JTextField inputComponent() { return input; }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        input.setEnabled(enabled);
    }

    JTextField inputForTest() { return input; }
    List<AutocompleteChoice> suggestionsForTest() { return suggestions.choices(); }
    boolean isSuggestionVisibleForTest() { return suggestions.isVisible(); }

    private void changed() {
        if (programmaticChange) return;
        selection = null;
        long candidate = ++requestSequence;
        String query = input.getText().strip();
        debouncer.cancel();
        suggestions.dismiss();
        if (query.isEmpty()) {
            status.setText(" ");
            return;
        }
        debouncer.schedule(DEBOUNCE_MILLIS, () -> load(query, candidate));
    }

    private void load(String query, long candidate) {
        status.setText("正在搜索…");
        try {
            loader.load(query, LIMIT).whenComplete((choices, failure) -> onEdt(() -> {
                if (candidate != requestSequence) return;
                if (failure != null) {
                    suggestions.dismiss();
                    status.setText("匹配结果加载失败");
                    return;
                }
                List<AutocompleteChoice> safe = choices == null ? List.of()
                        : choices.stream().limit(LIMIT).toList();
                suggestions.setChoices(safe);
                status.setText(safe.isEmpty() ? "没有匹配结果" : " ");
                suggestions.show(() -> input);
            }));
        } catch (RuntimeException failure) {
            if (candidate == requestSequence) status.setText("匹配结果加载失败");
        }
    }

    private void bindKeys() {
        bind("DOWN", "autocomplete.down", () -> suggestions.move(1));
        bind("UP", "autocomplete.up", () -> suggestions.move(-1));
        bind("ENTER", "autocomplete.choose", this::acceptSelection);
        bind("ESCAPE", "autocomplete.dismiss", suggestions::dismiss);
    }

    private void bind(String stroke, String key, Runnable action) {
        input.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(stroke), key);
        input.getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void acceptSelection() {
        AutocompleteChoice selected = suggestions.selected();
        if (selected != null) setSelection(selected.id(), selected.label());
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    interface Debouncer {
        void schedule(int delayMillis, Runnable task);
        void cancel();
    }

    private static final class SwingDebouncer implements Debouncer {
        private Timer timer;
        @Override public void schedule(int delayMillis, Runnable task) {
            cancel();
            timer = new Timer(delayMillis, event -> task.run());
            timer.setRepeats(false);
            timer.start();
        }
        @Override public void cancel() {
            if (timer != null) timer.stop();
            timer = null;
        }
    }
}
