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
     'uwuuXT1RFzt46ft+h+UoMwHVwvIs3yrWVW3yaU5BRzA=',
     'mW5pbqIFUpGT2Zlkq7TsSA==', 120000, 'SUPER_ADMIN', 'ACTIVE', TRUE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000201', 'STUDENT_ADMIN',
     'TsxZ970Xs6T8QU1REafhi/it3M9hmY2d+bz4eNDfv40=',
     'LuBOl8AL9Kft9pbAdRh+wQ==', 120000, 'STUDENT_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000204', 'COURSE_ADMIN',
     'TM89MXt4mofM75XUH3byZyxQiRNl7hnHpHUKuawhbgs=',
     'sEWKGBZ8mnsX7kHD09apGA==', 120000, 'COURSE_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000205', 'LIBRARY_ADMIN',
     '5+iq0myrnoFstN1dGNPRuuNWsVxOgC5mGQdSoI4yASU=',
     'lNUTMAzz1GnPQLJdmUZ2zw==', 120000, 'LIBRARY_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000206', 'SHOP_ADMIN',
     'y1met7eVngWk0LpJlbMny8l8AamHsa/YIECmq63q1Oc=',
     'LDAY5R25PDQbXVl/JGrh4w==', 120000, 'SHOP_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000207', 'USER_ADMIN',
     'Jj7rK/xAKDPBs4iBFQ7S53Zo9RWYku6FKEdK4439dXc=',
     'KXjGQ1/34jcu3Z575iiDCQ==', 120000, 'USER_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000202', 'CS_COLLEGE_ADMIN',
     'DmgK002eB4QOA7MS8+qtgiI/MLzEScmJJxdvfBtM9GA=',
     'W5S+FoQFDWUsViHY96GM6A==', 120000, 'COLLEGE_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000203', 'MATH_COLLEGE_ADMIN',
     'SUFUV5rguT7+XqdSlG0AFfVp+TyYj5MaXv7czXHcHSQ=',
     'NOyycieSNU0bm1e5TNCqYA==', 120000, 'COLLEGE_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000208', 'EE_COLLEGE_ADMIN',
     'DmgK002eB4QOA7MS8+qtgiI/MLzEScmJJxdvfBtM9GA=',
     'W5S+FoQFDWUsViHY96GM6A==', 120000, 'COLLEGE_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000209', 'FL_COLLEGE_ADMIN',
     'DmgK002eB4QOA7MS8+qtgiI/MLzEScmJJxdvfBtM9GA=',
     'W5S+FoQFDWUsViHY96GM6A==', 120000, 'COLLEGE_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000401', 'DEMO_ADMIN',
     'whBYxd/uU/rvJKNc/Vq5dlEw+XGtnQyq1Kb8k02U+3E=',
     'YjJJEx6Z0ak5t9I9M+AZvw==', 120000, 'SUPER_ADMIN', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000402', 'DEMO_TEACHER',
     'HNJTHqhJurDoVwDSrCAVijGS9mwz7qf8P0I86ZfnhSg=',
     'SyrLVrWznBst+9uXhUAkFA==', 120000, 'TEACHER', 'ACTIVE', FALSE, 0,
     NULL, NULL, 0, NOW(), NOW());

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000403', '213242478',
     'P2ZhX7POxlikjp8YNrxSWrXYLoNebE3LRNjXU767zy0=',
     'I3QPbcs0XeUBuZxnmoVqWw==', 120000, 'STUDENT', 'ACTIVE', FALSE, 0,
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
VALUES ('LIBRARY_ADMIN', '管理图书馆');
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
INSERT INTO tblPermission (permissionCode, permissionName)
VALUES ('COURSE_TEACHER_OPTIONS_READ', '查询选课教师选项');

INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('SUPER_ADMIN', 'PLATFORM_MODULE_ADMIN_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('SUPER_ADMIN', 'PLATFORM_MODULE_ADMIN_WRITE');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('SUPER_ADMIN', 'PLATFORM_GOVERNANCE_AUDIT_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('STUDENT_ADMIN', 'STUDENT_COLLEGE_ADMIN_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('STUDENT_ADMIN', 'STUDENT_COLLEGE_ADMIN_WRITE');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('COLLEGE_ADMIN', 'STUDENT_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('COLLEGE_ADMIN', 'STUDENT_WRITE');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('LIBRARY_ADMIN', 'LIBRARY_ADMIN');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('USER_ADMIN', 'USER_READ_ALL');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('USER_ADMIN', 'USER_STATUS_WRITE');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('USER_ADMIN', 'USER_AUDIT_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('USER_ADMIN', 'USER_PASSWORD_RESET');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('SUPER_ADMIN', 'COURSE_TEACHER_OPTIONS_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('COURSE_ADMIN', 'COURSE_TEACHER_OPTIONS_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('ADMIN', 'COURSE_TEACHER_OPTIONS_READ');
