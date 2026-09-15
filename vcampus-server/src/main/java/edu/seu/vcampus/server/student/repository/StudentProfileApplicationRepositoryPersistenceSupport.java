package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.common.student.*;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;

/** Holds mapping and SQL support for {@link StudentProfileApplicationRepository}. */
abstract class StudentProfileApplicationRepositoryPersistenceSupport {
    protected static final String COLUMNS = "applicationId, studentId, applicationStatus, "
            + "namePinyin, formerName, politicalStatus, ethnicity, maritalStatus, "
            + "idDocumentType, idDocumentNumber, idIssuedDate, birthDate, nativePlace, "
            + "countryRegion, birthplace, studentOriginPlace, householdRegistrationType, "
            + "householdBeforeEnrollment, householdAfterEnrollment, overseasChineseStatus, "
            + "religion, leagueMember, leagueJoinDate, partyMember, partyJoinDate, healthStatus, "
            + "bloodType, weightKg, heightCm, specialties, hobbies, onlyChild, email, phone, "
            + "attendanceMode, baseStudentVersion, applicationVersion, submittedAt, reviewerUserId, "
            + "reviewedAt, reviewComment, createdAt, updatedAt";

    protected List<StudentProfileApplicationView> list(Connection connection, String sql,
            String departmentId) {
        try (var statement = connection.prepareStatement(sql)) {
            if (departmentId != null) statement.setString(1, departmentId);
            try (var result = statement.executeQuery()) {
                List<StudentProfileApplicationView> values = new ArrayList<>();
                while (result.next()) values.add(map(result));
                return List.copyOf(values);
            }
        } catch (SQLException error) {
            throw failure("Cannot list pending profile applications", error);
        }
    }

    protected void review(Connection connection, String applicationId, String reviewerUserId,
            String reviewComment, Instant reviewedAt, String status) {
        String sql = "UPDATE tblStudentProfileApplication SET applicationStatus=?, reviewerUserId=?, "
                + "reviewedAt=?, reviewComment=?, applicationVersion=applicationVersion+1, updatedAt=? "
                + "WHERE applicationId=? AND applicationStatus='PENDING'";
        updateState(connection, sql, statement -> {
            statement.setString(1, status);
            statement.setString(2, reviewerUserId);
            statement.setTimestamp(3, Timestamp.from(reviewedAt));
            statement.setString(4, reviewComment);
            statement.setTimestamp(5, Timestamp.from(reviewedAt));
            statement.setString(6, applicationId);
        });
    }

