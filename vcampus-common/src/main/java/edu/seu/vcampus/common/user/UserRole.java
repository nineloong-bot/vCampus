package edu.seu.vcampus.common.user;

/** Base roles shared by user commands and persistence. */
public enum UserRole {
    /** Represents super admin. */ SUPER_ADMIN,
    /** Represents student admin. */ STUDENT_ADMIN,
    /** Represents college admin. */ COLLEGE_ADMIN,
    /** Represents course admin. */ COURSE_ADMIN,
    /** Represents library admin. */ LIBRARY_ADMIN,
    /** Represents shop admin. */ SHOP_ADMIN,
    /** Represents user admin. */ USER_ADMIN,
    /** Represents student. */ STUDENT,
    /** Represents teacher. */ TEACHER,
    /** @deprecated Retained only for reading legacy data during migration. */
    @Deprecated
    ADMIN
}
