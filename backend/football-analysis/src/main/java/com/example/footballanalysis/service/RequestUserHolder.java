package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.User;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * The user loaded while checking the token, kept for the rest of this HTTP request.
 * There is no request in unit tests, so those keep reading the repository.
 */
public final class RequestUserHolder {

    static final String ATTRIBUTE = "football.currentUser";

    private RequestUserHolder() {
    }

    public static void store(User user) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null || user == null || user.getId() == null) {
            return;
        }
        attributes.setAttribute(ATTRIBUTE, user, RequestAttributes.SCOPE_REQUEST);
    }

    public static void clear() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.removeAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        }
    }

    public static Optional<User> matchingSubject(String subject) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null || subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        Object value = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof User user) || user.getId() == null) {
            return Optional.empty();
        }
        try {
            if (user.getId().equals(UUID.fromString(subject))) {
                return Optional.of(user);
            }
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
