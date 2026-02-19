package org.geotools.data.surrealdb.config;

import org.geotools.data.surrealdb.client.ConnectionConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SurrealDBDataStoreParamsTest {

    @Test
    void toConnectionConfigMapsAllParameters() throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("host", "myhost");
        params.put("port", 9000);
        params.put("namespace", "myns");
        params.put("database", "mydb");
        params.put("user", "myuser");
        params.put("password", "mypass");
        params.put("use_tls", true);
        params.put("protocol", "ws");
        params.put("pool_size", 10);
        params.put("timeout", 60000);
        params.put("srid", 3857);

        ConnectionConfig config = SurrealDBDataStoreParams.toConnectionConfig(params);

        assertEquals("myhost", config.getHost());
        assertEquals(9000, config.getPort());
        assertEquals("myns", config.getNamespace());
        assertEquals("mydb", config.getDatabase());
        assertEquals("myuser", config.getUsername());
        assertEquals("mypass", config.getPassword());
        assertTrue(config.isUseTls());
        assertEquals("ws", config.getProtocol());
        assertEquals(10, config.getPoolSize());
        assertEquals(60000, config.getTimeoutMs());
        assertEquals(3857, config.getSrid());
    }

    @Test
    void toConnectionConfigUsesDefaultsForOptionals() throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("host", "localhost");
        params.put("namespace", "ns");
        params.put("database", "db");
        params.put("user", "user");
        params.put("password", "pass");

        ConnectionConfig config = SurrealDBDataStoreParams.toConnectionConfig(params);

        assertEquals(8000, config.getPort());
        assertFalse(config.isUseTls());
        assertEquals("http", config.getProtocol());
        assertEquals(5, config.getPoolSize());
        assertEquals(30000, config.getTimeoutMs());
        assertEquals(4326, config.getSrid());
    }

    @Test
    void allParamsHasCorrectCount() {
        assertEquals(13, SurrealDBDataStoreParams.ALL_PARAMS.length);
    }
}
