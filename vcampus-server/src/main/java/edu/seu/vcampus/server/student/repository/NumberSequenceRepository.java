package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.domain.NumberSequence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.Optional;

/** Reads and advances persistent numbering sequences inside caller-owned transactions. */
public final class NumberSequenceRepository {
    public NumberSequence require(Connection connection, String key) {
        return find(connection, key).orElseThrow(() ->
                new OrganizationPersistenceException("Missing number sequence " + key, null));
    }

    public NumberSequence getOrCreate(Connection connection, String key, int maxValue) {
        Optional<NumberSequence> existing = find(connection, key);
        if (existing.isPresent()) {
            return existing.get();
        }
        String sql = "INSERT INTO tblNumberSequence (sequenceKey, currentValue, maxValue, rowVersion, updatedAt) VALUES (?, 0, ?, 0, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setInt(2, maxValue);
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.executeUpdate();
            return new NumberSequence(key, 0, maxValue, 0);
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot create number sequence " + key, error);
        }
    }

    public NumberSequence advance(Connection connection, NumberSequence current) {
        NumberSequence next = current.incremented();
        String sql = "UPDATE tblNumberSequence SET currentValue = ?, rowVersion = ?, updatedAt = ? WHERE sequenceKey = ? AND rowVersion = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, next.currentValue());
            statement.setLong(2, next.rowVersion());
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.setString(4, current.sequenceKey());
            statement.setLong(5, current.rowVersion());
            if (statement.executeUpdate() != 1) {
                throw new ConcurrentModificationException(
                        "Number sequence changed: " + current.sequenceKey());
            }
            return next;
        } catch (SQLException error) {
            throw new OrganizationPersistenceException(
                    "Cannot advance number sequence " + current.sequenceKey(), error);
        }
    }

    private Optional<NumberSequence> find(Connection connection, String key) {
        String sql = "SELECT sequenceKey, currentValue, maxValue, rowVersion FROM tblNumberSequence WHERE sequenceKey = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new NumberSequence(result.getString("sequenceKey"),
                        result.getInt("currentValue"), result.getInt("maxValue"),
                        result.getLong("rowVersion")));
            }
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot read number sequence " + key, error);
        }
    }
}
