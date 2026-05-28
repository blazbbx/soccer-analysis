package com.example.footballanalysis.exception;

/**
 * 502/503 jellegű külső integrációs hiba: MinIO, RabbitMQ, presigner, stb.
 *
 * Ha az eredeti (kiváltó) kivétel is elérhető (pl. egy SDK IOException),
 * mindig add meg cause paraméterként – így getCause() helyesen ad vissza értéket,
 * és a cause megjelenik a szerver logban is.
 */
public class ExternalServiceException extends AppException {
    public ExternalServiceException(String fallbackMessage) {
        super(fallbackMessage);
    }

    /**
     * Cause-láncoló konstruktor.
     * Korábban initCause(cause) volt – ez helytelen: az initCause() csak utólag köti be
     * a cause-t, és egyes logolók/frameworkök nem veszik figyelembe megfelelően.
     * Most super(fallbackMessage, cause) – az AppException(String, Throwable) konstruktort
     * hívja, ami RuntimeException(String, Throwable)-n keresztül helyesen láncolja a cause-t.
     */
    public ExternalServiceException(String fallbackMessage, Throwable cause) {
        super(fallbackMessage, cause);
    }

    public ExternalServiceException(String messageCode, Object[] messageArgs, String fallbackMessage) {
        super(messageCode, messageArgs, fallbackMessage);
    }
}
