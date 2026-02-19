package org.geotools.data.surrealdb.filter;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.FeatureReader;
import org.geotools.api.data.Query;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.data.surrealdb.SurrealDBContainerIT;
import org.geotools.data.surrealdb.SurrealDBDataStoreFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for spatial and property filtering against a live SurrealDB instance.
 *
 * <p>Validates that OGC Filter expressions (BBOX, property comparisons, null checks,
 * LIKE patterns) are correctly translated to SurrealQL and produce the expected
 * feature subsets when executed against the test data.</p>
 *
 * <p>Test data (from {@link SurrealDBContainerIT}):
 * <ul>
 *   <li>poi table: 7 records (Point geometry)</li>
 *   <li>park table: 2 records (Polygon geometry)</li>
 * </ul>
 *
 * <p>NYC bounding box (-75, 40, -73, 41) contains 5 POIs:
 * Central Park, Times Square, Brooklyn Bridge, Statue of Liberty, Unnamed Spot.</p>
 */
class SurrealDBSpatialFilterIT extends SurrealDBContainerIT {

    private static final FilterFactory FF = CommonFactoryFinder.getFilterFactory(null);

    private static DataStore dataStore;

    @BeforeAll
    static void setUpDataStore() throws IOException {
        Map<String, Object> params = buildDataStoreParams();
        dataStore = new SurrealDBDataStoreFactory().createDataStore(params);
        assertNotNull(dataStore, "DataStore should be created successfully");
    }

    @AfterAll
    static void tearDownDataStore() {
        if (dataStore != null) {
            dataStore.dispose();
        }
    }

    // -------------------------------------------------------------------------
    // BBOX Spatial Filter Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("BBOX over NYC area (-75, 40, -73, 41) returns 5 POIs, excludes LA and SF")
    void bboxNycAreaReturnsFivePois() throws IOException {
        Filter bbox = FF.bbox("geometry", -75.0, 40.0, -73.0, 41.0, "EPSG:4326");
        Query query = new Query("poi", bbox);

        int count = countFeatures(dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT));

