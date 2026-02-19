package org.geotools.data.surrealdb.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SurrealDBSdkClient state machine behavior.
 * Tests verify connected/disconnected states and error paths without a live SurrealDB.
 */
class SurrealDBSdkClientTest {

    private SurrealDBSdkClient client;

    @BeforeEach
    void setUp() {
        client = new SurrealDBSdkClient();
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
    void queryAsJsonThrowsWhenNotConnected() {
        assertThrows(SurrealDBConnectionException.class, () ->
                client.queryAsJson("SELECT 1"));
    }

    @Test
    void queryBindAsJsonThrowsWhenNotConnected() {
        assertThrows(SurrealDBConnectionException.class, () ->
                client.queryBindAsJson("SELECT * FROM t WHERE name = $name",
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
}
