package edu.seu.vcampus.server.course.demo;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Removes obsolete synthetic course rows before installing the realistic demo fixture. */
final class CourseDemoCleanup {
    private CourseDemoCleanup() { }

    static void removeLegacySyntheticFixtures(ConnectionProvider connections) {
        new TransactionManager(connections).inTransaction(connection -> {
            for (String code : List.of("MATH101", "CS201", "DEMO-RACE",
                    "DEMO-MATH101", "DEMO-CS201")) deleteCourse(connection, code);
            return null;
        });
    }

    private static void deleteCourse(Connection connection, String code) {
        try {
            String courseId = courseId(connection, code);
            if (courseId == null) return;
            for (String offeringId : offeringIds(connection, code)) {
                deleteAdjustmentRows(connection, offeringId);
                for (String table : List.of(
                        "tblEnrollment", "tblCourseSchedule", "tblCourseRetakeQuota"))
                    deleteBy(connection, table, "offeringId", offeringId);
                deleteBy(connection, "tblCourseOffering", "offeringId", offeringId);
            }
            if (tableExists(connection, "tblTrainingPlan")) {
                deletePrerequisites(connection, "tblTrainingPlanPrerequisite", courseId);
                deleteBy(connection, "tblTrainingPlanCourse", "courseCode", code);
            } else if (tableExists(connection, "tblCurriculumPlan")) {
                deletePrerequisites(connection, "tblCurriculumPrerequisite", courseId);
                deleteBy(connection, "tblCurriculumCourse", "courseId", courseId);
            }
            deleteBy(connection, "tblCourseAttempt", "courseId", courseId);
            deleteBy(connection, "tblCourse", "courseCode", code);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to replace legacy demo fixtures", error);
        }
    }

    private static String courseId(Connection connection, String code) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT courseId FROM tblCourse WHERE courseCode=?")) {
            statement.setString(1, code);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private static List<String> offeringIds(Connection connection, String code) throws Exception {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT o.offeringId FROM tblCourseOffering o INNER JOIN tblCourse c "
                + "ON o.courseId=c.courseId WHERE c.courseCode=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) ids.add(rows.getString(1));
            }
        }
        return ids;
    }

    private static void deleteAdjustmentRows(Connection connection, String offeringId)
            throws Exception {
        try (var statement = connection.prepareStatement("DELETE FROM tblEnrollmentAdjustment "
                + "WHERE sourceOfferingId=? OR targetOfferingId=?")) {
            statement.setString(1, offeringId);
            statement.setString(2, offeringId);
            statement.executeUpdate();
        }
    }

    private static void deletePrerequisites(Connection connection, String table, String courseId)
            throws Exception {
        try (var statement = connection.prepareStatement("DELETE FROM " + table
                + " WHERE courseId=? OR prerequisiteCourseId=?")) {
            statement.setString(1, courseId);
            statement.setString(2, courseId);
            statement.executeUpdate();
        }
    }

    private static void deleteBy(Connection connection, String table, String column, String value)
            throws Exception {
        try (var statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE " + column + "=?")) {
            statement.setString(1, value);
            statement.executeUpdate();
        }
    }

    private static boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(
                null, null, table, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
