package edu.seu.vcampus.client.core.network;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/** Asynchronous protocol client with one response-reader thread. */
public final class ClientConnection implements Closeable,
        edu.seu.vcampus.client.student.service.StudentRequestClient {
    private final String host;
    private final int port;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final PendingRequests pending = new PendingRequests(scheduler);
    private final ReentrantLock writeLock = new ReentrantLock();
    private final CopyOnWriteArrayList<Consumer<ConnectionState>> stateListeners =
            new CopyOnWriteArrayList<>();
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile String sessionToken;
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;

    /** Creates a disconnected client for the given endpoint. */
    public ClientConnection(String host, int port) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
    }

    /** Connects and starts the dedicated response-reader thread. */
    public synchronized void connect(Duration timeout) throws IOException {
        if (state == ConnectionState.CONNECTED) {
            return;
        }
        changeState(ConnectionState.CONNECTING);
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), Math.toIntExact(timeout.toMillis()));
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());
            changeState(ConnectionState.CONNECTED);
            reader.submit(this::readResponses);
        } catch (IOException error) {
            changeState(ConnectionState.FAILED);
            closeSocketQuietly();
            throw error;
        }
    }

    /** Sends a request and returns a future completed by the matching response. */
    public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
            String command, Serializable body, Duration timeout) {
        if (state != ConnectionState.CONNECTED) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not connected"));
        }
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<Message> response = pending.register(requestId, timeout);
        Message request = new Message(requestId, MessageType.REQUEST, command,
                sessionToken, body, System.currentTimeMillis());
        try {
            write(request);
        } catch (IOException error) {
            pending.fail(requestId, error);
        }
        return response.thenApply(ClientConnection::<T>responseBody);
    }

    /** Replaces the in-memory session token attached to subsequent requests. */
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    /** Adds a connection-state listener, invoked on the connection's worker thread. */
    public void addStateListener(Consumer<ConnectionState> listener) {
        stateListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Returns the current connection state. */
    public ConnectionState state() {
        return state;
    }

    private void write(Message message) throws IOException {
        writeLock.lock();
        try {
            output.writeObject(message);
            output.flush();
            output.reset();
        } finally {
            writeLock.unlock();
        }
    }

    private void readResponses() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Object value = input.readObject();
                if (value instanceof Message message && message.type() == MessageType.RESPONSE) {
                    pending.complete(message);
                }
            }
        } catch (IOException | ClassNotFoundException error) {
            if (state == ConnectionState.CONNECTED) {
                changeState(ConnectionState.FAILED);
                pending.failAll(error);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> ResponseBody<T> responseBody(Message message) {
        if (!(message.body() instanceof ResponseBody<?> body)) {
            throw new CompletionException(new IOException("Invalid response body"));
        }
        return (ResponseBody<T>) body;
    }

    private void changeState(ConnectionState next) {
        state = next;
        stateListeners.forEach(listener -> listener.accept(next));
    }

    /** Closes the socket and stops client worker threads. */
    @Override
    public synchronized void close() {
        changeState(ConnectionState.DISCONNECTED);
        pending.failAll(new IOException("Connection closed"));
        closeSocketQuietly();
        reader.shutdownNow();
        scheduler.shutdownNow();
    }

    private void closeSocketQuietly() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best effort during shutdown.
            }
        }
    }
}
