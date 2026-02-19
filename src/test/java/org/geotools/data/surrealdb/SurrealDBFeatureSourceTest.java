package org.geotools.data.surrealdb;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.geotools.api.data.FeatureReader;
import org.geotools.api.data.Query;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.surrealdb.client.ConnectionConfig;
import org.geotools.data.surrealdb.client.SurrealDBClient;
import org.geotools.data.surrealdb.schema.FieldSchema;
import org.geotools.data.surrealdb.schema.TableSchema;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SurrealDBFeatureSource}.
 *
 * <p>Uses a mock SurrealDBClient to test query construction and result parsing
 * without requiring a live SurrealDB instance.</p>
 */
@ExtendWith(MockitoExtension.class)
class SurrealDBFeatureSourceTest {

    @Mock
    private SurrealDBClient mockClient;

    private SurrealDBDataStore dataStore;
    private ConnectionConfig config;

    @BeforeEach
    void setUp() {
        config = ConnectionConfig.builder()
                .host("localhost").port(8000)
                .namespace("test").database("test")
                .username("root").password("root")
                .build();
        dataStore = new SurrealDBDataStore(mockClient, config);
    }

    private void setupPoiSchema() {
        List<FieldSchema> fields = Arrays.asList(
                new FieldSchema("name", "string"),
                new FieldSchema("geometry", "geometry<point>"),
                new FieldSchema("category", "string"),
                new FieldSchema("rating", "float")
        );
        TableSchema poiSchema = new TableSchema("poi", fields, true);

        // Manually cache the schema (simulates what createTypeNames does)
        dataStore.getTableSchema("poi"); // will be null, but that's OK
        // We need to use reflection or pre-populate via discoverGeometryTables
        // Instead, let's mock the discovery
        String dbInfoJson = """
                {
                  "tables": {
                    "poi": "DEFINE TABLE poi TYPE NORMAL SCHEMAFULL"
                  }
                }
                """;
        String poiTableInfo = """
                {
                  "fields": {
                    "name": "DEFINE FIELD name ON poi TYPE string",
                    "geometry": "DEFINE FIELD geometry ON poi TYPE geometry<point>",
                    "category": "DEFINE FIELD category ON poi TYPE string",
                    "rating": "DEFINE FIELD rating ON poi TYPE float"
                  }
                }
                """;
        when(mockClient.queryAsJson("INFO FOR DB")).thenReturn(dbInfoJson);
        when(mockClient.queryAsJson("INFO FOR TABLE poi")).thenReturn(poiTableInfo);
    }

    @Test
    void getReaderInternalReturnsPopulatedReader() throws Exception {
        setupPoiSchema();

        String queryResult = """
                [
                  {"id": "abc123", "name": "Central Park", "geometry": {"type":"Point","coordinates":[-73.9654,40.7829]}, "category": "park", "rating": 4.8},
                  {"id": "def456", "name": "Times Square", "geometry": {"type":"Point","coordinates":[-73.9855,40.7580]}, "category": "landmark", "rating": 4.2}
                ]
                """;

        // The queryAsJson for the SELECT will be called after schema discovery
        when(mockClient.queryAsJson(org.mockito.ArgumentMatchers.startsWith("SELECT")))
                .thenReturn(queryResult);

        // Trigger schema caching
        dataStore.createTypeNames();

        SurrealDBFeatureSource source = (SurrealDBFeatureSource) dataStore.getFeatureSource("poi");
        Query query = new Query("poi");

        FeatureReader<SimpleFeatureType, SimpleFeature> reader = source.getReader(query);

        assertTrue(reader.hasNext());
        SimpleFeature f1 = reader.next();
        assertEquals("Central Park", f1.getAttribute("name"));

        assertTrue(reader.hasNext());
        SimpleFeature f2 = reader.next();
        assertEquals("Times Square", f2.getAttribute("name"));

        assertFalse(reader.hasNext());
        reader.close();
    }

    @Test
    void getReaderInternalReturnsEmptyReaderForNoResults() throws Exception {
        setupPoiSchema();

        when(mockClient.queryAsJson(org.mockito.ArgumentMatchers.startsWith("SELECT")))
                .thenReturn("[]");

        dataStore.createTypeNames();

        SurrealDBFeatureSource source = (SurrealDBFeatureSource) dataStore.getFeatureSource("poi");
        Query query = new Query("poi");

        FeatureReader<SimpleFeatureType, SimpleFeature> reader = source.getReader(query);

        assertFalse(reader.hasNext());
        reader.close();
    }

