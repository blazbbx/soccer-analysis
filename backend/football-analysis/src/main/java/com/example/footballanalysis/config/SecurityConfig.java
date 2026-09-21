package com.example.footballanalysis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import com.example.footballanalysis.service.UserRoleSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(RoutePermissionsConfig.class)
public class SecurityConfig {
    private final UserRoleSyncService userRoleSyncService;

    @Value("${react-app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean swaggerUiEnabled;

    @Value("${idp.keycloak.client-id}")
    private String keycloakClientId;

    // MinIO webhook uses `Authorization: Bearer <static token>`, not a JWT.
    // If the resource-server resolver sees that header it tries to decode it as
    // a Keycloak JWT, fails, and returns 401 before the controller can check
    // the shared secret. Skip JWT resolution on that path only.
    private static final BearerTokenResolver DEFAULT_BEARER_TOKEN_RESOLVER =
            new DefaultBearerTokenResolver();

    @Bean
    public JwtUserSyncFilter jwtUserSyncFilter() {
        return new JwtUserSyncFilter(userRoleSyncService, keycloakClientId);
    }

    @Bean
    @ConditionalOnMissingBean(Converter.class)
    public Converter<Jwt, AbstractAuthenticationToken> defaultJwtAuthenticationConverter() {
        return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RoutePermissionsConfig permissionsConfig, Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
        http
            //CORS engedélyezése
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(auth -> {
            if (swaggerUiEnabled) {
                auth.requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/webjars/**"
                ).authenticated();
            }

            if (permissionsConfig.routePermissions() != null) {
                for (var permission : permissionsConfig.routePermissions()) {
                    var method = permission.method();
                    var hasMethod = method != null && !method.isBlank();
                    var matcherMethod = hasMethod ? HttpMethod.valueOf(method.trim().toUpperCase(Locale.ROOT)) : null;

                    if (permission.isPublic()) {
                        if (hasMethod) {
                            auth.requestMatchers(matcherMethod, permission.path()).permitAll();
                        } else {
                            auth.requestMatchers(permission.path()).permitAll();
                        }
                    } else {
                        if (hasMethod) {
                            auth.requestMatchers(matcherMethod, permission.path())
                                .hasAnyRole(permission.roles().toArray(new String[0]));
                        } else {
                            auth.requestMatchers(permission.path())
                                .hasAnyRole(permission.roles().toArray(new String[0]));
                        }
                    }
                }
            }
            auth.anyRequest().authenticated();
        });
        http.csrf(AbstractHttpConfigurer::disable)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .bearerTokenResolver(this::resolveBearerToken)
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterAfter(jwtUserSyncFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    //CORS SZABÁLYOK
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));

        configuration.setAllowCredentials(true);

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/webhooks/")) {
            return null;
        }
        return DEFAULT_BEARER_TOKEN_RESOLVER.resolve(request);
    }
}
