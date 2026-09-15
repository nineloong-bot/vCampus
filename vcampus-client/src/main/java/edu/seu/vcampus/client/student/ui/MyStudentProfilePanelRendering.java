package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.AttendanceMode;
import edu.seu.vcampus.common.student.StudentAcademicProfile;
import edu.seu.vcampus.common.student.StudentPersonalProfile;
import edu.seu.vcampus.common.student.StudentProfileApplicationStatus;
import edu.seu.vcampus.common.student.StudentProfileApplicationView;
import edu.seu.vcampus.common.student.StudentProfileData;
import edu.seu.vcampus.common.student.StudentProfileWorkspace;
import edu.seu.vcampus.common.student.StudentView;

/** Workspace loading, failure handling and rendering for the profile panel. */
abstract class MyStudentProfilePanelRendering extends MyStudentProfilePanelEditState {

    /** Creates the rendering segment of the profile panel. */
    protected MyStudentProfilePanelRendering(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    @Override void connectionChanged(ConnectionState state) {
        onEdt(() -> { if (active && workspace != null) render(workspace); });
    }

    /**
     * Reloads the profile workspace from the server.
     *
     * <p>Invalidates any in-flight reload and refreshes the read-only and
     * editable views once the new workspace arrives.</p>
     */
    public void refreshProfile() {
        long current = generation.incrementAndGet(); onEdt(() -> { if (active) loading(); });
        students.getProfileWorkspace().whenComplete((body, failure) -> onEdt(() -> {
            if (!active || current != generation.get()) return;
            if (failure != null || body == null || !body.success() || body.data() == null)
                failure(message(body, "档案加载失败，请稍后重试"));
            else render(body.data());
        }));
    }

    void loading() { statuses.loading(); errorLabel.setText(" "); refreshButton.setEnabled(false); setControls(false); }
    void failure(String message) { statuses.failure(); errorLabel.setText(message); refreshButton.setEnabled(true); setControls(false); }
    void render(StudentProfileWorkspace value) {
        workspace = value; StudentProfileData formal = value.formalProfile(); StudentProfileApplicationView app = value.application();
        boolean draftVisible = app != null && (app.status() == StudentProfileApplicationStatus.DRAFT
                || app.status() == StudentProfileApplicationStatus.PENDING);
        StudentPersonalProfile personal = draftVisible ? app.personal() : formal.personal();
        AttendanceMode attendance = draftVisible ? app.attendanceMode() : formal.academic().attendanceMode();
        if (isPersonalEditing) {
            cancelPersonalEdit();
        }
        renderCore(formal, personal); renderAcademic(formal.academic(), attendance); statuses.showApplication(app);
        statuses.loaded(); errorLabel.setText(" "); refreshButton.setEnabled(true);
        boolean connected = connection.state() == ConnectionState.CONNECTED;
        boolean pending = app != null && app.status() == StudentProfileApplicationStatus.PENDING;
        personalEdit.setEnabled(connected); academicEdit.setEnabled(connected);
        String editHint = pending ? "当前申请正在审核，请先撤回申请再继续编辑" : null;
        personalEdit.setToolTipText(editHint); academicEdit.setToolTipText(editHint);
        exportButton.setEnabled(connected);
        boolean actionable = app != null && (app.status() == StudentProfileApplicationStatus.DRAFT || pending);
        submitButton.setText(pending ? "撤回申请" : "提交审核");
        submitButton.getAccessibleContext().setAccessibleName(submitButton.getText());
        submitButton.setEnabled(connected && actionable);
    }

    void renderCore(StudentProfileData data, StudentPersonalProfile p) {
        StudentView c = data.core(); put("card", c.campusCardNumber()); put("studentNumber", c.studentNumber()); put("name", c.studentName());
        put("namePinyin", p.namePinyin()); put("formerName", p.formerName()); put("gender", c.gender());
        put("politicalStatus", p.politicalStatus()); put("ethnicity", p.ethnicity()); put("maritalStatus", p.maritalStatus());
        put("idDocumentType", p.idDocumentType()); put("idDocumentNumber", p.idDocumentNumber()); put("idIssuedDate", p.idIssuedDate());
        put("birthDate", p.birthDate()); put("nativePlace", p.nativePlace()); put("countryRegion", p.countryRegion());
        put("birthplace", p.birthplace()); put("studentOriginPlace", p.studentOriginPlace());
        put("householdRegistrationType", p.householdRegistrationType()); put("householdBeforeEnrollment", p.householdBeforeEnrollment());
        put("householdAfterEnrollment", p.householdAfterEnrollment()); put("overseasChineseStatus", p.overseasChineseStatus());
        put("religion", p.religion()); put("leagueMember", yesNo(p.leagueMember())); put("leagueJoinDate", p.leagueJoinDate());
        put("partyMember", yesNo(p.partyMember())); put("partyJoinDate", p.partyJoinDate()); put("healthStatus", p.healthStatus());
        put("bloodType", p.bloodType()); put("weightKg", p.weightKg()); put("heightCm", p.heightCm()); put("specialties", p.specialties());
        put("hobbies", p.hobbies()); put("onlyChild", yesNo(p.onlyChild())); put("email", p.email()); put("phone", p.phone());
    }

    void renderAcademic(StudentAcademicProfile a, AttendanceMode attendance) {
        put("studentCategory", a.studentCategory()); put("enrolled", yesNo(a.enrolled())); put("onCampus", yesNo(a.onCampus()));
        put("academicStatus", a.academicStatus()); put("campus", a.campus()); put("currentGrade", a.currentGrade());
        put("department", a.departmentName()); put("major", a.majorName()); put("class", a.className());
        put("educationLevel", a.educationLevel()); put("trainingMode", a.trainingMode()); put("programLength", a.programLengthYears());
        put("attendanceMode", attendance == null ? null : attendance.displayName()); put("degreeName", a.degreeName());
        put("educationName", a.educationName()); put("expectedGraduationDate", a.expectedGraduationDate());
        put("graduationDate", a.graduationDate()); put("studentSource", a.studentSource());
        put("graduateStudyMode", a.graduateStudyMode()); put("counselorName", a.counselorName()); put("counselorContact", a.counselorContact());
    }
}
