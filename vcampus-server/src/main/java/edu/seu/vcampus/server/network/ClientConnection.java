package edu.seu.vcampus.server.network;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.server.routing.ClientContext;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

/** Owns one client socket and serializes all writes to its object stream. */
public final class ClientConnection implements Closeable {
    private final Socket socket;
    private final ObjectOutputStream output;
    private final ObjectInputStream input;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ClientContext context;

    /** Opens output first, flushes its header, then opens input to avoid handshake deadlock. */
    public ClientConnection(Socket socket, String connectionId) throws IOException {
        this.socket = socket;
        this.output = openOutput(socket.getOutputStream());
        this.input = new ObjectInputStream(socket.getInputStream());
        this.context = new ClientContext(connectionId,
                socket.getRemoteSocketAddress().toString());
    }

    private ClientConnection(OutputStream outputStream) throws IOException {
        this.socket = null;
        this.output = openOutput(outputStream);
        this.input = null;
        this.context = new ClientContext("test", "memory");
    }

    /** Creates an output-only connection for deterministic stream tests. */
    public static ClientConnection forOutput(OutputStream outputStream) throws IOException {
        return new ClientConnection(outputStream);
    }

    private static ObjectOutputStream openOutput(OutputStream stream) throws IOException {
        ObjectOutputStream output = new ObjectOutputStream(stream);
        output.flush();
        return output;
    }

    /** Returns this connection's routing context. */
    public ClientContext context() {
        return context;
    }

    /** Reads the next complete wire message. */
    public Message read() throws IOException, ClassNotFoundException {
        if (input == null) {
            throw new IllegalStateException("Connection has no input stream");
        }
        return (Message) input.readObject();
    }

    /** Writes and flushes one message without interleaving concurrent callers. */
    public void send(Message message) throws IOException {
        writeLock.lock();
        try {
            output.writeObject(message);
            output.flush();
            output.reset();
        } finally {
            writeLock.unlock();
        }
    }

    /** Closes the underlying socket or output-only test stream. */
    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        } else {
            output.close();
        }
    }
}
