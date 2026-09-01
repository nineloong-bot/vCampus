package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Downloadable PDF payload with a server-sanitized suggested filename. */
public record PdfDocument(String filename, byte[] content) implements Serializable { }
