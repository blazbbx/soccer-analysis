package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.UserRole;

public interface KeycloakUserAdminService {
    String createUser(String email, String firstName, String lastName, String rawPassword, UserRole role);
    void updateUser(String keycloakUserId, String firstName, String lastName);
    void deleteUser(String keycloakUserId);
    boolean existsUser(String keycloakUserId);

    /** Keycloak asks the user for a new password at the next login. */
    void requirePasswordChange(String keycloakUserId);
    void addClientRole(String keycloakUserId, UserRole role);
    void removeClientRole(String keycloakUserId, UserRole role);

    boolean hasClientRole(String keycloakUserId, UserRole role);

    java.util.List<String> listUserIdsWithClientRole(UserRole role);

    /** The id of the user with exactly this e-mail, if there is one. */
    java.util.Optional<String> findUserIdByEmail(String email);
}
