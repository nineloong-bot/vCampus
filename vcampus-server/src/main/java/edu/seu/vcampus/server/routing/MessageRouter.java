package edu.seu.vcampus.server.routing;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe command registry and dispatcher. */
public final class MessageRouter {
    private final Map<String, MessageHandler> handlers;

    /** Creates a router with an initial immutable snapshot of handlers. */
    public MessageRouter(Map<String, MessageHandler> handlers) {
        this.handlers = new ConcurrentHashMap<>(handlers);
    }

    /** Registers a command when no handler has already claimed it. */
    public void register(String command, MessageHandler handler) {
        if (handlers.putIfAbsent(command, handler) != null) {
            throw new IllegalStateException("Command already registered: " + command);
        }
    }

    /** Routes a message to its registered handler. */
    public ResponseBody<? extends Serializable> route(
            Message message, ClientContext context) {
        MessageHandler handler = handlers.get(message.command());
        if (handler == null) {
            throw new CommandNotFoundException(message.command());
        }
        return handler.handle(message, context);
    }
}
