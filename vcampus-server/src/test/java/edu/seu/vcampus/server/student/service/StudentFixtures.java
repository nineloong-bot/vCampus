package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;

import java.sql.Connection;

final class StudentFixtures {
    static void insertOrganization(Connection connection, AccessOrganizationRepository organizations) {
        organizations.insertDepartment(connection,
                new Department("department-1", "CS", "计算机学院", true, 0));
        organizations.insertMajor(connection,
                new Major("major-1", "department-1", "090", "计算机科学", true, 0));
        organizations.insertClass(connection,
                new StudentClass("class-1", "major-1", "090-24-1", "计科24-1", 2024, 1, true, 0));
    }
    private StudentFixtures() { }
}
