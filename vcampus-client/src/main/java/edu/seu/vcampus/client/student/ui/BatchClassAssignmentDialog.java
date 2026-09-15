package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.MajorView;

import java.awt.Window;
import java.util.List;

/**
 * Dialog for CSV-based batch student import with auto class distribution.
 *
 * <p>The preview model, CSV parsing, snake-round distribution, import submission and
 * page layout are implemented by the package-private segment chain this class
 * extends, keeping the public constructor and widget names unchanged.</p>
 */
public final class BatchClassAssignmentDialog extends BatchClassAssignmentDialogLayout {

    /** Creates the modal batch assignment dialog for one major and its classes. */
    public BatchClassAssignmentDialog(Window owner, StudentClientService students,
                                      MajorView major, List<ClassView> classes) {
        super(owner, students, major, classes);
    }
}
