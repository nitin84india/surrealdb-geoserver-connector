package org.geotools.data.surrealdb;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SurrealDBFeatureReader}.
 */
class SurrealDBFeatureReaderTest {

    private SimpleFeatureType featureType;

    @BeforeEach
    void setUp() throws Exception {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("poi");
        builder.setCRS(CRS.decode("EPSG:4326"));
        builder.add("id", String.class);
        builder.add("geometry", Point.class);
        builder.setDefaultGeometry("geometry");
        builder.add("name", String.class);
        builder.add("category", String.class);
        builder.add("rating", Double.class);
        featureType = builder.buildFeatureType();
    }

    @Test
    void emptyReaderHasNoFeatures() {
        SurrealDBFeatureReader reader = new SurrealDBFeatureReader(featureType);

        assertFalse(reader.hasNext());
        assertSame(featureType, reader.getFeatureType());
    }

    @Test
    void emptyReaderNextThrowsNoSuchElement() {
        SurrealDBFeatureReader reader = new SurrealDBFeatureReader(featureType);

        assertThrows(NoSuchElementException.class, reader::next);
    }

    @Test
    void singleRecordProducesSingleFeature() {
        JsonArray records = new JsonArray();
        JsonObject record = new JsonObject();
        record.addProperty("id", "abc123");
        record.addProperty("name", "Central Park");
        record.addProperty("category", "park");
        record.addProperty("rating", 4.8);
        record.add("geometry", JsonParser.parseString(
                "{\"type\":\"Point\",\"coordinates\":[-73.9654,40.7829]}"));
        records.add(record);

        SurrealDBFeatureReader reader = new SurrealDBFeatureReader(featureType, records);

        assertTrue(reader.hasNext());
        SimpleFeature feature = reader.next();
        assertNotNull(feature);
        assertEquals("abc123", feature.getID());
        assertEquals("Central Park", feature.getAttribute("name"));
        assertEquals("park", feature.getAttribute("category"));
        assertEquals(4.8, (Double) feature.getAttribute("rating"), 0.01);

        Point geom = (Point) feature.getDefaultGeometry();
        assertNotNull(geom);
        assertEquals(-73.9654, geom.getX(), 0.0001);
        assertEquals(40.7829, geom.getY(), 0.0001);

        assertFalse(reader.hasNext());
    }

    @Test
    void multipleRecordsIterateInOrder() {
        JsonArray records = new JsonArray();
        for (int i = 0; i < 3; i++) {
            JsonObject record = new JsonObject();
            record.addProperty("id", "id" + i);
            record.addProperty("name", "Feature " + i);
            record.addProperty("category", "cat");
            record.addProperty("rating", (double) i);
            record.add("geometry", JsonParser.parseString(
                    "{\"type\":\"Point\",\"coordinates\":[" + i + "," + i + "]}"));
            records.add(record);
        }

        SurrealDBFeatureReader reader = new SurrealDBFeatureReader(featureType, records);

        for (int i = 0; i < 3; i++) {
            assertTrue(reader.hasNext());
            SimpleFeature feature = reader.next();
            assertEquals("id" + i, feature.getID());
            assertEquals("Feature " + i, feature.getAttribute("name"));
        }
        assertFalse(reader.hasNext());
    }

    @Test
    void nullFieldsMapToNullAttributes() {
        JsonArray records = new JsonArray();
        JsonObject record = new JsonObject();
        record.addProperty("id", "test1");
        record.addProperty("name", "Test");
        record.add("category", JsonNull.INSTANCE);
        record.add("rating", JsonNull.INSTANCE);
        record.add("geometry", JsonParser.parseString(
                "{\"type\":\"Point\",\"coordinates\":[0,0]}"));
        records.add(record);

        SurrealDBFeatureReader reader = new SurrealDBFeatureReader(featureType, records);

        SimpleFeature feature = reader.next();
        assertNull(feature.getAttribute("category"));
        assertNull(feature.getAttribute("rating"));
    }

    @Test
    void missingFieldsMappedToNull() {
        JsonArray records = new JsonArray();
        JsonObject record = new JsonObject();
        record.addProperty("id", "test1");
        record.addProperty("name", "Test");
        // Missing: category, rating, geometry
        records.add(record);

        SurrealDBFeatureReader reader = new SurrealDBFeatureReader(featureType, records);

        SimpleFeature feature = reader.next();
        assertNull(feature.getAttribute("category"));
        assertNull(feature.getAttribute("rating"));
        assertNull(feature.getDefaultGeometry());
    }

    @Test
    void extractRecordIdStripsTablePrefix() {
        JsonObject record = new JsonObject();
        record.addProperty("id", "poi:abc123");

        assertEquals("abc123", SurrealDBFeatureReader.extractRecordId(record));
    }

    @Test
    void extractRecordIdReturnsCleanIdUnchanged() {
        JsonObject record = new JsonObject();
        record.addProperty("id", "abc123");

        assertEquals("abc123", SurrealDBFeatureReader.extractRecordId(record));
    }

    @Test
    void extractRecordIdHandlesMissingId() {
        JsonObject record = new JsonObject();

        String id = SurrealDBFeatureReader.extractRecordId(record);
        assertNotNull(id);
        assertTrue(id.startsWith("unknown_"));
    }

    @Test
    void closePreventsFurtherIteration() {
        JsonArray records = new JsonArray();
        JsonObject record = new JsonObject();
        record.addProperty("id", "test1");
        record.addProperty("name", "Test");
        record.add("geometry", JsonParser.parseString(
                "{\"type\":\"Point\",\"coordinates\":[0,0]}"));
        records.add(record);

        SurrealDBFeatureReader reader = new SurrealDBFeatureReader(featureType, records);
        assertTrue(reader.hasNext());

        reader.close();
        assertFalse(reader.hasNext());
    }

    @Test
    void exhaustedReaderThrowsNoSuchElement() {
        JsonArray records = new JsonArray();
        JsonObject record = new JsonObject();
        record.addProperty("id", "test1");
        record.addProperty("name", "Test");
        record.add("geometry", JsonParser.parseString(
                "{\"type\":\"Point\",\"coordinates\":[0,0]}"));
        records.add(record);

        SurrealDBFeatureReader reader = new SurrealDBFeatureReader(featureType, records);
        reader.next(); // consume the only record

        assertThrows(NoSuchElementException.class, reader::next);
    }

    @Test
    void coerceScalarHandlesAllTypes() {
        assertEquals("hello", SurrealDBFeatureReader.coerceScalar(
                JsonParser.parseString("\"hello\""), String.class));
        assertEquals(42L, SurrealDBFeatureReader.coerceScalar(
                JsonParser.parseString("42"), Long.class));
        assertEquals(3.14, (Double) SurrealDBFeatureReader.coerceScalar(
                JsonParser.parseString("3.14"), Double.class), 0.001);
        assertEquals(true, SurrealDBFeatureReader.coerceScalar(
                JsonParser.parseString("true"), Boolean.class));
        assertNull(SurrealDBFeatureReader.coerceScalar(JsonNull.INSTANCE, String.class));
    }
}
