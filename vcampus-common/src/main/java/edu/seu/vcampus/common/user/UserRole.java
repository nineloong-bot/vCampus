package edu.seu.vcampus.common.user;

/** Base roles shared by user commands and persistence. */
public enum UserRole {
    SUPER_ADMIN,
    STUDENT_ADMIN,
    COLLEGE_ADMIN,
    COURSE_ADMIN,
    LIBRARY_ADMIN,
    SHOP_ADMIN,
    USER_ADMIN,
    STUDENT,
    TEACHER,
    /** @deprecated Retained only for reading legacy data during migration. */
    @Deprecated
    ADMIN
}
