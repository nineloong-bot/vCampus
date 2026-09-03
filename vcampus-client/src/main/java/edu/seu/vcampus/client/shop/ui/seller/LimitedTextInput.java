package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Insets;

/** Seller-application text input with an in-field hint and remaining-character count. */
final class LimitedTextInput {
    private LimitedTextInput() { }

    static JTextField field(String name, String hint, int limit) {
        JTextField field = new PlaceholderField(hint);
        configure(field, name, hint, limit);
        return field;
    }

    static JTextArea area(String name, String hint, int limit, int rows, int columns) {
        JTextArea area = new PlaceholderArea(hint, rows, columns);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        configure(area, name, hint, limit);
        return area;
    }

    static JPanel wrap(JComponent input, String name, int limit) {
        return wrap(input, (javax.swing.text.JTextComponent) input, name, limit);
    }

    static JPanel wrap(JComponent content, javax.swing.text.JTextComponent input,
            String name, int limit) {
        JLabel remaining = new JLabel();
        remaining.setName(name + ".remaining");
        remaining.setForeground(UiColors.TEXT_SECONDARY);
        JPanel counter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        counter.setOpaque(false);
        counter.add(remaining);
        JPanel wrapper = new JPanel(new BorderLayout(0, 2));
        wrapper.setOpaque(false);
        wrapper.add(counter, BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);
        updateRemaining(input, remaining, limit);
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateRemaining(input, remaining, limit); }
            @Override public void removeUpdate(DocumentEvent event) { updateRemaining(input, remaining, limit); }
            @Override public void changedUpdate(DocumentEvent event) { updateRemaining(input, remaining, limit); }
        });
        return wrapper;
    }

    private static void configure(javax.swing.text.JTextComponent input, String name,
            String hint, int limit) {
        input.setName(name);
        input.getAccessibleContext().setAccessibleDescription(hint);
        ((AbstractDocument) input.getDocument()).setDocumentFilter(new CharacterLimitFilter(limit));
    }

    private static void updateRemaining(JComponent input, JLabel remaining, int limit) {
        String text = ((javax.swing.text.JTextComponent) input).getText();
        int used = text.codePointCount(0, text.length());
        remaining.setText("还可输入 " + Math.max(0, limit - used) + " 字");
    }

    private static final class CharacterLimitFilter extends DocumentFilter {
        private final int limit;

        private CharacterLimitFilter(int limit) {
            this.limit = limit;
        }

        @Override
        public void insertString(FilterBypass bypass, int offset, String text,
                AttributeSet attributes) throws BadLocationException {
            replace(bypass, offset, 0, text, attributes);
        }

        @Override
        public void replace(FilterBypass bypass, int offset, int length, String text,
                AttributeSet attributes) throws BadLocationException {
            String current = bypass.getDocument().getText(0, bypass.getDocument().getLength());
            String replacement = text == null ? "" : text;
            String proposed = current.substring(0, offset) + replacement
                    + current.substring(offset + length);
            String accepted = prefix(proposed, limit);
            bypass.replace(0, bypass.getDocument().getLength(), accepted, attributes);
        }

        private static String prefix(String value, int maximumCodePoints) {
            int count = value.codePointCount(0, value.length());
            if (count <= maximumCodePoints) return value;
            return value.substring(0, value.offsetByCodePoints(0, maximumCodePoints));
        }
    }

    private static final class PlaceholderField extends JTextField {
        private final String placeholder;

        private PlaceholderField(String placeholder) {
            this.placeholder = placeholder;
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!getText().isEmpty()) return;
            Insets insets = getInsets();
            graphics.setColor(UiColors.TEXT_SECONDARY);
            graphics.drawString(placeholder, insets.left + 2,
                    (getHeight() + graphics.getFontMetrics().getAscent()
                            - graphics.getFontMetrics().getDescent()) / 2);
        }
    }

    private static final class PlaceholderArea extends JTextArea {
        private final String placeholder;

        private PlaceholderArea(String placeholder, int rows, int columns) {
            super(rows, columns);
            this.placeholder = placeholder;
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!getText().isEmpty()) return;
            Insets insets = getInsets();
            graphics.setColor(UiColors.TEXT_SECONDARY);
            graphics.drawString(placeholder, insets.left + 2,
                    insets.top + graphics.getFontMetrics().getAscent());
        }
    }
}
