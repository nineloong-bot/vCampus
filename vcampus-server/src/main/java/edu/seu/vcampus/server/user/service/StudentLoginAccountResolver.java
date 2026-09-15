package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.UserRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Resolves a login identifier to a user account, supporting both canonical loginId
 * (e.g. campus card number) and student number (学号) from the student registry.
 */
final class StudentLoginAccountResolver {
    private StudentLoginAccountResolver() { }

    static Optional<UserAccount> resolveAccount(
            Connection connection, UserRepository users, String loginIdentifier) {
        if (loginIdentifier == null || loginIdentifier.isBlank()) {
            return Optional.empty();
        }
        Optional<UserAccount> byLoginId = users.findByNormalizedLoginId(connection, loginIdentifier);
        if (byLoginId.isPresent()) {
            return byLoginId;
        }
        return findByStudentNumber(connection, users, loginIdentifier);
    }

    private static Optional<UserAccount> findByStudentNumber(
            Connection connection, UserRepository users, String studentNumber) {
        String sql = "SELECT userId FROM tblStudent WHERE studentNumber=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentNumber);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    String userId = result.getString(1);
                    return users.findById(connection, userId);
                }
            }
        } catch (SQLException ignored) {
            // tblStudent might not exist in isolated user schema environments
        }
        return Optional.empty();
    }
}
