package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.StudentClass;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;

/** Implements a focused group of AccessOrganizationRepository persistence operations. */
abstract class AccessOrganizationRepositoryOperations2 extends AccessOrganizationRepositoryPersistenceSupport {

    @Override
    public void updateMajor(Connection connection, Major value, long expectedVersion) {
        if (!value.active() && count(connection,
                "SELECT COUNT(*) FROM tblClass WHERE majorId = ? AND isActive = TRUE", value.majorId()) > 0)
            throw new OrganizationHierarchyException("Major has active classes");
        update(connection, "UPDATE tblMajor SET departmentId=?, majorCode=?, majorName=?, grades=?, isActive=?, rowVersion=rowVersion+1 WHERE majorId=? AND rowVersion=?",
                statement -> { statement.setString(1, value.departmentId()); statement.setString(2, value.majorCode());
                    statement.setString(3, value.majorName()); statement.setString(4, value.grades());
                    statement.setBoolean(5, value.active());
                    statement.setString(6, value.majorId()); statement.setLong(7, expectedVersion); });
    }

    @Override
    public void updateClass(Connection connection, StudentClass value, long expectedVersion) {
        if (!value.active() && count(connection,
                "SELECT COUNT(*) FROM tblStudent WHERE classId = ? AND studentStatus = 'ACTIVE'", value.classId()) > 0)
            throw new OrganizationHierarchyException("Class has active students");
        update(connection, "UPDATE tblClass SET majorId=?, classCode=?, className=?, enrollmentYear=?, classNumber=?, isActive=?, rowVersion=rowVersion+1 WHERE classId=? AND rowVersion=?",
                statement -> { statement.setString(1, value.majorId()); statement.setString(2, value.classCode());
                    statement.setString(3, value.className()); statement.setInt(4, value.enrollmentYear());
                    statement.setInt(5, value.classNumber()); statement.setBoolean(6, value.active());
                    statement.setString(7, value.classId()); statement.setLong(8, expectedVersion); });
    }

    @Override
    public boolean classBelongsTo(Connection connection, String classId,
                                  String majorId, String departmentId) {
        String sql = "SELECT c.classId FROM tblClass c INNER JOIN tblMajor m ON c.majorId = m.majorId WHERE c.classId = ? AND c.majorId = ? AND m.departmentId = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, classId);
            statement.setString(2, majorId);
            statement.setString(3, departmentId);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException error) {
            throw failure("Cannot validate organization hierarchy", error);
        }
    }

    @Override
    public void deactivateDepartment(Connection connection, String departmentId, long expectedVersion) {
        if (count(connection, "SELECT COUNT(*) FROM tblMajor WHERE departmentId = ? AND isActive = TRUE", departmentId) > 0) {
            throw new OrganizationHierarchyException("Department has active majors");
        }
        deactivate(connection, "tblDepartment", "departmentId", departmentId, expectedVersion);
    }

    @Override
    public void deactivateMajor(Connection connection, String majorId, long expectedVersion) {
        if (count(connection, "SELECT COUNT(*) FROM tblClass WHERE majorId = ? AND isActive = TRUE", majorId) > 0) {
            throw new OrganizationHierarchyException("Major has active classes");
        }
        deactivate(connection, "tblMajor", "majorId", majorId, expectedVersion);
    }
}