    protected Optional<StudentProfileApplicationView> queryOne(Connection connection, String sql,
            String id) {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw failure("Cannot read profile application", error);
        }
    }

    protected static StudentProfileApplicationView map(ResultSet result) throws SQLException {
        StudentPersonalProfile personal = new StudentPersonalProfile(
                result.getString("namePinyin"), result.getString("formerName"),
                result.getString("politicalStatus"), result.getString("ethnicity"),
                result.getString("maritalStatus"), result.getString("idDocumentType"),
                result.getString("idDocumentNumber"), localDate(result, "idIssuedDate"),
                localDate(result, "birthDate"), result.getString("nativePlace"),
                result.getString("countryRegion"), result.getString("birthplace"),
                result.getString("studentOriginPlace"), result.getString("householdRegistrationType"),
                result.getString("householdBeforeEnrollment"), result.getString("householdAfterEnrollment"),
                result.getString("overseasChineseStatus"), result.getString("religion"),
                result.getBoolean("leagueMember"), localDate(result, "leagueJoinDate"),
                result.getBoolean("partyMember"), localDate(result, "partyJoinDate"),
                result.getString("healthStatus"), result.getString("bloodType"),
                nullableInt(result, "weightKg"), nullableInt(result, "heightCm"),
                result.getString("specialties"), result.getString("hobbies"),
                result.getBoolean("onlyChild"), result.getString("email"), result.getString("phone"));
        return new StudentProfileApplicationView(result.getString("applicationId"),
                result.getString("studentId"),
                StudentProfileApplicationStatus.valueOf(result.getString("applicationStatus")),
                personal, AttendanceMode.valueOf(result.getString("attendanceMode")),
                result.getLong("baseStudentVersion"), result.getLong("applicationVersion"),
                instant(result, "submittedAt"), result.getString("reviewerUserId"),
                instant(result, "reviewedAt"), result.getString("reviewComment"),
                instant(result, "createdAt"), instant(result, "updatedAt"));
    }

    protected static void bindAll(PreparedStatement statement, StudentProfileApplicationView value)
            throws SQLException {
        int next = 1;
        statement.setString(next++, value.applicationId());
        statement.setString(next++, value.studentId());
        statement.setString(next++, value.status().name());
        next = bindPersonal(statement, value.personal(), next);
        statement.setString(next++, value.attendanceMode().name());
        statement.setLong(next++, value.baseStudentVersion());
        statement.setLong(next++, value.applicationVersion());
        setInstant(statement, next++, value.submittedAt());
        statement.setString(next++, value.reviewerUserId());
        setInstant(statement, next++, value.reviewedAt());
        statement.setString(next++, value.reviewComment());
        setInstant(statement, next++, value.createdAt());
        setInstant(statement, next, value.updatedAt());
    }

    protected static int bindPersonal(PreparedStatement statement, StudentPersonalProfile value,
            int next) throws SQLException {
        statement.setString(next++, value.namePinyin());
        statement.setString(next++, value.formerName());
        statement.setString(next++, value.politicalStatus());
        statement.setString(next++, value.ethnicity());
        statement.setString(next++, value.maritalStatus());
        statement.setString(next++, value.idDocumentType());
        statement.setString(next++, value.idDocumentNumber());
        setDate(statement, next++, value.idIssuedDate());
        setDate(statement, next++, value.birthDate());
        statement.setString(next++, value.nativePlace());
        statement.setString(next++, value.countryRegion());
        statement.setString(next++, value.birthplace());
        statement.setString(next++, value.studentOriginPlace());
        statement.setString(next++, value.householdRegistrationType());
        statement.setString(next++, value.householdBeforeEnrollment());
        statement.setString(next++, value.householdAfterEnrollment());
        statement.setString(next++, value.overseasChineseStatus());
        statement.setString(next++, value.religion());
        statement.setBoolean(next++, value.leagueMember());
        setDate(statement, next++, value.leagueJoinDate());
        statement.setBoolean(next++, value.partyMember());
        setDate(statement, next++, value.partyJoinDate());
        statement.setString(next++, value.healthStatus());
        statement.setString(next++, value.bloodType());
        setInteger(statement, next++, value.weightKg());
        setInteger(statement, next++, value.heightCm());
        statement.setString(next++, value.specialties());
        statement.setString(next++, value.hobbies());
        statement.setBoolean(next++, value.onlyChild());
        statement.setString(next++, value.email());
        statement.setString(next++, value.phone());
        return next;
    }

    protected void updateState(Connection connection, String sql, SqlBinder binder) {
        try (var statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            requireUpdated(statement.executeUpdate());
        } catch (SQLException error) {
            throw failure("Cannot update profile application state", error);
        }
    }

    protected static void requireUpdated(int count) {
        if (count != 1) throw new ConcurrentModificationException("Profile application changed");
    }

    protected static LocalDate localDate(ResultSet result, String name) throws SQLException {
        Timestamp value = result.getTimestamp(name);
        return value == null ? null : value.toLocalDateTime().toLocalDate();
    }

    protected static Instant instant(ResultSet result, String name) throws SQLException {
        Timestamp value = result.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    protected static Integer nullableInt(ResultSet result, String name) throws SQLException {
        int value = result.getInt(name);
        return result.wasNull() ? null : value;
    }

    protected static void setDate(PreparedStatement statement, int index, LocalDate value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.TIMESTAMP);
        else statement.setDate(index, Date.valueOf(value));
    }

    protected static void setInstant(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.TIMESTAMP);
        else statement.setTimestamp(index, Timestamp.from(value));
    }

    protected static void setInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER);
        else statement.setInt(index, value);
    }

    protected static OrganizationPersistenceException failure(String message, SQLException error) {
        return new OrganizationPersistenceException(message, error);
    }

    @FunctionalInterface
    protected interface SqlBinder { void bind(PreparedStatement statement) throws SQLException; }
}
