package com.example.footballanalysis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(RoutePermissionsConfig.class)
public class SecurityConfig {
    @Value("${react-app.cors.allowed-origins}")
    private String[] allowedOrigins;

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
            if (permissionsConfig.routePermissions() != null) {
                for (var permission : permissionsConfig.routePermissions()) {

                    if (permission.isPublic()) {
                        auth.requestMatchers(permission.path()).permitAll();
                    } else {
                        auth.requestMatchers(permission.path())
                            .hasAnyRole(permission.roles().toArray(new String[0]));
                    }
                }
            }
            auth.anyRequest().permitAll();
        });
        http.csrf(AbstractHttpConfigurer::disable)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
