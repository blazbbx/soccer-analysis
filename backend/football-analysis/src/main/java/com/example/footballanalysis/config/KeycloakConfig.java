package com.example.footballanalysis.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "idp.provider", havingValue = "keycloak")
public class KeycloakConfig {

    @Bean
    public Keycloak keycloak( // ezt használd majd user creation-nél a service-ben
        @Value("${idp.keycloak.server-url}") String serverUrl,
        @Value("${idp.keycloak.realm}") String realm,
        @Value("${idp.keycloak.admin-client.id}") String clientId,
        @Value("${idp.keycloak.admin-client.secret}") String clientSecret)
    {
        return KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build();
    }
}
