package edu.seu.vcampus.server.course.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC persistence dedicated to manual selection phases. */
final class AccessSelectionPhaseRepository {
    SelectionPhase insert(Connection connection, SelectionPhase phase) {
        Instant now = Instant.now();
        SelectionPhase saved = new SelectionPhase(CourseJdbc.id(phase.phaseId()), phase.termId(),
                phase.phaseType(), phase.displayTitle(), phase.phaseStatus(), 0, now, now);
        String sql = "INSERT INTO tblCourseSelectionPhase (phaseId, termId, phaseType, displayTitle, "
                + "phaseStatus, rowVersion, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, saved.phaseId());
            statement.setString(2, saved.termId());
            statement.setString(3, saved.phaseType());
            statement.setString(4, saved.displayTitle());
            statement.setString(5, saved.phaseStatus());
            statement.setLong(6, saved.rowVersion());
            statement.setTimestamp(7, CourseJdbc.timestamp(saved.createdAt()));
            statement.setTimestamp(8, CourseJdbc.timestamp(saved.updatedAt()));
            statement.executeUpdate();
            return saved;
        } catch (SQLException error) {
            throw CourseJdbc.failure("insert selection phase", error);
        }
    }

    SelectionPhase require(Connection connection, String phaseId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblCourseSelectionPhase WHERE phaseId=?")) {
            statement.setString(1, phaseId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return map(result);
            }
        } catch (SQLException error) {
            throw CourseJdbc.failure("read selection phase", error);
        }
        throw CourseJdbc.missing("Selection phase", phaseId);
    }

    List<SelectionPhase> findAll(Connection connection) {
        List<SelectionPhase> phases = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblCourseSelectionPhase ORDER BY createdAt DESC, phaseId");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) phases.add(map(result));
            return phases;
        } catch (SQLException error) {
            throw CourseJdbc.failure("list selection phases", error);
        }
    }

    Optional<SelectionPhase> findOpen(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblCourseSelectionPhase WHERE phaseStatus IN ('PREVIEW','OPEN') ORDER BY updatedAt DESC, phaseId");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? Optional.of(map(result)) : Optional.empty();
        } catch (SQLException error) {
            throw CourseJdbc.failure("find open selection phase", error);
        }
    }

    SelectionPhase update(Connection connection, SelectionPhase phase, long expectedVersion) {
        Instant now = Instant.now();
        String sql = "UPDATE tblCourseSelectionPhase SET displayTitle=?, phaseStatus=?, rowVersion=?, "
                + "updatedAt=? WHERE phaseId=? AND rowVersion=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, phase.displayTitle());
            statement.setString(2, phase.phaseStatus());
            statement.setLong(3, expectedVersion + 1);
            statement.setTimestamp(4, CourseJdbc.timestamp(now));
            statement.setString(5, phase.phaseId());
            statement.setLong(6, expectedVersion);
            if (statement.executeUpdate() != 1) throw CourseJdbc.stale("selection phase", phase.phaseId());
            return new SelectionPhase(phase.phaseId(), phase.termId(), phase.phaseType(),
                    phase.displayTitle(), phase.phaseStatus(), expectedVersion + 1,
                    phase.createdAt(), now);
        } catch (SQLException error) {
            throw CourseJdbc.failure("update selection phase", error);
        }
    }

    private static SelectionPhase map(ResultSet result) throws SQLException {
        return new SelectionPhase(result.getString("phaseId"), result.getString("termId"),
                result.getString("phaseType"), result.getString("displayTitle"),
                result.getString("phaseStatus"), result.getLong("rowVersion"),
                CourseJdbc.instant(result, "createdAt"), CourseJdbc.instant(result, "updatedAt"));
    }
}
