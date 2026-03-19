package com.example.footballanalysis.util;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

@Component
@RequiredArgsConstructor
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Value("${idp.keycloak.client-id}")
    private String clientId;

    @Value("${idp.keycloak.principal-attribute}")
    private String principalAttribute;

    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter =
        new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                jwtGrantedAuthoritiesConverter.convert(jwt).stream(),
                extractResourceRoles(jwt).stream()
            )
            .collect(Collectors.toSet());
        return new JwtAuthenticationToken(
            jwt,
            authorities,
            getPrincipalClaimName(jwt)
        );
    }

    private String getPrincipalClaimName(Jwt jwt) {
        String claimName = JwtClaimNames.SUB;
        if (principalAttribute != null){
            claimName = principalAttribute;
        }
        return jwt.getClaim(claimName);
    }

    private Collection<? extends GrantedAuthority> extractResourceRoles(Jwt jwt) {
        return ofNullable(jwt.<Map<String, Object>>getClaim("resource_access"))
            .map(resourceAccess -> (Map<String, Object>) resourceAccess.get(clientId))
            .map(resource -> (Collection<String>) resource.get("roles"))
            .map(roles -> roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet())
            )
            // If ANY of the maps above return null, it safely drops down to this line!
            .orElseGet(Set::of);
    }

}

