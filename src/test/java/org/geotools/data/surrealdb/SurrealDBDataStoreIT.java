package org.geotools.data.surrealdb;

import org.geotools.api.data.DataStore;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.data.surrealdb.client.SurrealDBConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the full DataStore lifecycle via {@link SurrealDBDataStoreFactory}
 * against a live SurrealDB Testcontainer.
 *
 * <p>Validates factory creation, schema discovery, geometry type mapping,
 * attribute introspection, and disposal against real SurrealDB data.</p>
 */
class SurrealDBDataStoreIT extends SurrealDBContainerIT {

    @Test
    @DisplayName("Factory canProcess returns true for valid DataStore params")
    void factoryCanProcessValidParams() {
        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory();
        Map<String, Object> params = buildDataStoreParams();

        assertTrue(factory.canProcess(params),
                "Factory should accept valid DataStore parameters");
    }

    @Test
    @DisplayName("Factory createDataStore returns a non-null SurrealDBDataStore")
    void createDataStoreSucceeds() throws IOException {
        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory();
        Map<String, Object> params = buildDataStoreParams();

        DataStore dataStore = factory.createDataStore(params);
        try {
            assertNotNull(dataStore, "createDataStore should return a non-null DataStore");
            assertInstanceOf(SurrealDBDataStore.class, dataStore,
                    "DataStore should be an instance of SurrealDBDataStore");
        } finally {
            dataStore.dispose();
        }
    }

    @Test
    @DisplayName("getTypeNames returns only SCHEMAFULL tables with geometry fields")
    void getTypeNamesReturnsGeometryTables() throws IOException {
        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory();
        DataStore dataStore = factory.createDataStore(buildDataStoreParams());
        try {
            String[] typeNames = dataStore.getTypeNames();
            List<String> typeNameList = Arrays.asList(typeNames);

            // Tables with geometry fields should be included
            assertTrue(typeNameList.contains("poi"),
                    "Type names should include 'poi' (SCHEMAFULL with geometry<point>)");
            assertTrue(typeNameList.contains("park"),
                    "Type names should include 'park' (SCHEMAFULL with geometry<polygon>)");
            assertTrue(typeNameList.contains("trail"),
                    "Type names should include 'trail' (SCHEMAFULL with geometry<line>)");
            assertTrue(typeNameList.contains("empty_geo"),
                    "Type names should include 'empty_geo' (SCHEMAFULL with geometry<point>, no records)");

            // Tables without geometry or SCHEMALESS should be excluded
            assertFalse(typeNameList.contains("config"),
                    "Type names should NOT include 'config' (no geometry fields)");
            assertFalse(typeNameList.contains("event"),
                    "Type names should NOT include 'event' (SCHEMALESS table)");
        } finally {
            dataStore.dispose();
        }
    }

    @Test
    @DisplayName("poi schema has Point geometry binding")
    void poiSchemaHasPointGeometry() throws IOException {
        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory();
        DataStore dataStore = factory.createDataStore(buildDataStoreParams());
        try {
            SimpleFeatureType schema = dataStore.getSchema("poi");

            assertNotNull(schema, "Schema for 'poi' should not be null");
            assertNotNull(schema.getGeometryDescriptor(),
                    "poi schema should have a geometry descriptor");
            assertEquals(Point.class, schema.getGeometryDescriptor().getType().getBinding(),
                    "poi geometry should be bound to Point.class");
        } finally {
            dataStore.dispose();
        }
    }

    @Test
    @DisplayName("park schema has Polygon geometry binding")
    void parkSchemaHasPolygonGeometry() throws IOException {
        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory();
        DataStore dataStore = factory.createDataStore(buildDataStoreParams());
        try {
            SimpleFeatureType schema = dataStore.getSchema("park");

            assertNotNull(schema, "Schema for 'park' should not be null");
            assertNotNull(schema.getGeometryDescriptor(),
                    "park schema should have a geometry descriptor");
            assertEquals(Polygon.class, schema.getGeometryDescriptor().getType().getBinding(),
                    "park geometry should be bound to Polygon.class");
        } finally {
            dataStore.dispose();
        }
    }

