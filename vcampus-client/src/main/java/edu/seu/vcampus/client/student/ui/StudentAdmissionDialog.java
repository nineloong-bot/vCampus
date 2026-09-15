package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;

import java.awt.Window;

/**
 * Modal dialog that admits a new student and shows the generated campus card number.
 *
 * <p>The form layout, cascading loaders, submission flow and success receipt are
 * implemented by the package-private segment chain this class extends, keeping the
 * public constructor and widget names unchanged.</p>
 */
public final class StudentAdmissionDialog extends StudentAdmissionDialogAssembly {

    /** Creates the modal admission dialog for the given owner window. */
    public StudentAdmissionDialog(Window owner, StudentClientService students) {
        super(owner, students);
    }
}
