package edu.seu.vcampus.server.course.demo;

import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.server.course.repository.*;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.course.service.CourseService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;

/** Realistic demo fixture transcribed from the supplied 2024 Computer Science curriculum. */
public final class CourseDemoDataset {
    public static final String MAJOR = "080901";
    public static final int COHORT = 2024;
    private static final String UNIT = "计算机科学与工程学院";

    private static final List<Item> ITEMS = List.of(
            item("BJSL0011", "计算机大类新生研讨", "1", 1, AcademicSeason.AUTUMN, "REQUIRED", "导论类"),
            item("BJSL0021", "程序设计基础及语言I", "4", 1, AcademicSeason.AUTUMN, "REQUIRED", "大类学科基础课"),
            item("B07M1051", "工科数学分析I", "5", 1, AcademicSeason.AUTUMN, "REQUIRED", "自然科学类"),
            item("B07M2041", "线性代数", "3.5", 1, AcademicSeason.AUTUMN, "REQUIRED", "自然科学类"),
            item("BJSL0031", "程序设计基础及语言II", "2.5", 1, AcademicSeason.SPRING, "REQUIRED", "大类学科基础课"),
            item("BJSL0040", "离散数学", "4", 1, AcademicSeason.SPRING, "REQUIRED", "大类学科基础课"),
            item("BJSL0051", "数字逻辑电路", "3", 1, AcademicSeason.SPRING, "REQUIRED", "大类学科基础课"),
            item("B09L0010", "劳动教育与实践", "1", 2, AcademicSeason.SUMMER, "REQUIRED", "实践环节"),
            item("BJSL0090", "语言课程设计", "2", 2, AcademicSeason.SUMMER, "REQUIRED", "实践环节"),
            item("BJSL0061", "数据结构", "4", 2, AcademicSeason.AUTUMN, "REQUIRED", "大类学科基础课"),
            item("BJSL0071", "计算机组成原理", "4", 2, AcademicSeason.AUTUMN, "REQUIRED", "大类学科基础课"),
            item("B07M3010", "概率论与数理统计", "3", 2, AcademicSeason.AUTUMN, "REQUIRED", "自然科学类"),
            item("B71S1021", "Python编程(研讨)", "2", 2, AcademicSeason.AUTUMN, "ELECTIVE", "专业方向课"),
            item("B71S1170", "软件建模与UML", "2", 2, AcademicSeason.AUTUMN, "ELECTIVE", "专业方向课"),
            item("BJSL0082", "操作系统", "4", 2, AcademicSeason.SPRING, "REQUIRED", "大类学科基础课"),
            item("B09T0011", "算法设计与分析", "3", 2, AcademicSeason.SPRING, "REQUIRED", "专业主干课"),
            item("B09A0010", "人工智能概论", "3", 2, AcademicSeason.SPRING, "REQUIRED", "专业主干课"),
            item("B09S1031", "Java程序设计", "2", 2, AcademicSeason.SPRING, "ELECTIVE", "专业方向课"),
            item("B09P0040", "专业技能实训(校企)", "2", 3, AcademicSeason.SUMMER, "REQUIRED", "实践环节"),
            item("B09D0012", "数据库原理", "3", 3, AcademicSeason.AUTUMN, "REQUIRED", "专业主干课"),
            item("B09G0011", "数字图像处理", "3", 3, AcademicSeason.AUTUMN, "REQUIRED", "专业主干课"),
            item("B09N0014", "计算机网络", "3", 3, AcademicSeason.AUTUMN, "REQUIRED", "专业主干课"),
            item("B71S0032", "编译原理", "4", 3, AcademicSeason.AUTUMN, "REQUIRED", "专业主干课"),
            item("B0203750", "智能汽车与自动驾驶（全英文）（研讨）", "2", 3, AcademicSeason.AUTUMN, "ELECTIVE", "专业方向课"),
            item("B0493021", "通信电子线路基础（研讨）", "2", 3, AcademicSeason.AUTUMN, "ELECTIVE", "专业方向课"),
            item("B09A1111", "机器学习(研讨)", "2", 3, AcademicSeason.AUTUMN, "ELECTIVE", "专业方向课"),
            item("B09A1131", "模式识别(全英文、研讨)", "2", 3, AcademicSeason.AUTUMN, "ELECTIVE", "专业方向课"),
            item("B09D1021", "大数据处理(研讨)", "2", 3, AcademicSeason.AUTUMN, "ELECTIVE", "专业方向课"),
            item("B09G1031", "计算机图形学(研讨)", "2", 3, AcademicSeason.AUTUMN, "ELECTIVE", "专业方向课"),
            item("B09S0061", "软件工程", "3", 3, AcademicSeason.SPRING, "REQUIRED", "专业主干课")
    );

