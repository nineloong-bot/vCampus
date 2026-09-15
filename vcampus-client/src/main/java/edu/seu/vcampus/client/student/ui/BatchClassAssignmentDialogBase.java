package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Collaborators, view widgets and dialog shell for the batch assignment segments. */
abstract class BatchClassAssignmentDialogBase extends JDialog {
    final StudentClientService students;
    final MajorView major;
    final List<ClassView> availableClasses;
    final AtomicLong generation = new AtomicLong();
    boolean disposed;

    JTable previewTable;
    JPanel statsPanel;
    JLabel errorLabel;
    JButton importButton;
    JButton assignButton;
    JLabel fileLabel;

    BatchClassAssignmentDialogBase(Window owner, StudentClientService students,
                                      MajorView major, List<ClassView> classes) {
        super(owner, "批量分班", ModalityType.APPLICATION_MODAL);
        this.students = Objects.requireNonNull(students);
        this.major = Objects.requireNonNull(major);
        this.availableClasses = new ArrayList<>(classes);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        initializeDialog(owner);
    }

    /** Completes construction once the layout helpers are available. */
    abstract void initializeDialog(Window owner);

    @Override
    public void dispose() {
        disposed = true;
        generation.incrementAndGet();
        super.dispose();
    }
}
