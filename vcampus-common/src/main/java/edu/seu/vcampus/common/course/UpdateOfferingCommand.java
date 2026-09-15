package edu.seu.vcampus.common.course;
import java.io.*; import java.util.*;
/** Optimistically updates an offering aggregate without accepting a client counter. */
/**
 * Carries immutable update offering command data.
 * @param offeringId the offering identifier
 * @param termId the term identifier
 * @param courseId the course identifier
 * @param teacherUserId the teacher user identifier
 * @param className the class name
 * @param capacity the capacity
 * @param retakeCapacity the retake capacity
 * @param offeringStatus the offering status
 * @param expectedVersion the expected version
 * @param schedules the schedules
 */
public record UpdateOfferingCommand(String offeringId,String termId,String courseId,String teacherUserId,String className,int capacity,int retakeCapacity,String offeringStatus,long expectedVersion,List<CreateOfferingCommand.ScheduleInput> schedules) implements Serializable { @Serial private static final long serialVersionUID=1L;
/**
 * Validates the updated offering and its optimistic-lock version.
 * @param offeringId the offering identifier
 * @param termId the term identifier
 * @param courseId the course identifier
 * @param teacherUserId the teacher user identifier
 * @param className the teaching class name
 * @param capacity the normal enrollment capacity
 * @param retakeCapacity the retake enrollment capacity
 * @param offeringStatus the target offering status
 * @param expectedVersion the expected row version
 * @param schedules the replacement schedule rows
 */
public UpdateOfferingCommand{Objects.requireNonNull(offeringId);new CreateOfferingCommand(termId,courseId,teacherUserId,className,capacity,retakeCapacity,offeringStatus,schedules);schedules=List.copyOf(schedules);CourseValidation.text("offeringId",offeringId,36);if(expectedVersion<0)throw new IllegalArgumentException("invalid offering");} /**
 * Validates and creates a update offering command.
 * @param offeringId the offering identifier
 * @param termId the term identifier
 * @param courseId the course identifier
 * @param teacherUserId the teacher user identifier
 * @param className the class name
 * @param capacity the capacity
 * @param offeringStatus the offering status
 * @param expectedVersion the expected version
 * @param schedules the schedules
 */
public UpdateOfferingCommand(String offeringId,String termId,String courseId,String teacherUserId,String className,int capacity,String offeringStatus,long expectedVersion,List<CreateOfferingCommand.ScheduleInput> schedules){this(offeringId,termId,courseId,teacherUserId,className,capacity,5,offeringStatus,expectedVersion,schedules);} }
