package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** One field-specific validation failure suitable for both UI and server responses. */
/**
 * Carries immutable student field error data.
 * @param field the field
 * @param message the message
 */
public record StudentFieldError(String field, String message) implements Serializable { }
