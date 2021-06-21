package com.github.onsdigital.zebedee.session.store.exceptions;

public class SessionsKeyException extends RuntimeException {
    public SessionsKeyException(String message) {
        this(message, null);
    }

    public SessionsKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}