package edu.seu.vcampus.server.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedDistributionConfigurationTest {
    @Test
    void packagedLaunchersUseUnifiedRuntimeDatabaseAndAllSchemas() throws Exception {
        Path root = repositoryRoot();
        String pom = Files.readString(root.resolve("vcampus-server/pom.xml"));
        String config = Files.readString(
                root.resolve("vcampus-distribution/config/server-with-data.properties"));

        assertThat(pom).contains("<mainClass>edu.seu.vcampus.server.bootstrap.ServerMain</mainClass>");
        assertThat(config).contains("server.port=8888", "database.path=data/vCampus.accdb",
                "database.resourceRoot=database", "database.createIfMissing=true");
        for (String schema : new String[]{"001_common.sql", "010_user.sql", "020_student.sql",
                "030_course.sql", "040_library.sql", "050_shop.sql"}) {
            assertThat(root.resolve("vcampus-distribution/database/schema").resolve(schema))
                    .isRegularFile();
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        return current.getFileName().toString().equals("vcampus-server")
                ? current.getParent() : current;
    }
}
