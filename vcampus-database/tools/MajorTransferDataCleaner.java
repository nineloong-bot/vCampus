import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Audits and transactionally removes invalid major-transfer applications. */
public final class MajorTransferDataCleaner {
    private MajorTransferDataCleaner() { }

    /** Runs audit or clean mode against an existing Access database. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !(args[1].equals("--audit") || args[1].equals("--clean"))) {
            throw new IllegalArgumentException("usage: MajorTransferDataCleaner <database> --audit|--clean");
        }
        Path database = Path.of(args[0]).toAbsolutePath();
        if (!Files.isRegularFile(database)) throw new IllegalArgumentException("database does not exist");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true")) {
            Set<String> invalid = invalidApplicationIds(connection);
            if (args[1].equals("--audit")) {
                System.out.println("invalidApplications=" + invalid.size());
                return;
            }
            connection.setAutoCommit(false);
            try {
                int deleted = deleteInvalid(connection, invalid);
                if (!invalidApplicationIds(connection).isEmpty()) throw new SQLException("audit remains invalid");
                connection.commit();
                System.out.println("deletedApplications=" + deleted);
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
    }

    static Set<String> invalidApplicationIds(Connection connection) throws SQLException {
        String sql = "SELECT DISTINCT a.applicationId FROM "
                + "((tblMajorTransferApplication a INNER JOIN tblStudent s ON a.studentId=s.studentId) "
                + "INNER JOIN tblClass c ON s.classId=c.classId) "
                + "INNER JOIN tblMajor fromMajor ON c.majorId=fromMajor.majorId "
                + "INNER JOIN tblMajorTransferBatch b ON a.batchId=b.batchId "
                + "INNER JOIN tblMajorTransferOption o ON a.optionId=o.optionId "
                + "WHERE s.studentType<>'UNDERGRADUATE' OR s.studentStatus<>'ACTIVE' "
                + "OR s.enrolled IS NULL OR s.enrolled<>TRUE OR s.onCampus IS NULL OR s.onCampus<>TRUE "
                + "OR (Year(b.applicationStart)-IIf(Month(b.applicationStart)<9,1,0)-c.enrollmentYear+1)<>1 "
                + "OR s.birthDate IS NULL "
                + "OR DateDiff('yyyy',s.birthDate,b.applicationStart)-IIf(Format(s.birthDate,'mmdd')>Format(b.applicationStart,'mmdd'),1,0) NOT BETWEEN 17 AND 20 "
                + "OR fromMajor.departmentId=o.targetDepartmentId";
        Set<String> ids = new LinkedHashSet<>();
        try (var statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) ids.add(rows.getString(1));
        }
        return ids;
    }

    static int deleteInvalid(Connection connection, Set<String> ids) throws SQLException {
        int deleted = 0;
        List<String> tables = List.of("tblMajorTransferAttachment", "tblMajorTransferReview",
                "tblMajorTransferExecution", "tblMajorTransferApplication");
        for (String id : ids) {
            for (String table : tables) {
                String sql = "DELETE FROM " + table + " WHERE applicationId=?";
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setString(1, id);
                    if (table.equals("tblMajorTransferApplication")) deleted += statement.executeUpdate();
                    else statement.executeUpdate();
                }
            }
        }
        return deleted;
    }
}
