INSERT INTO tblRole (roleCode, roleName) VALUES ('STUDENT', '学生');
INSERT INTO tblRole (roleCode, roleName) VALUES ('TEACHER', '教师');
INSERT INTO tblRole (roleCode, roleName) VALUES ('SUPER_ADMIN', '超级管理员');
INSERT INTO tblRole (roleCode, roleName) VALUES ('STUDENT_ADMIN', '学籍管理员');
INSERT INTO tblRole (roleCode, roleName) VALUES ('COLLEGE_ADMIN', '学院管理员');
INSERT INTO tblRole (roleCode, roleName) VALUES ('COURSE_ADMIN', '课程管理员');
INSERT INTO tblRole (roleCode, roleName) VALUES ('LIBRARY_ADMIN', '图书管理员');
INSERT INTO tblRole (roleCode, roleName) VALUES ('SHOP_ADMIN', '商城管理员');
INSERT INTO tblRole (roleCode, roleName) VALUES ('USER_ADMIN', '用户管理员');
INSERT INTO tblRole (roleCode, roleName) VALUES ('ADMIN', '遗留管理员（已停用）');

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN',
     'qX+wANpmojiY0I1qjpBBoUCjiFP6bZJnWg5qgeHmNh4=',
     'mW5pbqIFUpGT2Zlkq7TsSA==', 120000, 'SUPER_ADMIN', 'ACTIVE', TRUE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000201', 'STUDENT_ADMIN',
     'OlDk8R2oHnN43UJRwIKTgbjZfncjxvp/PmoD2otv3WA=',
     'LuBOl8AL9Kft9pbAdRh+wQ==', 120000, 'STUDENT_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000204', 'COURSE_ADMIN',
     'id1XXqwdGPYmErSfRFWiFK6U9dzfIl0mg84YZfLR7So=',
     'sEWKGBZ8mnsX7kHD09apGA==', 120000, 'COURSE_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000205', 'LIBRARY_ADMIN',
     '2DinkJ+y1Ms6ezWSGyy9djSTHcRARJ2Vh/g62vBovtQ=',
     'lNUTMAzz1GnPQLJdmUZ2zw==', 120000, 'LIBRARY_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000206', 'SHOP_ADMIN',
     'i9UvLSlQ7vtTMbdG4f65Fe4xkZZ3Hz7llPChbbngaZ8=',
     'LDAY5R25PDQbXVl/JGrh4w==', 120000, 'SHOP_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000207', 'USER_ADMIN',
     'N1INQQICyex//tni9dqHj9FNc0IVZQ0aXtv1T7NcgWA=',
     'KXjGQ1/34jcu3Z575iiDCQ==', 120000, 'USER_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000202', 'CS_COLLEGE_ADMIN',
     'vyNAV7GH6GDRHfV+RvV++O8zMfx1REP6A4FXjTCr6tA=',
     'W5S+FoQFDWUsViHY96GM6A==', 120000, 'COLLEGE_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000203', 'MATH_COLLEGE_ADMIN',
     'DieURybqMO8qxYsMVmrE7IiHnYw0I7hxZm4yQ4y5E7U=',
     'NOyycieSNU0bm1e5TNCqYA==', 120000, 'COLLEGE_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000208', 'EE_COLLEGE_ADMIN',
     'vyNAV7GH6GDRHfV+RvV++O8zMfx1REP6A4FXjTCr6tA=',
     'W5S+FoQFDWUsViHY96GM6A==', 120000, 'COLLEGE_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000209', 'FL_COLLEGE_ADMIN',
     'vyNAV7GH6GDRHfV+RvV++O8zMfx1REP6A4FXjTCr6tA=',
     'W5S+FoQFDWUsViHY96GM6A==', 120000, 'COLLEGE_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000401', 'DEMO_ADMIN',
     'J2gQAOuLOjlxEXcl2dqRTVuLVF3iL8rjCgmmJvJtJ9g=',
     'YjJJEx6Z0ak5t9I9M+AZvw==', 120000, 'SUPER_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000402', 'DEMO_TEACHER',
     '8vSrfuVP5AQ/93ycqeykqLSXA49/93J1NpiUiO1/Fdk=',
     'SyrLVrWznBst+9uXhUAkFA==', 120000, 'TEACHER', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000403', '213242478',
     'w1z3hWWkFyQHs8ZdiZEqMAs684yPdGHFtNahPD80BqE=',
     'I3QPbcs0XeUBuZxnmoVqWw==', 120000, 'STUDENT', 'ACTIVE', TRUE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('USER_READ_ALL', '查询全部账户');
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('USER_ROLE_WRITE', '修改账户角色');
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('USER_STATUS_WRITE', '修改账户状态');
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('USER_AUDIT_READ', '查看安全审计');
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('USER_PASSWORD_RESET', '初始化学生密码');
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('STUDENT_WRITE', '学籍管理');
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('STUDENT_READ', '读取学籍');
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('PLATFORM_MODULE_ADMIN_READ', '查询模块管理员分配');
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('PLATFORM_MODULE_ADMIN_WRITE', '维护模块管理员分配');
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('PLATFORM_GOVERNANCE_AUDIT_READ', '查看平台治理审计');
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('STUDENT_COLLEGE_ADMIN_READ', '查询学院管理员分配');
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('STUDENT_COLLEGE_ADMIN_WRITE', '维护学院管理员分配');

INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('SUPER_ADMIN', 'PLATFORM_MODULE_ADMIN_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('SUPER_ADMIN', 'PLATFORM_MODULE_ADMIN_WRITE');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('SUPER_ADMIN', 'PLATFORM_GOVERNANCE_AUDIT_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('STUDENT_ADMIN', 'STUDENT_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('STUDENT_ADMIN', 'STUDENT_WRITE');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('COLLEGE_ADMIN', 'STUDENT_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('COLLEGE_ADMIN', 'STUDENT_WRITE');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('USER_ADMIN', 'USER_READ_ALL');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('USER_ADMIN', 'USER_STATUS_WRITE');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('USER_ADMIN', 'USER_AUDIT_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('USER_ADMIN', 'USER_PASSWORD_RESET');
