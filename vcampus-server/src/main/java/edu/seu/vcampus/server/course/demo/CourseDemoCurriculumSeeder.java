package edu.seu.vcampus.server.course.demo;

import edu.seu.vcampus.common.course.CourseView;
import edu.seu.vcampus.server.course.repository.AccessCurriculumRepository;
import edu.seu.vcampus.server.course.repository.CurriculumGraphValidator;
import edu.seu.vcampus.server.course.repository.CurriculumPlan;
import edu.seu.vcampus.server.course.repository.CurriculumCatalogCandidateRepository;
import edu.seu.vcampus.server.course.repository.CurriculumCourse;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.PersistenceException;
import edu.seu.vcampus.server.persistence.TransactionManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Installs canonical curriculum definitions before the demo course catalog is created. */
final class CourseDemoCurriculumSeeder {
    private static final String PLAN_ID = "curriculum-cs-2024";
    private static final String UNIT = "计算机科学与工程学院";
    private static final Map<String, Set<String>> EDGES = Map.of(
            "BJSL0031", Set.of("BJSL0021"), "BJSL0061", Set.of("BJSL0031"),
            "BJSL0071", Set.of("BJSL0051"), "BJSL0082", Set.of("BJSL0071"),
            "B09T0011", Set.of("BJSL0061"), "B09D0012", Set.of("BJSL0061"),
            "B09N0014", Set.of("BJSL0082"), "B09S0061", Set.of("B09D0012"));

    private CourseDemoCurriculumSeeder() { }

    /** Creates the published plan and its course definitions when absent. */
    static void installDefinitions(ConnectionProvider connections, List<CourseDemoDataset.Item> items) {
        new TransactionManager(connections).inTransaction(connection -> {
            if (!new CurriculumCatalogCandidateRepository().supports(connection)) return null;
            var repository = new AccessCurriculumRepository();
            if (repository.findPublishedPlan(connection, CourseDemoDataset.MAJOR,
                    CourseDemoDataset.COHORT).isPresent()) return null;
            repository.insertPlan(connection, new CurriculumPlan(PLAN_ID, CourseDemoDataset.MAJOR,
                    CourseDemoDataset.COHORT, "2024级计算机科学与技术本科专业培养方案", 1, "PUBLISHED"));
            for (CourseDemoDataset.Item item : items) insertDefinition(connection, item);
            return null;
        });
    }

    /** Links the canonical definitions to the catalog and installs prerequisite edges. */
    static void linkCatalogAndPrerequisites(ConnectionProvider connections,
                                            Map<String, CourseView> catalog,
                                            List<CourseDemoDataset.Item> items) {
        new TransactionManager(connections).inTransaction(connection -> {
            if (!new CurriculumCatalogCandidateRepository().supports(connection)) {
                installLegacy(connection, catalog, items);
                return null;
            }
            for (var entry : catalog.entrySet()) link(connection, entry.getKey(), entry.getValue().courseId());
            CurriculumGraphValidator.validate(EDGES);
            var repository = new AccessCurriculumRepository();
            for (var edge : EDGES.entrySet()) for (String required : edge.getValue()) {
                if (!prerequisiteExists(connection, "edge-" + edge.getKey() + "-" + required)) {
                    repository.insertPrerequisite(connection, "edge-" + edge.getKey() + "-" + required,
                            PLAN_ID, catalog.get(edge.getKey()).courseId(), catalog.get(required).courseId());
                }
            }
            return null;
        });
    }

    private static void installLegacy(Connection connection, Map<String, CourseView> catalog,
                                      List<CourseDemoDataset.Item> items) {
        var repository = new AccessCurriculumRepository();
        if (repository.findPublishedPlan(connection, CourseDemoDataset.MAJOR,
                CourseDemoDataset.COHORT).isPresent()) return;
        repository.insertPlan(connection, new CurriculumPlan(PLAN_ID, CourseDemoDataset.MAJOR,
                CourseDemoDataset.COHORT, "2024级计算机科学与技术本科专业培养方案", 1, "PUBLISHED"));
        for (CourseDemoDataset.Item item : items) {
            repository.insertCourse(connection, new CurriculumCourse("pc-" + item.code(), PLAN_ID,
                    catalog.get(item.code()).courseId(), item.year(), item.season(), item.nature(),
                    item.category(), UNIT));
        }
        CurriculumGraphValidator.validate(EDGES);
        for (var edge : EDGES.entrySet()) for (String required : edge.getValue()) {
            repository.insertPrerequisite(connection, "edge-" + edge.getKey() + "-" + required,
                    PLAN_ID, catalog.get(edge.getKey()).courseId(), catalog.get(required).courseId());
        }
    }

    private static void insertDefinition(Connection connection, CourseDemoDataset.Item item) {
        String sql = "INSERT INTO tblTrainingPlanCourse (planCourseId,planId,courseCode,courseName,credits,"
                + "totalHours,courseType,semester,courseNature,courseCategory,offeringUnit,isActive,"
                + "rowVersion,createdAt,updatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,TRUE,0,NOW(),NOW())";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, "pc-" + item.code()); statement.setString(2, PLAN_ID);
            statement.setString(3, item.code()); statement.setString(4, item.name());
            statement.setBigDecimal(5, item.credit());
            statement.setInt(6, Math.max(16, item.credit().intValue() * 16));
            statement.setString(7, item.nature()); statement.setInt(8, semester(item));
            statement.setString(9, item.nature()); statement.setString(10, item.category());
            statement.setString(11, UNIT); statement.executeUpdate();
        } catch (SQLException error) {
            throw new PersistenceException("Unable to seed curriculum definition", error);
        }
    }

    private static void link(Connection connection, String code, String courseId) {
        try (var statement = connection.prepareStatement(
                "UPDATE tblTrainingPlanCourse SET courseId=? WHERE planId=? AND courseCode=?")) {
            statement.setString(1, courseId); statement.setString(2, PLAN_ID);
            statement.setString(3, code); statement.executeUpdate();
        } catch (SQLException error) {
            throw new PersistenceException("Unable to link curriculum definition", error);
        }
    }

    private static int semester(CourseDemoDataset.Item item) {
        return (item.year() - 1) * 2 + item.season().curriculumTermOrdinal();
    }

    private static boolean prerequisiteExists(Connection connection, String id) {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblTrainingPlanPrerequisite WHERE prerequisiteId=?")) {
            statement.setString(1, id);
            try (var rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) > 0;
            }
        } catch (SQLException error) {
            throw new PersistenceException("Unable to inspect curriculum prerequisite", error);
        }
    }
}
