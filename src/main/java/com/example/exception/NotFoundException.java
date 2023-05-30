package com.example.exception;

/**
 * @author Mahdi Sharifi
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
