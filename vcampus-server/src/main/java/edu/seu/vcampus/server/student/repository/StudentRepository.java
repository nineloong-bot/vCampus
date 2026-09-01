package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.common.student.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

/** Stores student profiles inside caller-owned transactions. */
public final class StudentRepository {
    public void insert(Connection connection, Student student) {
        String sql = "INSERT INTO tblStudent (studentId, userId, studentNumber, studentType, studentName, gender, email, phone, classId, enrollmentDate, studentStatus, rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, student.studentId());
            statement.setString(2, student.userId());
            statement.setString(3, student.studentNumber());
            statement.setString(4, student.studentType().name());
            statement.setString(5, student.studentName());
            statement.setString(6, student.gender());
            statement.setString(7, student.email());
            statement.setString(8, student.phone());
            statement.setString(9, student.classId());
            statement.setDate(10, Date.valueOf(student.enrollmentDate()));
            statement.setString(11, student.status().name());
            statement.setLong(12, student.rowVersion());
            statement.setTimestamp(13, Timestamp.from(student.createdAt()));
            statement.setTimestamp(14, Timestamp.from(student.updatedAt()));
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot insert student", error);
        }
    }

    public Optional<Student> findById(Connection connection, String studentId) {
        return find(connection, "s.studentId = ?", studentId);
    }

    public Optional<Student> findByUserId(Connection connection, String userId) {
        return find(connection, "s.userId = ?", userId);
    }

    public List<Student> findAll(Connection connection) {
        String sql = "SELECT s.*, c.majorId FROM tblStudent s INNER JOIN tblClass c ON s.classId = c.classId ORDER BY s.studentNumber";
        try (var statement = connection.prepareStatement(sql); var result = statement.executeQuery()) {
            List<Student> values = new ArrayList<>();
            while (result.next()) values.add(map(result));
            return List.copyOf(values);
        } catch (SQLException error) { throw new OrganizationPersistenceException("Cannot list students", error); }
    }

    public StudentProfileData findProfileByUserId(Connection connection, String userId,
            String campusCardNumber) {
        return findProfile(connection, "s.userId = ?", userId, campusCardNumber);
    }

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
        update(connection, "classId = ?, studentNumber = ?", statement -> {
            statement.setString(1, classId); statement.setString(2, studentNumber);
        }, 3, studentId, expectedVersion, updatedAt);
    }

    private Optional<Student> find(Connection connection, String predicate, String value) {
        String sql = "SELECT s.*, c.majorId FROM tblStudent s INNER JOIN tblClass c ON s.classId = c.classId WHERE " + predicate;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot read student", error);
        }
    }

    private StudentProfileData findProfile(Connection connection, String predicate, String value,
            String campusCardNumber) {
        String sql = "SELECT s.*, c.majorId, c.className, c.enrollmentYear, m.majorName, "
                + "d.departmentName FROM ((tblStudent s INNER JOIN tblClass c ON s.classId=c.classId) "
                + "INNER JOIN tblMajor m ON c.majorId=m.majorId) INNER JOIN tblDepartment d "
                + "ON m.departmentId=d.departmentId WHERE " + predicate;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new edu.seu.vcampus.server.student.service.StudentNotFoundException();
                Student student = map(result);
                StudentView core = new StudentView(student.studentId(), student.userId(),
                        campusCardNumber, student.studentNumber(), student.studentType(),
                        student.studentName(), student.gender(), student.email(), student.phone(),
                        student.majorId(), student.classId(), student.enrollmentDate(),
                        student.status(), student.rowVersion());
                StudentPersonalProfile personal = mapPersonal(result);
                String mode = result.getString("attendanceMode");
                StudentAcademicProfile academic = new StudentAcademicProfile(
                        studentTypeLabel(student.studentType()), result.getBoolean("enrolled"),
                        result.getBoolean("onCampus"), statusLabel(student.status()),
                        result.getString("campus"), Integer.toString(result.getInt("enrollmentYear")),
                        result.getString("departmentName"), result.getString("majorName"),
                        result.getString("className"), result.getString("educationLevel"),
                        result.getString("trainingMode"), nullableInt(result, "programLengthYears"),
                        mode == null ? AttendanceMode.RESIDENT : AttendanceMode.valueOf(mode),
                        result.getString("degreeName"), result.getString("educationName"),
                        localDate(result, "expectedGraduationDate"), localDate(result, "graduationDate"),
                        result.getString("studentSource"), result.getString("graduateStudyMode"),
                        result.getString("counselorName"), result.getString("counselorContact"));
                return new StudentProfileData(core, personal, academic);
            }
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot read complete student profile", error);
        }
    }

    private void update(Connection connection, String assignments, Binder binder, int next,
            String studentId, long expectedVersion, Instant updatedAt) {
        String sql = "UPDATE tblStudent SET " + assignments + ", rowVersion = rowVersion + 1, updatedAt = ? WHERE studentId = ? AND rowVersion = ?";
        try (var statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.setTimestamp(next, Timestamp.from(updatedAt));
            statement.setString(next + 1, studentId);
            statement.setLong(next + 2, expectedVersion);
            if (statement.executeUpdate() != 1) throw new ConcurrentModificationException("Student version changed");
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot update student", error);
        }
    }

    private static Student map(ResultSet result) throws SQLException {
        return new Student(result.getString("studentId"), result.getString("userId"),
                result.getString("studentNumber"), edu.seu.vcampus.common.student.StudentType.valueOf(result.getString("studentType")),
                result.getString("studentName"), result.getString("gender"), result.getString("email"),
                result.getString("phone"), result.getString("majorId"), result.getString("classId"),
                result.getDate("enrollmentDate").toLocalDate(), edu.seu.vcampus.common.student.StudentStatus.valueOf(result.getString("studentStatus")),
                result.getLong("rowVersion"), result.getTimestamp("createdAt").toInstant(), result.getTimestamp("updatedAt").toInstant());
    }

    private static StudentPersonalProfile mapPersonal(ResultSet result) throws SQLException {
        return new StudentPersonalProfile(result.getString("namePinyin"),
                result.getString("formerName"), result.getString("politicalStatus"),
                result.getString("ethnicity"), result.getString("maritalStatus"),
                result.getString("idDocumentType"), result.getString("idDocumentNumber"),
                localDate(result, "idIssuedDate"), localDate(result, "birthDate"),
                result.getString("nativePlace"), result.getString("countryRegion"),
                result.getString("birthplace"), result.getString("studentOriginPlace"),
                result.getString("householdRegistrationType"),
                result.getString("householdBeforeEnrollment"),
                result.getString("householdAfterEnrollment"),
                result.getString("overseasChineseStatus"), result.getString("religion"),
                result.getBoolean("leagueMember"), localDate(result, "leagueJoinDate"),
                result.getBoolean("partyMember"), localDate(result, "partyJoinDate"),
                result.getString("healthStatus"), result.getString("bloodType"),
                nullableInt(result, "weightKg"), nullableInt(result, "heightCm"),
                result.getString("specialties"), result.getString("hobbies"),
                result.getBoolean("onlyChild"), result.getString("email"), result.getString("phone"));
    }

    private static int bindPersonal(java.sql.PreparedStatement statement,
            StudentPersonalProfile value, int next) throws SQLException {
        statement.setString(next++, value.namePinyin()); statement.setString(next++, value.formerName());
        statement.setString(next++, value.politicalStatus()); statement.setString(next++, value.ethnicity());
        statement.setString(next++, value.maritalStatus()); statement.setString(next++, value.idDocumentType());
        statement.setString(next++, value.idDocumentNumber()); setDate(statement, next++, value.idIssuedDate());
        setDate(statement, next++, value.birthDate()); statement.setString(next++, value.nativePlace());
        statement.setString(next++, value.countryRegion()); statement.setString(next++, value.birthplace());
        statement.setString(next++, value.studentOriginPlace()); statement.setString(next++, value.householdRegistrationType());
        statement.setString(next++, value.householdBeforeEnrollment()); statement.setString(next++, value.householdAfterEnrollment());
        statement.setString(next++, value.overseasChineseStatus()); statement.setString(next++, value.religion());
        statement.setBoolean(next++, value.leagueMember()); setDate(statement, next++, value.leagueJoinDate());
        statement.setBoolean(next++, value.partyMember()); setDate(statement, next++, value.partyJoinDate());
        statement.setString(next++, value.healthStatus()); statement.setString(next++, value.bloodType());
        setInteger(statement, next++, value.weightKg()); setInteger(statement, next++, value.heightCm());
        statement.setString(next++, value.specialties()); statement.setString(next++, value.hobbies());
        statement.setBoolean(next++, value.onlyChild()); statement.setString(next++, value.email());
        statement.setString(next++, value.phone()); return next;
    }

    private static void setDate(java.sql.PreparedStatement statement, int index, java.time.LocalDate value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.TIMESTAMP); else statement.setDate(index, Date.valueOf(value));
    }
    private static void setInteger(java.sql.PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.INTEGER); else statement.setInt(index, value);
    }
    private static java.time.LocalDate localDate(ResultSet result, String name) throws SQLException {
        Timestamp value = result.getTimestamp(name); return value == null ? null : value.toLocalDateTime().toLocalDate();
    }
    private static Integer nullableInt(ResultSet result, String name) throws SQLException {
        int value = result.getInt(name); return result.wasNull() ? null : value;
    }
    private static String studentTypeLabel(StudentType type) {
        return switch (type) { case UNDERGRADUATE -> "本科生"; case MASTER -> "硕士生"; case DOCTORATE -> "博士生"; };
    }
    private static String statusLabel(StudentStatus status) {
        return switch (status) { case ACTIVE -> "正常"; case SUSPENDED -> "休学"; case GRADUATED -> "已毕业"; case WITHDRAWN -> "已退学"; };
    }

    @FunctionalInterface private interface Binder { void bind(java.sql.PreparedStatement statement) throws SQLException; }
}
