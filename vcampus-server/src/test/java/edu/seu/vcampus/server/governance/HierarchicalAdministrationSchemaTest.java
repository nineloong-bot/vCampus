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
    void seedsRolesAndCoversEveryEnabledModuleAndDepartment() throws Exception {
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
                    "SELECT COUNT(*) FROM tblUser WHERE roleCode='SUPER_ADMIN'"))
                    .isEqualTo(2);
            assertThat(count(connection, """
                    SELECT COUNT(*) FROM tblUser u
                    WHERE u.roleCode IN ('STUDENT_ADMIN','COURSE_ADMIN','LIBRARY_ADMIN',
                                         'SHOP_ADMIN','USER_ADMIN')
                    AND u.accountStatus='ACTIVE'
                    AND u.mustChangePassword=FALSE
                    """))
                    .isEqualTo(5);
            assertThat(tableExists(connection, "tblManagedModule")).isFalse();
            assertThat(tableExists(connection, "tblModuleAdministrator")).isFalse();
            assertThat(count(connection, """
                    SELECT COUNT(*) FROM tblDepartment d
                    WHERE d.isActive=TRUE AND NOT EXISTS (
                        SELECT * FROM tblStudentCollegeAdministrator a
                        WHERE a.departmentId=d.departmentId AND a.isActive=TRUE)
                    """))
                    .isZero();
            assertThat(count(connection, """
                    SELECT COUNT(*) FROM (
                        SELECT userId FROM tblStudentCollegeAdministrator
                        WHERE isActive=TRUE GROUP BY userId HAVING COUNT(*) > 1)
                    """))
                    .isZero();
            assertThat(count(connection, """
                    SELECT COUNT(*) FROM tblStudentCollegeAdministrator a
                    INNER JOIN tblUser u ON u.userId=a.userId
                    WHERE a.isActive=TRUE AND u.roleCode='COLLEGE_ADMIN'
                    AND u.accountStatus='ACTIVE' AND u.mustChangePassword=FALSE
                    """))
                    .isEqualTo(4);
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
