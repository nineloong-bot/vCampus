package edu.seu.vcampus.common.course;
import java.io.*; import java.util.*;
/** Creates an offering and its aggregate schedule rows. */
/**
 * Carries immutable create offering command data.
 * @param termId the term identifier
 * @param courseId the course identifier
 * @param teacherUserId the teacher user identifier
 * @param className the class name
 * @param capacity the capacity
 * @param retakeCapacity the retake capacity
 * @param offeringStatus the offering status
 * @param schedules the schedules
 */
public record CreateOfferingCommand(String termId,String courseId,String teacherUserId,String className,int capacity,int retakeCapacity,String offeringStatus,List<ScheduleInput> schedules) implements Serializable { @Serial private static final long serialVersionUID=1L;
/**
 * Validates the complete offering and schedule aggregate.
 * @param termId the term identifier
 * @param courseId the course identifier
 * @param teacherUserId the teacher user identifier
 * @param className the teaching class name
 * @param capacity the normal enrollment capacity
 * @param retakeCapacity the retake enrollment capacity
 * @param offeringStatus the initial offering status
 * @param schedules the schedule rows
 */
public CreateOfferingCommand{Objects.requireNonNull(termId);Objects.requireNonNull(courseId);Objects.requireNonNull(teacherUserId);Objects.requireNonNull(className);Objects.requireNonNull(offeringStatus);schedules=List.copyOf(Objects.requireNonNull(schedules));CourseValidation.text("termId",termId,36);CourseValidation.text("courseId",courseId,36);CourseValidation.text("teacherUserId",teacherUserId,36);CourseValidation.text("className",className,64);if(capacity<1||retakeCapacity<0||!Set.of("DRAFT","OPEN","CLOSED","CANCELLED").contains(offeringStatus))throw new IllegalArgumentException("invalid offering");} /**
 * Validates and creates a create offering command.
 * @param termId the term identifier
 * @param courseId the course identifier
 * @param teacherUserId the teacher user identifier
 * @param className the class name
 * @param capacity the capacity
 * @param offeringStatus the offering status
 * @param schedules the schedules
 */
public CreateOfferingCommand(String termId,String courseId,String teacherUserId,String className,int capacity,String offeringStatus,List<ScheduleInput> schedules){this(termId,courseId,teacherUserId,className,capacity,5,offeringStatus,schedules);} /**
 * Carries immutable schedule input data.
 * @param dayOfWeek the day of week
 * @param startPeriod the start period
 * @param endPeriod the end period
 * @param startWeek the start week
 * @param endWeek the end week
 * @param classroom the classroom
 */
public record ScheduleInput(String dayOfWeek,int startPeriod,int endPeriod,int startWeek,int endWeek,String classroom) implements Serializable{@Serial private static final long serialVersionUID=1L;/**
 * Validates and creates a schedule input.
 * @param dayOfWeek the day of week
 * @param startPeriod the start period
 * @param endPeriod the end period
 * @param startWeek the start week
 * @param endWeek the end week
 * @param classroom the classroom
 */
public ScheduleInput{Objects.requireNonNull(dayOfWeek);Objects.requireNonNull(classroom);try{java.time.DayOfWeek.valueOf(dayOfWeek.toUpperCase(java.util.Locale.ROOT));}catch(IllegalArgumentException e){throw new IllegalArgumentException("invalid schedule",e);}CourseValidation.text("classroom",classroom,64);if(startPeriod<1||endPeriod<startPeriod||startWeek<1||endWeek<startWeek)throw new IllegalArgumentException("invalid schedule");}} }
