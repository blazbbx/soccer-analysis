package com.example.footballanalysis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(RoutePermissionsConfig.class)
public class SecurityConfig {

    @Bean
    @ConditionalOnMissingBean(Converter.class)
    public Converter<Jwt, AbstractAuthenticationToken> defaultJwtAuthenticationConverter() {
        return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RoutePermissionsConfig permissionsConfig, Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
        http.authorizeHttpRequests(auth -> {
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
