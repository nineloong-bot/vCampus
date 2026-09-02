INSERT INTO tblRole (roleCode, roleName) VALUES ('STUDENT', '学生');
INSERT INTO tblRole (roleCode, roleName) VALUES ('TEACHER', '教师');
INSERT INTO tblRole (roleCode, roleName) VALUES ('ADMIN', '管理员');

INSERT INTO tblUser
    (userId, loginId, passwordHash, passwordSalt, passwordIterations,
     roleCode, accountStatus, mustChangePassword, failedLoginCount,
     lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN',
     'qX+wANpmojiY0I1qjpBBoUCjiFP6bZJnWg5qgeHmNh4=',
     'mW5pbqIFUpGT2Zlkq7TsSA==', 120000, 'ADMIN', 'ACTIVE', TRUE, 0,
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

INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('ADMIN', 'USER_READ_ALL');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('ADMIN', 'USER_ROLE_WRITE');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('ADMIN', 'USER_STATUS_WRITE');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('ADMIN', 'USER_AUDIT_READ');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('ADMIN', 'USER_PASSWORD_RESET');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('ADMIN', 'STUDENT_WRITE');
INSERT INTO tblRolePermission (roleCode, permissionCode)
VALUES ('ADMIN', 'LIBRARY_ADMIN');
