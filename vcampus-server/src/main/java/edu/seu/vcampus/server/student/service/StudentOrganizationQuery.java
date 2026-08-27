package edu.seu.vcampus.server.student.service;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import java.util.List;
public interface StudentOrganizationQuery {
    List<DepartmentView> listDepartments(boolean activeOnly);
    List<MajorView> listMajors(String departmentId);
    List<ClassView> listClasses(String majorId);
}
