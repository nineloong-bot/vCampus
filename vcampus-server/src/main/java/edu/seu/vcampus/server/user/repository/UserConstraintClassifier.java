package edu.seu.vcampus.server.user.repository;

import java.sql.Connection;
import java.sql.SQLException;

/** Package-local classifier for user-table integrity constraint failures. */
final class UserConstraintClassifier {
    private UserConstraintClassifier() {
    }

    static boolean isDuplicateLoginId(
            Connection connection, String normalizedLoginId, SQLException failure) {
        if (!isIntegrityViolation(failure)) {
            return false;
        }
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblUser WHERE loginId=?")) {
            statement.setString(1, normalizedLoginId);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getLong(1) > 0;
            }
        } catch (SQLException classificationFailure) {
            failure.addSuppressed(classificationFailure);
            return false;
        }
    }

    private static boolean isIntegrityViolation(SQLException failure) {
        for (SQLException current = failure;
             current != null; current = current.getNextException()) {
            if (current.getSQLState() != null && current.getSQLState().startsWith("23")) {
                return true;
            }
        }
        return false;
    }
}
