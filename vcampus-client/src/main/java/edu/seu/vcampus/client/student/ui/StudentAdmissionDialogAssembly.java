package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/** Wires the assembled dialog: layout, listeners, focus order and disposal. */
abstract class StudentAdmissionDialogAssembly extends StudentAdmissionDialogSubmitting {

    StudentAdmissionDialogAssembly(Window owner, StudentClientService students) {
        super(owner, students);
    }

    @Override
    void initializeDialog(Window owner) {
        setContentPane(buildForm());
        loadDepartments();
        cancel.addActionListener(event -> dispose());
        submit.addActionListener(event -> submit());
        getRootPane().setDefaultButton(submit);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new AdmissionFocusTraversalPolicy());
        department.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                Object selected = department.getSelectedItem();
                if (selected instanceof DepartmentView dept) {
                    loadMajors(dept.departmentId());
                }
            }
        });
        major.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                Object selected = major.getSelectedItem();
                if (selected instanceof MajorView maj) {
                    loadClasses(maj.majorId());
                }
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) { establishInitialFocus(); }
            @Override public void windowActivated(WindowEvent event) { establishInitialFocus(); }
        });
        setSize(new Dimension(600, 520));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    @Override public void dispose() {
        disposed = true;
        requestGeneration.incrementAndGet();
        super.dispose();
    }

    /** Focus order over the admission form fields and actions. */
    final class AdmissionFocusTraversalPolicy extends FocusTraversalPolicy {
        private List<Component> order() {
            List<Component> components = new ArrayList<>();
            addIfEligible(components, studentName);
            addIfEligible(components, gender);
            addIfEligible(components, studentType);
            addIfEligible(components, department);
            addIfEligible(components, major);
            addIfEligible(components, classBox);
            addIfEligible(components, year);
            addIfEligible(components, email);
            addIfEligible(components, phone);
            addIfEligible(components, cancel);
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
