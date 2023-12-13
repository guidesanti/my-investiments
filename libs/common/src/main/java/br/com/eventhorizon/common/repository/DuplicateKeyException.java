package br.com.eventhorizon.common.repository;

public class DuplicateKeyException extends Exception {

    public DuplicateKeyException(String message) {
        super(message);
    }

    public DuplicateKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
