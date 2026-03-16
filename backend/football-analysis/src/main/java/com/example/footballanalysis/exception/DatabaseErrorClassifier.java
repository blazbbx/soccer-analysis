package com.example.footballanalysis.exception;

import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Belső segédosztály az adatbázis (JPA/Hibernate és mögöttes SQL driver) eredetű futásidejű
 * hibáinak finomhangolt elemzésére.
 */
final class DatabaseErrorClassifier {

    // Ismert PostgreSQL / Általános SQL State kódok
    private static final String SQL_STATE_UNIQUE_VIOLATION = "23505";
    private static final String SQL_STATE_NOT_NULL_VIOLATION = "23502";
    private static final String SQL_STATE_FOREIGN_KEY_VIOLATION = "23503";

    private DatabaseErrorClassifier() {
    }

    /**
     * Megvizsgálja az alacsony szintű adatbázis kivételt, és lefordítja egy általunk értelmezhető
     * státuszkóddá és szótár-kulccsá.
     */
    static ClassifiedDatabaseError classify(Throwable throwable) {
        SQLException sqlException = findSQLException(throwable);
        
        // 1. Első lépésként megpróbáljuk a hivatalos szabványosított SQLSTATE kód alapján besorolni a hibát
        if (sqlException != null) {
            ClassifiedDatabaseError bySqlState = classifyBySqlState(sqlException.getSQLState());
            if (bySqlState != null) {
                return bySqlState;
            }
        }

        // 2. Fallback (tartalék) megoldás: Ha a Driver valamiért nem küldött rendes SQL state-et, 
        // egy regex-szerű String mintaillesztést csinálunk a hiba legmélyebb okára (root_cause_message).
        String message = findMostSpecificMessage(throwable).toLowerCase(Locale.ROOT);

        if (message.contains("duplicate key") || message.contains("unique constraint")) {
            return new ClassifiedDatabaseError(HttpStatus.CONFLICT, "error.database.unique");
        }
        if (message.contains("not-null constraint") || message.contains("null value in column")) {
            return new ClassifiedDatabaseError(HttpStatus.BAD_REQUEST, "error.database.not_null");
        }
        if (message.contains("foreign key constraint")) {
            return new ClassifiedDatabaseError(HttpStatus.CONFLICT, "error.database.foreign_key");
        }

        // 3. Ha semmit se tudtunk róla mondani, marad egy általános Constraint (Megkötési) hiba
        return new ClassifiedDatabaseError(HttpStatus.CONFLICT, "error.database.constraint");
    }

    private static ClassifiedDatabaseError classifyBySqlState(String sqlState) {
        if (sqlState == null || sqlState.isBlank()) {
            return null;
        }

        return switch (sqlState) {
            case SQL_STATE_UNIQUE_VIOLATION -> new ClassifiedDatabaseError(
                    HttpStatus.CONFLICT,
                    "error.database.unique"
            );
            case SQL_STATE_NOT_NULL_VIOLATION -> new ClassifiedDatabaseError(
                    HttpStatus.BAD_REQUEST,
                    "error.database.not_null"
            );
            case SQL_STATE_FOREIGN_KEY_VIOLATION -> new ClassifiedDatabaseError(
                    HttpStatus.CONFLICT,
                    "error.database.foreign_key"
            );
            default -> sqlState.startsWith("23")
                    ? new ClassifiedDatabaseError(HttpStatus.CONFLICT, "error.database.constraint")
                    : null;
        };
    }

    private static SQLException findSQLException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String findMostSpecificMessage(Throwable throwable) {
        Throwable current = throwable;
        String lastMessage = "";

        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                lastMessage = current.getMessage();
            }
            current = current.getCause();
        }

        return lastMessage;
    }

    record ClassifiedDatabaseError(HttpStatus status, String message) {
    }
}
