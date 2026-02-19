package org.geotools.data.surrealdb;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.FeatureReader;
import org.geotools.api.data.Query;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.filter.FilterFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that exercise the SurrealDB DataStore with a larger dataset
 * of 150 generated point features in a {@code bulk_poi} table. Validates count,
 * spatial filtering, pagination, and concurrent reader behavior against a live
 * SurrealDB Testcontainer.
 *
 * <p>Extends {@link SurrealDBContainerIT} which provides the running container,
 * base schema initialization, and helper methods.</p>
 */
class SurrealDBLargeDatasetIT extends SurrealDBContainerIT {

    private static final int BULK_COUNT = 150;
    private static final String BULK_TABLE = "bulk_poi";

    /** Longitude range for generated points (NYC area). */
    private static final double LON_MIN = -74.1;
    private static final double LON_MAX = -73.7;

    /** Latitude range for generated points (NYC area). */
    private static final double LAT_MIN = 40.6;
    private static final double LAT_MAX = 40.9;

    private static DataStore dataStore;

    @BeforeAll
    static void setUpBulkData() throws Exception {
        // Define the bulk_poi table
        StringBuilder sql = new StringBuilder();
        sql.append("DEFINE TABLE bulk_poi SCHEMAFULL;\n");
        sql.append("DEFINE FIELD name ON bulk_poi TYPE string;\n");
        sql.append("DEFINE FIELD geometry ON bulk_poi TYPE geometry<point>;\n");
        sql.append("DEFINE FIELD category ON bulk_poi TYPE string;\n");

        // Generate 150 CREATE statements spread across the NYC area
        double lonStep = (LON_MAX - LON_MIN) / (BULK_COUNT - 1);
        double latStep = (LAT_MAX - LAT_MIN) / (BULK_COUNT - 1);

        for (int i = 0; i < BULK_COUNT; i++) {
            double lon = LON_MIN + (i * lonStep);
            double lat = LAT_MIN + (i * latStep);
            sql.append(String.format(
                    "CREATE bulk_poi SET name = 'POI %d', geometry = {\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]}, category = 'generated';\n",
                    i, lon, lat));
        }

        executeSql(sql.toString());

        // Create the shared DataStore for tests
        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory();
        dataStore = factory.createDataStore(buildDataStoreParams());
    }

    @AfterAll
    static void tearDown() {
        if (dataStore != null) {
            dataStore.dispose();
        }
    }

    @Test
    @DisplayName("Bulk insert of 150 POIs is reflected in getCount")
    void bulkInsertAndCountReturns150() throws IOException {
        int count = dataStore.getFeatureSource(BULK_TABLE).getCount(Query.ALL);
        assertEquals(BULK_COUNT, count,
                "getCount for bulk_poi should return exactly 150 records");
    }

    @Test
    @DisplayName("BBOX covering half the NYC area returns a spatial subset")
    void bboxOnLargeDatasetReturnsSpatialSubset() throws Exception {
        FilterFactory ff = CommonFactoryFinder.getFilterFactory(null);

        // BBOX covering roughly the western half of the generated point spread
        // Longitude: -74.1 to -73.9 (half of -74.1 to -73.7 range)
        // Latitude: full range 40.6 to 40.9
        double bboxMinX = -74.1;
        double bboxMaxX = -73.9;
        double bboxMinY = 40.6;
        double bboxMaxY = 40.9;

        Query query = new Query(BULK_TABLE);
        query.setFilter(ff.bbox("geometry", bboxMinX, bboxMinY, bboxMaxX, bboxMaxY, "EPSG:4326"));

        int count = 0;
        try (FeatureReader<SimpleFeatureType, SimpleFeature> reader =
                     dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT)) {
            while (reader.hasNext()) {
                SimpleFeature feature = reader.next();
                assertNotNull(feature.getDefaultGeometry(),
                        "Each returned feature should have a non-null geometry");
                count++;
            }
        }

        assertTrue(count > 0,
                "BBOX filter should return at least one feature from the western half");
        assertTrue(count < BULK_COUNT,
                "BBOX filter covering half the area should return fewer than " + BULK_COUNT
                        + " features, but got " + count);
    }

    @Test
    @DisplayName("Pagination returns distinct pages with no overlapping feature IDs")
    void paginationOnLargeDataset() throws Exception {
        // First page: LIMIT 10 START 0
        Query page1Query = new Query(BULK_TABLE);
        page1Query.setMaxFeatures(10);
        page1Query.setStartIndex(0);

        Set<String> page1Ids = new HashSet<>();
        try (FeatureReader<SimpleFeatureType, SimpleFeature> reader =
                     dataStore.getFeatureReader(page1Query, Transaction.AUTO_COMMIT)) {
            while (reader.hasNext()) {
                SimpleFeature feature = reader.next();
                page1Ids.add(feature.getID());
            }
        }
        assertEquals(10, page1Ids.size(),
                "First page (LIMIT 10 START 0) should return exactly 10 features");

        // Second page: LIMIT 10 START 10
        Query page2Query = new Query(BULK_TABLE);
        page2Query.setMaxFeatures(10);
        page2Query.setStartIndex(10);

        Set<String> page2Ids = new HashSet<>();
        try (FeatureReader<SimpleFeatureType, SimpleFeature> reader =
                     dataStore.getFeatureReader(page2Query, Transaction.AUTO_COMMIT)) {
            while (reader.hasNext()) {
                SimpleFeature feature = reader.next();
                page2Ids.add(feature.getID());
            }
        }
        assertEquals(10, page2Ids.size(),
                "Second page (LIMIT 10 START 10) should return exactly 10 features");

        // Verify no overlap between pages
        Set<String> overlap = new HashSet<>(page1Ids);
        overlap.retainAll(page2Ids);
        assertTrue(overlap.isEmpty(),
                "Page 1 and Page 2 should have no overlapping feature IDs, but found: " + overlap);
    }

    @Test
    @DisplayName("Concurrent readers each get all 150 features without interference")
    void concurrentReadersAllGetCorrectResults() throws Exception {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            List<CompletableFuture<Integer>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                    DataStore threadLocalDs = null;
                    try {
                        // Each thread creates its own DataStore instance
                        SurrealDBDataStoreFactory factory = new SurrealDBDataStoreFactory();
                        Map<String, Object> params = buildDataStoreParams();
                        threadLocalDs = factory.createDataStore(params);

                        int count = 0;
                        Query query = new Query(BULK_TABLE);
                        try (FeatureReader<SimpleFeatureType, SimpleFeature> reader =
                                     threadLocalDs.getFeatureReader(query, Transaction.AUTO_COMMIT)) {
                            while (reader.hasNext()) {
                                SimpleFeature feature = reader.next();
                                assertNotNull(feature, "Feature should not be null");
                                assertNotNull(feature.getDefaultGeometry(),
                                        "Feature geometry should not be null");
                                count++;
                            }
                        }
                        return count;
                    } catch (Exception e) {
                        throw new RuntimeException("Concurrent reader failed: " + e.getMessage(), e);
                    } finally {
                        if (threadLocalDs != null) {
                            threadLocalDs.dispose();
                        }
                    }
                }, executor);

                futures.add(future);
            }

            // Wait for all futures and verify results
            for (int i = 0; i < futures.size(); i++) {
                int result = futures.get(i).join();
                assertEquals(BULK_COUNT, result,
                        "Concurrent reader thread " + i + " should read exactly "
                                + BULK_COUNT + " features");
            }
        } finally {
            executor.shutdown();
        }
    }
}
