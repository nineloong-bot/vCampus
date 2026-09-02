package edu.seu.vcampus.server.bootstrap.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class DistributionDemoScriptsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesTheSharedClientSeededServerAndResetLaunchers() throws Exception {
        List<String> scripts;
        try (var paths = Files.list(distributionRoot().resolve("scripts"))) {
            scripts = paths.map(path -> path.getFileName().toString()).sorted().toList();
        }
        assertThat(scripts).contains(
                "reset-data.bat", "reset-data.sh",
                "start-client.bat", "start-client.sh",
                "start-server-with-data.bat", "start-server-with-data.sh");

        String readme = Files.readString(distributionRoot().resolve("README.md"));
        assertThat(readme).contains("start-client", "start-server-with-data", "reset-data")
                .doesNotContain("start-server.bat", "integrated-demo-client", "course-demo-client");
    }

    @Test
    void clientLauncherLeavesServerDataUntouchedAndServerUsesSeededConfig() throws Exception {
        assumeFalse(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));
        Path root = Files.createDirectories(temporaryDirectory.resolve("distribution"));
        Path scripts = Files.createDirectories(root.resolve("scripts"));
        Path data = Files.createDirectories(root.resolve("data"));
        Path sentinel = Files.writeString(data.resolve("vCampus.accdb"), "keep");
        Files.copy(distributionRoot().resolve("scripts/start-client.sh"),
                scripts.resolve("start-client.sh"));
        Files.copy(distributionRoot().resolve("scripts/start-server-with-data.sh"),
                scripts.resolve("start-server-with-data.sh"));
        Path bin = Files.createDirectories(temporaryDirectory.resolve("bin"));
        Path java = Files.writeString(bin.resolve("java"), """
                #!/usr/bin/env sh
                if [ "${1:-}" = "-version" ]; then
                  echo 'openjdk version "21.0.4"' >&2
                  exit 0
                fi
                printf '%s\\n' "$*"
                """);
        Files.setPosixFilePermissions(java, PosixFilePermissions.fromString("rwxr-xr-x"));

        ProcessBuilder clientBuilder = new ProcessBuilder(
                "sh", scripts.resolve("start-client.sh").toString())
                .redirectErrorStream(true);
        clientBuilder.environment().put("PATH",
                bin + System.getProperty("path.separator") + System.getenv("PATH"));
        Process client = clientBuilder.start();
        client.waitFor();
        assertThat(client.exitValue()).isZero();
        assertThat(client.inputReader().lines().toList())
                .containsExactly("-Dlogback.configurationFile=config/logback.xml -jar lib/vCampusClient.jar config/client.properties");
        assertThat(sentinel).hasContent("keep");

        ProcessBuilder serverBuilder = new ProcessBuilder(
                "sh", scripts.resolve("start-server-with-data.sh").toString())
                .redirectErrorStream(true);
        serverBuilder.environment().put("PATH",
                bin + System.getProperty("path.separator") + System.getenv("PATH"));
        Process server = serverBuilder.start();
        assertThat(server.waitFor()).isZero();
        assertThat(server.inputReader().lines().toList())
                .containsExactly("-Dlogback.configurationFile=config/logback.xml -jar lib/vCampusServer.jar config/server-with-data.properties");
        assertThat(sentinel).hasContent("keep");
    }

    @Test
    void resetDataConfirmsThenRemovesOnlyTheUnifiedDatabase() throws Exception {
        assumeFalse(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));
        Path source = distributionRoot().resolve("scripts/reset-data.sh");
        assertThat(source).exists();
        Path scripts = Files.createDirectories(temporaryDirectory.resolve("scripts"));
        Path data = Files.createDirectories(temporaryDirectory.resolve("data"));
        Path reset = scripts.resolve("reset-data.sh");
        Files.copy(source, reset);
        Path demoDatabase = Files.writeString(data.resolve("vCampus.accdb"), "demo");
        Path unrelatedDatabase = Files.writeString(data.resolve("unrelated.accdb"), "keep");

        Process process = new ProcessBuilder("sh", reset.toString())
                .redirectErrorStream(true)
                .start();
        process.outputWriter().write("y\n");
        process.outputWriter().flush();
        process.outputWriter().close();

        assertThat(process.waitFor()).isZero();
        assertThat(process.inputReader().lines().toList())
                .containsExactly(
                        "确认删除 data/vCampus.accdb 并恢复虚拟校园测试数据？[y/N]",
                        "已重置虚拟校园数据；下次启动服务端会重新创建。");
        assertThat(demoDatabase).doesNotExist();
        assertThat(unrelatedDatabase).exists();
    }

    private static Path distributionRoot() {
        Path root = Path.of("vcampus-distribution");
        return Files.isDirectory(root) ? root : Path.of("..", "vcampus-distribution");
    }
}
