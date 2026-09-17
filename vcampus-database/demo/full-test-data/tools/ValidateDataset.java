import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
/** Read-only release-dataset validation for counts, credentials, and business scenarios. */
class ValidateDataset {
    private static final List<String> BANNED = List.of(
            "test", "测试", "demo", "演示", "fake", "sample", "bulk", "dummy", "example");
    private static int checks;
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("DATABASE COUNTS_TSV required");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + args[0] + ";immediatelyReleaseResources=true")) {
            connection.setReadOnly(true);
            Map<String, Long> expected = readCounts(Path.of(args[1]));
            validateCounts(connection, expected);
            validatePeople(connection);
            validateAcademics(connection);
            validateTransfersAndLibrary(connection);
            validateCommerce(connection);
            validateText(connection, expected.keySet().stream().toList());
        }
        System.out.println("PASS checks=" + checks);
    }
    private static Map<String, Long> readCounts(Path file) throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.isBlank()) {
                String[] parts = line.split("\\t");
                counts.put(parts[0], Long.parseLong(parts[1]));
            }
        }
        return counts;
    }
    private static void validateCounts(Connection connection, Map<String, Long> expected)
            throws Exception {
        for (var entry : expected.entrySet()) {
            require(count(connection, "SELECT COUNT(*) FROM " + entry.getKey()) == entry.getValue(),
                    entry.getKey() + " count mismatch");
        }
        for (String legacy : List.of("tblCurriculumPlan", "tblCurriculumCourse",
                "tblCurriculumPrerequisite", "tblCourseAttempt")) {
            require(!tableExists(connection, legacy), "legacy table remains: " + legacy);
        }
    }
    private static void validatePeople(Connection connection) throws Exception {
        require(count(connection, "SELECT COUNT(*) FROM tblDepartment") == 2, "department count");
        require(count(connection, "SELECT COUNT(*) FROM tblMajor") == 5, "major count");
        require(count(connection, "SELECT COUNT(*) FROM tblStudent") == 120, "student count");
        require(count(connection, "SELECT COUNT(*) FROM tblClass c LEFT JOIN tblStudent s "
                + "ON c.classId=s.classId GROUP BY c.classId HAVING COUNT(s.studentId)<5") == 0,
                "class without enough students");
        Map<String, String> admins = Map.of(
                "ADMIN", "SUPER_ADMIN", "STUDENT", "STUDENT_ADMIN",
                "COURSE", "COURSE_ADMIN", "LIBRARY", "LIBRARY_ADMIN",
                "SHOP", "SHOP_ADMIN", "USER", "USER_ADMIN",
                "CSADMIN", "COLLEGE_ADMIN", "MATHADMIN", "COLLEGE_ADMIN");
        for (var admin : admins.entrySet()) {
            require(count(connection, "SELECT COUNT(*) FROM tblUser WHERE loginId='"
                    + admin.getKey() + "' AND roleCode='" + admin.getValue() + "'") == 1,
                    "missing administrator " + admin.getKey());
        }
        require(count(connection, "SELECT COUNT(*) FROM tblStudentCollegeAdministrator") == 2,
                "college administrator bindings");
        require(count(connection, "SELECT COUNT(*) FROM tblUser WHERE roleCode='STUDENT' "
                + "AND mustChangePassword=FALSE") == 0, "student password-change flag");
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT loginId FROM tblUser WHERE roleCode='STUDENT'")) {
            boolean[][] found = new boolean[3][41];
            while (rows.next()) {
                String login = rows.getString(1);
                require(login.matches("213(24|25|26)00(0[1-9]|[1-3][0-9]|40)"),
                        "invalid card number " + login);
                int year = Integer.parseInt(login.substring(3, 5)) - 24;
                int serial = Integer.parseInt(login.substring(5));
                found[year][serial] = true;
            }
            for (boolean[] cohort : found) for (int serial = 1; serial <= 40; serial++)
                require(cohort[serial], "card range is incomplete");
        }
        verifyPasswords(connection);
    }
    private static void verifyPasswords(Connection connection) throws Exception {
        int verified = 0;
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT loginId,passwordHash,passwordSalt,passwordIterations FROM tblUser")) {
            while (rows.next()) {
                var spec = new PBEKeySpec("123456".toCharArray(),
                        Base64.getDecoder().decode(rows.getString(3)), rows.getInt(4), 256);
                byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
                require(MessageDigest.isEqual(actual, Base64.getDecoder().decode(rows.getString(2))),
                        "password mismatch " + rows.getString(1));
                verified++;
            }
        }
        require(verified == 152, "verified password count");
    }
    private static void validateAcademics(Connection connection) throws Exception {
        require(count(connection, "SELECT COUNT(*) FROM tblEnrollment") == 0, "enrollments not empty");
        require(count(connection, "SELECT COUNT(*) FROM tblEnrollmentAdjustment") == 0,
                "enrollment adjustments not empty");
        require(count(connection, "SELECT COUNT(*) FROM tblTerm WHERE termStatus='ACTIVE'") == 1,
                "active term count");
        require(count(connection, "SELECT COUNT(*) FROM tblCourseSelectionPhase "
                + "WHERE phaseStatus='OPEN'") == 1, "open phase count");
        require(count(connection, "SELECT COUNT(*) FROM (SELECT courseId FROM tblCourseOffering "
                + "GROUP BY courseId HAVING COUNT(*)<>2)") == 0, "two offerings per course");
        require(count(connection, "SELECT COUNT(*) FROM tblCourseOffering WHERE teacherUserId IS NULL "
                + "OR className IS NULL OR capacity<=0 OR enrolledCount<>0") == 0,
                "incomplete offering");
        require(count(connection, "SELECT COUNT(*) FROM (SELECT o.offeringId FROM tblCourseOffering o "
                + "LEFT JOIN tblCourseSchedule s ON o.offeringId=s.offeringId GROUP BY o.offeringId "
                + "HAVING COUNT(s.scheduleId)<>1)") == 0, "offering schedule count");
        require(count(connection, "SELECT COUNT(*) FROM (SELECT planId FROM tblTrainingPlanCourse "
                + "GROUP BY planId HAVING MIN(semester)<>1 OR MAX(semester)<>8)") == 0,
                "training plan semester coverage");
    }
    private static void validateTransfersAndLibrary(Connection connection) throws Exception {
        require(count(connection, "SELECT COUNT(*) FROM tblMajorTransferApplication "
                + "WHERE applicationStatus='SUBMITTED'") == 5, "transfer applications");
        require(count(connection, "SELECT COUNT(*) FROM tblMajorTransferApplication a "
                + "INNER JOIN tblMajorTransferOption o ON a.optionId=o.optionId "
                + "WHERE a.fromDepartmentName='数学学院' "
                + "AND o.targetDepartmentName='计算机科学与工程学院'") == 5,
                "transfer direction");
        require(count(connection, "SELECT COUNT(*) FROM tblMajorTransferBatchCollege "
                + "WHERE batchId='transfer-2026-autumn' AND targetDepartmentId='dept-cse' "
                + "AND collegeStatus='PROCESSING' AND rowVersion=0") == 1,
                "transfer college lifecycle");
        require(count(connection, "SELECT COUNT(*) FROM tblMajorTransferPreparedTransfer") == 0,
                "prepared transfers initially empty");
        require(count(connection, "SELECT COUNT(*) FROM tblBookLoan WHERE loanStatus='OVERDUE' "
                + "AND overdueFine>0 AND dueAt<#2026-09-16 12:00:00#") == 2, "overdue loans");
        require(count(connection, "SELECT COUNT(*) FROM tblBookLoan l INNER JOIN tblBookCopy c "
                + "ON l.copyId=c.copyId WHERE l.loanStatus IN ('ACTIVE','OVERDUE') "
                + "AND c.copyStatus<>'BORROWED'") == 0, "loan-copy status mismatch");
    }
    private static void validateCommerce(Connection connection) throws Exception {
        require(count(connection, "SELECT COUNT(*) FROM tblShop WHERE shopStatus='ACTIVE'") == 5,
                "active shops");
        require(count(connection, "SELECT COUNT(*) FROM tblShop WHERE shopStatus='SUSPENDED'") == 1,
                "suspended shops");
        require(count(connection, "SELECT COUNT(*) FROM tblSellerApplication WHERE "
                + "applicationStatus='APPROVED'") == 6, "approved seller applications");
        require(count(connection, "SELECT COUNT(*) FROM tblSellerApplication WHERE "
                + "applicationStatus='PENDING'") == 1, "pending seller application");
        require(count(connection, "SELECT COUNT(*) FROM tblSellerApplication WHERE "
                + "applicationStatus='REJECTED'") == 1, "rejected seller application");
        require(count(connection, "SELECT COUNT(*) FROM tblProduct") == 72, "products");
        require(count(connection, "SELECT COUNT(*) FROM (SELECT productId FROM tblProductSku "
                + "GROUP BY productId HAVING COUNT(*)>=2)") >= 12, "multi-SKU products");
        require(count(connection, "SELECT COUNT(*) FROM tblOrder") == 20, "orders");
        require(count(connection, "SELECT COUNT(*) FROM tblShopQualification") == 5,
                "qualifications");
        require(count(connection, "SELECT COUNT(*) FROM tblShopGovCase") == 5, "governance cases");
        require(count(connection, "SELECT COUNT(*) FROM tblShopGovAudit") >= 5, "governance audit");
        require(count(connection, "SELECT COUNT(*) FROM tblWalletAccount") == 10, "wallet accounts");
        require(count(connection, "SELECT COUNT(*) FROM tblProductSku WHERE reservedQuantity<0 "
                + "OR reservedQuantity>stockQuantity") == 0, "SKU stock bounds");
        require(count(connection, "SELECT COUNT(*) FROM tblOrderItem WHERE lineAmount<>unitPrice*quantity")
                == 0, "order line amount");
    }
    private static void validateText(Connection connection, List<String> tables) throws Exception {
        for (String table : tables) try (var statement = connection.createStatement();
                                         var rows = statement.executeQuery("SELECT * FROM " + table)) {
            int columns = rows.getMetaData().getColumnCount();
            while (rows.next()) for (int index = 1; index <= columns; index++) {
                Object value = rows.getObject(index);
                if (value instanceof String text) for (String marker : BANNED)
                    require(!text.toLowerCase(Locale.ROOT).contains(marker),
                            "banned marker in " + table + "." + rows.getMetaData().getColumnName(index));
            }
        }
    }
    private static long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getLong(1) : 0;
        }
    }
    private static boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet rows = connection.getMetaData().getTables(null, null, table,
                new String[]{"TABLE"})) { return rows.next(); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        checks++;
    }
}
