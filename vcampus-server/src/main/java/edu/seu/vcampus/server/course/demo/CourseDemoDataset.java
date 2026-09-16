package edu.seu.vcampus.server.course.demo;

import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.course.service.CourseService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Realistic demo fixture transcribed from the supplied 2024 Computer Science curriculum. */
public final class CourseDemoDataset {
    public static final String MAJOR = "090";
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
            item("B09L0010", "劳动教育与实践", "1", 2, AcademicSeason.SPRING, "REQUIRED", "实践环节"),
            item("BJSL0090", "语言课程设计", "2", 2, AcademicSeason.SPRING, "REQUIRED", "实践环节"),
            item("BJSL0061", "数据结构", "4", 2, AcademicSeason.AUTUMN, "REQUIRED", "大类学科基础课"),
            item("BJSL0071", "计算机组成原理", "4", 2, AcademicSeason.AUTUMN, "REQUIRED", "大类学科基础课"),
            item("B07M3010", "概率论与数理统计", "3", 2, AcademicSeason.AUTUMN, "REQUIRED", "自然科学类"),
            item("B71S1021", "Python编程(研讨)", "2", 2, AcademicSeason.AUTUMN, "ELECTIVE", "专业方向课"),
            item("B71S1170", "软件建模与UML", "2", 2, AcademicSeason.AUTUMN, "ELECTIVE", "专业方向课"),
            item("BJSL0082", "操作系统", "4", 2, AcademicSeason.SPRING, "REQUIRED", "大类学科基础课"),
            item("B09T0011", "算法设计与分析", "3", 2, AcademicSeason.SPRING, "REQUIRED", "专业主干课"),
            item("B09A0010", "人工智能概论", "3", 2, AcademicSeason.SPRING, "REQUIRED", "专业主干课"),
            item("B09S1031", "Java程序设计", "2", 2, AcademicSeason.SPRING, "ELECTIVE", "专业方向课"),
            item("B09P0040", "专业技能实训(校企)", "2", 3, AcademicSeason.SPRING, "REQUIRED", "实践环节"),
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
        CourseDemoCleanup.removeLegacySyntheticFixtures(connections);
        Map<String, CourseView> catalog = new HashMap<>();
        for (CourseView course : service.searchCatalog(new CourseCatalogQuery("", null, 0, 100)).items()) {
            catalog.put(course.courseCode(), course);
        }
        CourseDemoCurriculumSeeder.installDefinitions(connections, ITEMS);
        for (Item item : ITEMS) {
            catalog.computeIfAbsent(item.code(), ignored -> service.createCourse(new CreateCourseCommand(
                    item.code(), item.name(), item.credit(), Math.max(16, item.credit().intValue() * 16),
                    "来源：2024级计算机科学与技术本科专业培养方案", true,
                    "pc-" + item.code())));
        }
        CourseDemoCurriculumSeeder.linkCatalogAndPrerequisites(connections, catalog, ITEMS);
        installCurrentOfferings(service, term, catalog, teacherId);
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
            service.createOffering(new CreateOfferingCommand(course.courseId(), teacherId,
                    String.format("%02d班", 1), 43, 8, "OPEN", List.of(new CreateOfferingCommand.ScheduleInput(
                    List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY").get(day),
                    period, period + 1, 1, 16, "教二-" + (301 + index)))));
            index++;
        }
    }

    private static Item item(String code, String name, String credit, int year,
                             AcademicSeason season, String nature, String category) {
        return new Item(code, name, new BigDecimal(credit), year, season, nature, category);
    }

    record Item(String code, String name, BigDecimal credit, int year,
                AcademicSeason season, String nature, String category) {}
}
