package edu.seu.vcampus.server.routing;

/** Raised when no handler is registered for a command. */
public final class CommandNotFoundException extends RuntimeException {
    /** Creates an exception naming the unknown command. */
    public CommandNotFoundException(String command) {
        super("Unknown command: " + command);
    }
}
