package edu.seu.vcampus.common.course; import java.io.*;
/** Administrator adjustment-audit filters and paging. */ /**
 * Carries immutable adjustment audit query data.
 * @param studentId the student identifier
 * @param termId the term identifier
 * @param adjustmentType the adjustment type
 * @param operationResult the operation result
 * @param page the page
 * @param pageSize the page size
 */
public record AdjustmentAuditQuery(String studentId,String termId,String adjustmentType,String operationResult,int page,int pageSize)implements Serializable{@Serial private static final long serialVersionUID=1L;/**
 * Validates and creates a adjustment audit query.
 * @param studentId the student identifier
 * @param termId the term identifier
 * @param adjustmentType the adjustment type
 * @param operationResult the operation result
 * @param page the page
 * @param pageSize the page size
 */
public AdjustmentAuditQuery{CourseValidation.optionalText("studentId",studentId,36);CourseValidation.optionalText("termId",termId,36);if(page<0||pageSize<1||pageSize>100||(long)page*pageSize+pageSize>Integer.MAX_VALUE)throw new IllegalArgumentException("invalid page");}}
