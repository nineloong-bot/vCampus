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
/** Defines the student service contract. */
public interface StudentService {
    /**
     * Performs the get student operation.
     * @param studentId the student identifier
     * @return the operation result
     */
    StudentView getStudent(String studentId);
    /**
     * Performs the get student operation.
     * @param studentId the student identifier
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default StudentView getStudent(String studentId, String trustedDepartmentId) {
        return getStudent(studentId);
    }
    /**
     * Performs the get current student operation.
     * @param userId the user identifier
     * @return the operation result
     */
    StudentView getCurrentStudent(String userId);
    /**
     * Performs the search students operation.
     * @param query the query
     * @return the operation result
     */
    PageResult<StudentSummary> searchStudents(StudentSearchQuery query);
    /**
     * Performs the search students operation.
     * @param query the query
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default PageResult<StudentSummary> searchStudents(StudentSearchQuery query,
            String trustedDepartmentId) {
        return searchStudents(query);
    }
    /**
     * Performs the update contact operation.
     * @param command the command
     * @return the operation result
     */
    StudentView updateContact(UpdateStudentContactCommand command);
    /**
     * Performs the update contact operation.
     * @param command the command
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default StudentView updateContact(UpdateStudentContactCommand command,
            String trustedDepartmentId) {
        return updateContact(command);
    }
    /**
     * Performs the update enrollment operation.
     * @param command the command
     * @return the operation result
     */
    StudentView updateEnrollment(UpdateStudentEnrollmentCommand command);
    /**
     * Performs the update enrollment operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    default StudentView updateEnrollment(UpdateStudentEnrollmentCommand command, String operatorUserId) {
        return updateEnrollment(command);
    }
    /**
     * Performs the update enrollment operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default StudentView updateEnrollment(UpdateStudentEnrollmentCommand command,
            String operatorUserId, String trustedDepartmentId) {
        return updateEnrollment(command, operatorUserId);
    }
    /**
     * Performs the change status operation.
     * @param command the command
     * @return the operation result
     */
    StudentView changeStatus(ChangeStudentStatusCommand command);
    /**
     * Performs the update student info operation.
     * @param command the command
     * @return the operation result
     */
    default StudentView updateStudentInfo(UpdateStudentInfoCommand command) {
        throw new UnsupportedOperationException();
    }
    /**
     * Performs the update student info operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    default StudentView updateStudentInfo(UpdateStudentInfoCommand command, String operatorUserId) {
        return updateStudentInfo(command);
    }
    /**
     * Performs the update student info operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default StudentView updateStudentInfo(UpdateStudentInfoCommand command,
            String operatorUserId, String trustedDepartmentId) {
        return updateStudentInfo(command, operatorUserId);
    }
    /**
     * Performs the update student academic operation.
     * @param command the command
     * @return the operation result
     */
    default StudentView updateStudentAcademic(UpdateStudentAcademicCommand command) {
        throw new UnsupportedOperationException();
    }
    /**
     * Performs the update student academic operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    default StudentView updateStudentAcademic(UpdateStudentAcademicCommand command, String operatorUserId) {
        return updateStudentAcademic(command);
    }
    /**
     * Performs the update student academic operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default StudentView updateStudentAcademic(UpdateStudentAcademicCommand command,
            String operatorUserId, String trustedDepartmentId) {
        return updateStudentAcademic(command, operatorUserId);
    }
    /**
     * Performs the list changes operation.
     * @param studentId the student identifier
     * @return the operation result
     */
    default java.util.List<edu.seu.vcampus.common.student.StudentChangeView> listChanges(String studentId) {
        return java.util.List.of();
    }
    /**
     * Performs the list changes operation.
     * @param studentId the student identifier
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default java.util.List<edu.seu.vcampus.common.student.StudentChangeView> listChanges(
            String studentId, String trustedDepartmentId) {
        return listChanges(studentId);
    }
    /**
     * Performs the change status operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    default StudentView changeStatus(ChangeStudentStatusCommand command, String operatorUserId) {
        return changeStatus(command);
    }
    /**
     * Performs the change status operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default StudentView changeStatus(ChangeStudentStatusCommand command, String operatorUserId,
            String trustedDepartmentId) {
        return changeStatus(command, operatorUserId);
    }
}
