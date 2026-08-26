package edu.seu.vcampus.common.error;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/** Safe, serializable error information returned to a client. */
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
