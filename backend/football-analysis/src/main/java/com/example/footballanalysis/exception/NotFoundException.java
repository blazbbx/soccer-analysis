package com.example.footballanalysis.exception;

/**
 * 404 Not Found – ha egy entitás (User, Team, Match stb.) nem található az adatbázisban.
 */
public class NotFoundException extends AppException {
    public NotFoundException(String fallbackMessage) {
        super(fallbackMessage);
    }

    public NotFoundException(String messageCode, Object[] messageArgs, String fallbackMessage) {
        super(messageCode, messageArgs, fallbackMessage);
    }
}
