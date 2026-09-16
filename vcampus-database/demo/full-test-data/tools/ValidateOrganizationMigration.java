import java.sql.DriverManager;
import java.util.Map;

/** Verifies the canonical college migration and campus-card cohort invariant. */
class ValidateOrganizationMigration {
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + args[0]
                + ";immediatelyReleaseResources=true")) {
            require(count(connection, "SELECT COUNT(*) FROM tblDepartment") == 9,
                    "expected nine canonical colleges");
            require(count(connection, "SELECT COUNT(*) FROM tblMajor") == 16,
                    "expected sixteen canonical majors");
            require(count(connection, "SELECT COUNT(*) FROM tblStudent") == 2745,
                    "legacy student accounts were not preserved");
            require(count(connection, "SELECT COUNT(*) FROM tblDepartment WHERE departmentId LIKE "
                    + "'00000000-0000-0000-0000-0000000001%'") == 0,
                    "legacy college remains");
            require(count(connection, "SELECT COUNT(*) FROM tblMajor WHERE majorId LIKE "
                    + "'00000000-0000-0000-0000-0000000001%'") == 0,
                    "legacy major remains");
            require(count(connection, "SELECT COUNT(*) FROM tblTrainingPlan WHERE "
                    + "planId='00000000-0000-0000-0000-000000000301'") == 0,
                    "legacy training plan remains");
            require(count(connection, "SELECT COUNT(*) FROM tblMajor WHERE majorId='bulk-major-12' "
                    + "AND departmentId='bulk-dept-09'") == 1,
                    "electronic information major is not in its dedicated college");
            verifyAdministrators(connection);
            verifyCampusCardCohorts(connection);
            require(count(connection, "SELECT COUNT(*) FROM tblStudentCollegeAdministrator a "
                    + "INNER JOIN (tblMajor m INNER JOIN tblTrainingPlan p ON m.majorId=p.majorId) "
                    + "ON a.departmentId=m.departmentId") >= 4,
                    "an administrator cannot see a canonical training plan");
            require(count(connection, "SELECT COUNT(*) FROM tblMajorTransferApplication") > 0,
                    "major-transfer applications are missing");
            require(count(connection, "SELECT COUNT(*) FROM tblMajorTransferApplication a "
                    + "LEFT JOIN tblDepartment d ON a.fromDepartmentId=d.departmentId "
                    + "WHERE d.departmentId IS NULL") == 0,
                    "major-transfer source college is orphaned");
            require(count(connection, "SELECT COUNT(*) FROM tblMajorTransferOption o "
                    + "LEFT JOIN tblDepartment d ON o.targetDepartmentId=d.departmentId "
                    + "WHERE d.departmentId IS NULL") == 0,
                    "major-transfer target college is orphaned");
            require(count(connection, "SELECT COUNT(*) FROM tblStudentCollegeAdministrator c "
                    + "INNER JOIN tblMajorTransferApplication a "
                    + "ON c.departmentId=a.fromDepartmentId") > 0,
                    "source-college administrators cannot see transfer applications");
            require(count(connection, "SELECT COUNT(*) FROM tblStudentCollegeAdministrator c "
                    + "INNER JOIN (tblMajorTransferOption o INNER JOIN tblMajorTransferApplication a "
                    + "ON o.optionId=a.optionId) ON c.departmentId=o.targetDepartmentId") > 0,
                    "target-college administrators cannot see transfer applications");
        }
        System.out.println("ORGANIZATION PASS checks=" + checks);
    }

    private static void verifyAdministrators(java.sql.Connection connection) throws Exception {
        Map<String, String> expected = Map.of(
                "00000000-0000-0000-0000-000000000202", "bulk-dept-01",
                "00000000-0000-0000-0000-000000000203", "bulk-dept-02",
                "00000000-0000-0000-0000-000000000208", "bulk-dept-09",
                "00000000-0000-0000-0000-000000000209", "bulk-dept-03");
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT userId,departmentId FROM tblStudentCollegeAdministrator")) {
            int matched = 0;
            while (result.next()) {
                String department = expected.get(result.getString("userId"));
                if (department != null) {
                    require(department.equals(result.getString("departmentId")),
                            "administrator college mismatch: " + result.getString("userId"));
                    matched++;
                }
            }
            require(matched == expected.size(), "canonical administrator binding is missing");
        }
    }

    private static void verifyCampusCardCohorts(java.sql.Connection connection) throws Exception {
        String sql = "SELECT u.loginId,c.enrollmentYear FROM (tblUser u INNER JOIN tblStudent s "
                + "ON u.userId=s.userId) INNER JOIN tblClass c ON s.classId=c.classId";
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            int students = 0;
            while (result.next()) {
                String login = result.getString("loginId");
                require(login != null && login.matches("2[123]3[0-9]{6}"),
                        "invalid student campus card: " + login);
                int cohort = 2000 + Integer.parseInt(login.substring(3, 5));
                require(cohort == result.getInt("enrollmentYear"),
                        "campus-card cohort mismatch: " + login);
                students++;
            }
            require(students == 2745, "student cohort verification was incomplete");
        }
    }

    private static long count(java.sql.Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        checks++;
    }
}
