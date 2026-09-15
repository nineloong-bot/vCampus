package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.course.composition.CourseSchemaInitializer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Ordered schema and seed installation for the application schema initializer segments. */
abstract class ApplicationSchemaInitializerLifecycle extends ApplicationSchemaInitializerInstalling {

    ApplicationSchemaInitializerLifecycle(Path resourceRoot) {
        super(resourceRoot);
    }

    /** Repeatedly safe installer for all module schemas and the unified manual-test dataset. */
    public void initialize(ConnectionProvider connections) throws IOException, SQLException {
        Objects.requireNonNull(connections, "connections");
        installSchema(connections, schema("001_common.sql"));
        installSchema(connections, schema("010_user.sql"));
        installSchema(connections, schema("020_student.sql"));
        AccessSchemaEvolution.ensureColumn(connections, "tblMajor", "grades", "VARCHAR(16)");
        installSchema(connections, schema("025_hierarchical_administration.sql"));
        installSchema(connections, schema("025_major_transfer.sql"));
        new CourseSchemaInitializer(schema("030_course.sql")).initialize(connections);
        installSchema(connections, schema("030_training_plan.sql"));
        AccessSchemaEvolution.ensureColumn(connections, "tblCourse", "departmentId", "VARCHAR(36)");
        AccessSchemaEvolution.ensureColumn(connections, "tblCourse", "departmentName", "VARCHAR(64)");
        AccessSchemaEvolution.ensureColumn(connections, "tblTrainingPlanCourse", "courseId", "VARCHAR(36)");
        AccessSchemaEvolution.ensureColumn(connections, "tblTrainingPlanCourse", "offeringDepartmentId", "VARCHAR(36)");
        AccessSchemaEvolution.ensureColumn(connections, "tblTrainingPlanCourse", "offeringDepartmentName", "VARCHAR(128)");
        AccessSchemaEvolution.ensureColumn(connections, "tblTrainingPlanCourse", "allocatedQuota", "LONG");
        installSchema(connections, schema("035_course_pool.sql"));
        installSchema(connections, schema("040_library.sql"));
        try (Connection connection = connections.open()) { LibraryPenaltySchema.initialize(connection); }
        installSchema(connections, schema("050_shop.sql"));
        installSeeds(connections, seed("010_roles_permissions.sql"));
        installSeeds(connections, seed("020_test_accounts.sql"));
        installSeeds(connections, seed("021_more_students.sql"));
        installSeeds(connections, seed("025_major_transfer_demo.sql"));
        installSeeds(connections, seed("030_training_plan_demo.sql"));
        installSeeds(connections, seed("035_course_pool_demo.sql"));
        installSeeds(connections, seed("040_library_policy.sql"));
        installSeeds(connections, seed("060_unified_demo_data.sql"));
    }
}
