package com.example.footballanalysis.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Egységtesztek (Unit tests) a GlobalExceptionHandler osztályhoz.
 *
 * ─── MI AZ EGYSÉGTESZT? ───────────────────────────────────────────────────
 * Az egységteszt KÖZVETLENÜL hívja meg a tesztelendő osztály metódusait,
 * Spring MVC, HTTP réteg és adatbázis indítása nélkül.
 *
 * Előnyök:
 *  - Nagyon gyors futás (milliszekunderek, nem másodpercek)
 *  - Nehezen HTTP-n keresztül kiváltható kivételeket is tesztelni lehet
 *    (pl. ConstraintViolationException, DataIntegrityViolationException)
 *  - A cause-láncolást, az exception hierarchiát, és az osztályozó logikát
 *    (DatabaseErrorClassifier) is könnyen és pontosan le lehet ellenőrizni
 *
 * ─── HOGYAN MŰKÖDIK? ──────────────────────────────────────────────────────
 * 1. @BeforeEach: minden teszt előtt létrehozunk egy handler példányt
 *    - Valódi GlobalExceptionHandler-t készítünk (nem mockot)
 *    - Egy StaticMessageSource-ba töltjük be a hibakódokat (messages.properties helyett)
 *    - Beállítjuk a locale-t angolra (hogy a tesztek determinisztikusak legyenek)
 *
 * 2. A tesztek közvetlenül meghívják az @ExceptionHandler metódusokat
 *    (pl. handler.handleNotFound(ex)) és a visszaadott ProblemDetail objektumot
 *    ellenőrzik AssertJ assertThat() hívásokkal.
 *
 * 3. A Mockito.mock()-ot ott használjuk, ahol Java interface-eket kell "eljátszani"
 *    (például a ConstraintViolation és Path interface-eket nem lehet sima new-val létrehozni).
 */
@DisplayName("GlobalExceptionHandler unit tesztek")
class GlobalExceptionHandlerUnitTest {

    /** A tesztelendő handler – nem mock, hanem valódi példány. */
    private GlobalExceptionHandler handler;

    /**
     * Minden teszt előtt futó inicializáció.
     * A StaticMessageSource egy egyszerű, in-memory MessageSource implementáció –
     * nem olvas fájlból, közvetlenül adjuk meg a kulcs-üzenet párokat.
     */
    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("error.user.not_found",       Locale.ENGLISH, "User not found: {0}");
        messageSource.addMessage("error.user.email.conflict",  Locale.ENGLISH, "Email already in use: {0}");
        messageSource.addMessage("error.database.unique",      Locale.ENGLISH, "Resource already exists.");
        messageSource.addMessage("error.database.not_null",    Locale.ENGLISH, "Required field is missing.");
        messageSource.addMessage("error.database.foreign_key", Locale.ENGLISH, "Relationship violation.");
        messageSource.addMessage("error.database.constraint",  Locale.ENGLISH, "Database constraint violated.");
        messageSource.addMessage("error.internal_server_error", Locale.ENGLISH, "An unexpected error occurred.");