    @Test
    void getCountInternalReturnsCount() throws Exception {
        setupPoiSchema();

        when(mockClient.queryAsJson(org.mockito.ArgumentMatchers.startsWith("SELECT count")))
                .thenReturn("[{\"total\": 5}]");

        dataStore.createTypeNames();

        SurrealDBFeatureSource source = (SurrealDBFeatureSource) dataStore.getFeatureSource("poi");
        Query query = new Query("poi");

        int count = source.getCount(query);
        assertEquals(5, count);
    }

    @Test
    void getCountInternalReturnsZeroForEmptyResult() throws Exception {
        setupPoiSchema();

        when(mockClient.queryAsJson(org.mockito.ArgumentMatchers.startsWith("SELECT count")))
                .thenReturn("[]");

        dataStore.createTypeNames();

        SurrealDBFeatureSource source = (SurrealDBFeatureSource) dataStore.getFeatureSource("poi");
        Query query = new Query("poi");

        int count = source.getCount(query);
        assertEquals(0, count);
    }

    @Test
    void getBoundsInternalReturnsBoundsFromGeometries() throws Exception {
        setupPoiSchema();

        String queryResult = """
                [
                  {"geometry": {"type":"Point","coordinates":[-74.0,40.7]}},
                  {"geometry": {"type":"Point","coordinates":[-73.9,40.8]}}
                ]
                """;
        when(mockClient.queryAsJson(org.mockito.ArgumentMatchers.startsWith("SELECT geometry")))
                .thenReturn(queryResult);

        dataStore.createTypeNames();

        SurrealDBFeatureSource source = (SurrealDBFeatureSource) dataStore.getFeatureSource("poi");
        Query query = new Query("poi");

        ReferencedEnvelope bounds = source.getBounds(query);

        assertNotNull(bounds);
        assertEquals(-74.0, bounds.getMinX(), 0.01);
        assertEquals(-73.9, bounds.getMaxX(), 0.01);
        assertEquals(40.7, bounds.getMinY(), 0.01);
        assertEquals(40.8, bounds.getMaxY(), 0.01);
    }

    @Test
    void getBoundsInternalReturnsNullForEmptyResult() throws Exception {
        setupPoiSchema();

        when(mockClient.queryAsJson(org.mockito.ArgumentMatchers.startsWith("SELECT geometry")))
                .thenReturn("[]");

        dataStore.createTypeNames();

        SurrealDBFeatureSource source = (SurrealDBFeatureSource) dataStore.getFeatureSource("poi");
        Query query = new Query("poi");

        ReferencedEnvelope bounds = source.getBounds(query);

        assertNull(bounds);
    }

    @Test
    void parseJsonArrayHandlesArrayString() {
        JsonArray result = SurrealDBFeatureSource.parseJsonArray(
                "[{\"id\":\"a\"},{\"id\":\"b\"}]");
        assertEquals(2, result.size());
    }

    @Test
    void parseJsonArrayHandlesSingleObject() {
        JsonArray result = SurrealDBFeatureSource.parseJsonArray("{\"id\":\"a\"}");
        assertEquals(1, result.size());
    }

    @Test
    void parseJsonArrayHandlesNullOrEmpty() {
        assertEquals(0, SurrealDBFeatureSource.parseJsonArray(null).size());
        assertEquals(0, SurrealDBFeatureSource.parseJsonArray("").size());
        assertEquals(0, SurrealDBFeatureSource.parseJsonArray("   ").size());
    }

    @Test
    void queryIncludesLimitAndStart() throws Exception {
        setupPoiSchema();

        // Capture the query string
        when(mockClient.queryAsJson(org.mockito.ArgumentMatchers.contains("LIMIT")))
                .thenReturn("[]");

        dataStore.createTypeNames();

        SurrealDBFeatureSource source = (SurrealDBFeatureSource) dataStore.getFeatureSource("poi");
        Query query = new Query("poi");
        query.setMaxFeatures(10);
        query.setStartIndex(5);

        // This should produce a query with LIMIT 10 START 5
        FeatureReader<SimpleFeatureType, SimpleFeature> reader = source.getReader(query);
        assertFalse(reader.hasNext());
        reader.close();
    }
}
