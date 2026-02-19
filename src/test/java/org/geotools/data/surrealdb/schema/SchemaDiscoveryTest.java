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
    void discoverTablesIncludesBothSchemafullAndSchemaless() {
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

        assertEquals(2, tables.size());
        assertTrue(tables.contains("poi"));
        assertTrue(tables.contains("event"));
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
        // First discover tables to populate the schemafull cache
        String dbInfoJson = """
                {
                  "tables": {
                    "poi": "DEFINE TABLE poi TYPE NORMAL SCHEMAFULL"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR DB")).thenReturn(dbInfoJson);
        schemaDiscovery.discoverTables();

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

    @Test
    void discoverTableSchemaFiltersSubFields() {
        String tableInfoJson = """
                {
                  "fields": {
                    "name": "DEFINE FIELD name ON tree TYPE string",
                    "location": "DEFINE FIELD location ON tree TYPE geometry<point>",
                    "address": "DEFINE FIELD address ON tree TYPE object",
                    "address.city": "DEFINE FIELD address.city ON tree TYPE string",
                    "address.line1": "DEFINE FIELD address.line1 ON tree TYPE string",
                    "photos": "DEFINE FIELD photos ON tree TYPE array<string>",
                    "photos.*": "DEFINE FIELD photos.* ON tree TYPE string"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR TABLE tree")).thenReturn(tableInfoJson);

        TableSchema schema = schemaDiscovery.discoverTableSchema("tree");

        // Only top-level fields should be included (no dot-separated sub-fields)
        assertEquals(4, schema.getFields().size());
        List<String> fieldNames = schema.getFields().stream()
                .map(FieldSchema::getFieldName)
                .toList();
        assertTrue(fieldNames.contains("name"));
        assertTrue(fieldNames.contains("location"));
        assertTrue(fieldNames.contains("address"));
        assertTrue(fieldNames.contains("photos"));
        assertFalse(fieldNames.contains("address.city"));
        assertFalse(fieldNames.contains("photos.*"));
    }

    @Test
    void discoverGeometryTablesIncludesSchemalessWithGeometry() {
        String dbInfoJson = """
                {
                  "tables": {
                    "poi": "DEFINE TABLE poi TYPE NORMAL SCHEMAFULL",
                    "tree": "DEFINE TABLE tree TYPE ANY SCHEMALESS"
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

        String treeTableInfo = """
                {
                  "fields": {
                    "name": "DEFINE FIELD name ON tree TYPE string",
                    "location": "DEFINE FIELD location ON tree TYPE geometry<point>"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR TABLE tree")).thenReturn(treeTableInfo);

        List<TableSchema> geometryTables = schemaDiscovery.discoverGeometryTables();

        assertEquals(2, geometryTables.size());
        List<String> names = geometryTables.stream()
                .map(TableSchema::getTableName).toList();
        assertTrue(names.contains("poi"));
        assertTrue(names.contains("tree"));

        // Verify schemafull flag is preserved
        TableSchema poiSchema = geometryTables.stream()
                .filter(t -> t.getTableName().equals("poi")).findFirst().orElseThrow();
        assertTrue(poiSchema.isSchemafull());

        TableSchema treeSchema = geometryTables.stream()
                .filter(t -> t.getTableName().equals("tree")).findFirst().orElseThrow();
        assertFalse(treeSchema.isSchemafull());
    }

    @Test
    void discoverTableSchemaSkipsFieldsWithNoType() {
        String tableInfoJson = """
                {
                  "fields": {
                    "name": "DEFINE FIELD name ON tree TYPE string",
                    "location": "DEFINE FIELD location ON tree TYPE geometry<point>",
                    "carbon_seq_kg": "DEFINE FIELD carbon_seq_kg ON tree VALUE fn::calculate_carbon(girth_cm, species, age_years) PERMISSIONS FULL"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR TABLE tree")).thenReturn(tableInfoJson);

        TableSchema schema = schemaDiscovery.discoverTableSchema("tree");

        // Computed VALUE field without TYPE should be skipped
        assertEquals(2, schema.getFields().size());
        List<String> fieldNames = schema.getFields().stream()
                .map(FieldSchema::getFieldName).toList();
        assertTrue(fieldNames.contains("name"));
        assertTrue(fieldNames.contains("location"));
        assertFalse(fieldNames.contains("carbon_seq_kg"));
    }
}
