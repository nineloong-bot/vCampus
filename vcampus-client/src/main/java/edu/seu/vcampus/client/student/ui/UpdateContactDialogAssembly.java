package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Wires the assembled contact dialog: layout, listeners, focus order and disposal. */
abstract class UpdateContactDialogAssembly extends UpdateContactDialogRefreshing {

    UpdateContactDialogAssembly(Window owner, StudentClientService students,
            StudentView initial, Consumer<StudentView> saved) {
        super(owner, students, initial, saved);
    }

    @Override
    void initializeDialog(Window owner) {
        setContentPane(buildForm());
        refresh.setVisible(false);
        refresh.addActionListener(event -> refreshBase());
        cancel.addActionListener(event -> dispose());
        submit.addActionListener(event -> save());
        getRootPane().setDefaultButton(submit);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new ContactFocusTraversalPolicy());
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) { establishInitialEmailFocus(); }
            @Override public void windowActivated(WindowEvent event) { establishInitialEmailFocus(); }
        });
        setSize(new Dimension(560, 360));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    @Override public void dispose() {
        disposed = true;
        requestGeneration.incrementAndGet();
        super.dispose();
    }

    /** Focus order over the contact fields and actions. */
    private final class ContactFocusTraversalPolicy extends FocusTraversalPolicy {
        private List<Component> order() {
            List<Component> components = new ArrayList<>();
            addIfEligible(components, email);
            addIfEligible(components, phone);
            addIfEligible(components, cancel);
            addIfEligible(components, refresh);
            addIfEligible(components, submit);
            return components;
        }

        private static void addIfEligible(List<Component> components, Component component) {
            if (component.isVisible() && component.isEnabled() && component.isFocusable()) components.add(component);
        }

        @Override public Component getComponentAfter(Container root, Component current) {
            List<Component> components = order();
            if (components.isEmpty()) return null;
            int index = components.indexOf(current);
            return components.get(index < 0 ? 0 : (index + 1) % components.size());
        }
        @Override public Component getComponentBefore(Container root, Component current) {
            List<Component> components = order();
            if (components.isEmpty()) return null;
            int index = components.indexOf(current);
            return components.get(index < 0 ? components.size() - 1 : (index - 1 + components.size()) % components.size());
        }
        @Override public Component getFirstComponent(Container root) { List<Component> components = order(); return components.isEmpty() ? null : components.getFirst(); }
        @Override public Component getLastComponent(Container root) { List<Component> components = order(); return components.isEmpty() ? null : components.getLast(); }
        @Override public Component getDefaultComponent(Container root) { return getFirstComponent(root); }
    }
}
