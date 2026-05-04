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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.footballanalysis.repository.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
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
    private final UserRepository userRepository;

    @Value("${react-app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    public JwtUserSyncFilter jwtUserSyncFilter() {
        return new JwtUserSyncFilter(userRepository);
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
            auth.requestMatchers(
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/webjars/**"
            ).permitAll();
            
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
