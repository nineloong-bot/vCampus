package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.*;

/** Profile value rendering and label formatting for the student detail panel segments. */
abstract class StudentDetailPanelRendering extends StudentDetailPanelSections {

    StudentDetailPanelRendering(StudentClientService students, ClientConnection connection,
            String studentId, boolean canEdit) {
        super(students, connection, studentId, canEdit);
    }

    void renderProfile(StudentProfileData data) {
        StudentView core = data.core();
        StudentPersonalProfile personal = data.personal();
        StudentAcademicProfile academic = data.academic();
        put("card", core.campusCardNumber()); put("studentNumber", core.studentNumber()); put("name", core.studentName());
        put("namePinyin", personal.namePinyin()); put("formerName", personal.formerName()); put("gender", gender(core.gender()));
        put("politicalStatus", personal.politicalStatus()); put("ethnicity", personal.ethnicity()); put("maritalStatus", personal.maritalStatus());
        put("idDocumentType", personal.idDocumentType()); put("idDocumentNumber", personal.idDocumentNumber()); put("idIssuedDate", personal.idIssuedDate());
        put("birthDate", personal.birthDate()); put("nativePlace", personal.nativePlace()); put("countryRegion", personal.countryRegion());
        put("birthplace", personal.birthplace()); put("studentOriginPlace", personal.studentOriginPlace());
        put("householdRegistrationType", personal.householdRegistrationType()); put("householdBeforeEnrollment", personal.householdBeforeEnrollment());
        put("householdAfterEnrollment", personal.householdAfterEnrollment()); put("overseasChineseStatus", personal.overseasChineseStatus());
        put("religion", personal.religion()); put("leagueMember", yesNo(personal.leagueMember())); put("leagueJoinDate", personal.leagueJoinDate());
        put("partyMember", yesNo(personal.partyMember())); put("partyJoinDate", personal.partyJoinDate()); put("healthStatus", personal.healthStatus());
        put("bloodType", personal.bloodType()); put("weightKg", personal.weightKg()); put("heightCm", personal.heightCm());
        put("specialties", personal.specialties()); put("hobbies", personal.hobbies()); put("onlyChild", yesNo(personal.onlyChild()));
        put("email", personal.email()); put("phone", personal.phone());

        put("studentCategory", academic.studentCategory()); put("enrolled", yesNo(academic.enrolled())); put("onCampus", yesNo(academic.onCampus()));
        put("academicStatus", academic.academicStatus()); put("campus", academic.campus()); put("currentGrade", academic.currentGrade());
        put("department", academic.departmentName()); put("major", academic.majorName()); put("class", academic.className());
        put("educationLevel", academic.educationLevel()); put("trainingMode", academic.trainingMode()); put("programLength", academic.programLengthYears());
        put("attendanceMode", academic.attendanceMode() == null ? null : academic.attendanceMode().displayName());
        put("degreeName", academic.degreeName()); put("educationName", academic.educationName());
        put("expectedGraduationDate", academic.expectedGraduationDate()); put("graduationDate", academic.graduationDate());
        put("studentSource", academic.studentSource()); put("graduateStudyMode", academic.graduateStudyMode());
        put("counselorName", academic.counselorName()); put("counselorContact", academic.counselorContact());
        loaded = true;
        statusLabel.setText(connection.state() == ConnectionState.CONNECTED ? "已加载" : "已断开连接");
        errorLabel.setText(" ");
        updateEditingState();
    }

    void renderLimitedProfile(StudentView core) {
        put("card", core.campusCardNumber());
        put("studentNumber", core.studentNumber());
        put("name", core.studentName());
        put("gender", gender(core.gender()));
        put("email", core.email());
        put("phone", core.phone());
        put("studentCategory", studentType(core.studentType()));
        put("academicStatus", status(core.status()));
        put("department", core.departmentName());
        put("major", core.majorName());
        put("class", core.className());
        loaded = true;
        statusLabel.setText(connection.state() == ConnectionState.CONNECTED ? "已加载" : "已断开连接");
        errorLabel.setText(" ");
    }

    private static String yesNo(boolean value) { return value ? "是" : "否"; }
    private static String gender(String value) {
        if (value == null || value.isBlank()) return "未填写";
        return switch (value) { case "MALE" -> "男"; case "FEMALE" -> "女"; default -> value; };
    }
    private static String studentType(StudentType value) {
        if (value == null) return "未填写";
        return switch (value) { case UNDERGRADUATE -> "本科生"; case MASTER -> "硕士生"; case DOCTORATE -> "博士生"; };
    }
    private static String status(StudentStatus value) {
        if (value == null) return "未填写";
        return switch (value) { case ACTIVE -> "正常"; case SUSPENDED -> "休学"; case GRADUATED -> "已毕业"; case WITHDRAWN -> "已退学"; };
    }
}
