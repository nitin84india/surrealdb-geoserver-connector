package org.geotools.data.surrealdb.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionConfigTest {

    private ConnectionConfig.Builder validBuilder() {
        return ConnectionConfig.builder()
                .host("localhost")
                .namespace("test_ns")
                .database("test_db")
                .username("user")
                .password("pass");
    }

    @Test
    void builderWithRequiredFieldsProducesValidConfig() {
        ConnectionConfig config = validBuilder().build();

        assertEquals("localhost", config.getHost());
        assertEquals("test_ns", config.getNamespace());
        assertEquals("test_db", config.getDatabase());
        assertEquals("user", config.getUsername());
        assertEquals("pass", config.getPassword());
    }

    @Test
    void builderUsesDefaultValues() {
        ConnectionConfig config = validBuilder().build();

        assertEquals(8000, config.getPort());
        assertFalse(config.isUseTls());
        assertEquals("http", config.getProtocol());
        assertEquals(5, config.getPoolSize());
        assertEquals(30000, config.getTimeoutMs());
        assertEquals(4326, config.getSrid());
    }

    @Test
    void builderWithCustomValues() {
        ConnectionConfig config = validBuilder()
                .port(9000)
                .useTls(true)
                .protocol("ws")
                .poolSize(10)
                .timeoutMs(60000)
                .srid(3857)
                .build();

        assertEquals(9000, config.getPort());
        assertTrue(config.isUseTls());
        assertEquals("ws", config.getProtocol());
        assertEquals(10, config.getPoolSize());
        assertEquals(60000, config.getTimeoutMs());
        assertEquals(3857, config.getSrid());
    }

    @Test
    void builderThrowsOnMissingNamespace() {
        assertThrows(NullPointerException.class, () ->
                ConnectionConfig.builder()
                        .host("localhost")
                        .database("db")
                        .username("user")
                        .password("pass")
                        .build());
    }

    @Test
    void builderThrowsOnInvalidPort() {
        assertThrows(IllegalArgumentException.class, () ->
                validBuilder().port(0).build());

        assertThrows(IllegalArgumentException.class, () ->
                validBuilder().port(70000).build());
    }

    @Test
    void builderThrowsOnInvalidProtocol() {
        assertThrows(IllegalArgumentException.class, () ->
                validBuilder().protocol("ftp").build());
    }

    @Test
    void buildConnectionUrlHttp() {
        ConnectionConfig config = validBuilder().build();
        assertEquals("http://localhost:8000", config.buildConnectionUrl());
    }

    @Test
    void buildConnectionUrlHttps() {
        ConnectionConfig config = validBuilder().useTls(true).build();
        assertEquals("https://localhost:8000", config.buildConnectionUrl());
    }

    @Test
    void buildConnectionUrlWs() {
        ConnectionConfig config = validBuilder().protocol("ws").build();
        assertEquals("ws://localhost:8000", config.buildConnectionUrl());
    }

    @Test
    void buildConnectionUrlWss() {
        ConnectionConfig config = validBuilder().protocol("ws").useTls(true).build();
        assertEquals("wss://localhost:8000", config.buildConnectionUrl());
    }

    @Test
    void equalityAndInequality() {
        ConnectionConfig a = validBuilder().build();
        ConnectionConfig b = validBuilder().build();
        ConnectionConfig c = validBuilder().port(9999).build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void toStringMasksPassword() {
        ConnectionConfig config = validBuilder().password("secret123").build();
        String str = config.toString();

        assertTrue(str.contains("****"));
        assertFalse(str.contains("secret123"));
    }
}
