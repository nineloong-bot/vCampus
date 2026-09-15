package edu.seu.vcampus.client.core.ui.autocomplete;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;

/** Page-local suggestion popup owned by an autocomplete field. */
final class SuggestionPopup {
    private final JPopupMenu popup = new JPopupMenu();
    private final DefaultListModel<AutocompleteChoice> model = new DefaultListModel<>();
    private final JList<AutocompleteChoice> list = new JList<>(model);
    private boolean requestedVisible;

    SuggestionPopup() {
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

    interface JComponentOwner {
        javax.swing.JComponent component();
    }
}
