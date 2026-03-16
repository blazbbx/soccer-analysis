package com.example.footballanalysis.exception;

/**
 * 400 Bad Request – ha a kérés tartalma érvénytelen (pl. ismeretlen role).
 */
public class BadRequestException extends AppException {
    public BadRequestException(String fallbackMessage) {
        super(fallbackMessage);
    }

    public BadRequestException(String messageCode, Object[] messageArgs, String fallbackMessage) {
        super(messageCode, messageArgs, fallbackMessage);
    }
}
