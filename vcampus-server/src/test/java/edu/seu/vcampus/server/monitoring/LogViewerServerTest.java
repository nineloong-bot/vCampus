package edu.seu.vcampus.server.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LogViewerServerTest {
    @TempDir
    Path logDirectory;

    @Test
    void servesEscapedRecentLogsOnLoopback() throws Exception {
        Files.writeString(logDirectory.resolve("server.log"),
                "服务端已启动\n请求 <SUCCESS>\n");
        try (LogViewerServer viewer = new LogViewerServer(0, logDirectory);
             HttpClient client = HttpClient.newHttpClient()) {
            viewer.start();
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + viewer.localPort() + "/")).build();

            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("VCampus 服务端日志", "服务端已启动",
                    "请求 &lt;SUCCESS&gt;", "security.log");
        }
    }
}
