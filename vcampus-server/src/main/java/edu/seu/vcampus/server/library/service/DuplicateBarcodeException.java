package edu.seu.vcampus.server.library.service;

/** Raised when a copy write would reuse an existing barcode. */
public final class DuplicateBarcodeException extends IllegalStateException {
    public DuplicateBarcodeException(String barcode) {
        super("Duplicate copy barcode: " + barcode);
    }
}
