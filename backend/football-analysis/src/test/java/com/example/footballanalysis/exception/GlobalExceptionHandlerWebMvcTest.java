package com.example.footballanalysis.exception;

import com.example.footballanalysis.controller.MatchController;
import com.example.footballanalysis.controller.TeamController;
import com.example.footballanalysis.controller.UserController;
import com.example.footballanalysis.service.MatchService;
import com.example.footballanalysis.service.TeamService;
import com.example.footballanalysis.service.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integrációs tesztek a GlobalExceptionHandler HTTP-szintű viselkedésére.
 *
 * ─── MI AZ INTEGRÁCIÓS TESZT (@WebMvcTest)? ──────────────────────────────
 * A @WebMvcTest egy "kvázi-valódi" Spring MVC közeget indít el:
 *  - Betölti a megadott Controller osztályokat (pl. UserController, TeamController)
 *  - Betölti a @RestControllerAdvice hibakezelőket (GlobalExceptionHandler)
 *  - NEM indít adatbázist, NEM tölt be @Service beaneket
 *    → ezeket a MockConfig-ban kézzel definiált Mockito mock-okkal helyettesítjük
 *
 * Az egységteszttől (GlobalExceptionHandlerUnitTest) ez a teszt abban különbözik,
 * hogy itt valódi HTTP kérés/válasz ciklust szimuláló MockMvc-vel dolgozunk.
 * A Jackson JSON deszializáció, a @Valid validáció, az URL routing mind "él":
 * pontosan azt látjuk, amit egy valódi kliens kapna.
 *
 * ─── RFC 7807 ProblemDetail – a hibaválasz szabványos JSON struktúrája ────
 *
 *   {
 *     "type":      "about:blank",
 *     "status":    404,                       ← HTTP státuszkód (int)
 *     "title":     "Resource Not Found",      ← rövid, gépi szöveg
 *     "detail":    "User not found: ...",     ← emberi üzenet
 *     "traceId":   "a1b2c3d4-...",            ← logból kereshetőség
 *     "timestamp": "2026-03-16T12:34:56Z",    ← hiba időpontja
 *     "errors":    { "mező": ["üzenet"] }     ← csak validációs/FieldConflict hibáknál
 *   }
 *
 * A JsonPath szintaxisáról ($.mező, $.errors.email[*]):
 *  - $  → a JSON gyökere
 *  - $.status → a "status" mező
 *  - $.errors.email[*] → az "errors" objektum "email" listájának összes eleme
 */
@WebMvcTest(controllers = {UserController.class, MatchController.class, TeamController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerWebMvcTest.MockConfig.class})
class GlobalExceptionHandlerWebMvcTest {

    /**
     * Mock konfiguráció: mivel a @WebMvcTest nem tölt be @Service beaneket,
     * kézzel adjuk meg őket Mockito mock-ként.
     * A given().willThrow() / willThrow().given() hívásokkal mondjuk meg,
     * hogy az adott teszt esetén mit "csináljon" a mock.
     */
    @TestConfiguration
    static class MockConfig {
        @Bean UserService userService()   { return Mockito.mock(UserService.class); }
        @Bean MatchService matchService() { return Mockito.mock(MatchService.class); }
        @Bean TeamService teamService()   { return Mockito.mock(TeamService.class); }

        /**
         * A tesztek mindig ENGLISH locale-t kapjanak.
         * Megjegyzés: @WebMvcTest-ben az AcceptHeaderLocaleResolver is aktív lehet,
         * ezért a @BeforeAll Locale.setDefault() is szükséges (Hibernate Validator miatt).
         */
        @Bean
        LocaleResolver localeResolver() {
            return new FixedLocaleResolver(Locale.ENGLISH);
        }
    }

