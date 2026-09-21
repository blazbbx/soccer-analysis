package com.example.footballanalysis.controller;

import com.example.footballanalysis.config.SecurityConfig;
import com.example.footballanalysis.service.MatchIngestionOrchestrator;
import com.example.footballanalysis.service.UserRoleSyncService;
import com.example.footballanalysis.util.KeycloakRoleConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MinioWebhookController.class)
@Import({SecurityConfig.class, KeycloakRoleConverter.class, MinioWebhookControllerWebMvcTest.MockConfig.class})
@DisplayName("MinIO webhook (security filters on)")
class MinioWebhookControllerWebMvcTest {

    private static final String WEBHOOK_TOKEN = "Bearer dev-minio-webhook-token";
    private static final String PAYLOAD = """
            {"EventName":"s3:ObjectCreated:Put","Records":[]}
            """;

    @TestConfiguration
    static class MockConfig {
        @Bean MatchIngestionOrchestrator matchIngestionOrchestrator() {
            return Mockito.mock(MatchIngestionOrchestrator.class);
        }
        @Bean UserRoleSyncService userRoleSyncService() {
            return Mockito.mock(UserRoleSyncService.class);
        }
        @Bean LocaleResolver localeResolver() {
            return new FixedLocaleResolver(Locale.ENGLISH);
        }
    }

    @BeforeAll
    static void forceEnglishLocale() {
        Locale.setDefault(Locale.ENGLISH);
    }

    @Autowired MockMvc mockMvc;
    @Autowired MatchIngestionOrchestrator matchIngestionOrchestrator;

    @BeforeEach
    void resetOrchestrator() {
        Mockito.reset(matchIngestionOrchestrator);
    }

    @Test
    void staticBearerToken_isNotTreatedAsJwt_andReachesController() throws Exception {
        mockMvc.perform(post("/api/webhooks/minio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", WEBHOOK_TOKEN)
                        .content(PAYLOAD))
                .andExpect(status().isOk());

        verify(matchIngestionOrchestrator).triggerProcessingPipeline(any());
    }

    @Test
    void wrongWebhookToken_returns401() throws Exception {
        mockMvc.perform(post("/api/webhooks/minio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer not-the-webhook-token")
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(matchIngestionOrchestrator);
    }

    @Test
    void missingAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/webhooks/minio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(matchIngestionOrchestrator);
    }
}
