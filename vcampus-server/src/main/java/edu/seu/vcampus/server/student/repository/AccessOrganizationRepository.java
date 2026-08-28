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

/** Access JDBC implementation of the organization repository. */
public final class AccessOrganizationRepository implements OrganizationRepository {
    private final NumberSequenceRepository sequences = new NumberSequenceRepository();

    @Override
    public void insertDepartment(Connection connection, Department department) {
        executeInsert(connection,
                "INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) VALUES (?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, department.departmentId());
                    statement.setString(2, department.departmentCode());
                    statement.setString(3, department.departmentName());
                    statement.setBoolean(4, department.active());
                    statement.setLong(5, department.rowVersion());
                });
    }

    @Override
    public void insertMajor(Connection connection, Major major) {
        executeInsert(connection,
                "INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, isActive, rowVersion) VALUES (?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, major.majorId());
                    statement.setString(2, major.departmentId());
                    statement.setString(3, major.majorCode());
                    statement.setString(4, major.majorName());
                    statement.setBoolean(5, major.active());
                    statement.setLong(6, major.rowVersion());
                });
    }

    @Override
    public void insertClass(Connection connection, StudentClass studentClass) {
        executeInsert(connection,
                "INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, studentClass.classId());
                    statement.setString(2, studentClass.majorId());
                    statement.setString(3, studentClass.classCode());
                    statement.setString(4, studentClass.className());
                    statement.setInt(5, studentClass.enrollmentYear());
                    statement.setInt(6, studentClass.classNumber());
                    statement.setBoolean(7, studentClass.active());
                    statement.setLong(8, studentClass.rowVersion());
                });
        Major major = findMajor(connection, studentClass.majorId()).orElseThrow(() ->
                new OrganizationHierarchyException("Class major does not exist"));
        String year = String.format("%02d", studentClass.enrollmentYear() % 100);
        String sequenceKey = "STUDENT_NUMBER:" + major.majorCode() + ":" + year
                + ":" + studentClass.classNumber();
        sequences.getOrCreate(connection, sequenceKey, 99);
    }

    @Override
    public Optional<Department> findDepartment(Connection connection, String departmentId) {
        String sql = "SELECT departmentId, departmentCode, departmentName, isActive, rowVersion FROM tblDepartment WHERE departmentId = ?";
        return queryOne(connection, sql, departmentId, this::mapDepartment);
    }

    @Override
    public Optional<Major> findMajor(Connection connection, String majorId) {
        String sql = "SELECT majorId, departmentId, majorCode, majorName, isActive, rowVersion FROM tblMajor WHERE majorId = ?";
        return queryOne(connection, sql, majorId, this::mapMajor);
    }

    @Override
    public Optional<StudentClass> findClass(Connection connection, String classId) {
        String sql = "SELECT classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion FROM tblClass WHERE classId = ?";
        return queryOne(connection, sql, classId, this::mapClass);
    }

    @Override
    public List<Department> listDepartments(Connection connection, boolean activeOnly) {
        String sql = "SELECT departmentId, departmentCode, departmentName, isActive, rowVersion FROM tblDepartment"
                + (activeOnly ? " WHERE isActive = TRUE" : "") + " ORDER BY departmentCode";
        try (var statement = connection.prepareStatement(sql); var result = statement.executeQuery()) {
            List<Department> values = new ArrayList<>();
            while (result.next()) values.add(mapDepartment(result));
            return List.copyOf(values);
        } catch (SQLException error) { throw failure("Cannot list departments", error); }
    }

    @Override
    public List<Major> listActiveMajors(Connection connection, String departmentId) {
        return listMajors(connection, departmentId, true);
    }

    @Override
    public List<Major> listMajors(Connection connection, String departmentId, boolean activeOnly) {
        String sql = "SELECT majorId, departmentId, majorCode, majorName, isActive, rowVersion FROM tblMajor WHERE departmentId = ? AND isActive = TRUE ORDER BY majorCode";
        if (!activeOnly) sql = "SELECT majorId, departmentId, majorCode, majorName, isActive, rowVersion FROM tblMajor WHERE departmentId = ? ORDER BY majorCode";
        return queryMany(connection, sql, departmentId, this::mapMajor);
    }

    @Override
    public List<StudentClass> listActiveClasses(Connection connection, String majorId) {
        return listClasses(connection, majorId, true);
    }

    @Override
    public List<StudentClass> listClasses(Connection connection, String majorId, boolean activeOnly) {
        String sql = "SELECT classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion FROM tblClass WHERE majorId = ? AND isActive = TRUE ORDER BY enrollmentYear, classNumber";
        if (!activeOnly) sql = "SELECT classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion FROM tblClass WHERE majorId = ? ORDER BY enrollmentYear, classNumber";
        return queryMany(connection, sql, majorId, this::mapClass);
    }

    @Override
    public void updateDepartment(Connection connection, Department value, long expectedVersion) {
        if (!value.active() && count(connection,
                "SELECT COUNT(*) FROM tblMajor WHERE departmentId = ? AND isActive = TRUE", value.departmentId()) > 0)
            throw new OrganizationHierarchyException("Department has active majors");
        update(connection, "UPDATE tblDepartment SET departmentCode=?, departmentName=?, isActive=?, rowVersion=rowVersion+1 WHERE departmentId=? AND rowVersion=?",
                statement -> { statement.setString(1, value.departmentCode()); statement.setString(2, value.departmentName());
                    statement.setBoolean(3, value.active()); statement.setString(4, value.departmentId()); statement.setLong(5, expectedVersion); });
    }

    @Override
    public void updateMajor(Connection connection, Major value, long expectedVersion) {
        if (!value.active() && count(connection,
                "SELECT COUNT(*) FROM tblClass WHERE majorId = ? AND isActive = TRUE", value.majorId()) > 0)
            throw new OrganizationHierarchyException("Major has active classes");
        update(connection, "UPDATE tblMajor SET departmentId=?, majorCode=?, majorName=?, isActive=?, rowVersion=rowVersion+1 WHERE majorId=? AND rowVersion=?",
                statement -> { statement.setString(1, value.departmentId()); statement.setString(2, value.majorCode());
                    statement.setString(3, value.majorName()); statement.setBoolean(4, value.active());
                    statement.setString(5, value.majorId()); statement.setLong(6, expectedVersion); });
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

    private void deactivate(Connection connection, String table, String idColumn,
                            String id, long expectedVersion) {
        String sql = "UPDATE " + table + " SET isActive = FALSE, rowVersion = rowVersion + 1 WHERE "
                + idColumn + " = ? AND rowVersion = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setLong(2, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ConcurrentModificationException("Organization version changed");
            }
        } catch (SQLException error) {
            throw failure("Cannot deactivate organization", error);
        }
    }

    private int count(Connection connection, String sql, String id) {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException error) {
            throw failure("Cannot count organization children", error);
        }
    }

    private Department mapDepartment(ResultSet result) throws SQLException {
        return new Department(result.getString("departmentId"), result.getString("departmentCode"),
                result.getString("departmentName"), result.getBoolean("isActive"),
                result.getLong("rowVersion"));
    }

    private Major mapMajor(ResultSet result) throws SQLException {
        return new Major(result.getString("majorId"), result.getString("departmentId"),
                result.getString("majorCode"), result.getString("majorName"),
                result.getBoolean("isActive"), result.getLong("rowVersion"));
    }

    private StudentClass mapClass(ResultSet result) throws SQLException {
        return new StudentClass(result.getString("classId"), result.getString("majorId"),
                result.getString("classCode"), result.getString("className"),
                result.getInt("enrollmentYear"), result.getInt("classNumber"),
                result.getBoolean("isActive"), result.getLong("rowVersion"));
    }

    private <T> Optional<T> queryOne(Connection connection, String sql, String id,
                                     SqlMapper<T> mapper) {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapper.map(result)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw failure("Cannot query organization", error);
        }
    }

    private <T> List<T> queryMany(Connection connection, String sql, String id,
                                  SqlMapper<T> mapper) {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (var result = statement.executeQuery()) {
                List<T> values = new ArrayList<>();
                while (result.next()) {
                    values.add(mapper.map(result));
                }
                return List.copyOf(values);
            }
        } catch (SQLException error) {
            throw failure("Cannot list organization", error);
        }
    }

    private void executeInsert(Connection connection, String sql, SqlBinder binder) {
        try (var statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw failure("Cannot insert organization", error);
        }
    }

    private void update(Connection connection, String sql, SqlBinder binder) {
        try (var statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            if (statement.executeUpdate() != 1)
                throw new ConcurrentModificationException("Organization version changed");
        } catch (SQLException error) {
            throw failure("Cannot update organization", error);
        }
    }

    private static OrganizationPersistenceException failure(String message, SQLException error) {
        return new OrganizationPersistenceException(message, error);
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(java.sql.PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlMapper<T> {
        T map(ResultSet result) throws SQLException;
    }
}
