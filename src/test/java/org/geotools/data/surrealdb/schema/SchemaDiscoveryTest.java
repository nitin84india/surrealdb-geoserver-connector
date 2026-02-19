package org.geotools.data.surrealdb.schema;

import org.geotools.data.surrealdb.client.SurrealDBClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaDiscoveryTest {

    @Mock
    private SurrealDBClient mockClient;

    private SchemaDiscovery schemaDiscovery;

    @BeforeEach
    void setUp() {
        schemaDiscovery = new SchemaDiscovery(mockClient);
    }

    @Test
    void discoverTablesParsesSchemafullTablesFromInfoForDb() {
        String dbInfoJson = """
                {
                  "accesses": {},
                  "analyzers": {},
                  "functions": {},
                  "models": {},
                  "params": {},
                  "tables": {
                    "poi": "DEFINE TABLE poi TYPE NORMAL SCHEMAFULL",
                    "park": "DEFINE TABLE park TYPE NORMAL SCHEMAFULL"
                  },
                  "users": {}
                }
                """;
        when(mockClient.queryAsJson("INFO FOR DB")).thenReturn(dbInfoJson);

        List<String> tables = schemaDiscovery.discoverTables();

        assertEquals(2, tables.size());
        assertTrue(tables.contains("poi"));
        assertTrue(tables.contains("park"));
    }

    @Test
    void discoverTablesFiltersSchemafullOnly() {
        String dbInfoJson = """
                {
                  "tables": {
                    "poi": "DEFINE TABLE poi TYPE NORMAL SCHEMAFULL",
                    "event": "DEFINE TABLE event TYPE NORMAL SCHEMALESS"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR DB")).thenReturn(dbInfoJson);

        List<String> tables = schemaDiscovery.discoverTables();

        assertEquals(1, tables.size());
        assertEquals("poi", tables.get(0));
    }

    @Test
    void discoverTablesHandlesEmptyDatabase() {
        String dbInfoJson = """
                {
                  "tables": {}
                }
                """;
        when(mockClient.queryAsJson("INFO FOR DB")).thenReturn(dbInfoJson);

        List<String> tables = schemaDiscovery.discoverTables();

        assertTrue(tables.isEmpty());
    }

    @Test
    void discoverTableSchemaParseFieldDefinitions() {
        String tableInfoJson = """
                {
                  "events": {},
                  "fields": {
                    "name": "DEFINE FIELD name ON poi TYPE string",
                    "geometry": "DEFINE FIELD geometry ON poi TYPE geometry<point>",
                    "category": "DEFINE FIELD category ON poi TYPE string"
                  },
                  "indexes": {},
                  "lives": {},
                  "tables": {}
                }
                """;
        when(mockClient.queryAsJson("INFO FOR TABLE poi")).thenReturn(tableInfoJson);

        TableSchema schema = schemaDiscovery.discoverTableSchema("poi");

        assertEquals("poi", schema.getTableName());
        assertTrue(schema.isSchemafull());
        assertEquals(3, schema.getFields().size());
        assertTrue(schema.hasGeometryField());

        // Verify geometry field
        List<FieldSchema> geomFields = schema.getGeometryFields();
        assertEquals(1, geomFields.size());
        assertEquals("geometry", geomFields.get(0).getFieldName());
        assertEquals("geometry<point>", geomFields.get(0).getSurrealKind());
    }

    @Test
    void discoverGeometryTablesFiltersTablesWithGeometry() {
        String dbInfoJson = """
                {
                  "tables": {
                    "poi": "DEFINE TABLE poi TYPE NORMAL SCHEMAFULL",
                    "config": "DEFINE TABLE config TYPE NORMAL SCHEMAFULL"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR DB")).thenReturn(dbInfoJson);

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

        List<TableSchema> geometryTables = schemaDiscovery.discoverGeometryTables();

        assertEquals(1, geometryTables.size());
        assertEquals("poi", geometryTables.get(0).getTableName());
    }

    @Test
    void discoverTableSchemaHandlesTableWithNoFields() {
        String tableInfoJson = """
                {
                  "fields": {}
                }
                """;
        when(mockClient.queryAsJson("INFO FOR TABLE empty")).thenReturn(tableInfoJson);

        TableSchema schema = schemaDiscovery.discoverTableSchema("empty");

        assertEquals("empty", schema.getTableName());
        assertTrue(schema.getFields().isEmpty());
        assertFalse(schema.hasGeometryField());
    }

    @Test
    void extractFieldKindParsesVariousTypes() {
        assertEquals("string", schemaDiscovery.extractFieldKind("DEFINE FIELD name ON poi TYPE string"));
        assertEquals("geometry<point>", schemaDiscovery.extractFieldKind("DEFINE FIELD geom ON poi TYPE geometry<point>"));
        assertEquals("geometry<polygon>", schemaDiscovery.extractFieldKind("DEFINE FIELD area ON park TYPE geometry<polygon>"));
        assertEquals("int", schemaDiscovery.extractFieldKind("DEFINE FIELD count ON t TYPE int"));
        assertEquals("float", schemaDiscovery.extractFieldKind("DEFINE FIELD area ON t TYPE float"));
        assertEquals("datetime", schemaDiscovery.extractFieldKind("DEFINE FIELD created ON t TYPE datetime"));
    }

    @Test
    void extractFieldKindReturnsNullForNoType() {
        assertNull(schemaDiscovery.extractFieldKind("DEFINE FIELD name ON poi"));
    }
}
