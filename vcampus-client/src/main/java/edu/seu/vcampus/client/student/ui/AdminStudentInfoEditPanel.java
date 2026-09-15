package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.StudentAcademicProfile;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.JComponent;
import java.util.Objects;
import java.util.function.Consumer;

/** Wide page-embedded editor for the administrator-maintained academic record. */
public final class AdminStudentInfoEditPanel implements EmbeddedEditor {
    private final AdminStudentInfoEditForm form;

    /** Creates an editor that preserves atomic save, version and hierarchy refresh behavior. */
    public AdminStudentInfoEditPanel(StudentClientService students, StudentView initial,
            StudentAcademicProfile academic, Consumer<StudentView> saved, Runnable close) {
        form = new AdminStudentInfoEditForm(students, initial, academic, saved,
                Objects.requireNonNull(close));
    }

    @Override public JComponent component() { return form; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return form.isDirty(); }
    @Override public void onClosed() { form.closeEditor(); }
}