        assertEquals(5, count,
                "NYC BBOX should contain Central Park, Times Square, Brooklyn Bridge, "
                        + "Statue of Liberty, and Unnamed Spot");
    }

    @Test
    @DisplayName("BBOX over entire world (-180, -90, 180, 90) returns all 7 POIs")
    void bboxEntireWorldReturnsAllSeven() throws IOException {
        Filter bbox = FF.bbox("geometry", -180.0, -90.0, 180.0, 90.0, "EPSG:4326");
        Query query = new Query("poi", bbox);

        int count = countFeatures(dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT));

        assertEquals(7, count,
                "World BBOX should return all 7 POI records");
    }

    @Test
    @DisplayName("BBOX over empty area (0, 0, 1, 1) returns zero features")
    void bboxEmptyAreaReturnsZero() throws IOException {
        Filter bbox = FF.bbox("geometry", 0.0, 0.0, 1.0, 1.0, "EPSG:4326");
        Query query = new Query("poi", bbox);

        int count = countFeatures(dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT));

        assertEquals(0, count,
                "BBOX over ocean/empty area should return 0 features");
    }

    // -------------------------------------------------------------------------
    // Property Comparison Filter Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PropertyEquals category='bridge' returns 2 POIs (Brooklyn Bridge, Golden Gate Bridge)")
    void propertyEqualsCategoryBridgeReturnsTwo() throws IOException {
        Filter eq = FF.equals(FF.property("category"), FF.literal("bridge"));
        Query query = new Query("poi", eq);

        List<SimpleFeature> features = collectFeatures(
                dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT));

        assertEquals(2, features.size(),
                "Should find exactly 2 POIs with category 'bridge'");

        List<String> names = features.stream()
                .map(f -> (String) f.getAttribute("name"))
                .sorted()
                .toList();
        assertTrue(names.contains("Brooklyn Bridge"),
                "Results should include Brooklyn Bridge");
        assertTrue(names.contains("Golden Gate Bridge"),
                "Results should include Golden Gate Bridge");
    }

    @Test
    @DisplayName("Combined BBOX(NYC) AND category='bridge' returns 1 POI (Brooklyn Bridge only)")
    void combinedBboxAndPropertyFilter() throws IOException {
        Filter bbox = FF.bbox("geometry", -75.0, 40.0, -73.0, 41.0, "EPSG:4326");
        Filter eq = FF.equals(FF.property("category"), FF.literal("bridge"));
        Filter combined = FF.and(bbox, eq);
        Query query = new Query("poi", combined);

        List<SimpleFeature> features = collectFeatures(
                dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT));

        assertEquals(1, features.size(),
                "NYC BBOX AND category='bridge' should return only Brooklyn Bridge");
        assertEquals("Brooklyn Bridge", features.get(0).getAttribute("name"),
                "The single result should be Brooklyn Bridge");
    }

    @Test
    @DisplayName("PropertyGreaterThan rating > 4.5 returns 4 POIs (Central Park, Brooklyn Bridge, Statue of Liberty, Golden Gate)")
    void ratingGreaterThan4point5() throws IOException {
        Filter gt = FF.greater(FF.property("rating"), FF.literal(4.5));
        Query query = new Query("poi", gt);

        List<SimpleFeature> features = collectFeatures(
                dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT));

        assertEquals(4, features.size(),
                "rating > 4.5 should return Central Park (4.8), Brooklyn Bridge (4.6), "
                        + "Statue of Liberty (4.9), Golden Gate Bridge (4.7)");

        List<String> names = features.stream()
                .map(f -> (String) f.getAttribute("name"))
                .sorted()
                .toList();
        assertTrue(names.contains("Central Park"), "Should include Central Park (4.8)");
        assertTrue(names.contains("Brooklyn Bridge"), "Should include Brooklyn Bridge (4.6)");
        assertTrue(names.contains("Statue of Liberty"), "Should include Statue of Liberty (4.9)");
        assertTrue(names.contains("Golden Gate Bridge"), "Should include Golden Gate Bridge (4.7)");
    }

    // -------------------------------------------------------------------------
    // Null and Pattern Filter Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PropertyIsNull on category returns 1 feature (Unnamed Spot with NONE category)")
    void propertyIsNullOnCategoryReturnsUnnamedSpot() throws IOException {
        Filter isNull = FF.isNull(FF.property("category"));
        Query query = new Query("poi", isNull);

        List<SimpleFeature> features = collectFeatures(
                dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT));

        assertEquals(1, features.size(),
                "isNull(category) should return exactly 1 feature");
        assertEquals("Unnamed Spot", features.get(0).getAttribute("name"),
                "The null-category feature should be 'Unnamed Spot'");
    }

    @Test
    @DisplayName("PropertyIsLike name matching '*Park*' returns features with 'Park' in name")
    void propertyIsLikeNameContainsPark() throws IOException {
        Filter like = FF.like(FF.property("name"), "*Park*", "*", "?", "\\");
        Query query = new Query("poi", like);

        List<SimpleFeature> features = collectFeatures(
                dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT));

        assertFalse(features.isEmpty(),
                "LIKE '*Park*' should return at least one feature");

        for (SimpleFeature feature : features) {
            String name = (String) feature.getAttribute("name");
            assertTrue(name.contains("Park"),
                    "Each matched feature name should contain 'Park', got: " + name);
        }
    }

    // -------------------------------------------------------------------------
    // Combined and Advanced Filter Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("BBOX(NYC) AND rating > 4.5 returns 3 POIs (Central Park, Brooklyn Bridge, Statue of Liberty)")
    void combinedBboxAndRatingFilter() throws IOException {
        Filter bbox = FF.bbox("geometry", -75.0, 40.0, -73.0, 41.0, "EPSG:4326");
        Filter gt = FF.greater(FF.property("rating"), FF.literal(4.5));
        Filter combined = FF.and(bbox, gt);
        Query query = new Query("poi", combined);

        List<SimpleFeature> features = collectFeatures(
                dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT));

        assertEquals(3, features.size(),
                "NYC BBOX AND rating > 4.5 should return Central Park (4.8), "
                        + "Brooklyn Bridge (4.6), Statue of Liberty (4.9)");

        List<String> names = features.stream()
                .map(f -> (String) f.getAttribute("name"))
                .sorted()
                .toList();
        assertTrue(names.contains("Central Park"), "Should include Central Park (4.8)");
        assertTrue(names.contains("Brooklyn Bridge"), "Should include Brooklyn Bridge (4.6)");
        assertTrue(names.contains("Statue of Liberty"), "Should include Statue of Liberty (4.9)");
    }

    @Test
    @DisplayName("Filter.INCLUDE returns all 7 POIs (no filter restriction)")
    void includeFilterReturnsAllFeatures() throws IOException {
        Query query = new Query("poi", Filter.INCLUDE);

        int count = countFeatures(dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT));

        assertEquals(7, count,
                "Filter.INCLUDE should return all 7 POI records without restriction");
    }

    // -------------------------------------------------------------------------
    // Cross-Table Spatial Filter Test
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("BBOX on park table returns Polygon geometry features")
    void bboxOnParkTableReturnsPolygons() throws IOException {
        // Both parks (Central Park, Bryant Park) are in the NYC area
        Filter bbox = FF.bbox("geometry", -75.0, 40.0, -73.0, 41.0, "EPSG:4326");
        Query query = new Query("park", bbox);

        List<SimpleFeature> features = collectFeatures(
                dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT));

        assertFalse(features.isEmpty(),
                "BBOX on park table should return at least one polygon feature");

        for (SimpleFeature feature : features) {
            Geometry geom = (Geometry) feature.getDefaultGeometry();
            assertNotNull(geom, "Park feature geometry should not be null");
            assertTrue(geom instanceof Polygon,
                    "Park features should have Polygon geometry, got: " + geom.getGeometryType());
        }
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    /**
     * Iterates the given FeatureReader, counts all features, and closes the reader.
     *
     * @param reader the FeatureReader to drain
     * @return the number of features in the reader
     */
    private int countFeatures(FeatureReader<SimpleFeatureType, SimpleFeature> reader)
            throws IOException {
        int count = 0;
        try {
            while (reader.hasNext()) {
                reader.next();
                count++;
            }
        } finally {
            reader.close();
        }
        return count;
    }

    /**
     * Iterates the given FeatureReader, collects all features into a list, and closes the reader.
     *
     * @param reader the FeatureReader to drain
     * @return a list of all features from the reader
     */
    private List<SimpleFeature> collectFeatures(FeatureReader<SimpleFeatureType, SimpleFeature> reader)
            throws IOException {
        List<SimpleFeature> features = new ArrayList<>();
        try {
            while (reader.hasNext()) {
                features.add(reader.next());
            }
        } finally {
            reader.close();
        }
        return features;
    }
}
