package edu.seu.vcampus.server.student.service;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import java.util.List;
/**
 * 院系、专业与行政班级组织架构只读查询接口。
 */
public interface StudentOrganizationQuery {
    List<DepartmentView> listDepartments(boolean activeOnly);
    List<MajorView> listMajors(String departmentId);
    List<ClassView> listClasses(String majorId);
    default List<MajorView> listMajors(String departmentId, boolean activeOnly) {
        return listMajors(departmentId);
    }
    default List<ClassView> listClasses(String majorId, boolean activeOnly) {
        return listClasses(majorId);
    }
    default DepartmentView saveDepartment(edu.seu.vcampus.common.student.SaveDepartmentCommand command) {
        throw new UnsupportedOperationException("组织维护未启用");
    }
    default MajorView saveMajor(edu.seu.vcampus.common.student.SaveMajorCommand command) {
        throw new UnsupportedOperationException("组织维护未启用");
    }
    default ClassView saveClass(edu.seu.vcampus.common.student.SaveClassCommand command) {
        throw new UnsupportedOperationException("组织维护未启用");
    }
}