        // A LocaleContextHolder egy ThreadLocal tároló – azt mondja meg Spring-nek,
        // hogy az aktuális szálban melyik nyelvet kell használni az üzenetek fordításához.
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        handler = new GlobalExceptionHandler(messageSource);
    }

    /** Minden teszt után törli a locale- és MDC-beállítást, hogy ne keveredjenek a tesztek. */
    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
        MDC.clear();
    }

    // =========================================================================
    // NotFoundException → HTTP 404
    // =========================================================================

    /**
     * A @Nested osztály egy logikai csoport – az összetartozó teszteket fogja össze.
     * Csak szervezési célú, nem változtat a tesztek működésén.
     */
    @Nested
    @DisplayName("NotFoundException (404)")
    class NotFoundTests {

        @Test
        @DisplayName("Fallback üzenettel → 404 Not Found, helyes title és detail")
        void fallbackMessage_returns404() {
            NotFoundException ex = new NotFoundException("User not found: abc");

            ProblemDetail pd = handler.handleNotFound(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(pd.getTitle()).isEqualTo("Resource Not Found");
            assertThat(pd.getDetail()).isEqualTo("User not found: abc");
        }

        @Test
        @DisplayName("i18n messageCode esetén a MessageSource fordítja le az üzenetet ('{0}' → id értéke)")
        void withMessageCode_returnsLocalizedDetail() {
            UUID id = UUID.randomUUID();
            NotFoundException ex = new NotFoundException(
                    "error.user.not_found", new Object[]{id}, "User not found: " + id);

            ProblemDetail pd = handler.handleNotFound(ex);

            // A StaticMessageSource feloldja: "User not found: {0}" → "User not found: <id>"
            assertThat(pd.getDetail()).isEqualTo("User not found: " + id);
        }

        @Test
        @DisplayName("Nincs 'errors' mező – azt csak FieldConflictException adja")
        void noErrorsProperty_inResponse() {
            ProblemDetail pd = handler.handleNotFound(new NotFoundException("not found"));

            assertThat(pd.getProperties()).doesNotContainKey("errors");
        }
    }

    // =========================================================================
    // ConflictException → HTTP 409 (mező-szintű info NÉLKÜL)
    // =========================================================================

    @Nested
    @DisplayName("ConflictException (409 – errors mező nélkül)")
    class ConflictTests {

        @Test
        @DisplayName("→ 409 Conflict, helyes title, nincs errors map")
        void returns409WithoutErrorsMap() {
            ConflictException ex =
                    new ConflictException("A team with this name already exists: Arsenal");

            ProblemDetail pd = handler.handleConflict(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(pd.getTitle()).isEqualTo("Resource Conflict");
            assertThat(pd.getDetail()).isEqualTo("A team with this name already exists: Arsenal");
            // Szándékosan nincs "errors" – a frontend a $.detail-t olvassa
            assertThat(pd.getProperties()).doesNotContainKey("errors");
        }
    }

    // =========================================================================
    // FieldConflictException → HTTP 409 (mező-szintű info-VAL)
    // Pont ugyanolyan $.errors struktúra, mint a @Valid validációs hibáknál –
    // ezért a frontend egységesen tudja kezelni mindkét esetet.
    // =========================================================================

    @Nested
    @DisplayName("FieldConflictException (409 – errors.{mező} struktúrával)")
    class FieldConflictTests {

        @Test
        @DisplayName("→ 409, errors.email lista tartalmazza a hibaüzenetet")
        void returns409WithFieldLevelErrorsMap() {
            FieldConflictException ex = new FieldConflictException(
                    "email",
                    "error.user.email.conflict", new Object[]{"test@example.com"},
                    "Email already in use: test@example.com");

            ProblemDetail pd = handler.handleFieldConflict(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(pd.getTitle()).isEqualTo("Resource Conflict");
            assertThat(pd.getDetail()).isEqualTo("Email already in use: test@example.com");

            @SuppressWarnings("unchecked")
            Map<String, List<String>> errors =
                    (Map<String, List<String>>) pd.getProperties().get("errors");
            assertThat(errors).containsKey("email");
            assertThat(errors.get("email")).contains("Email already in use: test@example.com");
        }

        @Test
        @DisplayName("getField() visszaadja, melyik mezőben van az ütközés")
        void getField_returnsStoredFieldName() {
            FieldConflictException ex = new FieldConflictException(
                    "username", null, null, "Username already taken");

            assertThat(ex.getField()).isEqualTo("username");
        }

        @Test
        @DisplayName("FieldConflictException a ConflictException és AppException leszármazottja")
        void isInstanceOfConflictExceptionAndAppException() {
            FieldConflictException ex = new FieldConflictException(
                    "email", null, null, "conflict");

            assertThat(ex).isInstanceOf(ConflictException.class);
            assertThat(ex).isInstanceOf(AppException.class);
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }

    // =========================================================================
    // BadRequestException → HTTP 400
    // =========================================================================

    @Nested
    @DisplayName("BadRequestException (400)")
    class BadRequestTests {

        @Test
        @DisplayName("→ 400 Bad Request, helyes title és detail")
        void returns400() {
            ProblemDetail pd = handler.handleBadRequestException(
                    new BadRequestException("Unknown role: SUPERUSER"));

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(pd.getTitle()).isEqualTo("Bad Request");
            assertThat(pd.getDetail()).isEqualTo("Unknown role: SUPERUSER");
        }
    }

    // =========================================================================
    // ExternalServiceException → HTTP 503 + cause-láncolás
    //
    // A cause-láncolás azért fontos, mert:
    //  - A szerver logban látszódik az EREDETI hiba (pl. "Connection refused to MinIO:9000")
    //  - A KLIENS csak a magas szintű üzenetet ("External service error") látja
    //  - Ha nem láncolnánk be, a logok elveszítenék a kontextust
    // =========================================================================

    @Nested
    @DisplayName("ExternalServiceException (503) – cause-láncolás")
    class ExternalServiceTests {

        @Test
        @DisplayName("→ 503 Service Unavailable, helyes title")
        void returns503() {
            ProblemDetail pd = handler.handleExternalService(
                    new ExternalServiceException("S3 upload failed"));

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
            assertThat(pd.getTitle()).isEqualTo("External Service Error");
            assertThat(pd.getDetail()).isEqualTo("S3 upload failed");
        }

        @Test
        @DisplayName("getCause() megőrzi az eredeti kivételt → a fix verifikálása (volt: initCause)")
        void causeConstructor_preservesCauseForLogging() {
            RuntimeException originalCause = new RuntimeException("Connection refused to MinIO:9000");
            ExternalServiceException ex =
                    new ExternalServiceException("MinIO nem érhető el", originalCause);

            // Ha initCause()-t használnánk (a régi kód), ez NULLT adna vissza
            // bizonyos JVM implementációkon, ha a super() üres üzenettel volt hívva.
            assertThat(ex.getCause()).isSameAs(originalCause);
            assertThat(ex.getCause().getMessage()).isEqualTo("Connection refused to MinIO:9000");
        }

        @Test
        @DisplayName("A handler a cause szövegét NEM adja ki a kliensnek (biztonsági garancia)")
        void handler_doesNotLeakCauseMessageToClient() {
            RuntimeException internalError = new RuntimeException("Internal DB credentials exposed!");
            ExternalServiceException ex =
                    new ExternalServiceException("RabbitMQ unavailable", internalError);

            ProblemDetail pd = handler.handleExternalService(ex);

            // A kliensnek NEM jelenik meg a cause szövege:
            assertThat(pd.getDetail()).doesNotContain("DB credentials");
            assertThat(pd.getDetail()).isEqualTo("RabbitMQ unavailable");
            // Szerver oldalon viszont elérhető marad a logolónak:
            assertThat(ex.getCause()).isSameAs(internalError);
        }
    }

    // =========================================================================
    // WebhookPayloadException → HTTP 400 + cause-láncolás
    // =========================================================================

    @Nested
    @DisplayName("WebhookPayloadException (400) – cause-láncolás")
    class WebhookPayloadTests {

        @Test
        @DisplayName("→ 400 Webhook Error, helyes title")
        void returns400() {
            ProblemDetail pd = handler.handleWebhookPayloadException(
                    new WebhookPayloadException("Missing 'status' field in webhook payload"));

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(pd.getTitle()).isEqualTo("Webhook Error");
            assertThat(pd.getDetail()).isEqualTo("Missing 'status' field in webhook payload");
        }

        @Test
        @DisplayName("getCause() megőrzi az eredeti parse kivételt → a fix verifikálása")
        void causeConstructor_preservesCause() {
            RuntimeException parseError = new RuntimeException("Unexpected token at position 42");
            WebhookPayloadException ex =
                    new WebhookPayloadException("Invalid JSON payload", parseError);

            assertThat(ex.getCause()).isSameAs(parseError);
            assertThat(ex.getMessage()).isEqualTo("Invalid JSON payload");
        }
    }

    // =========================================================================
    // Generikus Exception → HTTP 500 (fallback "mindenevő" handler)
    //
    // Ez a handler fogja el az ÖSSZES nem várt kivételt (NullPointerException stb.)
    // A legfontosabb biztonsági elem: belső részletek (stack trace, service nevek)
    // soha nem kerülnek ki a kliensnek.
    // =========================================================================

    @Nested
    @DisplayName("Generikus Exception fallback (500 – belső részletek elrejtve)")
    class GenericExceptionTests {

        @Test
        @DisplayName("RuntimeException → 500, kliens csak általános üzenetet kap")
        void runtimeException_returns500_withGenericMessage() {
            RuntimeException ex = new RuntimeException(
                    "NullPointer in UserService.getUser – INTERNAL DETAIL");

            ProblemDetail pd = handler.handleGeneric(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
            assertThat(pd.getTitle()).isEqualTo("Internal Server Error");
            // A belső részlet nem szivárog ki:
            assertThat(pd.getDetail()).doesNotContain("UserService");
            assertThat(pd.getDetail()).doesNotContain("INTERNAL DETAIL");
            assertThat(pd.getDetail()).isEqualTo("An unexpected error occurred.");
        }

        @Test
        @DisplayName("NullPointerException → 500, null pointer szöveg nem kerül a klienshez")
        void nullPointerException_returns500_safely() {
            NullPointerException ex = new NullPointerException("null at UserService.java:34");

            ProblemDetail pd = handler.handleGeneric(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
            assertThat(pd.getDetail()).isEqualTo("An unexpected error occurred.");
            assertThat(pd.getDetail()).doesNotContain("UserService");
        }

        @Test
        @DisplayName("IllegalStateException → 500, a belső állapot leírása sem kerül ki")
        void illegalStateException_returns500_safely() {
            IllegalStateException ex =
                    new IllegalStateException("Queue full – internal buffer capacity exceeded");

            ProblemDetail pd = handler.handleGeneric(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
            assertThat(pd.getDetail()).isEqualTo("An unexpected error occurred.");
        }
    }

    // =========================================================================
    // ConstraintViolationException → HTTP 400
    //
    // Ez a kivételosztály AKKOR keletkezik, amikor JPA entitáson vagy
    // @Validated service beanen validálási hiba történik – nem controller szinten.
    // Nem tévesztendő össze a @Valid-tól jövő MethodArgumentNotValidException-nel!
    //
    // A mockolás oka: a ConstraintViolation<T> és Path interface-eket nem lehet
    // sima new-val létrehozni, ezért Mockito-val "eljátsszuk" őket.
    // =========================================================================

    @Nested
    @DisplayName("ConstraintViolationException (JPA / @Validated service validáció → 400)")
    class ConstraintViolationTests {

        @Test
        @DisplayName("Egy megsértett mező → 400, az errors map tartalmazza a mezőt és üzenetét")
        void singleViolation_returns400WithFieldErrors() {
            ConstraintViolationException ex = new ConstraintViolationException(
                    Set.of(mockViolation("email", "must not be blank")));

            ProblemDetail pd = handler.handleConstraintViolation(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(pd.getTitle()).isEqualTo("Validation Failed");

            @SuppressWarnings("unchecked")
            Map<String, List<String>> errors =
                    (Map<String, List<String>>) pd.getProperties().get("errors");
            assertThat(errors).containsKey("email");
            assertThat(errors.get("email")).contains("must not be blank");
        }

        @Test
        @DisplayName("Több megsértett mező → mindkét mező benne van az errors map-ben")
        void multipleViolations_allFieldsPresentInErrors() {
            ConstraintViolationException ex = new ConstraintViolationException(Set.of(
                    mockViolation("firstName", "must not be blank"),
                    mockViolation("email", "must be a valid email address")));

            ProblemDetail pd = handler.handleConstraintViolation(ex);

            @SuppressWarnings("unchecked")
            Map<String, List<String>> errors =
                    (Map<String, List<String>>) pd.getProperties().get("errors");
            assertThat(errors).containsKeys("firstName", "email");
        }

        @Test
        @DisplayName("Ugyanarra a mezőre több megszorítás is megsérülhet → mindkét üzenet megjelenik")
        void multipleViolationsOnSameField_allMessagesPresent() {
            ConstraintViolation<?> v1 = mockViolation("password", "must not be blank");
            ConstraintViolation<?> v2 = mockViolation("password", "size must be between 6 and 72");
            ConstraintViolationException ex = new ConstraintViolationException(Set.of(v1, v2));

            ProblemDetail pd = handler.handleConstraintViolation(ex);

            @SuppressWarnings("unchecked")
            Map<String, List<String>> errors =
                    (Map<String, List<String>>) pd.getProperties().get("errors");
            assertThat(errors.get("password"))
                    .contains("must not be blank", "size must be between 6 and 72");
        }

        /**
         * Segédmetódus: egy hamis (mock) ConstraintViolation<T> létrehozása.
         *
         * Miért kell mockolni?
         * A ConstraintViolation és Path interface-ek (Jakarta Validation API) részei.
         * Csak a Jakarta Validator implementáció (Hibernate Validator) hozza létre
         * ezeket valódi Bean Validation futtatáskor – mi ezt teszt-közegből nem tudjuk
         * könnyen előállítani. Mockito-val megadjuk, mit adjon vissza a getPropertyPath()
         * és getMessage() hívásokra, anélkül, hogy valódi validációt kellene futtatnunk.
         */
        private ConstraintViolation<?> mockViolation(String fieldName, String message) {
            ConstraintViolation<?> violation = mock(ConstraintViolation.class);
            Path path = mock(Path.class);
            Path.Node node = mock(Path.Node.class);
            when(node.getName()).thenReturn(fieldName);
            // A Path Iterable<Path.Node>, a handler for-each ciklussal iterálja végig
            when(path.iterator()).thenReturn(List.<Path.Node>of(node).iterator());
            when(violation.getPropertyPath()).thenReturn(path);
            when(violation.getMessage()).thenReturn(message);
            return violation;
        }
    }

    // =========================================================================
    // DataIntegrityViolationException → Adatbázis hiba osztályozása
    //
    // Amikor Hibernate/Spring Data egy adatbázis constraint sértést (UNIQUE, NOT NULL,
    // FOREIGN KEY) tapasztal, ezt a Spring DataIntegrityViolationException-be csomagolja.
    // A DatabaseErrorClassifier ezt elemzi ki, és emberi üzenetre fordítja.
    //
    // Besorolás sorrendje:
    //  1. SQL State kód alapján (pl. 23505 = PostgreSQL UNIQUE violation)
    //  2. Szöveg-alapú fallback, ha nincs SQL state (pl. régi JDBC driverek)
    // =========================================================================

    @Nested
    @DisplayName("DataIntegrityViolationException – SQL state és szöveg-alapú osztályozás")
    class DataIntegrityTests {

        @Test
        @DisplayName("UNIQUE constraint (SQL state 23505) → 409 Conflict")
        void uniqueViolation_bySqlState_returns409() {
            DataIntegrityViolationException ex = wrap(new SQLException("dup key", "23505"));

            ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(pd.getTitle()).isEqualTo("Database Error");
            assertThat(pd.getDetail()).isEqualTo("Resource already exists.");
        }

        @Test
        @DisplayName("NOT NULL constraint (SQL state 23502) → 400 Bad Request")
        void notNullViolation_bySqlState_returns400() {
            DataIntegrityViolationException ex = wrap(new SQLException("null val", "23502"));

            ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(pd.getDetail()).isEqualTo("Required field is missing.");
        }

        @Test
        @DisplayName("FOREIGN KEY constraint (SQL state 23503) → 409 Conflict")
        void foreignKeyViolation_bySqlState_returns409() {
            DataIntegrityViolationException ex = wrap(new SQLException("fk violation", "23503"));

            ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(pd.getDetail()).isEqualTo("Relationship violation.");
        }

        @Test
        @DisplayName("Ismeretlen 23xxx SQL state (pl. 23514 CHECK) → 409 általános constraint hiba")
        void unknownConstraintSqlState23xxx_returns409Generic() {
            DataIntegrityViolationException ex = wrap(new SQLException("check fail", "23514"));

            ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(pd.getDetail()).isEqualTo("Database constraint violated.");
        }

        @Test
        @DisplayName("Nincs SQL state, 'duplicate key' szöveg → 409 (szöveg-alapú fallback)")
        void noSqlState_duplicateKeyText_returns409() {
            // Szimulálunk egy JDBC drivert, ami nem küld SQL state-et,
            // csak a hibaüzenetbe írja bele az okot.
            RuntimeException rootCause = new RuntimeException(
                    "ERROR: duplicate key value violates unique constraint \"users_email_key\"");
            DataIntegrityViolationException ex =
                    new DataIntegrityViolationException("constraint violation", rootCause);

            ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        }

        @Test
        @DisplayName("Nincs SQL state, 'null value in column' szöveg → 400 (szöveg-alapú fallback)")
        void noSqlState_nullValueText_returns400() {
            RuntimeException rootCause = new RuntimeException(
                    "ERROR: null value in column \"name\" violates not-null constraint");
            DataIntegrityViolationException ex =
                    new DataIntegrityViolationException("constraint violation", rootCause);

            ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }

        @Test
        @DisplayName("Nincs SQL state, 'foreign key constraint' szöveg → 409 (szöveg-alapú fallback)")
        void noSqlState_foreignKeyText_returns409() {
            RuntimeException rootCause = new RuntimeException(
                    "ERROR: insert or update on table violates foreign key constraint");
            DataIntegrityViolationException ex =
                    new DataIntegrityViolationException("constraint violation", rootCause);

            ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        }

        /**
         * Segédmetódus: SQLException becsomagolása DataIntegrityViolationException-be.
         * Így szimulálható, ahogy a Spring JPA réteg becsomagolja az adatbázis kivételeket.
         */
        private DataIntegrityViolationException wrap(SQLException sqlEx) {
            return new DataIntegrityViolationException("constraint violation", sqlEx);
        }
    }

    // =========================================================================
    // TraceID – minden ProblemDetail válaszban jelen kell lennie
    //
    // A traceId azért fontos, mert:
    //  - A kliens hibajelentésben tudja küldeni ("a hibánál ezt a traceId-t kaptam")
    //  - A szerver logban a traceId alapján meg lehet találni az egész kérést
    //  - Ez köti össze a kliens hibát a szerver logbejegyzésekkel
    // =========================================================================

    @Nested
    @DisplayName("TraceID és timestamp – minden hibában jelen vannak")
    class TraceIdTests {

        @Test
        @DisplayName("Ha MDC-ben nincs traceId, UUID-t generál a handler")
        void noMdcTraceId_generatesRandomUuid() {
            ProblemDetail pd = handler.handleNotFound(new NotFoundException("not found"));

            Object traceId = pd.getProperties().get("traceId");
            assertThat(traceId).isNotNull().isInstanceOf(String.class);
            // UUID formátum: 8-4-4-4-12 → összesen 36 karakter
            assertThat((String) traceId).hasSize(36);
        }

        @Test
        @DisplayName("Ha MDC-ben van traceId (Request Filterből), azt veszi át – nem generál újat")
        void mdcTraceId_isUsedInProblemDetail() {
            // A valódi alkalmazásban egy HTTP filter tölti be a traceId-t az MDC-be
            MDC.put("traceId", "trace-from-request-filter-abc123");
            try {
                ProblemDetail pd = handler.handleNotFound(new NotFoundException("not found"));

                assertThat(pd.getProperties().get("traceId"))
                        .isEqualTo("trace-from-request-filter-abc123");
            } finally {
                MDC.remove("traceId");
            }
        }

        @Test
        @DisplayName("timestamp mező Instant típusú (ISO-8601 formátum JSON-ben)")
        void timestampProperty_isInstant() {
            ProblemDetail pd = handler.handleGeneric(new RuntimeException("error"));

            assertThat(pd.getProperties().get("timestamp"))
                    .isNotNull()
                    .isInstanceOf(java.time.Instant.class);
        }

        @Test
        @DisplayName("500-as hibánál is van traceId (hogy a logból megtalálhassa a fejlesztő)")
        void genericException_alsoContainsTraceId() {
            ProblemDetail pd = handler.handleGeneric(new RuntimeException("crash"));

            assertThat(pd.getProperties().get("traceId")).isNotNull();
        }

        @Test
        @DisplayName("DataIntegrityViolation hibánál is van traceId és timestamp")
        void dataIntegrityViolation_alsoHasTraceIdAndTimestamp() {
            DataIntegrityViolationException ex =
                    new DataIntegrityViolationException("constraint", new SQLException("dup", "23505"));

            ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

            assertThat(pd.getProperties()).containsKey("traceId");
            assertThat(pd.getProperties()).containsKey("timestamp");
        }
    }

    // =========================================================================
    // AppException öröklési hierarchia és konstruktorok
    // =========================================================================

    @Nested
    @DisplayName("AppException hierarchia és konstruktorok")
    class HierarchyAndConstructorTests {

        @Test
        @DisplayName("NotFoundException → AppException → RuntimeException (unchecked)")
        void notFoundException_fullHierarchy() {
            NotFoundException ex = new NotFoundException("not found");

            assertThat(ex).isInstanceOf(AppException.class);
            assertThat(ex).isInstanceOf(RuntimeException.class);
            // Unchecked: nem kell throws deklarálni, "felfelé buborékol" a GlobalExceptionHandler-ig
            // nem közvetlenül Throwable-ből öröklődik – a szuperosztály AppException, nem Throwable
            assertThat(ex.getClass().getSuperclass()).isNotEqualTo(Throwable.class);
        }

        @Test
        @DisplayName("Minden AppException leszármazott RuntimeException (unchecked kivétel)")
        void allAppExceptionsAreUnchecked() {
            assertThat(new NotFoundException("e")).isInstanceOf(RuntimeException.class);
            assertThat(new ConflictException("e")).isInstanceOf(RuntimeException.class);
            assertThat(new BadRequestException("e")).isInstanceOf(RuntimeException.class);
            assertThat(new ExternalServiceException("e")).isInstanceOf(RuntimeException.class);
            assertThat(new WebhookPayloadException("e")).isInstanceOf(RuntimeException.class);
            assertThat(new FieldConflictException("f", null, null, "e"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Fallback konstruktornál messageCode null, messageArgs üres tömb")
        void fallbackOnlyConstructor_messageCodeNullAndArgsEmpty() {
            NotFoundException ex = new NotFoundException("just a fallback message");

            assertThat(ex.getMessageCode()).isNull();
            assertThat(ex.getMessageArgs()).isEmpty();
            assertThat(ex.getMessage()).isEqualTo("just a fallback message");
        }

        @Test
        @DisplayName("messageCode + args konstruktornál mindkét érték helyesen tárolódik")
        void messageCodeConstructor_storesCodeAndArgs() {
            Object[] args = {"john@example.com"};
            NotFoundException ex = new NotFoundException(
                    "error.user.not_found", args, "User not found: john@example.com");

            assertThat(ex.getMessageCode()).isEqualTo("error.user.not_found");
            assertThat(ex.getMessageArgs()).containsExactly("john@example.com");
            assertThat(ex.getMessage()).isEqualTo("User not found: john@example.com");
        }

        @Test
        @DisplayName("Null args tömb esetén messageArgs üres tömbbé konvertálódik (nem null)")
        void nullArgs_convertedToEmptyArray() {
            NotFoundException ex = new NotFoundException("error.user.not_found", null, "fallback");

            // Null helyett üres tömb → elkerüljük a NullPointerException-t a handler-ben
            assertThat(ex.getMessageArgs()).isNotNull().isEmpty();
        }
    }
}
