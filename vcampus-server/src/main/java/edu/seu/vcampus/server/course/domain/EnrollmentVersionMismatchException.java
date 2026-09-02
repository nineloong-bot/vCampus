package edu.seu.vcampus.server.course.domain;

/** Raised when an adjustment command does not match the retained enrollment version. */
public final class EnrollmentVersionMismatchException extends CourseConcurrentModificationException {
    public static final String CODE = CourseConcurrentModificationException.CODE;

    public EnrollmentVersionMismatchException() {
        super();
    }
}
