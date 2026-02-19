package org.geotools.data.surrealdb;

import org.geotools.data.surrealdb.client.ConnectionConfig;
import org.geotools.data.surrealdb.client.SurrealDBClient;
import org.geotools.data.surrealdb.config.SurrealDBDataStoreParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SurrealDBDataStoreFactoryTest {

    @Mock
    private SurrealDBClient mockClient;

    private SurrealDBDataStoreFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SurrealDBDataStoreFactory() {
            @Override
            protected SurrealDBClient createClient() {
                return mockClient;
            }
        };
    }

    private Map<String, Object> validParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("dbtype", "surrealdb");
        params.put("host", "localhost");
        params.put("port", 8000);
        params.put("namespace", "test_ns");
        params.put("database", "test_db");
        params.put("user", "testuser");
        params.put("password", "testpass");
        return params;
    }

    @Test
    void getDisplayNameReturnsSurrealDB() {
        assertEquals("SurrealDB", factory.getDisplayName());
    }

    @Test
    void getDescriptionReturnsExpectedString() {
        assertNotNull(factory.getDescription());
        assertTrue(factory.getDescription().contains("SurrealDB"));
    }

    @Test
    void getParametersInfoReturnsCorrectCount() {
        assertEquals(13, factory.getParametersInfo().length);
    }

    @Test
    void isAvailableTrueWithSdkOnClasspath() {
        assertTrue(factory.isAvailable());
    }

    @Test
    void canProcessValidParams() {
        assertTrue(factory.canProcess(validParams()));
    }

    @Test
    void canProcessReturnsFalseForInvalidInput() {
        assertFalse(factory.canProcess(null));

        Map<String, Object> wrongType = new HashMap<>();
        wrongType.put("dbtype", "postgis");
        assertFalse(factory.canProcess(wrongType));

        Map<String, Object> missingRequired = new HashMap<>();
        missingRequired.put("dbtype", "surrealdb");
        assertFalse(factory.canProcess(missingRequired));
    }

    @Test
    void createNewDataStoreThrowsUnsupported() {
        assertThrows(UnsupportedOperationException.class, () ->
                factory.createNewDataStore(validParams()));
    }

    @Test
    void createDataStoreConnectsAndThrowsPhase2Placeholder() throws IOException {
        doNothing().when(mockClient).connect(any(ConnectionConfig.class));
        doNothing().when(mockClient).close();

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () ->
                factory.createDataStore(validParams()));

        assertTrue(ex.getMessage().contains("Phase 2"));
        verify(mockClient).connect(any(ConnectionConfig.class));
        verify(mockClient).close();
    }
}
