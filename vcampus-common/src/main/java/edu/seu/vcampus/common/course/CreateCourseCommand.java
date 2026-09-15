package edu.seu.vcampus.common.course;
import java.io.*; import java.math.BigDecimal; import java.util.Objects;
/** Creates a catalog course. */
/**
 * Carries immutable create course command data.
 * @param courseCode the course code
 * @param courseName the course name
 * @param credit the credit
 * @param totalHours the total hours
 * @param description the description
 * @param active the active
 */
public record CreateCourseCommand(String courseCode,String courseName,BigDecimal credit,int totalHours,String description,boolean active) implements Serializable { @Serial private static final long serialVersionUID=1L; /**
 * Validates and creates a create course command.
 * @param courseCode the course code
 * @param courseName the course name
 * @param credit the credit
 * @param totalHours the total hours
 * @param description the description
 * @param active the active
 */
public CreateCourseCommand { Objects.requireNonNull(courseCode);Objects.requireNonNull(courseName);Objects.requireNonNull(credit);CourseValidation.text("courseCode",courseCode,24);CourseValidation.text("courseName",courseName,128);if(credit.signum()<=0||totalHours<=0)throw new IllegalArgumentException("invalid course"); } }
