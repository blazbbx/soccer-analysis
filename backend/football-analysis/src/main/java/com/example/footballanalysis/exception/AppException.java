package com.example.footballanalysis.exception;

import org.springframework.lang.Nullable;

/**
 * Az összes alkalmazás-szintű kivétel közös ősosztálya.
 *
 * Kétféle konstruktora van:
 *   1. Csak szöveg: AppException("User not found")
 *      → A GlobalExceptionHandler a nyers üzenetet adja a kliensnek.
 *
 *   2. Kulcs + args + fallback: AppException("error.user.not_found", new Object[]{id}, "User not found: " + id)
 *      → A GlobalExceptionHandler a MessageSource-ból fordítja le a kívánt locale szerint,
 *         ha a kulcs nem található, visszaesik a fallback szövegre.
 *
 * Cause-láncolás:
 *   Ha az eredeti (kiváltó) kivételt is meg akarjuk őrizni (pl. egy 3rd party hívás dobott kivétele),
 *   a Throwable cause paramétert fogadó konstruktorokat kell használni.
 *   FONTOS: a cause-t mindig a super(message, cause) hívással adjuk át –
 *   soha ne initCause()-t használj, mert az megszakítja a konstruktorláncon alapuló cause-kezelést.
 */
public abstract class AppException extends RuntimeException {

    @Nullable
    private final String messageCode;
    private final Object[] messageArgs;

    protected AppException(String fallbackMessage) {
        super(fallbackMessage);
        this.messageCode = null;
        this.messageArgs = new Object[0];
    }

    /**
     * Cause-láncolást támogató konstruktor.
     * Akkor használd, ha az eredeti kivételt (cause) is meg akarod őrizni a logban,
     * például: throw new ExternalServiceException("MinIO nem érhető el", ioException)
     */
    protected AppException(String fallbackMessage, Throwable cause) {
        super(fallbackMessage, cause);
        this.messageCode = null;
        this.messageArgs = new Object[0];
    }

    protected AppException(String messageCode, Object[] messageArgs, String fallbackMessage) {
        super(fallbackMessage);
        this.messageCode = messageCode;
        this.messageArgs = messageArgs != null ? messageArgs : new Object[0];
    }

    @Nullable
    public String getMessageCode() {
        return messageCode;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
