package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.AttendanceMode;
import edu.seu.vcampus.common.student.SaveStudentPersonalDraftCommand;
import edu.seu.vcampus.common.student.StudentFieldError;
import edu.seu.vcampus.common.student.StudentFieldValidator;
import edu.seu.vcampus.common.student.StudentPersonalProfile;
import edu.seu.vcampus.common.student.StudentProfileApplicationStatus;
import edu.seu.vcampus.common.student.StudentProfileApplicationView;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Edit actions, draft value building and saving for the profile panel. */
abstract class MyStudentProfilePanelEditing extends MyStudentProfilePanelRendering {

    /** Creates the editing segment of the profile panel. */
    protected MyStudentProfilePanelEditing(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    void editPersonal() {
        togglePersonalEdit();
    }

    void togglePersonalEdit() {
        if (isPersonalEditing) {
            cancelPersonalEdit();
        } else {
            if (pendingApplication()) { promptWithdrawBeforeEditing(); return; }
            if (workspace == null || !personalEdit.isEnabled()) return;
            startPersonalEdit();
        }
    }

    StudentPersonalProfile buildPersonalProfileValue() {
        try {
            String namePinyin = editVal("namePinyin");
            String formerName = editVal("formerName");
            String political = editVal("politicalStatus");
            boolean isMasses = "群众".equals(political);
            String ethnicity = editVal("ethnicity");
            String maritalStatus = editVal("maritalStatus");
            String idDocumentType = editReadOnlyLabels.containsKey("idDocumentType")
                    ? editReadOnlyLabels.get("idDocumentType").getText() : null;
            if ("未填写".equals(idDocumentType)) idDocumentType = null;
            String idDocumentNumber = editReadOnlyLabels.containsKey("idDocumentNumber")
                    ? editReadOnlyLabels.get("idDocumentNumber").getText() : null;
            if ("未填写".equals(idDocumentNumber)) idDocumentNumber = null;
            LocalDate idIssuedDate = editDate("idIssuedDate");

            StudentView core = workspace != null && workspace.formalProfile() != null
                    ? workspace.formalProfile().core() : null;
            LocalDate birthDate = workspace != null && workspace.formalProfile() != null && workspace.formalProfile().personal() != null
                    ? workspace.formalProfile().personal().birthDate() : null;
            String nativePlace = editVal("nativePlace");
            String countryRegion = editVal("countryRegion");
            String birthplace = editVal("birthplace");
            String studentOriginPlace = editVal("studentOriginPlace");
            String householdRegistrationType = editVal("householdRegistrationType");
            String householdBefore = editVal("householdBeforeEnrollment");
            String householdAfter = editVal("householdAfterEnrollment");
            String overseasChineseStatus = editVal("overseasChineseStatus");
            String religion = editVal("religion");

            boolean isLeague = !isMasses && "是".equals(editVal("leagueMember"));
            LocalDate leagueDate = isLeague ? editDate("leagueJoinDate") : null;

            boolean isParty = !isMasses && "是".equals(editVal("partyMember"));
            LocalDate partyDate = isParty ? editDate("partyJoinDate") : null;

            String healthStatus = editVal("healthStatus");
            String bloodType = editVal("bloodType");
            Integer weightKg = editInt("weightKg");
            Integer heightCm = editInt("heightCm");
            String specialties = editVal("specialties");
            String hobbies = editVal("hobbies");
            boolean onlyChild = "是".equals(editVal("onlyChild"));
            String email = editVal("email");
            String phone = editVal("phone");

            StudentPersonalProfile value = new StudentPersonalProfile(
                    namePinyin, formerName, political, ethnicity, maritalStatus,
                    idDocumentType, idDocumentNumber, idIssuedDate, birthDate,
                    nativePlace, countryRegion, birthplace, studentOriginPlace,
                    householdRegistrationType, householdBefore, householdAfter,
                    overseasChineseStatus, religion, isLeague, leagueDate,
                    isParty, partyDate, healthStatus, bloodType, weightKg,
                    heightCm, specialties, hobbies, onlyChild, email, phone);

            LocalDate enrollmentDate = core != null ? core.enrollmentDate() : null;
            List<StudentFieldError> errors = StudentFieldValidator.validatePersonal(
                    value, LocalDate.now(), enrollmentDate);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(errors.getFirst().message());
            }
            return value;
        } catch (DateTimeParseException | NumberFormatException error) {
            throw new IllegalArgumentException("日期须为 yyyy-MM-dd，身高和体重须为整数");
        }
    }

    void savePersonalDraft() {
        StudentPersonalProfile value;
        try {
            value = buildPersonalProfileValue();
        } catch (IllegalArgumentException invalid) {
            errorLabel.setText(invalid.getMessage());
            return;
        }
        personalSave.setEnabled(false);
        errorLabel.setText("正在暂存…");
        StudentProfileApplicationView app = workspace != null ? workspace.application() : null;
        boolean draft = app != null && app.status() == StudentProfileApplicationStatus.DRAFT;
        long expected = draft ? app.applicationVersion() : 0;
        students.savePersonalDraft(new SaveStudentPersonalDraftCommand(value, expected))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (failure != null || body == null || !body.success() || body.data() == null) {
                        errorLabel.setText(message(body, "暂存失败，请稍后重试"));
                        personalSave.setEnabled(true);
                        return;
                    }
                    render(body.data());
                    cancelPersonalEdit();
                    personalSave.setEnabled(true);
                    errorLabel.setText("个人信息已暂存");
                }));
    }

    void editAttendance() {
        if (pendingApplication()) { promptWithdrawBeforeEditing(); return; }
        if (workspace == null || !academicEdit.isEnabled()) return;
        StudentProfileApplicationView app = workspace.application(); boolean draft = app != null && app.status() == StudentProfileApplicationStatus.DRAFT;
        AttendanceMode initial = draft ? app.attendanceMode() : workspace.formalProfile().academic().attendanceMode();
        long expected = draft ? app.applicationVersion() : 0;
        new AttendanceModeEditDialog(SwingUtilities.getWindowAncestor(this), students, initial, expected, this::render).setVisible(true);
    }

    boolean pendingApplication() {
        return workspace != null && workspace.application() != null
                && workspace.application().status() == StudentProfileApplicationStatus.PENDING;
    }

    void promptWithdrawBeforeEditing() {
        JOptionPane.showMessageDialog(this, "请先点击“撤回申请”，再继续编辑。",
                "申请正在审核", JOptionPane.INFORMATION_MESSAGE);
    }
}
