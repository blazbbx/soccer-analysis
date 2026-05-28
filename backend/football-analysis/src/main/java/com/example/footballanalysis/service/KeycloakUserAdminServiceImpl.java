package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.model.db.user.UserRole;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import jakarta.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class KeycloakUserAdminServiceImpl implements KeycloakUserAdminService {

    private final Keycloak keycloak;
    private final String realm;
    private final String clientId;

    public KeycloakUserAdminServiceImpl(
            Keycloak keycloak,
            @Value("${idp.keycloak.realm}") String realm,
            @Value("${idp.keycloak.client-id}") String clientId) {
        this.keycloak = keycloak;
        this.realm = realm;
        this.clientId = clientId;
    }

    /**
     * Új felhasználót hoz létre a Keycloak Identity Provider (IDP) rendszerben.
     * Beállítja a felhasználó alapvető adatait, a hitelesítő adatait (jelszót) és hozzárendeli
     * a megfelelő kliens szintű szerepkört.
     *
     * @param email a felhasználó e-mail címe (amely a felhasználónév is lesz egyben)
     * @param firstName a felhasználó keresztneve
     * @param lastName a felhasználó vezetékneve
     * @param rawPassword a felhasználó nyers, titkosítatlan jelszava, amit a Keycloak fog titkosítani
     * @param role az a jogosultsági szerepkör, amelyet hozzá kell rendelni a felhasználóhoz
     * @return a létrehozott Keycloak felhasználó egyedi azonosítója (external ID)
     * @throws ConflictException ha már létezik felhasználó a megadott e-mail címmel
     * @throws ExternalServiceException ha a Keycloak valamilyen egyéb hibát vagy HTTP státuszkódot ad vissza a létrehozás során
     */
    @Override
    public String createUser(String email, String firstName, String lastName, String rawPassword, UserRole role) {
        UserRepresentation representation = new UserRepresentation();
        representation.setUsername(email);
        representation.setEmail(email);
        representation.setFirstName(firstName);
        representation.setLastName(lastName);
        representation.setEnabled(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(rawPassword);
        credential.setTemporary(false);
        representation.setCredentials(List.of(credential));

        String createdUserId = null;

        try (Response response = keycloak.realm(realm).users().create(representation)) {
            int status = response.getStatus();
            if (status == Response.Status.CONFLICT.getStatusCode()) {
                throw new ConflictException("error.user.email.conflict", new Object[]{email}, "Email already in use: " + email);
            }
            if (status != Response.Status.CREATED.getStatusCode()) {
                throw new ExternalServiceException(
                        "error.keycloak.user_creation_failed",
                        new Object[]{status},
                        "Keycloak user creation failed with status: " + status);
            }

            createdUserId = extractCreatedUserId(response, email);
            assignClientRole(createdUserId, role);
            return createdUserId;
        } catch (RuntimeException ex) {
            if (ex instanceof ExternalServiceException || ex instanceof ConflictException) {
                throw ex;
            }
            cleanupCreatedUser(createdUserId);
            throw ex;
        }
    }

    /**
     * Töröl egy meglévő felhasználót a Keycloak rendszerből a megadott azonosító alapján.
     *
     * @param keycloakUserId a Keycloakban tárolt felhasználó egyedi azonosítója
     */
    @Override
    public void deleteUser(String keycloakUserId) {
        keycloak.realm(realm).users().delete(keycloakUserId);
        log.debug("Deleted Keycloak user with ID: {}", keycloakUserId);
    }

    /**
     * Frissíti a Keycloak rendszerben található felhasználó bizonyos adatait.
     *
     * @param keycloakUserId a Keycloak felhasználó azonosítója
     * @param firstName a felhasználó új keresztneve
     * @param lastName a felhasználó új vezetékneve
     */
    @Override
    public void updateUser(String keycloakUserId, String firstName, String lastName) {
        try {
            UserRepresentation user = keycloak.realm(realm).users().get(keycloakUserId).toRepresentation();
            if (firstName != null) user.setFirstName(firstName);
            if (lastName != null) user.setLastName(lastName);
            keycloak.realm(realm).users().get(keycloakUserId).update(user);
            log.debug("Updated Keycloak user with ID: {}", keycloakUserId);
        } catch (Exception ex) {
            log.error("Failed to update user {} in Keycloak", keycloakUserId, ex);
            throw new ExternalServiceException(
                    "error.keycloak.user_update_failed",
                    new Object[]{keycloakUserId},
                    "Keycloak user update failed: " + ex.getMessage());
        }
    }

    /**
     * Kinyeri a sikeres létrehozás után kapott HTTP válaszból az újonnan létrejött
     * felhasználó Keycloak azonosítóját (Location header alapján).
     *
     * @param response a Keycloak admin kliens által visszaadott HTTP válasz
     * @param email a felhasználó e-mail címe, amivel a hibát logolni tudjuk sikertelenség esetén
     * @return a létrehozott felhasználó Keycloak által generált egyedi azonosítója (UUID)
     * @throws ExternalServiceException ha a válaszból hiányzik a létrehozott ID
     */
    private String extractCreatedUserId(Response response, String email) {
        String createdUserId = CreatedResponseUtil.getCreatedId(response);
        if (createdUserId == null || createdUserId.isBlank()) {
            throw new ExternalServiceException(
                    "error.keycloak.user_creation_missing_id",
                    new Object[]{email},
                    "Keycloak user creation did not return an id for: " + email);
        }
        log.debug("Extracted created Keycloak user ID: {} for email: {}", createdUserId, email);
        return createdUserId;
    }

    /**
     * Megkísérli törölni a létrejött felhasználót, ha valamilyen hiba (például a jogosultságok
     * beállítása vagy adatbázis hiba) történne a folyamat egy későbbi fázisában.
     * Csendben elnyeli a kivételeket, hogy ne rejtse el az eredeti - a tranzakció bukását okozó - kivételt.
     *
     * @param createdUserId a Keycloak által már kiosztott és létrejött felhasználói azonosító
     */
    private void cleanupCreatedUser(String createdUserId) {
        if (createdUserId == null || createdUserId.isBlank()) {
            return;
        }

        try {
            deleteUser(createdUserId);
        } catch (RuntimeException cleanupEx) {
            // cleanup best-effort: a failed delete must not hide the original error
        }
    }

    /**
     * Hozzárendel egy adott klienshez tartozó szerepkört (Client Role) az újonnan létrehozott felhasználóhoz
     * a Keycloak rendszerben.
     *
     * @param userId a Keycloak-beli felhasználó egyedi azonosítója
     * @param role az alkalmazásunkban használt szerepkör entitás (UserRole)
     * @throws ExternalServiceException ha a hivatkozott kliens azonosító nem található a Keycloakban
     */
    private void assignClientRole(String userId, UserRole role) {
        ClientRepresentation client = keycloak.realm(realm).clients().findByClientId(clientId).stream()
                .findFirst()
            .orElseThrow(() -> new ExternalServiceException(
                "error.keycloak.client_not_found",
                new Object[]{clientId},
                "Keycloak client not found: " + clientId));

        String keycloakRoleName = toKeycloakRoleName(role);
        RoleRepresentation roleRepresentation = keycloak.realm(realm)
                .clients()
                .get(client.getId())
                .roles()
                .get(keycloakRoleName)
                .toRepresentation();

        keycloak.realm(realm)
                .users()
                .get(userId)
                .roles()
                .clientLevel(client.getId())
                .add(List.of(roleRepresentation));
        log.debug("Assigned client role {} to user ID {} in realm {}", keycloakRoleName, userId, realm);
    }

    /**
     * Végrehajtja az alkalmazás belső UserRole enumerációja és a Keycloak által elvárt
     * szöveges szerepkör név közötti leképezést (mapping).
     *
     * @param role a belső szerepkör enum értéke
     * @return a szerepkör a Keycloakban definiált szöveges formátumban
     */
    private String toKeycloakRoleName(UserRole role) {
        return switch (role) {
            case COACH -> "coach";
            case PLAYER -> "player";
            case FAN -> "fan";
            case ADMIN -> "admin";
        };
    }
}