package edu.seu.vcampus.server.student.service;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import java.util.List;
/** Defines the student organization query contract. */
public interface StudentOrganizationQuery {
    /**
     * Performs the list departments operation.
     * @param activeOnly the active only
     * @return the operation result
     */
    List<DepartmentView> listDepartments(boolean activeOnly);
    /**
     * Performs the list majors operation.
     * @param departmentId the department identifier
     * @return the operation result
     */
    List<MajorView> listMajors(String departmentId);
    /**
     * Performs the list classes operation.
     * @param majorId the major identifier
     * @return the operation result
     */
    List<ClassView> listClasses(String majorId);
    /**
     * Performs the list majors operation.
     * @param departmentId the department identifier
     * @param activeOnly the active only
     * @return the operation result
     */
    default List<MajorView> listMajors(String departmentId, boolean activeOnly) {
        return listMajors(departmentId);
    }
    /**
     * Performs the list classes operation.
     * @param majorId the major identifier
     * @param activeOnly the active only
     * @return the operation result
     */
    default List<ClassView> listClasses(String majorId, boolean activeOnly) {
        return listClasses(majorId);
    }
    /**
     * Performs the list departments operation.
     * @param activeOnly the active only
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default List<DepartmentView> listDepartments(boolean activeOnly, String trustedDepartmentId) {
        return listDepartments(activeOnly).stream()
                .filter(value -> value.departmentId().equals(trustedDepartmentId)).toList();
    }
    /**
     * Performs the list majors operation.
     * @param departmentId the department identifier
     * @param activeOnly the active only
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default List<MajorView> listMajors(String departmentId, boolean activeOnly,
            String trustedDepartmentId) {
        return listMajors(trustedDepartmentId, activeOnly);
    }
    /**
     * Performs the list classes operation.
     * @param majorId the major identifier
     * @param activeOnly the active only
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default List<ClassView> listClasses(String majorId, boolean activeOnly,
            String trustedDepartmentId) {
        return listClasses(majorId, activeOnly);
    }
    /**
     * Performs the save department operation.
     * @param command the command
     * @return the operation result
     */
    default DepartmentView saveDepartment(edu.seu.vcampus.common.student.SaveDepartmentCommand command) {
        throw new UnsupportedOperationException("组织维护未启用");
    }
    /**
     * Performs the save major operation.
     * @param command the command
     * @return the operation result
     */
    default MajorView saveMajor(edu.seu.vcampus.common.student.SaveMajorCommand command) {
        throw new UnsupportedOperationException("组织维护未启用");
    }
    /**
     * Performs the save major operation.
     * @param command the command
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default MajorView saveMajor(edu.seu.vcampus.common.student.SaveMajorCommand command,
            String trustedDepartmentId) {
        return saveMajor(command);
    }
    /**
     * Performs the save class operation.
     * @param command the command
     * @return the operation result
     */
    default ClassView saveClass(edu.seu.vcampus.common.student.SaveClassCommand command) {
        throw new UnsupportedOperationException("组织维护未启用");
    }
    /**
     * Performs the save class operation.
     * @param command the command
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default ClassView saveClass(edu.seu.vcampus.common.student.SaveClassCommand command,
            String trustedDepartmentId) {
        return saveClass(command);
    }
}
