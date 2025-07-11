package com.ladyluh.nekoffee.api.exception;

public class NekoffeeException extends RuntimeException {
    public NekoffeeException(String message) {
        super(message);
    }

    public NekoffeeException(String message, Throwable cause) {
        super(message, cause);
    }
}