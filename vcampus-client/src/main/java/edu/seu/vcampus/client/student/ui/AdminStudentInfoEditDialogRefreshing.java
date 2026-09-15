package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.StudentAcademicProfile;
import edu.seu.vcampus.common.student.StudentView;

import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Conflict refresh and hierarchy rebinding flows for the admin edit dialog. */
abstract class AdminStudentInfoEditDialogRefreshing extends AdminStudentInfoEditDialogLoading {

    /** Creates the refreshing segment of the admin edit dialog. */
    protected AdminStudentInfoEditDialogRefreshing(Window owner, StudentClientService students,
            StudentView initial, StudentAcademicProfile academic, Consumer<StudentView> saved) {
        super(owner, students, initial, academic, saved);
    }

    void refreshBase() {
        if (disposed || !conflict) return;
        long generation = requestGeneration.incrementAndGet();
        refresh.setEnabled(false);
        refresh.setText("正在刷新");
        CompletableFuture<ResponseBody<StudentView>> response;
        try { response = students.get(base.studentId()); }
        catch (RuntimeException failure) { response = CompletableFuture.failedFuture(failure); }
        response.whenComplete((body, failure) -> onEdt(() -> finishRefresh(generation, body, failure)));
    }

    void finishRefresh(long generation, ResponseBody<StudentView> body, Throwable failure) {
        if (disposed || generation != requestGeneration.get()) return;
        refresh.setText("刷新数据");
        if (failure == null && body != null && body.success() && body.data() != null) {
            base = body.data();
            conflict = true;
            refresh.setVisible(true);
            refresh.setEnabled(false);
            submit.setEnabled(false);
            studentNumberField.setText(base.studentNumber());
            studentTypeCombo.setSelectedItem(studentTypeLabel(base.studentType()));
            statusCombo.setSelectedItem(statusLabel(base.status()));
            error.setText("正在重新绑定院系、专业和班级……");
            loadDepartments(success -> finishHierarchyRefresh(generation, success));
            return;
        }
        refresh.setEnabled(true);
        error.setText(failure == null ? safeMessage(body, "刷新失败，请稍后重试") : "刷新失败，请稍后重试");
    }

    void finishHierarchyRefresh(long generation, boolean success) {
        if (disposed || generation != requestGeneration.get()) return;
        refresh.setText("刷新数据");
        if (success) {
            conflict = false;
            refresh.setVisible(false);
            refresh.setEnabled(true);
            submit.setEnabled(true);
            error.setText("数据已刷新，请确认后保存");
            return;
        }
        conflict = true;
        refresh.setVisible(true);
        refresh.setEnabled(true);
        submit.setEnabled(false);
        error.setText("院系、专业或班级刷新失败，请重试");
    }
}
