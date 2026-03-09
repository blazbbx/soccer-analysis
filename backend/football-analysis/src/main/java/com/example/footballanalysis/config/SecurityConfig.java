package com.example.footballanalysis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for local testing
                .authorizeHttpRequests(auth -> auth
                        // TEMPORARY: Allow anyone to hit the videos and webhooks endpoints
                        //.requestMatchers("/api/matches/**", "/api/webhooks/**").permitAll()
                        // Everything else requires authentication
                        //.anyRequest().authenticated()
                        .anyRequest().permitAll()
                );
        // .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {})); // We will uncomment this when Keycloak is ready

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
