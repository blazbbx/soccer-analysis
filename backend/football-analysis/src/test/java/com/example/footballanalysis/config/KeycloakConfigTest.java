package com.example.footballanalysis.config;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.JacksonProvider;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakConfigTest {

    @Test
    void theClientKeepsKeycloaksJsonProvider() {
        Client client = KeycloakConfig.httpClient(2000, 5000);
        try {
            assertThat(client.getConfiguration().isRegistered(JacksonProvider.class)).isTrue();
        } finally {
            client.close();
        }
    }

    @Test
    void aServerThatNeverAnswersFailsAfterTheReadTimeoutInsteadOfHanging() throws IOException {
        // Accepts the connection but never writes a byte back: the case a missing read timeout would hang on.
        List<Socket> accepted = new ArrayList<>();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try {
                    accepted.add(server.accept());
                } catch (IOException ignored) {
                    // The socket closes when the test ends.
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            Client client = KeycloakConfig.httpClient(1000, 300);
            long started = System.nanoTime();
            try {
                assertThatThrownBy(() -> client.target("http://localhost:" + server.getLocalPort()).request().get())
                        .isInstanceOf(ProcessingException.class);
            } finally {
                client.close();
            }
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            assertThat(elapsedMs).isLessThan(5_000);
        } finally {
            for (Socket socket : accepted) {
                socket.close();
            }
        }
    }
}
