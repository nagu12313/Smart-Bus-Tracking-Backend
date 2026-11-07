package com.smartbus.exception;

public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
    public DuplicateResourceException(String resourceName, String field, Object value) {
        super(resourceName + " already exists with " + field + ": " + value);
    }
}
