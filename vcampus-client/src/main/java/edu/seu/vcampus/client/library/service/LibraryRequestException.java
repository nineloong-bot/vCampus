package edu.seu.vcampus.client.library.service;

/** User-safe failure returned by a library server command. */
public final class LibraryRequestException extends RuntimeException {
    private final String code;

    public LibraryRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
