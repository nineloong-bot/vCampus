package edu.seu.vcampus.server.course.repository;

import edu.seu.vcampus.server.persistence.PersistenceException;
import edu.seu.vcampus.server.course.domain.CourseConcurrentModificationException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Small JDBC conversion helpers shared only by the Access course repositories. */
final class CourseJdbc {
    private CourseJdbc() {
    }

    static PersistenceException failure(String action, SQLException error) {
        return new PersistenceException("Could not " + action, error);
    }

    static String id(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    static Timestamp timestamp(Instant value) {
        return Timestamp.from(value == null ? Instant.now() : value);
    }

    static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    static IllegalStateException missing(String entity, String id) {
        return new IllegalStateException(entity + " not found: " + id);
    }

    static CourseConcurrentModificationException stale(String entity, String id) {
        return new CourseConcurrentModificationException();
    }
}
