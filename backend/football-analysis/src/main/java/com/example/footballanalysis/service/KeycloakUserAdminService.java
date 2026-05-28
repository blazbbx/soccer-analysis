package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.UserRole;

public interface KeycloakUserAdminService {
    String createUser(String email, String firstName, String lastName, String rawPassword, UserRole role);
    void updateUser(String keycloakUserId, String firstName, String lastName);
    void deleteUser(String keycloakUserId);
}