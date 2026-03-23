package com.example.footballanalysis.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
import java.util.UUID;

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
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TRACE_ID_PROPERTY = "traceId";
    private static final String TIMESTAMP_PROPERTY = "timestamp";
    private static final String ERRORS_PROPERTY = "errors";
    private static final String VALIDATION_DEFAULT_MESSAGE_CODE = "error.validation.default";
    private static final String AUTH_FORBIDDEN_MESSAGE_CODE = "error.auth.forbidden";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
        String traceId = MDC.get(TRACE_ID_PROPERTY);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            log.debug("No traceId found in MDC. Generated new one: {}", traceId);
        }

        // ProblemDetail létrehozása a beépített logika szerint
        ProblemDetail problemDetail = super.createProblemDetail(ex, status, defaultDetail, detailMessageCode, detailMessageArguments, request);

        // További egyedi tulajdonságok hozzácsatolása (ezek bekerülnek a kimenő JSON-be)
        problemDetail.setProperty(TRACE_ID_PROPERTY, traceId);
        problemDetail.setProperty(TIMESTAMP_PROPERTY, java.time.Instant.now());

        return problemDetail;
    }

    /**
     * Minden Spring MVC belső szabályok alapján kezelt hiba esetén meghívódik.
     * Itt egy központi logolást végzünk, hogy a belső hibák se vesszenek el.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // Logolás minden kezelt hibánál
        log.warn("Handling exception: {} - Status: {}", ex.getClass().getSimpleName(), statusCode, ex);
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
        ProblemDetail problemDetail = createProblemDetail(ex, status, "Validation failed", VALIDATION_DEFAULT_MESSAGE_CODE, new Object[0], request);

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
        problemDetail.setProperty(ERRORS_PROPERTY, validationErrors);
        problemDetail.setTitle(resolveMessage(VALIDATION_DEFAULT_MESSAGE_CODE, "Validation Failed"));

        return createResponseEntity(problemDetail, headers, status, request);
    }

    // ------------------------------------------------------------------------
    // JPA / Hibernate Validation Errors (@Validated)
    // ------------------------------------------------------------------------

    /**
     * JPA (Adatbázis entitás) vagy a Service réteg szintjén történő validációkor keletkezik.
     * HTTP 400 Bad Request hibát ad vissza, mivel a kérés tartalma érvénytelen (pl. hiányzó @NotNull mezők).
     * A választ strukturáltan, az érintett mezőkre lebontva adja át.
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

        problemDetail.setProperty(ERRORS_PROPERTY, validationErrors);
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
     * Elkapja az adatbázis szintű natív hibákat (pl. idegenkulcs- vagy egyedi megszorítások megsértése).
     * Általában HTTP 409 Conflict hibát ad vissza (vagy a probléma típusától függően más hibakódot),
     * elrejtve ezáltal a belső SQL kivétel részleteit, és érthető formátumú válaszban jelezve a műveleti ütközést.
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
     * Mező-szintű ütközés (FieldConflictException) esetén hívódik meg.
     * HTTP 409 Conflict állapotkódot ad vissza, kiegészítve a problémás mező pontos megjelölésével.
     * Ezt akkor használjuk, ha egy specifikus adat (pl. egy már foglalt email cím) miatt nem hajtható végre a művelet.
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
     * Saját üzleti logikai ütközések (ConflictException) kezelésére szolgál.
     * HTTP 409 Conflict kódú választ küld vissza a kliensnek.
     * Olyan esetekben dobjuk, amikor az erőforrás aktuális állapota nem teszi lehetővé a műveletet (pl. már létezik a felhasználó).
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
     * A keresett erőforrás hiánya (NotFoundException) esetén fut le.
     * HTTP 404 Not Found hibát eredményez, amely jelzi a kliensnek, 
     * hogy az általa kért azonosítójú elem nem található az adatbázisban vagy a rendszerben.
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, resolveExceptionDetail(ex));
        problemDetail.setTitle("Resource Not Found");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * Általános hibás vagy érvénytelen kérések (BadRequestException) elfogására szolgál.
     * HTTP 400 Bad Request kódot ad vissza a szerver.
     * Olyankor dobjuk ezt a hibát, ha a kérés logikailag vagy formailag helytelen, illetve értelmezhetetlen.
     */
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequestException(BadRequestException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, resolveExceptionDetail(ex));
        problemDetail.setTitle("Bad Request");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * Hiányzó vagy érvénytelen hitelesítési adatok (UnauthorizedException) esetén aktiválódik.
     * HTTP 401 Unauthorized hibakódot állít be a válaszba, 
     * amivel jelzi a kliensnek, hogy a végpont eléréséhez bejelentkezés / megfelelő hitelesítés szükséges.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorizedException(UnauthorizedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, resolveExceptionDetail(ex));
        problemDetail.setTitle("Unauthorized");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * Hitelesített, de nem megfelelő jogosultságokkal rendelkező kérések (AuthorizationDeniedException) esetén fut le.
     * HTTP 403 Forbidden hibával tér vissza, jelezve, hogy a felhasználónak 
     * nincs felhatalmazása a kért művelet végrehajtására.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, resolveMessage(AUTH_FORBIDDEN_MESSAGE_CODE, "Access denied."));
        problemDetail.setTitle("Forbidden");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * URL paraméterek vagy kérdőjeles paraméterek típuskonverziós hibájakor fut le (@PathVariable, @RequestParam eltérés).
     * HTTP 400 Bad Request hibát eredményez, megjelölve a hibás paraméter nevét és elvárt típusát,
     * figyelmeztetve a klienst például arra, hogy szám/UUID helyett szöveget küldött.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = String.format("Invalid format for parameter '%s': '%s'.", ex.getName(), ex.getValue());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problemDetail.setTitle("Type Mismatch");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * Érvénytelen webhook bejövő adatok (WebhookPayloadException) esetén kerül meghívásra.
     * HTTP 400 Bad Request kódot ad vissza, utalva arra, hogy a 
     * külső rendszertől érkező webhook tartalom feldolgozhatatlan számunkra.
     */
    @ExceptionHandler(WebhookPayloadException.class)
    public ProblemDetail handleWebhookPayloadException(WebhookPayloadException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, resolveExceptionDetail(ex));
        problemDetail.setTitle("Webhook Error");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * Külső szolgáltatások (pl. API-k, integrációk) hívásakor fellépő hibákat kezeli (ExternalServiceException).
     * HTTP 503 Service Unavailable hibát ad ki, jelezve a kliensnek, 
     * hogy a funkcióhoz szükséges külső függőség átmenetileg nem elérhető vagy nem válaszol.
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalService(ExternalServiceException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, resolveExceptionDetail(ex));
        problemDetail.setTitle("External Service Error");
        enrichProblemDetail(problemDetail);
        return problemDetail;
    }

    /**
     * Fallback (mindenevő) hibakezelő minden egyéb, dedikáltan nem kezelt kivétel (Exception) esetére.
     * HTTP 500 Internal Server Error kódot ad vissza. Biztonsági okokból elrejti 
     * a konkrét hibaokot és stacktrace-t a kliens elől, csupán egy általános hibaüzenetet közvetít, 
     * míg saját logunkban a problémát lementjük egy TraceID kíséretében.
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
        String traceId = MDC.get(TRACE_ID_PROPERTY);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        problemDetail.setProperty(TRACE_ID_PROPERTY, traceId);
        problemDetail.setProperty(TIMESTAMP_PROPERTY, java.time.Instant.now());
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
            Object[] messageArgs = ex.getMessageArgs();
                return messageSource.getMessage(
                    ex.getMessageCode(),
                    messageArgs == null ? new Object[0] : messageArgs,
                    ex.getMessage(),
                    LocaleContextHolder.getLocale()
                );
        }
        return ex.getMessage();
    }
}