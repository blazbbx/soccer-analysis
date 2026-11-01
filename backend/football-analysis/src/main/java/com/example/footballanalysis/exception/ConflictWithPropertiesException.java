package com.example.footballanalysis.exception;

import java.util.Map;

/**
 * 409 Conflict that carries extra ProblemDetail fields, for example how many records a
 * confirmed action would change. The GlobalExceptionHandler copies each entry to the response.
 */
public class ConflictWithPropertiesException extends ConflictException {

    private final Map<String, Object> properties;

    public ConflictWithPropertiesException(String messageCode, Object[] messageArgs, String fallbackMessage,
                                           Map<String, Object> properties) {
        super(messageCode, messageArgs, fallbackMessage);
        this.properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
