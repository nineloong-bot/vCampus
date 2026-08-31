package edu.seu.vcampus.server.network;

import edu.seu.vcampus.common.error.ErrorDetail;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.routing.CommandNotFoundException;
import edu.seu.vcampus.server.routing.MessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Accepts socket clients and dispatches their requests on a bounded pool. */
public final class SocketServer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketServer.class);

    private final ServerSocket serverSocket;
    private final MessageRouter router;
    private final ThreadPoolExecutor workers;
    private final AtomicBoolean accepting = new AtomicBoolean();

    /** Creates a bounded socket runtime. */
    public SocketServer(int port, int workerCount, int queueCapacity,
            MessageRouter router) throws IOException {
        this.serverSocket = new ServerSocket(port, 50);
        this.router = router;
        this.workers = new ThreadPoolExecutor(workerCount, workerCount,
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity));
    }

    /** Returns the bound local port, including an OS-selected port when constructed with zero. */
    public int localPort() {
        return serverSocket.getLocalPort();
    }

    /** Runs the accept loop on the calling thread until stopped. */
    public void serve() throws IOException {
        accepting.set(true);
        while (accepting.get()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (SocketException error) {
                if (!accepting.get()) {
                    break;
                }
                throw error;
            }
            try {
                workers.execute(() -> handle(socket));
            } catch (RuntimeException rejected) {
                sendBusyAndClose(socket);
            }
        }
    }

    private void handle(Socket socket) {
        try (ClientConnection connection = new ClientConnection(socket, UUID.randomUUID().toString())) {
            while (!socket.isClosed()) {
                Message request = connection.read();
                ResponseBody<?> body;
                try {
                    body = router.route(request, connection.context());
                } catch (CommandNotFoundException error) {
                    body = ResponseBody.failure("COMMON_UNKNOWN_COMMAND", "未知命令", null);
                } catch (RuntimeException error) {
                    LOGGER.error("处理请求 {} 失败", request.command(), error);
                    body = ResponseBody.failure("COMMON_SERVER_ERROR", "服务器内部错误", null);
                }
                connection.send(new Message(request.requestId(), MessageType.RESPONSE,
                        request.command(), null, body, System.currentTimeMillis()));
            }
        } catch (IOException | ClassNotFoundException ignored) {
            // The connection is finished; request failures are mapped above this runtime.
        }
    }

    private void sendBusyAndClose(Socket socket) {
        ErrorDetail detail = new ErrorDetail("COMMON_SERVER_BUSY", "服务器繁忙",
                Map.of(), UUID.randomUUID().toString(), true);
        try (ClientConnection connection = new ClientConnection(socket, "rejected")) {
            connection.send(new Message(UUID.randomUUID().toString(), MessageType.RESPONSE,
                    "SERVER_BUSY", null, ResponseBody.failure(detail.code(),
                    detail.message(), detail), System.currentTimeMillis()));
        } catch (IOException ignored) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                ignored.addSuppressed(closeFailure);
            }
        }
    }

    /** Stops accepting new sockets. */
    public void stopAccepting() throws IOException {
        accepting.set(false);
        serverSocket.close();
        workers.shutdown();
    }

    /** Waits for active requests for at most the supplied duration. */
    public boolean awaitRequests(Duration timeout) throws InterruptedException {
        return workers.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Stops the runtime and interrupts work that exceeds shutdown. */
    @Override
    public void close() throws IOException {
        if (!serverSocket.isClosed()) {
            stopAccepting();
        }
        workers.shutdownNow();
    }
}
