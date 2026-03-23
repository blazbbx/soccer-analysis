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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        Collection<GrantedAuthority> authorities = new HashSet<>();
        Collection<GrantedAuthority> defaultAuthorities = jwtGrantedAuthoritiesConverter.convert(jwt);
        if (defaultAuthorities != null) {
            authorities.addAll(defaultAuthorities);
        }
        authorities.addAll(extractRealmRoles(jwt));
        authorities.addAll(extractResourceRoles(jwt));
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
        Object resourceAccessClaim = jwt.getClaim("resource_access");
        if (!(resourceAccessClaim instanceof Map<?, ?> resourceAccess)) {
            return Set.of();
        }

        Object clientRolesObject = resourceAccess.get(clientId);
        if (!(clientRolesObject instanceof Map<?, ?> clientRolesMap)) {
            return Set.of();
        }

        return extractRoleAuthorities(clientRolesMap.get("roles"));
    }

    private Collection<? extends GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Object realmAccessClaim = jwt.getClaim("realm_access");
        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            return Set.of();
        }

        return extractRoleAuthorities(realmAccess.get("roles"));
    }

    private SimpleGrantedAuthority toRoleAuthority(String role) {
        return new SimpleGrantedAuthority("ROLE_" + role);
    }

    private Set<GrantedAuthority> extractRoleAuthorities(Object rolesObject) {
        if (!(rolesObject instanceof Collection<?> roles)) {
            return Set.of();
        }

        Set<GrantedAuthority> authorities = new HashSet<>();
        for (Object roleObject : roles) {
            if (roleObject instanceof String role) {
                authorities.add(toRoleAuthority(role));
                authorities.add(toRoleAuthority(role.toUpperCase()));
            }
        }
        return authorities;
    }

}

