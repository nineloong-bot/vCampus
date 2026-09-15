package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.StudentView;

import java.awt.Window;
import java.util.function.Consumer;

/**
 * Modal dialog that changes a student's class and handles edit conflicts.
 *
 * <p>Form layout, cascading loaders, submission, conflict refresh and focus order
 * are implemented by the package-private segment chain this class extends, keeping
 * the public constructor and widget names unchanged.</p>
 */
public final class EnrollmentChangeDialog extends EnrollmentChangeDialogAssembly {

    /** Creates the modal enrollment change dialog for the given owner window. */
    public EnrollmentChangeDialog(Window owner, StudentClientService students,
                                  StudentView initial, Consumer<StudentView> saved) {
        super(owner, students, initial, saved);
    }
}
