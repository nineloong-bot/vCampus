package edu.seu.vcampus.server.course.repository;

import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.server.persistence.PersistenceException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads catalog definitions from the canonical training-plan tables. */
public final class CurriculumCatalogCandidateRepository {
    /** One raw active training-plan course definition. */
    public record Definition(String planCourseId, String courseCode, String courseName,
                             BigDecimal credits, Integer totalHours, String courseNature,
                             String departmentId, String departmentName) { }

    /** Returns whether the canonical training-plan tables exist in this database. */
    public boolean supports(Connection connection) {
        try (var tables = connection.getMetaData().getTables(
                null, null, "tblTrainingPlanCourse", new String[]{"TABLE"})) {
            return tables.next();
        } catch (SQLException error) {
            throw new PersistenceException("Unable to inspect curriculum tables", error);
        }
    }

    /** Returns active and complete definitions; aggregation belongs to the service. */
    public List<Definition> findActive(Connection connection) {
        String sql = "SELECT pc.planCourseId,pc.courseCode,pc.courseName,pc.credits,"
                + "pc.totalHours,pc.courseType,pc.offeringDepartmentId,pc.offeringDepartmentName,"
                + "d.departmentId AS planDepartmentId,d.departmentName AS planDepartmentName "
                + "FROM ((tblTrainingPlanCourse pc INNER JOIN tblTrainingPlan tp ON pc.planId=tp.planId) "
                + "INNER JOIN tblMajor m ON tp.majorId=m.majorId) "
                + "INNER JOIN tblDepartment d ON m.departmentId=d.departmentId "
                + "WHERE pc.isActive=TRUE AND tp.isActive=TRUE";
        try (var statement = connection.prepareStatement(sql); var rows = statement.executeQuery()) {
            List<Definition> result = new ArrayList<>();
            while (rows.next()) {
                String ownerId = text(rows.getString("offeringDepartmentId"), rows.getString("planDepartmentId"));
                String ownerName = text(rows.getString("offeringDepartmentName"), rows.getString("planDepartmentName"));
                Number storedHours = (Number) rows.getObject("totalHours");
                Integer hours = storedHours == null ? null : storedHours.intValue();
                result.add(new Definition(rows.getString("planCourseId"), rows.getString("courseCode"),
                        rows.getString("courseName"), rows.getBigDecimal("credits"), hours,
                        courseNature(rows.getString("courseType")), ownerId, ownerName));
            }
            return List.copyOf(result);
        } catch (SQLException error) {
            throw new PersistenceException("Unable to read curriculum course definitions", error);
        }
    }

    private static String text(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static String courseNature(String storedValue) {
        return "CROSS".equals(storedValue)
                ? CourseType.CROSS_DISCIPLINARY.name() : CourseType.valueOf(storedValue).name();
    }
}
