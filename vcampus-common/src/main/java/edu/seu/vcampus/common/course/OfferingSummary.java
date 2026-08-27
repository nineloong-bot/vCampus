package edu.seu.vcampus.common.course;
import java.io.*; import java.util.List;
/** Offering row used by query lists. */
public record OfferingSummary(String offeringId,String termId,String courseId,String courseCode,String courseName,String teacherUserId,String className,int capacity,int enrolledCount,String offeringStatus,long rowVersion,List<ScheduleItem> schedules) implements Serializable { @Serial private static final long serialVersionUID=1L; public OfferingSummary{schedules=List.copyOf(schedules);} }
