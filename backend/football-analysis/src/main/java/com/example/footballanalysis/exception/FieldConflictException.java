package com.example.footballanalysis.exception;

/**
 * Mező-szintű üzleti ütközés – olyan ConflictException, ami tudja, melyik mezőben van a probléma.
 *
 * Példa:
 *   throw new FieldConflictException("email",
 *           "error.user.email.conflict", new Object[]{req.email()},
 *           "Email already in use: " + req.email());
 *
 * A GlobalExceptionHandler ennek hatására az alábbi JSON-t adja vissza (409):
 * {
 *   "status":  409,
 *   "title":   "Resource Conflict",
 *   "detail":  "Email already in use: test@example.com",
 *   "errors":  { "email": ["Email already in use: test@example.com"] }
 * }
 *
 * Így a frontend a ConflictException-t is ugyanolyan $.errors.{mező} struktúrából tudja kezelni,
 * mint a @Valid validációs hibákat.
 */
public class FieldConflictException extends ConflictException {

    private final String field;

    public FieldConflictException(String field, String messageCode, Object[] messageArgs, String fallbackMessage) {
        super(messageCode, messageArgs, fallbackMessage);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
