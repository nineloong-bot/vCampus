package edu.seu.vcampus.server.config;

/** Reports an actionable server configuration error. */
public final class ConfigurationException extends RuntimeException {
    /** Creates a configuration exception with a user-facing message. */
    public ConfigurationException(String message) {
        super(message);
    }

    /** Creates a configuration exception with its underlying cause. */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
