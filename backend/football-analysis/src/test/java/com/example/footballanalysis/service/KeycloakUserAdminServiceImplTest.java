package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeycloakUserAdminServiceImpl role mapping tesztek")
class KeycloakUserAdminServiceImplTest {

    @Mock
    private Keycloak keycloak;

    @Test
    void mapsAdminToKeycloakAdminClientRole() throws Exception {
        KeycloakUserAdminServiceImpl service = new KeycloakUserAdminServiceImpl(
                keycloak,
                "football-realm",
                "football-web-client"
        );

        Method method = KeycloakUserAdminServiceImpl.class.getDeclaredMethod("toKeycloakRoleName", UserRole.class);
        method.setAccessible(true);

        String roleName = (String) method.invoke(service, UserRole.ADMIN);

        assertThat(roleName).isEqualTo("admin");
    }
}