package edu.seu.vcampus.client.core.ui.autocomplete;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/** Page-local suggestion popup owned by an autocomplete field. */
final class SuggestionPopup {
    private final JPopupMenu popup = new JPopupMenu();
    private final DefaultListModel<AutocompleteChoice> model = new DefaultListModel<>();
    private final JList<AutocompleteChoice> list = new JList<>(model);
    private boolean requestedVisible;

    SuggestionPopup(Consumer<AutocompleteChoice> accepted) {
        list.setVisibleRowCount(8);
        list.setCellRenderer((source, value, index, selected, focused) -> {
            DefaultListCellRenderer renderer = new DefaultListCellRenderer();
            String detail = value.detail().isBlank() ? "" : "  ·  " + value.detail();
            return renderer.getListCellRendererComponent(source, value.label() + detail,
                    index, selected, focused);
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(420, 190));
        popup.add(scroll);
        list.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index >= 0) accepted.accept(model.get(index));
            }
        });
    }

    void show(JComponentOwner owner) {
        requestedVisible = !model.isEmpty();
        if (requestedVisible && owner.component().isShowing()) {
            popup.show(owner.component(), 0, owner.component().getHeight());
        }
    }

    void dismiss() {
        requestedVisible = false;
        popup.setVisible(false);
    }

    void setChoices(List<AutocompleteChoice> choices) {
        model.clear();
        choices.forEach(model::addElement);
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    List<AutocompleteChoice> choices() {
        return java.util.Collections.list(model.elements());
    }

    void move(int delta) {
        if (model.isEmpty()) return;
        int current = Math.max(0, list.getSelectedIndex());
        list.setSelectedIndex(Math.max(0, Math.min(model.size() - 1, current + delta)));
        list.ensureIndexIsVisible(list.getSelectedIndex());
    }

    AutocompleteChoice selected() { return list.getSelectedValue(); }
    boolean isVisible() { return requestedVisible; }
    JList<AutocompleteChoice> listForTest() { return list; }

    interface JComponentOwner {
        javax.swing.JComponent component();
    }
}
