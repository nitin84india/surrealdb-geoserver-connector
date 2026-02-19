package org.geotools.data.surrealdb.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SurrealDBSdkClient that don't require the native SurrealDB SDK driver.
 * Tests verify state machine behavior (connected/disconnected) and error paths.
 *
 * <p>Integration tests with a real SurrealDB instance via Testcontainers
 * are in a separate IT class (Phase 4).</p>
 */
class SurrealDBSdkClientTest {

    private SurrealDBSdkClient client;

    @BeforeEach
    void setUp() {
        client = new SurrealDBSdkClient();
    }

    private ConnectionConfig testConfig() {
        return ConnectionConfig.builder()
                .host("localhost")
                .port(8000)
                .namespace("test_ns")
                .database("test_db")
                .username("user")
                .password("pass")
                .build();
    }

    @Test
    void isConnectedFalseBeforeConnect() {
        assertFalse(client.isConnected());
    }

    @Test
    void getConfigNullBeforeConnect() {
        assertNull(client.getConfig());
    }

    @Test
    void queryThrowsWhenNotConnected() {
        assertThrows(SurrealDBConnectionException.class, () ->
                client.query("SELECT 1"));
    }

    @Test
    void queryBindThrowsWhenNotConnected() {
        assertThrows(SurrealDBConnectionException.class, () ->
                client.queryBind("SELECT * FROM t WHERE name = $name",
                        java.util.Map.of("name", "test")));
    }

    @Test
    void isHealthyFalseWhenNotConnected() {
        assertFalse(client.isHealthy());
    }

    @Test
    void closeOnDisconnectedClientIsNoop() {
        assertDoesNotThrow(() -> client.close());
        assertFalse(client.isConnected());
    }

    @Test
    void authManagerIsAccessible() {
        assertNotNull(client.getAuthManager());
    }

    @Test
    void constructorWithCustomAuthManager() {
        AuthManager customAuth = new AuthManager();
        SurrealDBSdkClient customClient = new SurrealDBSdkClient(customAuth);

        assertSame(customAuth, customClient.getAuthManager());
    }
}
