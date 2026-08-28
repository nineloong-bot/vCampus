package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.SaveClassCommand;
import edu.seu.vcampus.common.student.SaveDepartmentCommand;
import edu.seu.vcampus.common.student.SaveMajorCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentOrganizationAdminServiceTest {
    @Test
    void createsUpdatesAndListsInactiveHierarchy() throws Exception {
        var database = new StudentAccessTestDatabase();
        var repository = new AccessOrganizationRepository();
        var service = new StudentOrganizationAdminService(database.transactions(),
                new StripedResourceLockManager(), repository);

        var department = service.saveDepartment(new SaveDepartmentCommand(
                null, "EE", "电子工程学院", true, 0));
        var major = service.saveMajor(new SaveMajorCommand(
                null, department.departmentId(), "080", "电子信息工程", true, 0));
        var studentClass = service.saveClass(new SaveClassCommand(
                null, major.majorId(), "080-24-1", "电子信息24-1", 2024, 1, true, 0));

        var renamed = service.saveClass(new SaveClassCommand(studentClass.classId(),
                major.majorId(), studentClass.code(), "电子信息实验班", 2024, 1, true,
                studentClass.rowVersion()));
        var inactive = service.saveClass(new SaveClassCommand(renamed.classId(),
                major.majorId(), renamed.code(), renamed.name(), 2024, 1, false,
                renamed.rowVersion()));

        assertThat(inactive.active()).isFalse();
        assertThat(service.listClasses(major.majorId(), false))
                .extracting("name").containsExactly("电子信息实验班");
        assertThat(service.listClasses(major.majorId(), true)).isEmpty();
    }

    @Test
    void rejectsStaleOrganizationUpdate() throws Exception {
        var database = new StudentAccessTestDatabase();
        var repository = new AccessOrganizationRepository();
        var service = new StudentOrganizationAdminService(database.transactions(),
                new StripedResourceLockManager(), repository);
        var created = service.saveDepartment(new SaveDepartmentCommand(
                null, "CS", "计算机学院", true, 0));
        service.saveDepartment(new SaveDepartmentCommand(created.departmentId(), "CS",
                "计算机科学与工程学院", true, created.rowVersion()));

        assertThatThrownBy(() -> service.saveDepartment(new SaveDepartmentCommand(
                created.departmentId(), "CS", "过期修改", true, created.rowVersion())))
                .isInstanceOf(java.util.ConcurrentModificationException.class);
    }

    @Test
    void rejectsDeactivatingDepartmentWithActiveMajor() throws Exception {
        var database = new StudentAccessTestDatabase();
        var service = new StudentOrganizationAdminService(database.transactions(),
                new StripedResourceLockManager(), new AccessOrganizationRepository());
        var department = service.saveDepartment(new SaveDepartmentCommand(
                null, "CS", "计算机学院", true, 0));
        service.saveMajor(new SaveMajorCommand(null, department.departmentId(),
                "090", "计算机科学", true, 0));

        assertThatThrownBy(() -> service.saveDepartment(new SaveDepartmentCommand(
                department.departmentId(), department.code(), department.name(), false,
                department.rowVersion())))
                .isInstanceOf(edu.seu.vcampus.server.student.repository.OrganizationHierarchyException.class);
    }
}
