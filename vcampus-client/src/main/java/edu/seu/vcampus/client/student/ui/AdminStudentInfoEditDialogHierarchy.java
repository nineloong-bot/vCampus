package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.StudentAcademicProfile;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.DefaultComboBoxModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Cascading major and class loading for the admin edit dialog. */
abstract class AdminStudentInfoEditDialogHierarchy extends AdminStudentInfoEditDialogLabels {

    /** Creates the hierarchy segment of the admin edit dialog. */
    protected AdminStudentInfoEditDialogHierarchy(Window owner, StudentClientService students,
            StudentView initial, StudentAcademicProfile academic, Consumer<StudentView> saved) {
        super(owner, students, initial, academic, saved);
    }

    void cascadeLoadMajors(String departmentId) {
        cascadeLoadMajors(departmentId, ignored -> {});
    }

    void cascadeLoadMajors(String departmentId, Consumer<Boolean> completed) {
        long generation = hierarchyGeneration.incrementAndGet();
        suppressComboEvents = true;
        try {
            majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"正在加载..."}));
            classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
        } finally { suppressComboEvents = false; }
        if (departmentId == null) {
            suppressComboEvents = true;
            try { majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"})); }
            finally { suppressComboEvents = false; }
            completed.accept(false);
            return;
        }
        students.listMajors(departmentId, false).whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != hierarchyGeneration.get()
                    || !departmentId.equals(selectedDepartmentId())) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<MajorView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    majorCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                    if (base.majorId() != null) {
                        for (int i = 1; i < comboItems.length; i++) {
                            if (comboItems[i] instanceof MajorView m
                                    && base.majorId().equals(m.majorId())) {
                                majorCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                } finally { suppressComboEvents = false; }
                Object selected = majorCombo.getSelectedItem();
                if (selected instanceof MajorView m) cascadeLoadClasses(m.majorId(), completed);
                else completed.accept(false);
            } else {
                suppressComboEvents = true;
                try { majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"})); }
                finally { suppressComboEvents = false; }
                completed.accept(false);
            }
        }));
    }

    void cascadeLoadClasses(String majorId) {
        cascadeLoadClasses(majorId, ignored -> {});
    }

    void cascadeLoadClasses(String majorId, Consumer<Boolean> completed) {
        long generation = hierarchyGeneration.incrementAndGet();
        suppressComboEvents = true;
        try { classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"正在加载..."})); }
        finally { suppressComboEvents = false; }
        if (majorId == null) {
            suppressComboEvents = true;
            try { classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"})); }
            finally { suppressComboEvents = false; }
            completed.accept(false);
            return;
        }
        students.listClasses(majorId, false).whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != hierarchyGeneration.get()
                    || !majorId.equals(selectedMajorId())) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<ClassView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    classCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                    if (base.classId() != null) {
                        for (int i = 1; i < comboItems.length; i++) {
                            if (comboItems[i] instanceof ClassView cv
                                    && base.classId().equals(cv.classId())) {
                                classCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                } finally { suppressComboEvents = false; }
                completed.accept(base.classId() != null && base.classId().equals(selectedClassId()));
            } else {
                suppressComboEvents = true;
                try { classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"})); }
                finally { suppressComboEvents = false; }
                completed.accept(false);
            }
        }));
    }
}
