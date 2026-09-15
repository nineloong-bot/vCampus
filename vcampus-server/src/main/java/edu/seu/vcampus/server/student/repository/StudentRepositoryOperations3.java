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
abstract class StudentRepositoryOperations3 extends StudentRepositoryPersistenceSupport {

    public void updateAcademicProfile(Connection connection, String studentId, String classId,
            String studentNumber, String studentType, String status, Boolean enrolled,
            Boolean onCampus, String campus, String educationLevel, String trainingMode,
            Integer programLengthYears, String attendanceMode, String degreeName,
            String educationName, LocalDate expectedGraduationDate, LocalDate graduationDate,
            String studentSource, String graduateStudyMode, String counselorName,
            String counselorContact, long expectedVersion, Instant updatedAt) {
        String sql = "UPDATE tblStudent SET classId=?, studentNumber=?, studentType=?, "
                + "studentStatus=?, enrolled=?, onCampus=?, campus=?, educationLevel=?, "
                + "trainingMode=?, programLengthYears=?, attendanceMode=?, degreeName=?, "
                + "educationName=?, expectedGraduationDate=?, graduationDate=?, studentSource=?, "
                + "graduateStudyMode=?, counselorName=?, counselorContact=?, "
                + "rowVersion=rowVersion+1, updatedAt=? WHERE studentId=? AND rowVersion=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, classId);
            statement.setString(2, studentNumber);
            statement.setString(3, studentType);
            statement.setString(4, status);
            if (enrolled == null) statement.setNull(5, java.sql.Types.BOOLEAN);
            else statement.setBoolean(5, enrolled);
            if (onCampus == null) statement.setNull(6, java.sql.Types.BOOLEAN);
            else statement.setBoolean(6, onCampus);
            statement.setString(7, campus);
            statement.setString(8, educationLevel);
            statement.setString(9, trainingMode);
            setInteger(statement, 10, programLengthYears);
            statement.setString(11, attendanceMode);
            statement.setString(12, degreeName);
            statement.setString(13, educationName);
            setDate(statement, 14, expectedGraduationDate);
            setDate(statement, 15, graduationDate);
            statement.setString(16, studentSource);
            statement.setString(17, graduateStudyMode);
            statement.setString(18, counselorName);
            statement.setString(19, counselorContact);
            statement.setTimestamp(20, Timestamp.from(updatedAt));
            statement.setString(21, studentId);
            statement.setLong(22, expectedVersion);
            if (statement.executeUpdate() != 1) throw new ConcurrentModificationException("Student version changed");
        } catch (SQLException error) {
            if (error.getErrorCode() == 19 || error.getMessage() != null
                    && error.getMessage().contains("UNIQUE constraint failed")
                    && error.getMessage().contains("studentNumber")) {
                throw new edu.seu.vcampus.server.student.service.StudentAdmissionException(
                        "STUDENT_NUMBER_DUPLICATE", "学号 " + studentNumber + " 已被其他学生使用");
            }
            throw new OrganizationPersistenceException("Cannot update student academic profile", error);
        }
    }
}