    private CourseDemoDataset() {}

    static void install(ConnectionProvider connections, CourseService service, TermView term) {
        install(connections, service, term, "student-demo-1", "student-demo-2", "teacher-user");
    }

    /** Installs the shared fixture for a runtime's own passed-course and retake students. */
    public static void install(ConnectionProvider connections, CourseService service, TermView term,
                               String passedStudentId, String retakeStudentId, String teacherId) {
        removeLegacySyntheticFixtures(connections);
        Map<String, CourseView> catalog = new HashMap<>();
        for (CourseView course : service.searchCatalog(new CourseCatalogQuery("", null, 0, 100)).items()) {
            catalog.put(course.courseCode(), course);
        }
        for (Item item : ITEMS) {
            catalog.computeIfAbsent(item.code(), ignored -> service.createCourse(new CreateCourseCommand(
                    item.code(), item.name(), item.credit(), Math.max(16, item.credit().intValue() * 16),
                    "来源：2024级计算机科学与技术本科专业培养方案", true)));
        }
        installCurriculum(connections, catalog);
        installCurrentOfferings(service, term, catalog, teacherId);
        installRetakeHistory(service, term, passedStudentId, retakeStudentId);
    }

    private static void installCurriculum(ConnectionProvider connections, Map<String, CourseView> catalog) {
        new TransactionManager(connections).inTransaction(connection -> {
            CurriculumRepository repository = new AccessCurriculumRepository();
            if (repository.findPublishedPlan(connection, MAJOR, COHORT).isPresent()) return null;
            String planId = "curriculum-cs-2024";
            repository.insertPlan(connection, new CurriculumPlan(planId, MAJOR, COHORT,
                    "2024级计算机科学与技术本科专业培养方案", 1, "PUBLISHED"));
            for (Item item : ITEMS) {
                repository.insertCourse(connection, new CurriculumCourse("pc-" + item.code(), planId,
                        catalog.get(item.code()).courseId(), item.year(), item.season(), item.nature(),
                        item.category(), UNIT));
            }
            Map<String, Set<String>> edges = Map.of(
                    "BJSL0031", Set.of("BJSL0021"),
                    "BJSL0061", Set.of("BJSL0031"),
                    "BJSL0071", Set.of("BJSL0051"),
                    "BJSL0082", Set.of("BJSL0071"),
                    "B09T0011", Set.of("BJSL0061"),
                    "B09D0012", Set.of("BJSL0061"),
                    "B09N0014", Set.of("BJSL0082"),
                    "B09S0061", Set.of("B09D0012"));
            CurriculumGraphValidator.validate(edges);
            for (var edge : edges.entrySet()) for (String required : edge.getValue()) {
                repository.insertPrerequisite(connection, "edge-" + edge.getKey() + "-" + required,
                        planId, catalog.get(edge.getKey()).courseId(), catalog.get(required).courseId());
            }
            return null;
        });
    }

    private static void installCurrentOfferings(CourseService service, TermView term,
                                                Map<String, CourseView> catalog, String teacherId) {
        Set<String> existing = new HashSet<>();
        for (OfferingSummary offering : service.searchOfferings(
                new OfferingSearchQuery(term.termId(), "", null, false, 0, 100)).items()) {
            existing.add(offering.courseId());
        }
        List<Item> offered = new ArrayList<>(ITEMS.stream()
                .filter(item -> item.year() == 3 && item.season() == AcademicSeason.AUTUMN).toList());
        offered.add(ITEMS.stream().filter(item -> "BJSL0061".equals(item.code())).findFirst().orElseThrow());
        // Deliberately publish one future-plan offering: the student policy must still hide and reject it.
        offered.add(ITEMS.stream().filter(item -> "B09S0061".equals(item.code())).findFirst().orElseThrow());
        int index = 0;
        for (Item item : offered) {
            CourseView course = catalog.get(item.code());
            if (existing.contains(course.courseId())) continue;
            int day = index % 5;
            int period = 1 + (index % 4) * 2;
            service.createOffering(new CreateOfferingCommand(term.termId(), course.courseId(), teacherId,
                    String.format("%02d班", 1), 43, 8, "OPEN", List.of(new CreateOfferingCommand.ScheduleInput(
                    List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY").get(day),
                    period, period + 1, 1, 16, "教二-" + (301 + index)))));
            index++;
        }
    }

