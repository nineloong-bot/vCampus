package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Complete approved profile split into core, personal, and academic sections. */
public record StudentProfileData(StudentView core, StudentPersonalProfile personal,
                                 StudentAcademicProfile academic) implements Serializable { }
