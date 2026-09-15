package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Downloadable PDF payload with a server-sanitized suggested filename. */
/**
 * Carries immutable pdf document data.
 * @param filename the filename
 * @param content the content
 */
public record PdfDocument(String filename, byte[] content) implements Serializable { }
