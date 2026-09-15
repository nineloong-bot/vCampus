package edu.seu.vcampus.common.course;
import java.io.*;
/** Serializable schedule row, optionally enriched with offering labels. */
/**
 * Carries immutable schedule item data.
 * @param scheduleId the schedule identifier
 * @param offeringId the offering identifier
 * @param courseCode the course code
 * @param courseName the course name
 * @param className the class name
 * @param teacherUserId the teacher user identifier
 * @param dayOfWeek the day of week
 * @param startPeriod the start period
 * @param endPeriod the end period
 * @param startWeek the start week
 * @param endWeek the end week
 * @param classroom the classroom
 */
public record ScheduleItem(String scheduleId,String offeringId,String courseCode,String courseName,String className,String teacherUserId,String dayOfWeek,int startPeriod,int endPeriod,int startWeek,int endWeek,String classroom) implements Serializable { @Serial private static final long serialVersionUID=1L; }
