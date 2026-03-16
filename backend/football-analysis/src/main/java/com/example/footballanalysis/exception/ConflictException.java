package com.example.footballanalysis.exception;

/**
 * 409 Conflict – ha egy erőforrás már létezik (pl. email már foglalt).
 */
public class ConflictException extends AppException {
    public ConflictException(String fallbackMessage) {
        super(fallbackMessage);
    }

    public ConflictException(String messageCode, Object[] messageArgs, String fallbackMessage) {
        super(messageCode, messageArgs, fallbackMessage);
    }
}
