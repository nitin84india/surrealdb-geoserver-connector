package org.geotools.data.surrealdb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.geotools.api.data.FeatureReader;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.data.surrealdb.geometry.GeoJsonToJtsConverter;
import org.geotools.data.surrealdb.schema.GeometryFieldDetector;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

/**
 * Streaming FeatureReader over a pre-parsed JsonArray of SurrealDB records.
 * Each call to {@link #next()} converts one JSON record to a {@link SimpleFeature}.
 *
 * <p>Key behaviors:</p>
 * <ul>
 *   <li>Record IDs are extracted from the "id" field, stripping the table prefix (e.g. "poi:abc" -> "abc")</li>
 *   <li>Geometry fields are converted via {@link GeoJsonToJtsConverter}</li>
 *   <li>Scalar fields are coerced to the Java type declared in the featureType binding</li>
 *   <li>Null/missing JSON fields map to null attribute values</li>
 * </ul>
 */
public class SurrealDBFeatureReader implements FeatureReader<SimpleFeatureType, SimpleFeature> {

    private static final Logger LOG = LoggerFactory.getLogger(SurrealDBFeatureReader.class);

    private final SimpleFeatureType featureType;
    private final JsonArray records;
    private final SimpleFeatureBuilder featureBuilder;
    private int currentIndex;
    private boolean closed;

    /**
     * Creates a reader over the given JSON records.
     *
     * @param featureType the feature type schema
     * @param records     the JSON array of SurrealDB records
     */
    public SurrealDBFeatureReader(SimpleFeatureType featureType, JsonArray records) {
        this.featureType = featureType;
        this.records = records;
        this.featureBuilder = new SimpleFeatureBuilder(featureType);
        this.currentIndex = 0;
        this.closed = false;
    }

    /**
     * Creates an empty reader that returns no features.
     *
     * @param featureType the feature type schema
     */
    public SurrealDBFeatureReader(SimpleFeatureType featureType) {
        this(featureType, new JsonArray());
    }

    @Override
    public SimpleFeatureType getFeatureType() {
        return featureType;
    }

    @Override
    public boolean hasNext() {
        if (closed) return false;
        return currentIndex < records.size();
    }

    @Override
    public SimpleFeature next() throws NoSuchElementException {
        if (!hasNext()) {
            throw new NoSuchElementException("No more features available");
        }

        JsonObject record = records.get(currentIndex).getAsJsonObject();
        currentIndex++;

        String featureId = extractRecordId(record);

        for (AttributeDescriptor descriptor : featureType.getAttributeDescriptors()) {
            String attrName = descriptor.getLocalName();
            Class<?> binding = descriptor.getType().getBinding();

            if ("id".equals(attrName)) {
                featureBuilder.set(attrName, featureId);
                continue;
            }

            JsonElement value = record.has(attrName) ? record.get(attrName) : null;

            if (value == null || value instanceof JsonNull) {
                featureBuilder.set(attrName, null);
            } else if (Geometry.class.isAssignableFrom(binding)) {
                try {
                    Geometry geom = GeoJsonToJtsConverter.convert(value.getAsJsonObject());
                    featureBuilder.set(attrName, geom);
                } catch (Exception e) {
                    LOG.warn("Failed to convert geometry for field '{}': {}", attrName, e.getMessage());
                    featureBuilder.set(attrName, null);
                }
            } else {
                Object coerced = coerceScalar(value, binding);
                featureBuilder.set(attrName, coerced);
            }
        }

        return featureBuilder.buildFeature(featureId);
    }

    @Override
    public void close() {
        this.closed = true;
    }

    /**
     * Extracts the record-local ID from a SurrealDB record.
     * If the "id" field contains a table prefix (e.g. "poi:abc123"),
     * only the part after the colon is returned. If meta::id(id) was used
     * in the query, the value is already clean.
     *
     * @param record the JSON record
     * @return the extracted record ID, or a generated fallback
     */
    static String extractRecordId(JsonObject record) {
        if (!record.has("id") || record.get("id") instanceof JsonNull) {
            return "unknown_" + System.nanoTime();
        }

        String rawId = record.get("id").getAsString();

        // Handle "table:id" format from SurrealDB
        int colonIndex = rawId.indexOf(':');
        if (colonIndex >= 0) {
            return rawId.substring(colonIndex + 1);
        }

        return rawId;
    }

    /**
     * Coerces a JSON primitive to the expected Java type.
     */
    static Object coerceScalar(JsonElement value, Class<?> binding) {
        if (value == null || value instanceof JsonNull) {
            return null;
        }

        try {
            if (binding == String.class) {
                return value.getAsString();
            } else if (binding == Long.class || binding == long.class) {
                return value.getAsLong();
            } else if (binding == Integer.class || binding == int.class) {
                return value.getAsInt();
            } else if (binding == Double.class || binding == double.class) {
                return value.getAsDouble();
            } else if (binding == Float.class || binding == float.class) {
                return value.getAsFloat();
            } else if (binding == Boolean.class || binding == boolean.class) {
                return value.getAsBoolean();
            } else if (binding == BigDecimal.class) {
                return value.getAsBigDecimal();
            } else {
                // Fallback: return as string
                return value.getAsString();
            }
        } catch (Exception e) {
            LOG.debug("Failed to coerce value '{}' to {}: {}", value, binding.getSimpleName(), e.getMessage());
            return value.getAsString();
        }
    }
}
