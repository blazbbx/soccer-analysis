package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.model.db.user.UserRole;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeycloakUserAdminServiceImpl")
class KeycloakUserAdminServiceImplTest {

    @Mock private Keycloak keycloak;
    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;
    @Mock private UserResource userResource;
    @Mock private ClientsResource clientsResource;
    @Mock private Response response;

    private KeycloakUserAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KeycloakUserAdminServiceImpl(keycloak, "football-realm", "football-web-client");
        when(keycloak.realm("football-realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    @Test
    void existsUser_trueWhenRepresentationFound() {
        when(usersResource.get("user-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(new UserRepresentation());

        assertThat(service.existsUser("user-1")).isTrue();
    }

    @Test
    void existsUser_falseOnNotFound() {
        when(usersResource.get("gone")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenThrow(new NotFoundException());

        assertThat(service.existsUser("gone")).isFalse();
    }

    @Test
    void existsUser_falseOnUnexpectedError() {
        when(usersResource.get("broken")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenThrow(new RuntimeException("kc down"));

        assertThat(service.existsUser("broken")).isFalse();
    }

    @Test
    void createUser_conflictWhenEmailTaken() {
        when(usersResource.create(any())).thenReturn(response);
        when(response.getStatus()).thenReturn(409);

        assertThatThrownBy(() -> service.createUser("a@b.hu", "A", "B", "pw", UserRole.PLAYER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createUser_rollsBackWhenClientMissing() {
        when(usersResource.create(any())).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getStatusInfo()).thenReturn(Response.Status.CREATED);
        when(response.getLocation()).thenReturn(URI.create("http://keycloak/users/created-1"));
        when(realmResource.clients()).thenReturn(clientsResource);
        when(clientsResource.findByClientId("football-web-client")).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> service.createUser("a@b.hu", "A", "B", "pw", UserRole.COACH))
                .isInstanceOf(ExternalServiceException.class);

        verify(usersResource).delete("created-1");
    }
}
