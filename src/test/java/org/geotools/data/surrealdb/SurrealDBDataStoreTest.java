package org.geotools.data.surrealdb;

import org.geotools.api.data.DataStore;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.surrealdb.client.ConnectionConfig;
import org.geotools.data.surrealdb.client.SurrealDBClient;
import org.geotools.data.surrealdb.schema.TableSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SurrealDBDataStoreTest {

    @Mock
    private SurrealDBClient mockClient;

    private ConnectionConfig config;
    private SurrealDBDataStore dataStore;

    @BeforeEach
    void setUp() {
        config = ConnectionConfig.builder()
                .host("localhost")
                .port(8000)
                .namespace("test")
                .database("testdb")
                .username("user")
                .password("pass")
                .srid(4326)
                .build();
        dataStore = new SurrealDBDataStore(mockClient, config);
    }

    @Test
    void createTypeNamesReturnsDiscoveredGeometryTables() throws IOException {
        // Setup: INFO FOR DB returns two SCHEMAFULL tables
        String dbInfoJson = """
                {
                  "tables": {
                    "poi": "DEFINE TABLE poi TYPE NORMAL SCHEMAFULL",
                    "config": "DEFINE TABLE config TYPE NORMAL SCHEMAFULL"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR DB")).thenReturn(dbInfoJson);

        // poi has geometry, config does not
        String poiTableInfo = """
                {
                  "fields": {
                    "name": "DEFINE FIELD name ON poi TYPE string",
                    "geometry": "DEFINE FIELD geometry ON poi TYPE geometry<point>"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR TABLE poi")).thenReturn(poiTableInfo);

        String configTableInfo = """
                {
                  "fields": {
                    "key": "DEFINE FIELD key ON config TYPE string",
                    "value": "DEFINE FIELD value ON config TYPE string"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR TABLE config")).thenReturn(configTableInfo);

        String[] typeNames = dataStore.getTypeNames();

        assertEquals(1, typeNames.length);
        assertEquals("poi", typeNames[0]);
    }

    @Test
    void getSchemaReturnsCorrectFeatureType() throws IOException {
        // Setup discovery
        String dbInfoJson = """
                {
                  "tables": {
                    "poi": "DEFINE TABLE poi TYPE NORMAL SCHEMAFULL"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR DB")).thenReturn(dbInfoJson);

        String poiTableInfo = """
                {
                  "fields": {
                    "name": "DEFINE FIELD name ON poi TYPE string",
                    "geometry": "DEFINE FIELD geometry ON poi TYPE geometry<point>",
                    "category": "DEFINE FIELD category ON poi TYPE string"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR TABLE poi")).thenReturn(poiTableInfo);

        SimpleFeatureType schema = dataStore.getSchema("poi");

        assertNotNull(schema);
        assertEquals("poi", schema.getTypeName());
        assertNotNull(schema.getGeometryDescriptor());
        assertEquals("geometry", schema.getGeometryDescriptor().getLocalName());
    }

    @Test
    void getTableSchemaReturnsCachedSchema() throws IOException {
        // Trigger discovery to populate cache
        String dbInfoJson = """
                {
                  "tables": {
                    "poi": "DEFINE TABLE poi TYPE NORMAL SCHEMAFULL"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR DB")).thenReturn(dbInfoJson);

        String poiTableInfo = """
                {
                  "fields": {
                    "geometry": "DEFINE FIELD geometry ON poi TYPE geometry<point>"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR TABLE poi")).thenReturn(poiTableInfo);

        // Trigger type name discovery (populates cache)
        dataStore.getTypeNames();

        TableSchema cached = dataStore.getTableSchema("poi");
        assertNotNull(cached);
        assertEquals("poi", cached.getTableName());
    }

    @Test
    void disposeClosesClient() {
        dataStore.dispose();

        verify(mockClient).close();
    }

    @Test
    void createDataStoreReturnsRealDataStore() throws IOException {
        doNothing().when(mockClient).connect(any(ConnectionConfig.class));

        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory() {
            @Override
            protected SurrealDBClient createClient() {
                return mockClient;
            }
        };

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("dbtype", "surrealdb");
        params.put("host", "localhost");
        params.put("port", 8000);
        params.put("surreal_ns", "test_ns");
        params.put("database", "test_db");
        params.put("user", "testuser");
        params.put("password", "testpass");

        DataStore ds = factory.createDataStore(params);

        assertNotNull(ds);
        assertInstanceOf(SurrealDBDataStore.class, ds);
    }
}
