package org.geotools.data.surrealdb.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.geotools.data.surrealdb.SurrealDBContainerIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link SurrealDBSdkClient} against a live SurrealDB instance.
 *
 * <p>Validates the full client lifecycle (connect, query, close) against a real
 * SurrealDB container managed by Testcontainers. The base class provides container
 * setup, schema initialization, and helper methods.</p>
 */
class SurrealDBClientIT extends SurrealDBContainerIT {

    @Test
    @DisplayName("connect with valid credentials succeeds and reports connected")
    void connectWithValidCredentials() {
        SurrealDBSdkClient client = assertDoesNotThrow(() -> createConnectedClient());
        try {
            assertTrue(client.isConnected(),
                    "Client should report connected after successful connect()");
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("connect with wrong password throws SurrealDBConnectionException")
    void connectWithWrongPassword() {
        ConnectionConfig badConfig = ConnectionConfig.builder()
                .host(SURREAL_DB.getHost())
                .port(SURREAL_DB.getMappedPort(8000))
                .namespace(NAMESPACE)
                .database(DATABASE)
                .username(ROOT_USER)
                .password("wrong-password")
                .protocol("http")
                .timeoutMs(30000)
                .build();

        SurrealDBSdkClient client = new SurrealDBSdkClient();

        assertThrows(SurrealDBConnectionException.class, () -> client.connect(badConfig),
                "Connecting with wrong password should throw SurrealDBConnectionException");
    }

    @Test
    @DisplayName("queryAsJson returns parseable JSON with expected record count")
    void queryAsJsonReturnsParsableJson() {
        SurrealDBSdkClient client = createConnectedClient();
        try {
            String json = client.queryAsJson("SELECT * FROM poi");

            assertNotNull(json, "queryAsJson should not return null");
            assertFalse(json.isEmpty(), "queryAsJson should not return empty string");

            JsonArray results = JsonParser.parseString(json).getAsJsonArray();
            assertEquals(7, results.size(),
                    "poi table should contain exactly 7 records");
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("queryAsJson preserves GeoJSON geometry structure in results")
    void queryAsJsonPreservesGeoJsonGeometry() {
        SurrealDBSdkClient client = createConnectedClient();
        try {
            String json = client.queryAsJson("SELECT * FROM poi");
            JsonArray results = JsonParser.parseString(json).getAsJsonArray();

            assertTrue(results.size() > 0, "Should have at least one POI record");

            JsonObject firstRecord = results.get(0).getAsJsonObject();
            assertTrue(firstRecord.has("geometry"),
                    "POI record should have a 'geometry' field");

            JsonObject geometry = firstRecord.getAsJsonObject("geometry");
            assertTrue(geometry.has("type"),
                    "Geometry should have a 'type' field");
            assertEquals("Point", geometry.get("type").getAsString(),
                    "POI geometry type should be 'Point'");
            assertTrue(geometry.has("coordinates"),
                    "Geometry should have a 'coordinates' field");
            assertTrue(geometry.get("coordinates").isJsonArray(),
                    "Coordinates should be a JSON array");
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("queryBindAsJson with parameters returns filtered results")
    void queryBindAsJsonWithParamsReturnsFilteredResults() {
        SurrealDBSdkClient client = createConnectedClient();
        try {
            String json = client.queryBindAsJson(
                    "SELECT * FROM poi WHERE category = $cat",
                    Map.of("cat", "bridge"));

            assertNotNull(json, "queryBindAsJson should not return null");

            JsonArray results = JsonParser.parseString(json).getAsJsonArray();
            assertEquals(2, results.size(),
                    "Should find exactly 2 POIs with category 'bridge'");
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("queryAsJson INFO FOR DB returns JSON with tables key")
    void queryAsJsonInfoForDb() {
        SurrealDBSdkClient client = createConnectedClient();
        try {
            String json = client.queryAsJson("INFO FOR DB");

            assertNotNull(json, "INFO FOR DB should not return null");

            // INFO FOR DB returns an object (wrapped in the result extraction),
            // which contains a "tables" key
            JsonObject dbInfo = JsonParser.parseString(json).getAsJsonObject();
            assertTrue(dbInfo.has("tables"),
                    "INFO FOR DB result should contain a 'tables' key");
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("queryAsJson INFO FOR TABLE returns JSON with fields key")
    void queryAsJsonInfoForTable() {
        SurrealDBSdkClient client = createConnectedClient();
        try {
            String json = client.queryAsJson("INFO FOR TABLE poi");

            assertNotNull(json, "INFO FOR TABLE should not return null");

            JsonObject tableInfo = JsonParser.parseString(json).getAsJsonObject();
            assertTrue(tableInfo.has("fields"),
                    "INFO FOR TABLE result should contain a 'fields' key");
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("close disconnects the client")
    void closeDisconnects() {
        SurrealDBSdkClient client = createConnectedClient();
        assertTrue(client.isConnected(), "Client should be connected before close");

        client.close();

        assertFalse(client.isConnected(),
                "Client should report disconnected after close()");
    }

    @Test
    @DisplayName("queryAsJson after close throws SurrealDBConnectionException")
    void queryAfterCloseThrows() {
        SurrealDBSdkClient client = createConnectedClient();
        client.close();

        assertThrows(SurrealDBConnectionException.class,
                () -> client.queryAsJson("SELECT * FROM poi"),
                "Querying after close should throw SurrealDBConnectionException");
    }

    @Test
    @DisplayName("isHealthy returns true when connected")
    void isHealthyReturnsTrueWhenConnected() {
        SurrealDBSdkClient client = createConnectedClient();
        try {
            assertTrue(client.isHealthy(),
                    "isHealthy() should return true for a connected client");
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("isHealthy returns false after close")
    void isHealthyReturnsFalseAfterClose() {
        SurrealDBSdkClient client = createConnectedClient();
        client.close();

        assertFalse(client.isHealthy(),
                "isHealthy() should return false after close()");
    }
}
