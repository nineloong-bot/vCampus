package edu.seu.vcampus.server.monitoring;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

/** Local-only HTTP page that displays recent VCampus server logs. */
public final class LogViewerServer implements AutoCloseable {
    private static final List<String> LOG_FILES = List.of(
            "server.log", "security.log", "database.log", "business.log");
    private static final int MAX_LINES = 200;

    private final HttpServer server;
    private final Path logDirectory;

    public LogViewerServer(int port, Path logDirectory) throws IOException {
        this.logDirectory = logDirectory.toAbsolutePath().normalize();
        this.server = HttpServer.create(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), port), 10);
        this.server.createContext("/", this::handle);
        this.server.setExecutor(Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "vcampus-log-viewer");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** Starts accepting local browser requests. */
    public void start() {
        server.start();
    }

    /** Returns the bound port, including an OS-selected port in tests. */
    public int localPort() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "仅支持 GET");
            return;
        }
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            send(exchange, 404, "页面不存在");
            return;
        }
        send(exchange, 200, render());
    }

    private String render() {
        StringBuilder page = new StringBuilder("""
                <!doctype html>
                <html lang="zh-CN"><head><meta charset="UTF-8">
                <meta http-equiv="refresh" content="3">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>VCampus 服务端日志</title>
                <style>
                body{margin:0;background:#0f172a;color:#e2e8f0;font:14px Consolas,monospace}
                header{position:sticky;top:0;padding:18px 24px;background:#111827;border-bottom:1px solid #334155}
                h1{margin:0 0 6px;font:600 22px system-ui}p{margin:0;color:#94a3b8}
                main{display:grid;gap:18px;padding:20px}section{background:#111827;border:1px solid #334155;border-radius:8px}
                h2{margin:0;padding:12px 16px;color:#7dd3fc;font:600 16px system-ui;border-bottom:1px solid #334155}
                pre{margin:0;padding:16px;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.55}
                </style></head><body><header><h1>VCampus 服务端日志</h1>
                <p>仅本机可访问 · 每 3 秒自动刷新 · 每个文件显示最近 200 行</p></header><main>
                """);
        for (String fileName : LOG_FILES) {
            page.append("<section><h2>").append(fileName).append("</h2><pre>")
                    .append(escape(readTail(logDirectory.resolve(fileName))))
                    .append("</pre></section>");
        }
        return page.append("</main></body></html>").toString();
    }

    private static String readTail(Path file) {
        if (!Files.isRegularFile(file)) {
            return "暂无日志";
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int first = Math.max(0, lines.size() - MAX_LINES);
            return lines.isEmpty() ? "暂无日志" : String.join("\n", lines.subList(first, lines.size()));
        } catch (IOException error) {
            return "无法读取日志";
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void send(HttpExchange exchange, int status, String content) throws IOException {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    /** Stops the local log page. */
    @Override
    public void close() {
        server.stop(0);
    }
}
