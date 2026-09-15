package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.common.student.*;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;

/** Implements a focused group of StudentProfileApplicationRepository persistence operations. */
abstract class StudentProfileApplicationRepositoryOperations1 extends StudentProfileApplicationRepositoryOperations2 {

    public Optional<StudentProfileApplicationView> findOpen(Connection connection, String studentId) {
        return queryOne(connection, "SELECT TOP 1 " + COLUMNS
                + " FROM tblStudentProfileApplication WHERE studentId=? AND "
                + "(applicationStatus='DRAFT' OR applicationStatus='PENDING') ORDER BY updatedAt DESC",
                studentId);
    }

    public Optional<StudentProfileApplicationView> findLatest(Connection connection, String studentId) {
        return queryOne(connection, "SELECT TOP 1 " + COLUMNS
                + " FROM tblStudentProfileApplication WHERE studentId=? ORDER BY updatedAt DESC",
                studentId);
    }

    public Optional<StudentProfileApplicationView> findById(Connection connection, String applicationId) {
        return queryOne(connection, "SELECT " + COLUMNS
                + " FROM tblStudentProfileApplication WHERE applicationId=?", applicationId);
    }

    public List<StudentProfileApplicationView> listPending(Connection connection) {
        String sql = "SELECT " + COLUMNS + " FROM tblStudentProfileApplication "
                + "WHERE applicationStatus='PENDING' ORDER BY submittedAt";
        return list(connection, sql, null);
    }

    /** Lists pending profile applications for students in the trusted department. */
    public List<StudentProfileApplicationView> listPending(Connection connection,
            String departmentId) {
        String sql = """
                SELECT a.* FROM ((tblStudentProfileApplication a
                INNER JOIN tblStudent s ON a.studentId=s.studentId)
                INNER JOIN tblClass c ON s.classId=c.classId)
                INNER JOIN tblMajor m ON c.majorId=m.majorId
                WHERE a.applicationStatus='PENDING' AND m.departmentId=?
                ORDER BY a.submittedAt
                """;
        return list(connection, sql, departmentId);
    }

    public void insertDraft(Connection connection, StudentProfileApplicationView value) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(43, "?"));
        String sql = "INSERT INTO tblStudentProfileApplication (" + COLUMNS + ") VALUES ("
                + placeholders + ")";
        try (var statement = connection.prepareStatement(sql)) {
            bindAll(statement, value);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw failure("Cannot insert profile draft", error);
        }
    }

    public void updatePersonal(Connection connection, String applicationId,
            StudentPersonalProfile value, long expectedVersion, Instant updatedAt) {
        String sql = "UPDATE tblStudentProfileApplication SET namePinyin=?, formerName=?, "
                + "politicalStatus=?, ethnicity=?, maritalStatus=?, idDocumentType=?, "
                + "idDocumentNumber=?, idIssuedDate=?, birthDate=?, nativePlace=?, countryRegion=?, "
                + "birthplace=?, studentOriginPlace=?, householdRegistrationType=?, "
                + "householdBeforeEnrollment=?, householdAfterEnrollment=?, overseasChineseStatus=?, "
                + "religion=?, leagueMember=?, leagueJoinDate=?, partyMember=?, partyJoinDate=?, "
                + "healthStatus=?, bloodType=?, weightKg=?, heightCm=?, specialties=?, hobbies=?, "
                + "onlyChild=?, email=?, phone=?, applicationVersion=applicationVersion+1, updatedAt=? "
                + "WHERE applicationId=? AND applicationStatus='DRAFT' AND applicationVersion=?";
        try (var statement = connection.prepareStatement(sql)) {
            int next = bindPersonal(statement, value, 1);
            statement.setTimestamp(next++, Timestamp.from(updatedAt));
            statement.setString(next++, applicationId);
            statement.setLong(next, expectedVersion);
            requireUpdated(statement.executeUpdate());
        } catch (SQLException error) {
            throw failure("Cannot update personal profile draft", error);
        }
    }

    public void updateAttendance(Connection connection, String applicationId,
            AttendanceMode value, long expectedVersion, Instant updatedAt) {
        String sql = "UPDATE tblStudentProfileApplication SET attendanceMode=?, "
                + "applicationVersion=applicationVersion+1, updatedAt=? WHERE applicationId=? "
                + "AND applicationStatus='DRAFT' AND applicationVersion=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, value.name());
            statement.setTimestamp(2, Timestamp.from(updatedAt));
            statement.setString(3, applicationId);
            statement.setLong(4, expectedVersion);
            requireUpdated(statement.executeUpdate());
        } catch (SQLException error) {
            throw failure("Cannot update attendance profile draft", error);
        }
    }

    public void submit(Connection connection, String applicationId, long expectedVersion,
            Instant submittedAt) {
        updateState(connection, "UPDATE tblStudentProfileApplication SET applicationStatus='PENDING', "
                + "submittedAt=?, applicationVersion=applicationVersion+1, updatedAt=? "
                + "WHERE applicationId=? AND applicationStatus='DRAFT' AND applicationVersion=?",
                statement -> {
                    statement.setTimestamp(1, Timestamp.from(submittedAt));
                    statement.setTimestamp(2, Timestamp.from(submittedAt));
                    statement.setString(3, applicationId);
                    statement.setLong(4, expectedVersion);
                });
    }

    public void withdraw(Connection connection, String applicationId, long expectedVersion,
            Instant updatedAt) {
        updateState(connection, "UPDATE tblStudentProfileApplication SET applicationStatus='DRAFT', "
                + "submittedAt=NULL, reviewerUserId=NULL, reviewedAt=NULL, reviewComment=NULL, "
                + "applicationVersion=applicationVersion+1, updatedAt=? "
                + "WHERE applicationId=? AND applicationStatus='PENDING' AND applicationVersion=?",
                statement -> {
                    statement.setTimestamp(1, Timestamp.from(updatedAt));
                    statement.setString(2, applicationId);
                    statement.setLong(3, expectedVersion);
                });
    }
}
