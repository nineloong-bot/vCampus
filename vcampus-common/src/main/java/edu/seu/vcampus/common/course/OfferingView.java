package edu.seu.vcampus.common.course;
import java.io.*; import java.time.Instant; import java.util.List;
/** Complete serializable teaching offering. */
public record OfferingView(String offeringId,String termId,String courseId,String teacherUserId,String className,int capacity,int enrolledCount,String offeringStatus,long rowVersion,Instant createdAt,Instant updatedAt,List<ScheduleItem> schedules) implements Serializable { @Serial private static final long serialVersionUID=1L; public OfferingView{schedules=List.copyOf(schedules);} }