    private static void installRetakeHistory(CourseService service, TermView term,
                                             String passedStudentId, String retakeStudentId) {
        CourseView dataStructures = service.searchCatalog(
                new CourseCatalogQuery("BJSL0061", true, 0, 10)).items().getFirst();
        CourseView operatingSystems = service.searchCatalog(
                new CourseCatalogQuery("BJSL0082", true, 0, 10)).items().getFirst();
        List<ImportCourseOutcomesCommand.OutcomeEntry> outcomes = new ArrayList<>();
        if (passedStudentId != null) {
            outcomes.add(new ImportCourseOutcomesCommand.OutcomeEntry(passedStudentId,
                    dataStructures.courseId(), term.termId(), CourseOutcome.PASSED,
                    "demo-passed-" + passedStudentId + "-BJSL0061"));
            outcomes.add(new ImportCourseOutcomesCommand.OutcomeEntry(passedStudentId,
                    operatingSystems.courseId(), term.termId(), CourseOutcome.PASSED,
                    "demo-passed-" + passedStudentId + "-BJSL0082"));
        }
        if (retakeStudentId != null) {
            outcomes.add(new ImportCourseOutcomesCommand.OutcomeEntry(retakeStudentId,
                    operatingSystems.courseId(), term.termId(), CourseOutcome.PASSED,
                    "demo-passed-" + retakeStudentId + "-BJSL0082"));
            outcomes.add(new ImportCourseOutcomesCommand.OutcomeEntry(retakeStudentId,
                    dataStructures.courseId(), term.termId(), CourseOutcome.FAILED,
                    "demo-failed-" + retakeStudentId + "-BJSL0061"));
        }
        if (outcomes.isEmpty()) return;
        try {
            service.importCourseOutcomes(new ImportCourseOutcomesCommand(outcomes));
        } catch (RuntimeException duplicateImport) {
            // Idempotent source references are already accepted; mismatched persisted data should still fail.
            if (retakeStudentId == null
                    || !service.checkRetakeEligibility(retakeStudentId, dataStructures.courseId()).eligible()) {
                throw duplicateImport;
            }
        }
    }

    private static void removeLegacySyntheticFixtures(ConnectionProvider connections) {
        new TransactionManager(connections).inTransaction(connection -> {
            for (String code : List.of("MATH101", "CS201", "DEMO-RACE",
                    "DEMO-MATH101", "DEMO-CS201")) deleteCourse(connection, code);
            return null;
        });
    }

    private static void deleteCourse(Connection c, String code) {
        try {
            List<String> offeringIds = new ArrayList<>();
            String courseId = null;
            try (var s = c.prepareStatement("SELECT courseId FROM tblCourse WHERE courseCode=?")) {
                s.setString(1, code); try (var r = s.executeQuery()) { if (r.next()) courseId = r.getString(1); }
            }
            if (courseId == null) return;
            try (var s = c.prepareStatement("SELECT o.offeringId FROM tblCourseOffering o INNER JOIN tblCourse c ON o.courseId=c.courseId WHERE c.courseCode=?")) {
                s.setString(1, code); try (var r = s.executeQuery()) { while (r.next()) offeringIds.add(r.getString(1)); }
            }
            for (String id : offeringIds) {
                try (var s = c.prepareStatement("DELETE FROM tblEnrollmentAdjustment WHERE sourceOfferingId=? OR targetOfferingId=?")) { s.setString(1, id); s.setString(2, id); s.executeUpdate(); }
                for (String table : List.of("tblEnrollment", "tblCourseSchedule", "tblCourseRetakeQuota"))
                    try (var s = c.prepareStatement("DELETE FROM " + table + " WHERE offeringId=?")) { s.setString(1, id); s.executeUpdate(); }
                try (var s = c.prepareStatement("DELETE FROM tblCourseOffering WHERE offeringId=?")) { s.setString(1, id); s.executeUpdate(); }
            }
            try (var s = c.prepareStatement("DELETE FROM tblCurriculumPrerequisite WHERE courseId=? OR prerequisiteCourseId=?")) { s.setString(1, courseId); s.setString(2, courseId); s.executeUpdate(); }
            for (String table : List.of("tblCurriculumCourse", "tblCourseAttempt"))
                try (var s = c.prepareStatement("DELETE FROM " + table + " WHERE courseId=?")) { s.setString(1, courseId); s.executeUpdate(); }
            try (var s = c.prepareStatement("DELETE FROM tblCourse WHERE courseCode=?")) { s.setString(1, code); s.executeUpdate(); }
        } catch (Exception error) { throw new IllegalStateException("Unable to replace legacy demo fixtures", error); }
    }

    private static Item item(String code, String name, String credit, int year,
                             AcademicSeason season, String nature, String category) {
        return new Item(code, name, new BigDecimal(credit), year, season, nature, category);
    }

    private record Item(String code, String name, BigDecimal credit, int year,
                        AcademicSeason season, String nature, String category) {}
}