    /**
     * A Hibernate Validator üzenet-interpolátora a Locale.getDefault()-ot használja,
     * ha nincs Spring LocaleContextMessageInterpolator bekötve.
     * Ez azt jelenti, hogy a FieldError.getDefaultMessage() a JVM default locale-jával
     * (tipikusan hu_HU fejlesztői gépen) interpolálódna.
     * Az összes teszt determinisztikusságához ENGLISH-re állítjuk.
     */
    @BeforeAll
    static void forceEnglishLocale() {
        Locale.setDefault(Locale.ENGLISH);
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserService userService;
    @Autowired MatchService matchService;
    @Autowired TeamService teamService;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(userService, matchService, teamService);
    }

    // =========================================================================
    // USER – NotFoundException (404)
    // =========================================================================

    @Nested
    @DisplayName("User – NotFoundException (404)")
    class UserNotFoundTests {

        @Test
        @DisplayName("GET /api/users/{id} nem létező ID → 404, helyes ProblemDetail mezők + traceId")
        void getUser_notFound_returns404WithProblemDetail() throws Exception {
            UUID missingId = UUID.randomUUID();
            given(userService.getUser(missingId))
                    .willThrow(new NotFoundException("User not found: " + missingId));

            mockMvc.perform(get("/api/users/{id}", missingId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Resource Not Found"))
                    .andExpect(jsonPath("$.detail").value("User not found: " + missingId))
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").exists())
                    // NotFoundException-nél nincs "errors" mező – csak validációs hibáknál van
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("DELETE /api/users/{id} nem létező ID → 404")
        void deleteUser_notFound_returns404() throws Exception {
            UUID missingId = UUID.randomUUID();
            // Void visszatérésű metódusoknál a willThrow().given() szintaxist kell használni
            // (nem a given().willThrow()-t, mert a void-nak nincs visszatérési értéke)
            willThrow(new NotFoundException("User not found: " + missingId))
                    .given(userService).deleteUser(missingId);

            mockMvc.perform(delete("/api/users/{id}", missingId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Resource Not Found"))
                    .andExpect(jsonPath("$.detail").value("User not found: " + missingId))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }
    }

    // =========================================================================
    // USER – @Valid validációs hibák (400)
    //
    // A @Valid annotáció a UserController POST metódusán van:
    //   public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest req)
    //
    // Ha bármelyik @NotBlank / @Email / @Size megszorítás megbukik,
    // Spring automatikusan MethodArgumentNotValidException-t dob.
    // A handler ezt elkapja és mezőnév szerint csoportosítva adja vissza.
    // =========================================================================

    @Nested
    @DisplayName("User – @Valid validációs hibák (400)")
    class UserValidationTests {

        @Test
        @DisplayName("POST /api/users minden mező üres → 400, minden megsértett mező az errors-ban")
        void createUser_allFieldsEmpty_returns400WithAllFieldErrors() throws Exception {
            String body = """
                    {
                      "email":     "",
                      "firstName": "",
                      "lastName":  "",
                      "password":  "",
                      "role":      "X"
                    }
                    """;

            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Validation Failed"))
                    .andExpect(jsonPath("$.detail").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.email[*]",    hasItem("Email is required.")))
                    .andExpect(jsonPath("$.errors.email[*]",    hasItem("Please provide a valid email address.")))
                    .andExpect(jsonPath("$.errors.firstName[*]", hasItem("First name is required.")))
                    .andExpect(jsonPath("$.errors.firstName[*]", hasItem("First name must be between 2 and 100 characters long.")))
                    .andExpect(jsonPath("$.errors.lastName[*]",  hasItem("Last name is required.")))
                    .andExpect(jsonPath("$.errors.lastName[*]",  hasItem("Last name must be between 2 and 100 characters long.")))
                    .andExpect(jsonPath("$.errors.password[*]",  hasItem("Password is required.")))
                    .andExpect(jsonPath("$.errors.password[*]",  hasItem("Password must be between 6 and 72 characters long.")))
                    .andExpect(jsonPath("$.errors.role[*]",      hasItem("Role must be one of ADMIN, PLAYER, COACH, or FAN.")));
        }

        @Test
        @DisplayName("POST /api/users érvénytelen email formátum → 400, errors.email")
        void createUser_invalidEmailFormat_returns400WithEmailError() throws Exception {
            String body = """
                    {
                      "email":     "not-an-email",
                      "firstName": "John",
                      "lastName":  "Doe",
                      "password":  "secret123",
                      "role":      "PLAYER"
                    }
                    """;

            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email[*]",
                            hasItem("Please provide a valid email address.")));
        }
    }

    // =========================================================================
    // USER – FieldConflictException (409 – errors map-pel)
    //
    // A FieldConflictException-t akkor dobjuk, ha egy üzleti szabály sérül,
    // és tudni akarjuk, hogy MELYIK mezőben van a probléma.
    // Ugyanolyan $.errors.{mező} struktúrát ad, mint a @Valid → frontend egységesen kezeli.
    // =========================================================================

    @Nested
    @DisplayName("User – FieldConflictException (409 – errors map-pel, mint @Valid)")
    class UserConflictTests {

        @Test
        @DisplayName("POST /api/users foglalt email → 409, errors.email tartalmazza az üzenetet")
        void createUser_emailAlreadyInUse_returns409WithFieldErrors() throws Exception {
            String body = """
                    {
                      "email":     "test@example.com",
                      "firstName": "John",
                      "lastName":  "Doe",
                      "password":  "secret123",
                      "role":      "PLAYER"
                    }
                    """;
            given(userService.createUser(any())).willThrow(
                    new FieldConflictException("email",
                            "error.user.email.conflict", new Object[]{"test@example.com"},
                            "Email already in use: test@example.com"));

            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.title").value("Resource Conflict"))
                    .andExpect(jsonPath("$.detail").value("Email already in use: test@example.com"))
                    .andExpect(jsonPath("$.errors.email[*]", hasItem("Email already in use: test@example.com")))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // =========================================================================
    // TEAM – NotFoundException, validáció, ConflictException
    //
    // A TeamController-en keresztül demonstráljuk, hogy:
    //  - NotFoundException → 404 (nincs errors mező)
    //  - @Valid validáció → 400 (van errors mező)
    //  - ConflictException → 409 (NINCS errors mező – csak FieldConflictException-nél van)
    // =========================================================================

    @Nested
    @DisplayName("Team – NotFoundException, @Valid validáció, ConflictException")
    class TeamTests {

        @Test
        @DisplayName("GET /api/teams/{id} nem létező csapat → 404, nincs errors mező")
        void getTeam_notFound_returns404WithoutErrorsMap() throws Exception {
            UUID teamId = UUID.randomUUID();
            given(teamService.getTeam(teamId))
                    .willThrow(new NotFoundException("Team not found: " + teamId));

            mockMvc.perform(get("/api/teams/{id}", teamId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Resource Not Found"))
                    .andExpect(jsonPath("$.detail").value("Team not found: " + teamId))
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("POST /api/teams üres névvel → 400, errors.name tartalmaz üzenetet")
        void createTeam_blankName_returns400ValidationError() throws Exception {
            String body = """
                    {
                      "name": ""
                    }
                    """;

            mockMvc.perform(post("/api/teams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Validation Failed"))
                    .andExpect(jsonPath("$.errors.name[*]", hasItem("Team name is required.")));
        }

        @Test
        @DisplayName("POST /api/teams 100+ karakteres névvel → 400, errors.name max hossz üzenet")
        void createTeam_nameTooLong_returns400WithSizeError() throws Exception {
            String body = String.format("""
                    {
                      "name": "%s"
                    }
                    """, "A".repeat(101));

            mockMvc.perform(post("/api/teams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name[*]",
                            hasItem("Team name must be at most 100 characters long.")));
        }

        @Test
        @DisplayName("POST /api/teams duplikált névvel → 409 ConflictException, NINCS errors mező")
        void createTeam_duplicateName_returns409WithoutErrorsMap() throws Exception {
            // A ConflictException és a FieldConflictException közötti különbség:
            //  - ConflictException: a probléma az egész erőforrásra vonatkozik → NINCS errors mező
            //  - FieldConflictException: pontosan egy mező okozza → VAN errors.{mező} mező
            String body = """
                    {
                      "name":      "Team1",
                      "shortName": "TM1"
                    }
                    """;
            given(teamService.createTeam(any())).willThrow(
                    new ConflictException("A team with this name already exists: Team1"));

            mockMvc.perform(post("/api/teams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.title").value("Resource Conflict"))
                    .andExpect(jsonPath("$.detail").value("A team with this name already exists: Team1"))
                    .andExpect(jsonPath("$.errors").doesNotExist())
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("DELETE /api/teams/{id} nem létező csapat → 404")
        void deleteTeam_notFound_returns404() throws Exception {
            UUID teamId = UUID.randomUUID();
            willThrow(new NotFoundException("Team not found: " + teamId))
                    .given(teamService).deleteTeam(teamId);

            mockMvc.perform(delete("/api/teams/{id}", teamId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Resource Not Found"));
        }

        @Test
        @DisplayName("PUT /api/teams/{id} üres névvel → 400 validációs hiba")
        void updateTeam_blankName_returns400() throws Exception {
            UUID teamId = UUID.randomUUID();
            String body = """
                    {
                      "name": ""
                    }
                    """;

            mockMvc.perform(put("/api/teams/{id}", teamId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name[*]", hasItem("Team name is required.")));
        }
    }

    // =========================================================================
    // MATCH – ExternalServiceException (503) és WebhookPayloadException (400)
    // =========================================================================

    @Nested
    @DisplayName("Match – ExternalServiceException (503) és WebhookPayloadException (400)")
    class MatchExternalErrorTests {

        @Test
        @DisplayName("POST /api/matches/upload → S3/MinIO elérhetetlenség → 503 Service Unavailable")
        void initiateUpload_externalServiceError_returns503() throws Exception {
            String body = """
                    {
                      "originalFilename": "match.mp4"
                    }
                    """;
            given(matchService.initiateMatchUpload(any()))
                    .willThrow(new ExternalServiceException("S3 service unavailable"));

            mockMvc.perform(post("/api/matches/upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.title").value("External Service Error"))
                    .andExpect(jsonPath("$.detail").value("S3 service unavailable"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("POST /api/matches/upload → hibás webhook payload → 400 Webhook Error")
        void initiateUpload_webhookPayloadError_returns400() throws Exception {
            String body = """
                    {
                      "originalFilename": "match.mp4"
                    }
                    """;
            given(matchService.initiateMatchUpload(any()))
                    .willThrow(new WebhookPayloadException("Missing required field 'matchId'"));

            mockMvc.perform(post("/api/matches/upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Webhook Error"))
                    .andExpect(jsonPath("$.detail").value("Missing required field 'matchId'"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("GET /api/matches/{id} nem létező mérkőzés → 404")
        void getMatch_notFound_returns404() throws Exception {
            UUID matchId = UUID.randomUUID();
            given(matchService.getMatchDetails(matchId))
                    .willThrow(new NotFoundException("Match not found: " + matchId));

            mockMvc.perform(get("/api/matches/{id}", matchId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Resource Not Found"));
        }
    }

    // =========================================================================
    // Generikus Exception → 500 (belső részletek elrejtve, biztonsági garancia)
    //
    // Ez a handler fogja el az összes nem várt kivételt.
    // A legfontosabb biztonsági garancia: a stack trace, az osztálynevak, a belső
    // üzenetek SOHA nem kerülnek ki a klienshez – csak egy általános szöveg.
    // =========================================================================

    @Nested
    @DisplayName("Generikus Exception → 500 (belső részletek elrejtve)")
    class GenericExceptionTests {

        @Test
        @DisplayName("NullPointerException a service-ben → 500, belső üzenet NEM látható a kliensnek")
        void nullPointerException_returns500_withGenericDetail() throws Exception {
            given(userService.getAllUsers())
                    .willThrow(new NullPointerException("null at UserRepository.java:42 – INTERNAL"));

            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.title").value("Internal Server Error"))
                    // "INTERNAL", "UserRepository" stb. NEM jelenhet meg a kliensnek:
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("Bármilyen váratlan hiba → traceId és timestamp van a válaszban")
        void anyUnexpectedError_traceIdAndTimestampPresent() throws Exception {
            given(userService.getAllUsers())
                    .willThrow(new IllegalStateException("internal state corrupt"));

            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // =========================================================================
    // Rosszul formázott HTTP kérések – Spring MVC belső hibák
    //
    // Ezeket a Spring ResponseEntityExceptionHandler kezeli (amit örökölünk),
    // de a createProblemDetail() override gondoskodik arról, hogy traceId
    // és timestamp ezekbe is belekerüljön.
    // =========================================================================

    @Nested
    @DisplayName("Rosszul formázott kérések – Spring MVC belső hibák (400)")
    class MalformedRequestTests {

        @Test
        @DisplayName("Rosszul formált JSON body → 400 Bad Request")
        void malformedJson_returns400() throws Exception {
            mockMvc.perform(post("/api/matches/upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.detail").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("POST /api/matches/upload üres originalFilename → 400, errors.originalFilename")
        void blankOriginalFilename_returns400WithValidationError() throws Exception {
            String body = """
                    {
                      "originalFilename": ""
                    }
                    """;

            mockMvc.perform(post("/api/matches/upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.originalFilename[*]",
                            hasItem("Original filename is required.")));
        }

        @Test
        @DisplayName("GET /api/users/{id} UUID-nak nem megfelelő path → 400 Type Mismatch, pontos detail")
        void invalidUuidPathVariable_returns400TypeMismatch() throws Exception {
            mockMvc.perform(get("/api/users/{id}", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Type Mismatch"))
                    .andExpect(jsonPath("$.detail").value("Invalid format for parameter 'id': 'not-a-uuid'."))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("UUID mezőbe nem-UUID string a body-ban → 400 Bad Request (Jackson parse hiba)")
        void invalidUuidInRequestBody_returns400() throws Exception {
            // Jackson nem tudja UUID-vá alakítani a "not-a-uuid" stringet →
            // HttpMessageNotReadableException → Spring alaphandlere fogja el
            String body = """
                    {
                      "originalFilename": "match.mp4",
                      "homeTeamId": "not-a-uuid"
                    }
                    """;

            mockMvc.perform(post("/api/matches/upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // =========================================================================
    // TraceID és timestamp – minden HTTP hibatípusnál jelen kell lenniük
    //
    // Ez a "cross-cutting concern" teszt: nem egy konkrét kivételtípust tesztelünk,
    // hanem azt, hogy MINDEN hibakódon átmegy-e a traceId.
    // Ez azért fontos, mert a traceId az egyetlen kapocs a kliens által látott hiba
    // és a szerver logban lévő részletes stack trace között.
    // =========================================================================

    @Nested
    @DisplayName("TraceID és timestamp – minden hibatípusnál jelen vannak")
    class TraceIdPresenceTests {

        @Test
        @DisplayName("404-es válaszban traceId és timestamp jelen vannak")
        void notFoundResponse_hasTraceIdAndTimestamp() throws Exception {
            UUID id = UUID.randomUUID();
            given(userService.getUser(id)).willThrow(new NotFoundException("not found"));

            mockMvc.perform(get("/api/users/{id}", id))
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("409-es ConflictException válaszban is van traceId")
        void conflictResponse_hasTraceId() throws Exception {
            String body = """
                    {
                      "name": "Arsenal"
                    }
                    """;
            given(teamService.createTeam(any())).willThrow(new ConflictException("already exists"));

            mockMvc.perform(post("/api/teams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("503-as ExternalServiceException válaszban is van traceId")
        void serviceUnavailableResponse_hasTraceId() throws Exception {
            String body = """
                    {
                      "originalFilename": "match.mp4"
                    }
                    """;
            given(matchService.initiateMatchUpload(any()))
                    .willThrow(new ExternalServiceException("service down"));

            mockMvc.perform(post("/api/matches/upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("500-as Internal Server Error válaszban is van traceId – logból megtalálható")
        void internalServerErrorResponse_hasTraceId() throws Exception {
            given(userService.getAllUsers()).willThrow(new RuntimeException("crash"));

            mockMvc.perform(get("/api/users"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }
}
