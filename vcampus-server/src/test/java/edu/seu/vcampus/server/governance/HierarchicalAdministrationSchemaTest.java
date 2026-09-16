package edu.seu.vcampus.server.governance;

import edu.seu.vcampus.server.bootstrap.DatabaseInitializer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the release schema and seed invariants for hierarchical administration. */
class HierarchicalAdministrationSchemaTest {
    @Test
    void seedsRolesAndKeepsCollegeBindingsCanonical() throws Exception {
        Path database = Path.of("target", "test-data", UUID.randomUUID() + ".accdb");
        Files.createDirectories(database.getParent());
        DatabaseInitializer.main(new String[] {
                projectDirectory("schema").toString(),
                projectDirectory("seed").toString(), database.toString()
        });

        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true")) {
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblRole WHERE roleCode IN "
                            + "('SUPER_ADMIN','STUDENT_ADMIN','COLLEGE_ADMIN',"
                            + "'COURSE_ADMIN','LIBRARY_ADMIN','SHOP_ADMIN','USER_ADMIN')"))
                    .isEqualTo(7);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblRole WHERE roleCode IN "
                            + "('MODULE_ADMIN','STUDENT_COLLEGE_ADMIN')"))
                    .isZero();
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblUser WHERE roleCode='ADMIN'"))
                    .isZero();
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblUser"))
                    .isZero();
            assertThat(tableExists(connection, "tblManagedModule")).isFalse();
            assertThat(tableExists(connection, "tblModuleAdministrator")).isFalse();
            assertThat(count(connection, """
                    SELECT COUNT(*) FROM tblStudentCollegeAdministrator a
                    LEFT JOIN tblDepartment d ON d.departmentId=a.departmentId
                    WHERE a.isActive=TRUE AND (d.departmentId IS NULL OR d.isActive=FALSE)
                    """))
                    .isZero();
            assertThat(count(connection, """
                    SELECT COUNT(*) FROM (
                        SELECT userId FROM tblStudentCollegeAdministrator
                        WHERE isActive=TRUE GROUP BY userId HAVING COUNT(*) > 1)
                    """))
                    .isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM tblStudentCollegeAdministrator"))
                    .isZero();
            assertThat(count(connection, """
                    SELECT COUNT(*) FROM tblRolePermission
                    WHERE roleCode='STUDENT_ADMIN'
                      AND permissionCode IN ('STUDENT_READ','STUDENT_WRITE')
                    """)).isZero();
            assertThat(count(connection, """
                    SELECT COUNT(*) FROM tblRolePermission
                    WHERE roleCode='STUDENT_ADMIN'
                      AND permissionCode IN ('STUDENT_COLLEGE_ADMIN_READ',
                                             'STUDENT_COLLEGE_ADMIN_WRITE')
                    """)).isEqualTo(2);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblMajorTransferApplication "
                            + "WHERE applicationStatus='PRO" + "POSED'"))
                    .isZero();
        }
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static boolean tableExists(Connection connection, String name) throws Exception {
        try (var tables = connection.getMetaData().getTables(null, null, name, null)) {
            return tables.next();
        }
    }

    private static Path projectDirectory(String child) {
        Path current = Path.of("").toAbsolutePath();
        Path databaseModule = current.getFileName().toString().equals("vcampus-server")
                ? current.resolve("..").resolve("vcampus-database")
                : current.resolve("vcampus-database");
        return databaseModule.resolve(child).normalize();
    }
}
