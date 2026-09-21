package com.example.footballanalysis.exception;

/**
 * 403 Forbidden – a felhasználó hitelesített, de nincs joga a kért erőforráshoz.
 */
public class ForbiddenException extends AppException {
    public ForbiddenException(String fallbackMessage) {
        super(fallbackMessage);
    }

    public ForbiddenException(String messageCode, Object[] messageArgs, String fallbackMessage) {
        super(messageCode, messageArgs, fallbackMessage);
    }
}
