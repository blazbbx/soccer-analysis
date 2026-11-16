package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * The roles (hats) an account acts with in the current request. They come only from the request's token,
 * and only when the token belongs to that account: there is no stored role to fall back on, so an account
 * without a token, or someone else's account, has no hats here.
 */
@Component
public class ActorHats {

    public Set<UserRole> of(User actor) {
        if (actor == null || actor.getId() == null) {
            return Set.of();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Set.of();
        }
        if (!principalMatchesActor(actor, auth)) {
            return Set.of();
        }
        Set<UserRole> hats = EnumSet.noneOf(UserRole.class);
        for (GrantedAuthority granted : auth.getAuthorities()) {
            UserRole hat = parse(granted.getAuthority());
            if (hat != null) {
                hats.add(hat);
            }
        }
        return hats;
    }

    private static boolean principalMatchesActor(User actor, Authentication auth) {
        String actorId = actor.getId().toString();
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return actorId.equals(jwt.getSubject());
        }
        return actorId.equals(auth.getName());
    }

    private static UserRole parse(String authority) {
        if (authority == null) {
            return null;
        }
        String name = authority.startsWith("ROLE_") ? authority.substring(5) : authority;
        try {
            return UserRole.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
