package edu.seu.vcampus.common.course;
import java.io.*;
/** Filters and zero-based paging for teaching offerings. */
/**
 * Carries immutable offering search query data.
 * @param termId the term identifier
 * @param keyword the keyword
 * @param dayOfWeek the day of week
 * @param availableOnly the available only
 * @param page the page
 * @param pageSize the page size
 */
public record OfferingSearchQuery(String termId,String keyword,String dayOfWeek,Boolean availableOnly,int page,int pageSize) implements Serializable {
 @Serial private static final long serialVersionUID=1L;
 /**
 * Validates and creates a offering search query.
 * @param termId the term identifier
 * @param keyword the keyword
 * @param dayOfWeek the day of week
 * @param availableOnly the available only
 * @param page the page
 * @param pageSize the page size
 */
public OfferingSearchQuery { CourseValidation.optionalText("termId",termId,36);if(page<0||pageSize<1||pageSize>100||(long)page*pageSize+pageSize>Integer.MAX_VALUE) throw new IllegalArgumentException("invalid page"); }
}
