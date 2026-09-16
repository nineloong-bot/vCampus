package edu.seu.vcampus.server.bootstrap.demo;

import edu.seu.vcampus.server.persistence.ConnectionProvider;

import java.sql.Timestamp;
import java.time.Instant;

final class IntegratedDemoStudentSeeder {
    private static final String DEPARTMENT_ID = "integrated-course-department";
    private static final String MAJOR_ID = "integrated-course-major";
    private static final String CLASS_ID = "integrated-course-class";

    private IntegratedDemoStudentSeeder() {
    }

    static void seed(ConnectionProvider connections, String userId, Instant now) throws Exception {
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                if (!exists(connection, "tblDepartment", "departmentId", DEPARTMENT_ID)) {
                    execute(connection, "INSERT INTO tblDepartment "
                            + "(departmentId,departmentCode,departmentName,isActive,rowVersion) "
                            + "VALUES ('" + DEPARTMENT_ID + "','ICD','计算机科学与工程学院',TRUE,0)");
                }
                if (!exists(connection, "tblMajor", "majorId", MAJOR_ID)) {
                    execute(connection, "INSERT INTO tblMajor "
                            + "(majorId,departmentId,majorCode,majorName,grades,isActive,rowVersion) "
                            + "VALUES ('" + MAJOR_ID + "','" + DEPARTMENT_ID
                            + "','802','计算机科学与技术','1,2,3,4',TRUE,0)");
                }
                if (!exists(connection, "tblClass", "classId", CLASS_ID)) {
                    execute(connection, "INSERT INTO tblClass "
                            + "(classId,majorId,classCode,className,enrollmentYear,classNumber,isActive,rowVersion) "
                            + "VALUES ('" + CLASS_ID + "','" + MAJOR_ID
                            + "','802-2024-01','计算机科学与技术2401班',2024,1,TRUE,0)");
                }
                if (!exists(connection, "tblStudent", "studentId", "demo-student")) {
                    insertStudent(connection, userId, now);
                }
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static boolean exists(java.sql.Connection connection, String table,
                                  String column, String value) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE " + column + "=?")) {
            statement.setString(1, value);
            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static void execute(java.sql.Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void insertStudent(java.sql.Connection connection, String userId, Instant now)
            throws Exception {
        try (var insert = connection.prepareStatement("""
                INSERT INTO tblStudent
                    (studentId, userId, studentNumber, studentType, studentName, gender,
                     email, phone, classId, enrollmentDate, studentStatus, rowVersion,
                     createdAt, updatedAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            Timestamp timestamp = Timestamp.from(now);
            insert.setString(1, "demo-student");
            insert.setString(2, userId);
            insert.setString(3, "80224999");
            insert.setString(4, "UNDERGRADUATE");
            insert.setString(5, "王晨");
            insert.setString(6, "男");
            insert.setString(7, "wang.chen@seu.edu.cn");
            insert.setString(8, "13824000999");
            insert.setString(9, CLASS_ID);
            insert.setTimestamp(10, timestamp);
            insert.setString(11, "ACTIVE");
            insert.setLong(12, 0);
            insert.setTimestamp(13, timestamp);
            insert.setTimestamp(14, timestamp);
            insert.executeUpdate();
        }
    }
}
