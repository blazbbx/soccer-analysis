package com.example.footballanalysis.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Globális hibakezelő (Global Exception Handler) – központosítja az összes alkalmazásszintű kivétel (Exception) kezelését.
 * A @RestControllerAdvice annotáció azt jelenti, hogy ez az osztály egy "elfogó háló" (interceptor),
 * ami automatikusan bekapcsolódik az összes @RestController-ből kirepülő hiba esetén.
 *
 * Céljai:
 * 1. Kódismétlés elkerülése: Nem kell minden Controller metódusba try-catch blokkokat írni.
 * 2. Szabványosítás: Minden hiba RFC 7807 szabványú (ProblemDetail) JSON formátumban kerül a klienshez.
 * 3. Biztonság: Megakadályozza, hogy belső szerver/adatbázis információk (stacktrace) szivárogjanak ki a frontend felé.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // ------------------------------------------------------------------------
    // Standard Override methods (TraceID, ProblemDetail customization)
    // ------------------------------------------------------------------------

    /**
     * Bővíti az alapértelmezett Spring-es hibaobjektumot (ProblemDetail) egyedi mezőkkel.
     * Minden támogatott beépített kivétel ez alapján konvertálódik JSON-né.
     */
    @Override
    protected ProblemDetail createProblemDetail(Exception ex, HttpStatusCode status, String defaultDetail, String detailMessageCode, Object[] detailMessageArguments, WebRequest request) {
        // Trace ID lekérése a naplózási kontextusból (MDC), amivel a beérkező kérés naplóbejegyzéseit
        // és az elszálló hibát össze tudjuk kötni a log elemző rendszerekben.
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            log.debug("No traceId found in MDC. Generated new one: {}", traceId);
        }

        // ProblemDetail létrehozása a beépített logika szerint
        ProblemDetail problemDetail = super.createProblemDetail(ex, status, defaultDetail, detailMessageCode, detailMessageArguments, request);

        // További egyedi tulajdonságok hozzácsatolása (ezek bekerülnek a kimenő JSON-be)
        problemDetail.setProperty("traceId", traceId);
        problemDetail.setProperty("timestamp", java.time.Instant.now());

        return problemDetail;
    }

    /**
     * Minden Spring MVC belső szabályok alapján kezelt hiba esetén meghívódik.
     * Itt egy központi logolást végzünk, hogy a belső hibák se vesszenek el.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // Logolás minden kezelt hibánál
        log.error("Handling exception: {} - Status: {}", ex.getClass().getSimpleName(), statusCode, ex);
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    // ------------------------------------------------------------------------
    // Validation Errors (Spring MVC @Valid)
    // ------------------------------------------------------------------------

    /**
     * Ez a metódus akkor hívódik meg, ha egy Controllerben a bejövő DTO-n 
     * lévő @Valid annotáció valamelyik feltétele (pl. @NotNull, @Email) elbukik.
     * Célja, hogy a nyers hibaüzeneteket megragadja, és egy szépen formázott 
     * Map-be szervezze mezőnevek szerint strukturálva.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problemDetail = createProblemDetail(ex, status, "Validation failed", "error.validation.default", null, request);

        // A validációs hibákat mezőnév alapján csoportosítjuk. Map: "mezőnév" -> ["Hiba 1", "Hiba 2"]
        Map<String, List<String>> validationErrors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField)) // Rendezés: hogy a frontend mindig ugyanabban a sorrendben kapja meg
                .forEach(fieldError -> {
                    String fieldName = fieldError.getField();
                    
                    // Lokalizáció (i18n): megpróbáljuk a beépített properties fájlokból (messages_hu.properties) beolvasni a kulcsot
                    String resolved = messageSource.getMessage(fieldError, LocaleContextHolder.getLocale());

                    validationErrors.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(resolved);
                });

        // Hozzáadjuk a letisztított Map-et a válasz JSON-hez egy "errors" kulcs alatt
        problemDetail.setProperty("errors", validationErrors);
        problemDetail.setTitle(resolveMessage("error.validation.default", "Validation Failed"));

        return createResponseEntity(problemDetail, headers, status, request);
    }

    // ------------------------------------------------------------------------
    // JPA / Hibernate Validation Errors (@Validated)
    // ------------------------------------------------------------------------

    /**
     * JPA (Adatbázis entitás) vagy a Service réteg szintjén történő validációkor keletkezik
     * (pl. amikor nem egy Controller paraméteren van a @Valid, hanem egy @Validated beanen).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setTitle(resolveMessage("error.validation.default", "Validation Failed"));

        Map<String, List<String>> validationErrors = new LinkedHashMap<>();

        // Itt magukon a megkötéseken (Constraint) iterálunk végig
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = extractFieldName(violation.getPropertyPath());
            String message = violation.getMessage(); // Általában már feloldott szöveg (i18n-ből)

            validationErrors.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(message);
        }

        problemDetail.setProperty("errors", validationErrors);
        // Csak ezentúl történő generálás miatt muszáj kézzel bővítenünk traceId-val:
        enrichProblemDetail(problemDetail);

        return problemDetail;
    }

    /**
     * Kinyeri a pontos property nevét a ConstraintViolation path-jából.
     * Pl: metódusnév.paraméternév.mezőnév -> csak a legutolsó "mezőnév"-et tartja meg.
     */
    private String extractFieldName(Path path) {
        String fieldName = null;
        for (Path.Node node : path) {
            fieldName = node.getName();
        }
        return fieldName != null ? fieldName : "unknown";
    }

    // ------------------------------------------------------------------------
    // Database & Integrity Errors
    // ------------------------------------------------------------------------

    /**
     * Elkapja az adatbázis szintű natív hibákat (pl. Ha egy UNIQUE mezőbe másodszorra is ugyanazt inzertálnánk)
     * Enélkül egy hatalmas 500-as SQL nyers hiba menne ki. Mi ezt emberi nyelvre fordítjuk és átváltjuk 409 Conflict-ra.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // Átadjuk az elemzőnek, hogy mondja meg mi okozta (pl. Unique sértés, Külső kulcs sértés, Not Null)
        DatabaseErrorClassifier.ClassifiedDatabaseError classified = DatabaseErrorClassifier.classify(ex);

        // Lefordítjuk az elemző által visszaadott hiba-kulcsot helyi nyelvre
        String localizedDetail = resolveMessage(classified.message(), "Database error occurred");

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(classified.status(), localizedDetail);
        problemDetail.setTitle("Database Error");
        problemDetail.setType(URI.create("urn:problem-type:database-error"));

        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * Mező-szintű ütközés: ugyanolyan 409 állapotot ad, mint a ConflictException,
     * de az errors Map-ben jelzi melyik mezőben van a probléma – pont mint a @Valid validáció.
     * Spring a ConflictException handler előtt futtatja ezt, mert specifikusabb kivételtípusra szól.
     */
    @ExceptionHandler(FieldConflictException.class)
    public ProblemDetail handleFieldConflict(FieldConflictException ex) {
        String detail = resolveExceptionDetail(ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problemDetail.setTitle("Resource Conflict");
        problemDetail.setProperty("errors", Map.of(ex.getField(), List.of(detail)));
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * Saját "Conflict" vagyis "Ütközés" nevű logikai hibánk (Pl. már létezik egy felhasználó ezzel az emaillel)
     * Visszatérése 409-es (Conflict) HTTP kód lesz.
     */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, resolveExceptionDetail(ex));
        problemDetail.setTitle("Resource Conflict");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    // ------------------------------------------------------------------------
    // Custom & Other Exceptions
    // ------------------------------------------------------------------------

    /**
     * Saját 404-es hibánk. A Service réteg dobálhatja nyugodtan, ha nem talál egy rekordot az adatbázisban.
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, resolveExceptionDetail(ex));
        problemDetail.setTitle("Resource Not Found");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * Saját 400-as hibánk. Bármilyen egyedi validációs vagy formátum hiba dobható így.
     */
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequestException(BadRequestException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, resolveExceptionDetail(ex));
        problemDetail.setTitle("Bad Request");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * Akkor fut le, ha egy @PathVariable vagy @RequestParam URL-ben érkező paramétere típusban nem egyezik.
     * Pl: /users/XYZ (itt UUID-t, int-et várunk, de string-et kaptunk a webes útvonalban)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = String.format("Invalid format for parameter '%s': '%s'.", ex.getName(), ex.getValue());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problemDetail.setTitle("Type Mismatch");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    @ExceptionHandler(WebhookPayloadException.class)
    public ProblemDetail handleWebhookPayloadException(WebhookPayloadException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, resolveExceptionDetail(ex));
        problemDetail.setTitle("Webhook Error");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalService(ExternalServiceException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, resolveExceptionDetail(ex));
        problemDetail.setTitle("External Service Error");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * "Mindenevő" / Fallback: Olyan kivételeket kap el, amikre nem készült dedikált ExceptionHandler.
     * Ezekből elrejtjük a konkrét okot a külvilág elől (pl. NullPointerException miatt nem esik ki bizalmas kód részlet)
     * Kliens csak egy mezei "An unexpected server error occurred." üzenetet kap és 500-as státuszkódot. (Plusz a TraceID-t amivel mi tudunk logból keresni)
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        // Ennél KÖTEZELŐ logolnunk, hiszen nem vártuk ezt a hibát a rendszerben
        log.error("Unexpected error occurred: ", ex);
        String detail = resolveMessage("error.internal_server_error", "An unexpected server error occurred.");
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail);
        problemDetail.setTitle("Internal Server Error");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    // ------------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------------

    private void enrichProblemDetail(ProblemDetail problemDetail) {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        problemDetail.setProperty("traceId", traceId);
        problemDetail.setProperty("timestamp", java.time.Instant.now());
    }

    private String resolveMessage(String code, String defaultMessage) {
        try {
            return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return defaultMessage;
        }
    }

    /**
     * Ha az exception messageCode-ot tartalmaz, a MessageSource-ból fordítja le a kívánt locale szerint.
     * Ellenkező esetben visszaadja a nyers getMessage() szöveget.
     */
    private String resolveExceptionDetail(AppException ex) {
        if (ex.getMessageCode() != null) {
            return messageSource.getMessage(
                    ex.getMessageCode(),
                    ex.getMessageArgs(),
                    ex.getMessage(),
                    LocaleContextHolder.getLocale()
            );
        }
        return ex.getMessage();
    }
}