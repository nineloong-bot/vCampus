package edu.seu.vcampus.server.student.service;
import edu.seu.vcampus.common.student.ChangeStudentStatusCommand;
import edu.seu.vcampus.common.student.UpdateStudentAcademicCommand;
import edu.seu.vcampus.common.student.UpdateStudentInfoCommand;
import edu.seu.vcampus.common.student.StudentView;
import edu.seu.vcampus.common.student.UpdateStudentContactCommand;
import edu.seu.vcampus.common.student.UpdateStudentEnrollmentCommand;
import edu.seu.vcampus.common.student.StudentSearchQuery;
import edu.seu.vcampus.common.student.StudentSummary;
import edu.seu.vcampus.common.paging.PageResult;
public interface StudentService {
    StudentView getStudent(String studentId);
    StudentView getCurrentStudent(String userId);
    PageResult<StudentSummary> searchStudents(StudentSearchQuery query);
    StudentView updateContact(UpdateStudentContactCommand command);
    StudentView updateEnrollment(UpdateStudentEnrollmentCommand command);
    default StudentView updateEnrollment(UpdateStudentEnrollmentCommand command, String operatorUserId) {
        return updateEnrollment(command);
    }
    StudentView changeStatus(ChangeStudentStatusCommand command);
    default StudentView updateStudentInfo(UpdateStudentInfoCommand command) {
        throw new UnsupportedOperationException();
    }
    default StudentView updateStudentInfo(UpdateStudentInfoCommand command, String operatorUserId) {
        return updateStudentInfo(command);
    }
    default StudentView updateStudentAcademic(UpdateStudentAcademicCommand command) {
        throw new UnsupportedOperationException();
    }
    default StudentView updateStudentAcademic(UpdateStudentAcademicCommand command, String operatorUserId) {
        return updateStudentAcademic(command);
    }
    default java.util.List<edu.seu.vcampus.common.student.StudentChangeView> listChanges(String studentId) {
        return java.util.List.of();
    }
    default StudentView changeStatus(ChangeStudentStatusCommand command, String operatorUserId) {
        return changeStatus(command);
    }
}
