package edu.seu.vcampus.common.error;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/** Safe, serializable error information returned to a client. */
/**
 * Carries immutable error detail data.
 * @param code the code
 * @param message the message
 * @param fieldErrors the field errors
 * @param traceId the trace identifier
 * @param retryable the retryable
 */
public record ErrorDetail(
        String code,
        String message,
        Map<String, String> fieldErrors,
        String traceId,
        boolean retryable) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Defensively copies field errors to preserve immutability. */
    public ErrorDetail {
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }
}
