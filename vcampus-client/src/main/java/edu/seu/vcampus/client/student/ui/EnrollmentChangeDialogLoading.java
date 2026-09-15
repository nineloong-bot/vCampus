package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.StudentView;

import java.awt.Window;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Cascading department, major and class loading for the enrollment change segments. */
abstract class EnrollmentChangeDialogLoading extends EnrollmentChangeDialogForm {

    EnrollmentChangeDialogLoading(Window owner, StudentClientService students,
            StudentView initial, Consumer<StudentView> saved) {
        super(owner, students, initial, saved);
    }

    void setupCascading() {
        departmentCombo.addActionListener(event -> {
            if (suppressingEvents) return;
            DepartmentView selected = (DepartmentView) departmentCombo.getSelectedItem();
            if (selected == null) { majorCombo.removeAllItems(); classCombo.removeAllItems(); return; }
            loadMajors(selected.departmentId());
        });
        majorCombo.addActionListener(event -> {
            if (suppressingEvents) return;
            MajorView selected = (MajorView) majorCombo.getSelectedItem();
            if (selected == null) { classCombo.removeAllItems(); return; }
            loadClasses(selected.majorId());
        });
    }

    void loadDepartments() {
        suppressingEvents = true;
        departmentCombo.removeAllItems();
        CompletableFuture<ResponseBody<ArrayList<DepartmentView>>> response;
        try {
            response = students.listDepartments(true);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                for (DepartmentView dept : body.data()) departmentCombo.addItem(dept);
            }
            suppressingEvents = false;
        }));
    }

    void loadMajors(String departmentId) {
        suppressingEvents = true;
        majorCombo.removeAllItems();
        classCombo.removeAllItems();
        suppressingEvents = false;
        CompletableFuture<ResponseBody<ArrayList<MajorView>>> response;
        try {
            response = students.listMajors(departmentId);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                for (MajorView major : body.data()) majorCombo.addItem(major);
            }
        }));
    }

    void loadClasses(String majorId) {
        suppressingEvents = true;
        classCombo.removeAllItems();
        suppressingEvents = false;
        CompletableFuture<ResponseBody<ArrayList<ClassView>>> response;
        try {
            response = students.listClasses(majorId);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                for (ClassView cls : body.data()) classCombo.addItem(cls);
            }
        }));
    }
}
