package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;

import java.awt.Window;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/** Cascading department, major and class loading for the admission dialog segments. */
abstract class StudentAdmissionDialogLoading extends StudentAdmissionDialogForm {

    StudentAdmissionDialogLoading(Window owner, StudentClientService students) {
        super(owner, students);
    }

    void loadDepartments() {
        long generation = requestGeneration.get();
        CompletableFuture<ResponseBody<ArrayList<DepartmentView>>> response;
        try {
            response = students.listDepartments(true);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                replaceItems(department, body.data());
            }
        }));
    }

    void loadMajors(String departmentId) {
        long generation = requestGeneration.get();
        CompletableFuture<ResponseBody<ArrayList<MajorView>>> response;
        try {
            response = students.listMajors(departmentId);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        major.removeAllItems();
        major.addItem(null);
        classBox.removeAllItems();
        classBox.addItem(null);
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                replaceItems(major, body.data());
            }
        }));
    }

    void loadClasses(String majorId) {
        long generation = requestGeneration.get();
        CompletableFuture<ResponseBody<ArrayList<ClassView>>> response;
        try {
            response = students.listClasses(majorId);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        classBox.removeAllItems();
        classBox.addItem(null);
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                replaceItems(classBox, body.data());
            }
        }));
    }
}
