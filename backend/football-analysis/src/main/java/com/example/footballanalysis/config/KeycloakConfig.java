package com.example.footballanalysis.config;

import jakarta.ws.rs.client.Client;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.ClientBuilderWrapper;
import org.keycloak.admin.client.JacksonProvider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnProperty(name = "idp.provider", havingValue = "keycloak")
public class KeycloakConfig {

    @Bean
    public Keycloak keycloak( // ezt használd majd user creation-nél a service-ben
        @Value("${idp.keycloak.server-url}") String serverUrl,
        @Value("${idp.keycloak.realm}") String realm,
        @Value("${idp.keycloak.admin-client.id}") String clientId,
        @Value("${idp.keycloak.admin-client.secret}") String clientSecret,
        @Value("${idp.keycloak.admin-client.connect-timeout-ms:2000}") long connectTimeoutMs,
        @Value("${idp.keycloak.admin-client.read-timeout-ms:5000}") long readTimeoutMs)
    {
        return KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .resteasyClient(httpClient(connectTimeoutMs, readTimeoutMs))
            .build();
    }

    /**
     * The same client Keycloak builds by default (its JSON provider included), with time limits added.
     * Without them a Keycloak that accepts the connection but never answers would hold the calling request.
     */
    static Client httpClient(long connectTimeoutMs, long readTimeoutMs) {
        return ClientBuilderWrapper.create(null, false)
            .register(JacksonProvider.class, 100)
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .build();
    }
}
