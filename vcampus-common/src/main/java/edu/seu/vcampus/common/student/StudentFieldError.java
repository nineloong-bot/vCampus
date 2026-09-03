package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** One field-specific validation failure suitable for both UI and server responses. */
public record StudentFieldError(String field, String message) implements Serializable { }
