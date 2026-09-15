package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.StudentAcademicProfile;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.DefaultComboBoxModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Initial department loading for the admin edit dialog. */
abstract class AdminStudentInfoEditDialogLoading extends AdminStudentInfoEditDialogHierarchy {

    /** Creates the loading segment of the admin edit dialog. */
    protected AdminStudentInfoEditDialogLoading(Window owner, StudentClientService students,
            StudentView initial, StudentAcademicProfile academic, Consumer<StudentView> saved) {
        super(owner, students, initial, academic, saved);
    }

    void loadDepartments() {
        loadDepartments(ignored -> {});
    }

    void loadDepartments(Consumer<Boolean> completed) {
        long generation = hierarchyGeneration.incrementAndGet();
        students.listDepartments(false).whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != hierarchyGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<DepartmentView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    departmentCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                    if (base.departmentName() != null) {
                        for (int i = 1; i < comboItems.length; i++) {
                            if (comboItems[i] instanceof DepartmentView d
                                    && base.departmentName().equals(d.name())) {
                                departmentCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                } finally { suppressComboEvents = false; }
                Object selected = departmentCombo.getSelectedItem();
                if (selected instanceof DepartmentView d) cascadeLoadMajors(d.departmentId(), completed);
                else completed.accept(false);
            } else completed.accept(false);
        }));
    }
}