    @Test
    @DisplayName("trail schema has LineString geometry binding")
    void trailSchemaHasLineStringGeometry() throws IOException {
        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory();
        DataStore dataStore = factory.createDataStore(buildDataStoreParams());
        try {
            SimpleFeatureType schema = dataStore.getSchema("trail");

            assertNotNull(schema, "Schema for 'trail' should not be null");
            assertNotNull(schema.getGeometryDescriptor(),
                    "trail schema should have a geometry descriptor");
            assertEquals(LineString.class, schema.getGeometryDescriptor().getType().getBinding(),
                    "trail geometry should be bound to LineString.class");
        } finally {
            dataStore.dispose();
        }
    }

    @Test
    @DisplayName("poi schema has expected attributes: id, name, geometry, category, rating")
    void poiSchemaHasExpectedAttributes() throws IOException {
        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory();
        DataStore dataStore = factory.createDataStore(buildDataStoreParams());
        try {
            SimpleFeatureType schema = dataStore.getSchema("poi");
            assertNotNull(schema, "Schema for 'poi' should not be null");

            // Verify id attribute (always added as String)
            AttributeDescriptor idAttr = schema.getDescriptor("id");
            assertNotNull(idAttr, "poi schema should have an 'id' attribute");
            assertEquals(String.class, idAttr.getType().getBinding(),
                    "id attribute should be bound to String.class");

            // Verify name attribute
            AttributeDescriptor nameAttr = schema.getDescriptor("name");
            assertNotNull(nameAttr, "poi schema should have a 'name' attribute");
            assertEquals(String.class, nameAttr.getType().getBinding(),
                    "name attribute should be bound to String.class");

            // Verify geometry attribute
            AttributeDescriptor geomAttr = schema.getDescriptor("geometry");
            assertNotNull(geomAttr, "poi schema should have a 'geometry' attribute");
            assertEquals(Point.class, geomAttr.getType().getBinding(),
                    "geometry attribute should be bound to Point.class");

            // Verify category attribute
            AttributeDescriptor categoryAttr = schema.getDescriptor("category");
            assertNotNull(categoryAttr, "poi schema should have a 'category' attribute");
            assertEquals(String.class, categoryAttr.getType().getBinding(),
                    "category attribute should be bound to String.class");

            // Verify rating attribute
            AttributeDescriptor ratingAttr = schema.getDescriptor("rating");
            assertNotNull(ratingAttr, "poi schema should have a 'rating' attribute");
            assertEquals(Double.class, ratingAttr.getType().getBinding(),
                    "rating attribute should be bound to Double.class");
        } finally {
            dataStore.dispose();
        }
    }

    @Test
    @DisplayName("dispose closes the DataStore cleanly without exceptions")
    void disposeClosesCleanly() throws IOException {
        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory();
        DataStore dataStore = factory.createDataStore(buildDataStoreParams());

        // Trigger type name discovery to fully initialize
        dataStore.getTypeNames();

        // Dispose should not throw
        assertDoesNotThrow(dataStore::dispose,
                "Disposing an active DataStore should not throw exceptions");
    }

    @Test
    @DisplayName("Bad credentials cause exception on createDataStore")
    void badCredentialsCausesIOException() {
        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory();

        Map<String, Object> params = buildDataStoreParams();
        params.put("password", "wrong_password_that_should_fail");

        // SurrealDBConnectionException (RuntimeException) is thrown when authentication fails
        // because the SDK raises a native exception before the DataStore wrapping layer
        assertThrows(SurrealDBConnectionException.class,
                () -> factory.createDataStore(params),
                "createDataStore with bad credentials should throw SurrealDBConnectionException");
    }
}
