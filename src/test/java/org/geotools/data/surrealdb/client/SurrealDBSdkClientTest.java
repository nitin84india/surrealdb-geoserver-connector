package org.geotools.data.surrealdb.client;

import org.geotools.data.surrealdb.filter.RecordId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

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

    // --- toSurrealQLLiteral tests via buildQueryWithParams ---

    @Test
    void toSurrealQLLiteralHandlesRecordId() throws Exception {
        // Access the private toSurrealQLLiteral method via reflection
        Method method = SurrealDBSdkClient.class.getDeclaredMethod("toSurrealQLLiteral", Object.class);
        method.setAccessible(true);

        RecordId recordId = new RecordId("species:neem");
        String result = (String) method.invoke(client, recordId);

        // RecordId should be emitted raw/unquoted
        assertEquals("species:neem", result);
    }

    @Test
    void toSurrealQLLiteralHandlesStringWithQuotes() throws Exception {
        Method method = SurrealDBSdkClient.class.getDeclaredMethod("toSurrealQLLiteral", Object.class);
        method.setAccessible(true);

        String result = (String) method.invoke(client, "species:neem");

        // Plain string should be quoted
        assertEquals("'species:neem'", result);
    }

    @Test
    void buildQueryWithParamsEmitsRecordIdUnquoted() throws Exception {
        Method method = SurrealDBSdkClient.class.getDeclaredMethod(
                "buildQueryWithParams", String.class, Map.class);
        method.setAccessible(true);

        RecordId recordId = new RecordId("species:neem");
        Map<String, Object> params = Map.of("p0", recordId);
        String result = (String) method.invoke(client,
                "SELECT * FROM tree WHERE species = $p0", params);

        // The LET statement should contain the unquoted record ID
        assertTrue(result.contains("LET $p0 = species:neem;"),
                "Expected unquoted record ID in LET, got: " + result);
        assertTrue(result.contains("SELECT * FROM tree WHERE species = $p0"));
    }
}
