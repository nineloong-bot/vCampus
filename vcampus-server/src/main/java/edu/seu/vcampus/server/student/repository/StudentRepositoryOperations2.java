package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.common.student.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

/** Implements a focused group of StudentRepository persistence operations. */
abstract class StudentRepositoryOperations2 extends StudentRepositoryOperations3 {

    public StudentProfileData findProfileByStudentId(Connection connection, String studentId,
            String campusCardNumber) {
        return findProfile(connection, "s.studentId = ?", studentId, campusCardNumber);
    }

    public void applyApprovedProfile(Connection connection,
            StudentProfileApplicationView application, Instant updatedAt) {
        String sql = "UPDATE tblStudent SET namePinyin=?, formerName=?, politicalStatus=?, "
                + "ethnicity=?, maritalStatus=?, idDocumentType=?, idDocumentNumber=?, "
                + "idIssuedDate=?, birthDate=?, nativePlace=?, countryRegion=?, birthplace=?, "
                + "studentOriginPlace=?, householdRegistrationType=?, householdBeforeEnrollment=?, "
                + "householdAfterEnrollment=?, overseasChineseStatus=?, religion=?, leagueMember=?, "
                + "leagueJoinDate=?, partyMember=?, partyJoinDate=?, healthStatus=?, bloodType=?, "
                + "weightKg=?, heightCm=?, specialties=?, hobbies=?, onlyChild=?, email=?, phone=?, "
                + "attendanceMode=?, rowVersion=rowVersion+1, updatedAt=? WHERE studentId=? AND rowVersion=?";
        try (var statement = connection.prepareStatement(sql)) {
            int next = bindPersonal(statement, application.personal(), 1);
            statement.setString(next++, application.attendanceMode().name());
            statement.setTimestamp(next++, Timestamp.from(updatedAt));
            statement.setString(next++, application.studentId());
            statement.setLong(next, application.baseStudentVersion());
            if (statement.executeUpdate() != 1)
                throw new ConcurrentModificationException("Student profile version changed");
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot apply approved student profile", error);
        }
    }

    public void updateContact(Connection connection, String studentId, String email,
            String phone, long expectedVersion, Instant updatedAt) {
        update(connection, "email = ?, phone = ?", statement -> {
            statement.setString(1, email);
            statement.setString(2, phone);
        }, 3, studentId, expectedVersion, updatedAt);
    }

    public void updateStatus(Connection connection, String studentId, String status,
            long expectedVersion, Instant updatedAt) {
        update(connection, "studentStatus = ?", statement -> statement.setString(1, status),
                2, studentId, expectedVersion, updatedAt);
    }

    public void updateEnrollment(Connection connection, String studentId, String classId,
            String studentNumber, long expectedVersion, Instant updatedAt) {
        try {
            update(connection, "classId = ?, studentNumber = ?", statement -> {
                statement.setString(1, classId); statement.setString(2, studentNumber);
            }, 3, studentId, expectedVersion, updatedAt);
        } catch (OrganizationPersistenceException e) {
            if (e.getCause() instanceof SQLException sql
                    && (sql.getErrorCode() == 19 || sql.getMessage() != null
                        && sql.getMessage().contains("UNIQUE constraint failed")
                        && sql.getMessage().contains("studentNumber"))) {
                throw new edu.seu.vcampus.server.student.service.StudentAdmissionException(
                        "STUDENT_NUMBER_DUPLICATE", "学号 " + studentNumber + " 已被其他学生使用");
            }
            throw e;
        }
    }

    public void updateAcademicInfo(Connection connection, String studentId, String classId,
            String studentNumber, String status, long expectedVersion, Instant updatedAt) {
        try {
            update(connection, "classId = ?, studentNumber = ?, studentStatus = ?", statement -> {
                statement.setString(1, classId);
                statement.setString(2, studentNumber);
                statement.setString(3, status);
            }, 4, studentId, expectedVersion, updatedAt);
        } catch (OrganizationPersistenceException error) {
            if (error.getCause() instanceof SQLException sql
                    && (sql.getErrorCode() == 19 || sql.getMessage() != null
                    && sql.getMessage().contains("studentNumber"))) {
                throw new edu.seu.vcampus.server.student.service.StudentAdmissionException(
                        "STUDENT_NUMBER_DUPLICATE", "学号 " + studentNumber + " 已被其他学生使用");
            }
            throw error;
        }
    }
}
