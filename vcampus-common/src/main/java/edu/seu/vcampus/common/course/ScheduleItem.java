package edu.seu.vcampus.common.course;
import java.io.*;
/** Serializable schedule row, optionally enriched with offering labels. */
public record ScheduleItem(String scheduleId,String offeringId,String courseCode,String courseName,String className,String teacherUserId,String dayOfWeek,int startPeriod,int endPeriod,int startWeek,int endWeek,String classroom) implements Serializable { @Serial private static final long serialVersionUID=1L; }
