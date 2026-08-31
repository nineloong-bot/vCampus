package edu.seu.vcampus.server.bootstrap.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class DistributionDemoScriptsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resetDataRemovesOnlyTheSeededDemoDatabase() throws Exception {
        assumeFalse(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));
        Path source = distributionRoot().resolve("scripts/reset-data.sh");
        assertThat(source).exists();
        Path scripts = Files.createDirectories(temporaryDirectory.resolve("scripts"));
        Path data = Files.createDirectories(temporaryDirectory.resolve("data"));
        Path reset = scripts.resolve("reset-data.sh");
        Files.copy(source, reset);
        Path demoDatabase = Files.writeString(data.resolve("course-user-demo.accdb"), "demo");
        Path unrelatedDatabase = Files.writeString(data.resolve("vCampus.accdb"), "keep");

        Process process = new ProcessBuilder("sh", reset.toString())
                .redirectErrorStream(true)
                .start();

        assertThat(process.waitFor()).isZero();
        assertThat(process.inputReader().lines().toList())
                .containsExactly("已重置带数据 Demo；下次启动服务端会重新创建。");
        assertThat(demoDatabase).doesNotExist();
        assertThat(unrelatedDatabase).exists();
    }

    private static Path distributionRoot() {
        Path root = Path.of("vcampus-distribution");
        return Files.isDirectory(root) ? root : Path.of("..", "vcampus-distribution");
    }
}
