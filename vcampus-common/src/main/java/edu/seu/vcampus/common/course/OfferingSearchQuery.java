package edu.seu.vcampus.common.course;
import java.io.*;
/** Filters and zero-based paging for teaching offerings. */
public record OfferingSearchQuery(String termId,String keyword,String dayOfWeek,Boolean availableOnly,int page,int pageSize) implements Serializable {
 @Serial private static final long serialVersionUID=1L;
 public OfferingSearchQuery { if(page<0||pageSize<1||pageSize>100||(long)page*pageSize>Integer.MAX_VALUE) throw new IllegalArgumentException("invalid page"); }
}
