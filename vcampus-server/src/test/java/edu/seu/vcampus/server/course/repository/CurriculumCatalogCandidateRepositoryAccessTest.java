package edu.seu.vcampus.server.course.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CurriculumCatalogCandidateRepositoryAccessTest {
    @TempDir Path directory;

    @Test
    void readsLegacyCrossNatureAsCrossDisciplinary() throws Exception {
        Path database = directory.resolve("curriculum.accdb");
        try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblDepartment (departmentId VARCHAR(36), departmentName VARCHAR(100))");
            statement.execute("CREATE TABLE tblMajor (majorId VARCHAR(36), departmentId VARCHAR(36))");
            statement.execute("CREATE TABLE tblTrainingPlan (planId VARCHAR(36), majorId VARCHAR(36), isActive YESNO)");
            statement.execute("CREATE TABLE tblTrainingPlanCourse (planCourseId VARCHAR(36), planId VARCHAR(36), courseCode VARCHAR(32), courseName VARCHAR(100), credits DECIMAL(6,2), totalHours INTEGER, courseType VARCHAR(24), offeringDepartmentId VARCHAR(36), offeringDepartmentName VARCHAR(100), isActive YESNO)");
            statement.execute("INSERT INTO tblDepartment VALUES ('D1','计算机学院')");
            statement.execute("INSERT INTO tblMajor VALUES ('M1','D1')");
            statement.execute("INSERT INTO tblTrainingPlan VALUES ('P1','M1',TRUE)");
            statement.execute("INSERT INTO tblTrainingPlanCourse VALUES ('PC1','P1','CS101','程序设计',3,48,'CROSS',NULL,NULL,TRUE)");
            assertThat(new CurriculumCatalogCandidateRepository().findActive(connection))
                    .singleElement().extracting(CurriculumCatalogCandidateRepository.Definition::courseNature)
                    .isEqualTo("CROSS_DISCIPLINARY");
        }
    }
}
