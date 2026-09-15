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

/** Holds mapping and SQL support for {@link AccessOrganizationRepository}. */
abstract class AccessOrganizationRepositoryPersistenceSupport implements OrganizationRepository {
    protected final NumberSequenceRepository sequences = new NumberSequenceRepository();

    protected void deactivate(Connection connection, String table, String idColumn,
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

    protected int count(Connection connection, String sql, String id) {
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

    protected Department mapDepartment(ResultSet result) throws SQLException {
        return new Department(result.getString("departmentId"), result.getString("departmentCode"),
                result.getString("departmentName"), result.getBoolean("isActive"),
                result.getLong("rowVersion"));
    }

    protected Major mapMajor(ResultSet result) throws SQLException {
        return new Major(result.getString("majorId"), result.getString("departmentId"),
                result.getString("majorCode"), result.getString("majorName"),
                result.getString("grades"), result.getBoolean("isActive"), result.getLong("rowVersion"));
    }

    protected StudentClass mapClass(ResultSet result) throws SQLException {
        return new StudentClass(result.getString("classId"), result.getString("majorId"),
                result.getString("classCode"), result.getString("className"),
                result.getInt("enrollmentYear"), result.getInt("classNumber"),
                result.getBoolean("isActive"), result.getLong("rowVersion"));
    }

    protected <T> Optional<T> queryOne(Connection connection, String sql, String id,
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

    protected <T> List<T> queryMany(Connection connection, String sql, String id,
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

    protected void executeInsert(Connection connection, String sql, SqlBinder binder) {
        try (var statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw failure("Cannot insert organization", error);
        }
    }

    protected void update(Connection connection, String sql, SqlBinder binder) {
        try (var statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            if (statement.executeUpdate() != 1)
                throw new ConcurrentModificationException("Organization version changed");
        } catch (SQLException error) {
            throw failure("Cannot update organization", error);
        }
    }

    protected static OrganizationPersistenceException failure(String message, SQLException error) {
        return new OrganizationPersistenceException(message, error);
    }

    @FunctionalInterface
    protected interface SqlBinder {
        void bind(java.sql.PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    protected interface SqlMapper<T> {
        T map(ResultSet result) throws SQLException;
    }
}
