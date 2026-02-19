package org.geotools.data.surrealdb.schema;

import org.geotools.data.surrealdb.SurrealDBContainerIT;
import org.geotools.data.surrealdb.client.SurrealDBSdkClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link SchemaDiscovery} against a live SurrealDB instance.
 *
 * <p>Uses Testcontainers (via {@link SurrealDBContainerIT}) to spin up a SurrealDB
 * container pre-loaded with poi, park, trail, empty_geo, config, and event tables.</p>
 */
class SurrealDBSchemaDiscoveryIT extends SurrealDBContainerIT {

    private static SurrealDBSdkClient client;
    private static SchemaDiscovery discovery;

    @BeforeAll
    static void setUpClient() {
        client = createConnectedClient();
        discovery = new SchemaDiscovery(client);
    }

    @AfterAll
    static void tearDownClient() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("discoverTables() returns all tables including SCHEMALESS")
    void discoverTablesReturnsAllTables() {
        List<String> tables = discovery.discoverTables();

        assertTrue(tables.contains("poi"), "Should contain poi");
        assertTrue(tables.contains("park"), "Should contain park");
        assertTrue(tables.contains("trail"), "Should contain trail");
        assertTrue(tables.contains("empty_geo"), "Should contain empty_geo");
        assertTrue(tables.contains("config"), "Should contain config");
        assertTrue(tables.contains("event"), "Should contain event (SCHEMALESS)");
    }

    @Test
    @DisplayName("discoverGeometryTables() returns tables with geometry, excluding config and event")
    void discoverGeometryTablesExcludesTablesWithoutGeometry() {
        List<TableSchema> geoTables = discovery.discoverGeometryTables();
        List<String> geoTableNames = geoTables.stream()
                .map(TableSchema::getTableName)
                .collect(Collectors.toList());

        assertTrue(geoTableNames.contains("poi"), "Should contain poi");
        assertTrue(geoTableNames.contains("park"), "Should contain park");
        assertTrue(geoTableNames.contains("trail"), "Should contain trail");
        assertTrue(geoTableNames.contains("empty_geo"), "Should contain empty_geo");

        assertFalse(geoTableNames.contains("config"),
                "Should NOT contain config (no geometry fields)");
        assertFalse(geoTableNames.contains("event"),
                "Should NOT contain event (no defined geometry fields)");
    }

    @Test
    @DisplayName("poi schema has correct fields: name(string), geometry(geometry<point>), category(option<string>), rating(option<float>)")
    void poiSchemaHasCorrectFields() {
        TableSchema schema = discovery.discoverTableSchema("poi");

        assertEquals("poi", schema.getTableName());
        assertTrue(schema.hasGeometryField());

        Map<String, String> fieldMap = schema.getFields().stream()
                .collect(Collectors.toMap(FieldSchema::getFieldName, FieldSchema::getSurrealKind));

        assertEquals("string", fieldMap.get("name"),
                "name field should be string");
        assertEquals("geometry<point>", fieldMap.get("geometry"),
                "geometry field should be geometry<point>");
        assertEquals("option<string>", fieldMap.get("category"),
                "category field should be option<string>");
        assertEquals("option<float>", fieldMap.get("rating"),
                "rating field should be option<float>");
    }

    @Test
    @DisplayName("park schema has polygon geometry and area_sqm float field")
    void parkSchemaHasPolygonGeometry() {
        TableSchema schema = discovery.discoverTableSchema("park");

        assertEquals("park", schema.getTableName());
        assertTrue(schema.hasGeometryField());

        Map<String, String> fieldMap = schema.getFields().stream()
                .collect(Collectors.toMap(FieldSchema::getFieldName, FieldSchema::getSurrealKind));

        assertEquals("geometry<polygon>", fieldMap.get("geometry"),
                "geometry field should be geometry<polygon>");
        assertEquals("float", fieldMap.get("area_sqm"),
                "area_sqm field should be float");
    }

    @Test
    @DisplayName("trail schema has line geometry and difficulty string field")
    void trailSchemaHasLineGeometry() {
        TableSchema schema = discovery.discoverTableSchema("trail");

        assertEquals("trail", schema.getTableName());
        assertTrue(schema.hasGeometryField());

        Map<String, String> fieldMap = schema.getFields().stream()
                .collect(Collectors.toMap(FieldSchema::getFieldName, FieldSchema::getSurrealKind));

        assertEquals("geometry<line>", fieldMap.get("geometry"),
                "geometry field should be geometry<line>");
        assertEquals("string", fieldMap.get("difficulty"),
                "difficulty field should be string");
    }

    @Test
    @DisplayName("empty_geo table with zero records still reports correct schema")
    void emptyTableStillReportsCorrectSchema() {
        TableSchema schema = discovery.discoverTableSchema("empty_geo");

        assertEquals("empty_geo", schema.getTableName());
        assertTrue(schema.hasGeometryField());

        Map<String, String> fieldMap = schema.getFields().stream()
                .collect(Collectors.toMap(FieldSchema::getFieldName, FieldSchema::getSurrealKind));

        assertEquals("string", fieldMap.get("name"),
                "name field should be string");
        assertEquals("geometry<point>", fieldMap.get("geometry"),
                "geometry field should be geometry<point>");
    }
}
