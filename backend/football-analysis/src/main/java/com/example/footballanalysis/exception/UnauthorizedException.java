package com.example.footballanalysis.exception;

/**
 * 401 Unauthorized – ha a kéréshez szükséges hitelesítés hiányzik vagy nem használható.
 */
public class UnauthorizedException extends AppException {
    public UnauthorizedException(String fallbackMessage) {
        super(fallbackMessage);
    }

    public UnauthorizedException(String messageCode, Object[] messageArgs, String fallbackMessage) {
        super(messageCode, messageArgs, fallbackMessage);
    }
}