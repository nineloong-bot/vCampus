-- Canonical colleges and majors shared by student records, plans, and course selection.
INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) VALUES ('bulk-dept-01', 'TEST01', '计算机学院', TRUE, 0);
INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) VALUES ('bulk-dept-02', 'TEST02', '数学学院', TRUE, 0);
INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) VALUES ('bulk-dept-03', 'TEST03', '外国语学院', TRUE, 0);
INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) VALUES ('bulk-dept-04', 'TEST04', '经济管理学院', TRUE, 0);
INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) VALUES ('bulk-dept-05', 'TEST05', '艺术设计学院', TRUE, 0);
INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) VALUES ('bulk-dept-06', 'TEST06', '物理学院', TRUE, 0);
INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) VALUES ('bulk-dept-07', 'TEST07', '生命科学学院', TRUE, 0);
INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) VALUES ('bulk-dept-08', 'TEST08', '法学学院', TRUE, 0);
INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) VALUES ('bulk-dept-09', 'TEST09', '信息科学与工程学院', TRUE, 0);

INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-01', 'bulk-dept-01', '801', '软件工程', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-02', 'bulk-dept-01', '802', '计算机科学', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-03', 'bulk-dept-02', '803', '数学应用', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-04', 'bulk-dept-02', '804', '统计学', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-05', 'bulk-dept-03', '805', '英语', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-06', 'bulk-dept-03', '806', '日语', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-07', 'bulk-dept-04', '807', '经济学', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-08', 'bulk-dept-04', '808', '管理学', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-09', 'bulk-dept-05', '809', '视觉传达', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-10', 'bulk-dept-05', '810', '产品设计', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-11', 'bulk-dept-06', '811', '物理学', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-12', 'bulk-dept-09', '812', '电子信息科学', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-13', 'bulk-dept-07', '813', '生物科学', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-14', 'bulk-dept-07', '814', '生物技术', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-15', 'bulk-dept-08', '815', '法学', '1,2,3,4', TRUE, 0);
INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, grades, isActive, rowVersion) VALUES ('bulk-major-16', 'bulk-dept-08', '816', '知识产权', '1,2,3,4', TRUE, 0);
