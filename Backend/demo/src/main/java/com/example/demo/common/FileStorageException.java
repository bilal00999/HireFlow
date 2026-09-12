package com.example.demo.common;

/**
 * Thrown when an external file-storage operation (Cloudinary upload/delete) or
 * the persistence of its result fails. Maps to HTTP 502 — the request was valid
 * but an upstream/storage dependency could not complete it. The cause is logged
 * server-side; the client sees a sanitized message (no storage internals).
 */
public class FileStorageException extends RuntimeException {
    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
