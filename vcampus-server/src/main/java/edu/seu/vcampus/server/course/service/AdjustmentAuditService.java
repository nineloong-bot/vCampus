package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.AdjustmentAuditQuery;
import edu.seu.vcampus.common.course.AdjustmentAuditView;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.EnrollmentAdjustment;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Builds privacy-safe, human-readable course adjustment audit rows. */
final class AdjustmentAuditService {
    private final CourseRepository repository;
    private final CourseStudentGateway students;
    private final TransactionManager transactions;

    /** Creates the audit query collaborator. */
    AdjustmentAuditService(CourseRepository repository, CourseStudentGateway students,
                           TransactionManager transactions) {
        this.repository = Objects.requireNonNull(repository);
        this.students = Objects.requireNonNull(students);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /** Searches audit rows using student numbers rather than internal identifiers. */
    PageResult<AdjustmentAuditView> search(AdjustmentAuditQuery query) {
        Objects.requireNonNull(query, "query");
        List<AuditProjection> projections = transactions.inTransaction(connection ->
                repository.findAdjustments(connection).stream()
                        .filter(row -> blank(query.adjustmentType())
                                || row.adjustmentType().equals(query.adjustmentType()))
                        .filter(row -> blank(query.operationResult())
                                || row.operationResult().equals(query.operationResult()))
                        .filter(row -> blank(query.termId())
                                || belongsToTerm(connection, row, query.termId()))
                        .map(row -> project(connection, row)).toList());
        List<AdjustmentAuditView> rows = projections.stream().map(this::toView)
                .filter(row -> blank(query.studentNumber())
                        || row.studentNumber().contains(query.studentNumber()))
                .toList();
        int from = Math.min(rows.size(), Math.multiplyExact(query.page(), query.pageSize()));
        int to = Math.min(rows.size(), from + query.pageSize());
        return new PageResult<>(rows.subList(from, to), query.page(), query.pageSize(), rows.size());
    }

    private AuditProjection project(Connection connection, EnrollmentAdjustment row) {
        return new AuditProjection(row.adjustmentId(), row.studentId(), row.adjustmentType(),
                offeringDisplay(connection, row.sourceOfferingId()),
                offeringDisplay(connection, row.targetOfferingId()), row.operationResult(),
                row.failureCode(), row.operatedAt());
    }

    private AdjustmentAuditView toView(AuditProjection row) {
        return new AdjustmentAuditView(row.adjustmentId(), studentNumber(row.studentId()),
                row.adjustmentType(), row.sourceOffering(), row.targetOffering(),
                row.operationResult(), row.failureCode(), row.operatedAt());
    }

    private String studentNumber(String studentId) {
        try {
            return students.findStudentNumber(studentId);
        } catch (RuntimeException unavailable) {
            return studentId;
        }
    }

    private String offeringDisplay(Connection connection, String offeringId) {
        if (blank(offeringId)) return null;
        var offering = repository.requireOffering(connection, offeringId);
        var course = repository.requireCourse(connection, offering.courseId());
        return course.courseCode() + " · " + offering.className();
    }

    private boolean belongsToTerm(Connection connection, EnrollmentAdjustment row, String termId) {
        String offeringId = row.targetOfferingId() != null
                ? row.targetOfferingId() : row.sourceOfferingId();
        return offeringId != null
                && repository.requireOffering(connection, offeringId).termId().equals(termId);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private record AuditProjection(String adjustmentId, String studentId, String adjustmentType,
                                   String sourceOffering, String targetOffering,
                                   String operationResult, String failureCode, Instant operatedAt) { }
}
