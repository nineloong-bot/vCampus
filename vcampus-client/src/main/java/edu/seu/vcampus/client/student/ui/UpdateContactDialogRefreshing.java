package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.StudentView;

import java.awt.Window;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Conflict recovery refresh flow for the contact dialog segments. */
abstract class UpdateContactDialogRefreshing extends UpdateContactDialogSaving {

    UpdateContactDialogRefreshing(Window owner, StudentClientService students,
            StudentView initial, Consumer<StudentView> saved) {
        super(owner, students, initial, saved);
    }

    void refreshBase() {
        if (disposed || !conflict) return;
        long generation = requestGeneration.incrementAndGet();
        refresh.setEnabled(false);
        refresh.setText("正在刷新");
        CompletableFuture<ResponseBody<StudentView>> response;
        try {
            response = students.getCurrent();
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> finishRefresh(generation, body, failure)));
    }

    private void finishRefresh(long generation, ResponseBody<StudentView> body, Throwable failure) {
        if (disposed || generation != requestGeneration.get()) return;
        refresh.setText("刷新数据");
        if (failure == null && body != null && body.success() && body.data() != null) {
            base = body.data();
            conflict = false;
            refresh.setVisible(false);
            refresh.setEnabled(true);
            submit.setEnabled(true);
            error.setText("数据已刷新，请确认后保存");
            return;
        }
        refresh.setEnabled(true);
        error.setText(failure == null ? safeMessage(body, "刷新失败，请稍后重试") : "刷新失败，请稍后重试");
    }
}
