package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.student.service.AutumnTermCalendarPort;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

/** Access-backed implementation of the student module's autumn-term calendar port. */
public final class AccessAutumnTermCalendar implements AutumnTermCalendarPort {
    @Override
    public Optional<LocalDate> findAutumnStartDate(Connection connection, int enrollmentYear) {
        String sql = "SELECT startDate FROM tblTerm WHERE academicYearStart=? AND season='AUTUMN'";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, enrollmentYear);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(result.getDate(1).toLocalDate());
            }
        } catch (SQLException error) {
            throw new OrganizationPersistenceException("Cannot read autumn term calendar", error);
        }
    }
}
