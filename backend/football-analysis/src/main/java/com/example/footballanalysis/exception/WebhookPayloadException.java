package com.example.footballanalysis.exception;

/**
 * Hibás vagy hiányos webhook payload esetén dobjuk.
 *
 * Ha az eredeti parse-kivétel is elérhető, add meg cause paraméterként,
 * hogy a szerver logban teljes cause-lánc látszódjon.
 */
public class WebhookPayloadException extends AppException {
    public WebhookPayloadException(String fallbackMessage) {
        super(fallbackMessage);
    }

    /**
     * Cause-láncoló konstruktor.
     * Korábban initCause(cause) volt – javítva: super(fallbackMessage, cause)
     * − ugyanaz az indok, mint az ExternalServiceException esetén.
     */
    public WebhookPayloadException(String fallbackMessage, Throwable cause) {
        super(fallbackMessage, cause);
    }

    public WebhookPayloadException(String messageCode, Object[] messageArgs, String fallbackMessage) {
        super(messageCode, messageArgs, fallbackMessage);
    }
}
