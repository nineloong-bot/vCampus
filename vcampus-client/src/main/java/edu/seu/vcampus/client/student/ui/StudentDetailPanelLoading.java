package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.*;
import java.util.List;

/** Lifecycle, loading and edit entry points for the student detail panel segments. */
abstract class StudentDetailPanelLoading extends StudentDetailPanelRendering {

    StudentDetailPanelLoading(StudentClientService students, ClientConnection connection,
            String studentId, boolean canEdit) {
        super(students, connection, studentId, canEdit);
    }

    @Override
    void editAcademic() {
        if (profile == null || connection.state() != ConnectionState.CONNECTED) return;
        new AdminStudentInfoEditDialog(SwingUtilities.getWindowAncestor(this), students,
                profile.core(), profile.academic(), saved -> loadProfile()).setVisible(true);
    }

    @Override public void addNotify() {
        super.addNotify();
        active = true;
        connectionChanged(connection.state());
        if (studentId != null) loadProfile();
    }

    public void loadStudent(String newStudentId) {
        this.studentId = newStudentId;
        this.loaded = false;
        this.profile = null;
        requestGeneration.incrementAndGet();
        values.forEach((k, v) -> v.setText("未填写"));
        changesModel.setData(List.of());
        if (newStudentId != null) loadProfile();
        else clear();
    }

    public void clear() {
        this.studentId = null;
        this.loaded = false;
        this.profile = null;
        requestGeneration.incrementAndGet();
        values.forEach((k, v) -> v.setText("未填写"));
        changesModel.setData(List.of());
        statusLabel.setText("请选择学生");
        errorLabel.setText(" ");
        setEditingEnabled(false);
    }

    @Override public void removeNotify() {
        active = false;
        requestGeneration.incrementAndGet();
        super.removeNotify();
    }

    void loadProfile() {
        long generation = requestGeneration.incrementAndGet();
        onEdt(() -> {
            if (active && generation == requestGeneration.get()) renderLoading();
        });
        if (!canEdit) {
            students.get(studentId).whenComplete((body, failure) -> onEdt(() -> {
                if (!active || generation != requestGeneration.get()) return;
                if (failure != null || body == null || !body.success() || body.data() == null) {
                    renderError(message(body, "学生信息加载失败，请稍后重试"));
                    return;
                }
                renderLimitedProfile(body.data());
            }));
            return;
        }
        students.getProfile(studentId).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure != null || body == null || !body.success() || body.data() == null) {
                renderError(message(body, "学生档案加载失败，请稍后重试"));
                return;
            }
            profile = body.data();
            renderProfile(profile);
            loadChanges(generation);
        }));
    }

    private void loadChanges(long generation) {
        students.listChanges(studentId).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure != null) errorLabel.setText("变更记录加载失败");
            else if (body != null && body.success() && body.data() != null) changesModel.setData(body.data());
        }));
    }

    private void renderLoading() {
        statusLabel.setText("正在加载...");
        errorLabel.setText(" ");
        setEditingEnabled(false);
    }

    private void renderError(String value) {
        statusLabel.setText("加载失败");
        errorLabel.setText(value);
        setEditingEnabled(false);
    }
}
