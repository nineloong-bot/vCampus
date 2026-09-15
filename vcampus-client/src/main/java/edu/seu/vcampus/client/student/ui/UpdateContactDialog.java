package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.StudentView;

import java.awt.Window;
import java.util.function.Consumer;

/**
 * Modal editor for the signed-in student's contact details.
 *
 * <p>Form layout, submission, conflict refresh and focus order are implemented by
 * the package-private segment chain this class extends, keeping the public
 * constructor and widget names unchanged.</p>
 */
public final class UpdateContactDialog extends UpdateContactDialogAssembly {

    /** Creates the modal contact editor for the given owner window. */
    public UpdateContactDialog(Window owner, StudentClientService students,
                               StudentView initial, Consumer<StudentView> saved) {
        super(owner, students, initial, saved);
    }
}
