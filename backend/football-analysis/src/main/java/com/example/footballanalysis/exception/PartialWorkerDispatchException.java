package com.example.footballanalysis.exception;

/**
 * At least one worker message left the broker, so the match must stay in PROCESSING.
 */
public class PartialWorkerDispatchException extends ExternalServiceException {

    public PartialWorkerDispatchException(String fallbackMessage, Throwable cause) {
        super(fallbackMessage, cause);
    }
}
