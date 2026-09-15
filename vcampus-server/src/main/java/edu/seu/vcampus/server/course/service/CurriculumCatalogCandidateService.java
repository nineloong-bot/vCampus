package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.CurriculumCourseCandidate;
import edu.seu.vcampus.common.course.CurriculumCourseCandidateQuery;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.CurriculumCatalogCandidateRepository;
import edu.seu.vcampus.server.course.repository.CurriculumCatalogCandidateRepository.Definition;
import edu.seu.vcampus.server.persistence.TransactionManager;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import edu.seu.vcampus.server.course.repository.Course;

/** Aggregates conflict-safe catalog candidates from canonical curricula. */
public final class CurriculumCatalogCandidateService {
    private final CurriculumCatalogCandidateRepository curricula;
    private final CourseRepository courses;
    private final TransactionManager transactions;

    /** Creates the candidate service from its narrow persistence collaborators. */
    public CurriculumCatalogCandidateService(CurriculumCatalogCandidateRepository curricula,
                                             CourseRepository courses,
                                             TransactionManager transactions) {
        this.curricula = Objects.requireNonNull(curricula);
        this.courses = Objects.requireNonNull(courses);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /** Searches unique definitions from active canonical training plans. */
    public PageResult<CurriculumCourseCandidate> search(CurriculumCourseCandidateQuery query) {
        Objects.requireNonNull(query, "query");
        return transactions.inTransaction(connection -> search(connection, query));
    }

    /** Returns whether the connected database exposes canonical curriculum definitions. */
    public boolean supports(Connection connection) {
        return curricula.supports(connection);
    }

    /** Reloads one selected definition and rejects ambiguous curriculum metadata. */
    public CurriculumCourseCandidate requireAvailable(Connection connection, String planCourseId) {
        if (planCourseId == null || planCourseId.isBlank()) throw new IllegalArgumentException("planCourseId");
        return aggregate(definitions(connection)).stream()
                .filter(candidate -> planCourseId.equals(candidate.planCourseId()))
                .filter(candidate -> !candidate.conflicted())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("curriculum course unavailable"));
    }

    private PageResult<CurriculumCourseCandidate> search(Connection connection,
                                                          CurriculumCourseCandidateQuery query) {
        String keyword = normalize(query.keyword());
        List<CurriculumCourseCandidate> all = aggregate(definitions(connection)).stream()
                .filter(row -> keyword.isEmpty() || normalize(row.courseCode()).contains(keyword)
                        || normalize(row.courseName()).contains(keyword)).toList();
        int from = Math.min(all.size(), Math.multiplyExact(query.page(), query.pageSize()));
        return new PageResult<>(all.subList(from, Math.min(all.size(), from + query.pageSize())),
                query.page(), query.pageSize(), all.size());
    }

    static List<CurriculumCourseCandidate> aggregate(List<Definition> definitions) {
        Map<String, List<Definition>> grouped = new LinkedHashMap<>();
        for (Definition row : definitions) grouped.computeIfAbsent(normalize(row.courseCode()), ignored -> new java.util.ArrayList<>()).add(row);
        return grouped.values().stream().map(CurriculumCatalogCandidateService::candidate).toList();
    }

    static List<Definition> mergeWithCatalog(List<Definition> definitions, List<Course> catalog) {
        Map<String, Course> byCode = new LinkedHashMap<>();
        for (Course course : catalog) byCode.put(normalize(course.courseCode()), course);
        return definitions.stream().map(row -> merge(row, byCode.get(normalize(row.courseCode()))))
                .filter(row -> row.totalHours() != null && row.totalHours() > 0).toList();
    }

    private List<Definition> definitions(Connection connection) {
        return mergeWithCatalog(curricula.findActive(connection), courses.findCourses(connection));
    }

    private static Definition merge(Definition row, Course catalog) {
        if (catalog == null) return row;
        int hours = row.totalHours() == null || row.totalHours() <= 0
                ? catalog.totalHours() : row.totalHours();
        boolean catalogOwnsDepartment = catalog.departmentId() != null || catalog.departmentName() != null;
        String departmentId = catalogOwnsDepartment ? catalog.departmentId() : row.departmentId();
        String departmentName = catalogOwnsDepartment ? catalog.departmentName() : row.departmentName();
        return new Definition(row.planCourseId(), row.courseCode(), catalog.courseName(), catalog.credit(),
                hours, row.courseNature(), departmentId, departmentName);
    }

    private static CurriculumCourseCandidate candidate(List<Definition> rows) {
        Definition first = rows.getFirst();
        boolean conflict = rows.stream().skip(1).anyMatch(row -> !same(first, row));
        return new CurriculumCourseCandidate(first.planCourseId(), first.courseCode(), first.courseName(),
                first.credits(), first.totalHours(), first.courseNature(), first.departmentId(),
                first.departmentName(), conflict, conflict ? "多个培养方案中的课程定义不一致，请先由学院管理员统一" : null);
    }

    private static boolean same(Definition a, Definition b) {
        return normalize(a.courseName()).equals(normalize(b.courseName()))
                && a.credits().compareTo(b.credits()) == 0
                && Objects.equals(a.totalHours(), b.totalHours());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }
}
