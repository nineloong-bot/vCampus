package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Complete approved profile split into core, personal, and academic sections. */
/**
 * Carries immutable student profile data data.
 * @param core the core
 * @param personal the personal
 * @param academic the academic
 */
public record StudentProfileData(StudentView core, StudentPersonalProfile personal,
                                 StudentAcademicProfile academic) implements Serializable { }
