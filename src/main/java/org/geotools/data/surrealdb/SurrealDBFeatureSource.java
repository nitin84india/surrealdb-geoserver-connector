package org.geotools.data.surrealdb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.geotools.api.data.FeatureReader;
import org.geotools.api.data.Query;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.feature.type.GeometryDescriptor;
import org.geotools.api.filter.Filter;
import org.geotools.data.surrealdb.client.SurrealDBClient;
import org.geotools.data.surrealdb.filter.SurrealQLFilterTranslator;
import org.geotools.data.surrealdb.filter.TranslationResult;
import org.geotools.data.surrealdb.geometry.GeoJsonToJtsConverter;
import org.geotools.data.surrealdb.schema.GeometryFieldDetector;
import org.geotools.data.surrealdb.schema.TableSchema;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.StringJoiner;

/**
 * ContentFeatureSource implementation for a single SurrealDB table.
 * Builds FeatureTypes from table schema and executes SurrealQL queries
 * to stream features, compute bounds, and count records.
 */
public class SurrealDBFeatureSource extends ContentFeatureSource {

    private static final Logger LOG = LoggerFactory.getLogger(SurrealDBFeatureSource.class);

    public SurrealDBFeatureSource(ContentEntry entry, Query query, SurrealDBDataStore dataStore) {
        super(entry, query);
    }

    @Override
    protected boolean canFilter() {
        return true;
    }

    @Override
    protected SimpleFeatureType buildFeatureType() throws IOException {
        SurrealDBDataStore dataStore = (SurrealDBDataStore) getDataStore();
        String tableName = entry.getName().getLocalPart();

        TableSchema schema = dataStore.getTableSchema(tableName);
        if (schema == null) {
            throw new IOException("No schema found for table: " + tableName);
        }

        LOG.info("Building feature type for table: {}", tableName);
        return dataStore.getFeatureTypeMapper().buildFeatureType(schema);
    }

    @Override
    protected FeatureReader<SimpleFeatureType, SimpleFeature> getReaderInternal(Query query)
            throws IOException {
        SurrealDBDataStore dataStore = (SurrealDBDataStore) getDataStore();
        SurrealDBClient client = dataStore.getSurrealDBClient();
        String tableName = entry.getName().getLocalPart();
        SimpleFeatureType featureType = getSchema();

        try {
            // Build SELECT clause
            String selectClause = buildSelectClause(query, featureType);

            // Build WHERE clause from filter
            TranslationResult filterResult = translateFilter(query.getFilter(), dataStore, tableName);
            String whereClause = filterResult.isEmpty() ? "" : " WHERE " + filterResult.getWhereClause();

            // Build LIMIT/START
            String limitClause = buildLimitClause(query);

            String surrealQL = "SELECT " + selectClause + " FROM " + tableName + whereClause + limitClause;
            LOG.debug("Executing feature query: {}", surrealQL);

            // Execute query
            String jsonResult;
            Map<String, Object> params = filterResult.getParams();
            if (params.isEmpty()) {
                jsonResult = client.queryAsJson(surrealQL);
            } else {
                jsonResult = client.queryBindAsJson(surrealQL, params);
            }

            JsonArray records = parseJsonArray(jsonResult);
            LOG.debug("Query returned {} records for table '{}'", records.size(), tableName);

            return new SurrealDBFeatureReader(featureType, records);
        } catch (Exception e) {
            LOG.error("Failed to read features from table '{}': {}", tableName, e.getMessage());
            throw new IOException("Failed to read features from " + tableName, e);
        }
    }

    @Override
    protected ReferencedEnvelope getBoundsInternal(Query query) throws IOException {
        SurrealDBDataStore dataStore = (SurrealDBDataStore) getDataStore();
        SurrealDBClient client = dataStore.getSurrealDBClient();
        String tableName = entry.getName().getLocalPart();
        SimpleFeatureType featureType = getSchema();

        GeometryDescriptor geomDescriptor = featureType.getGeometryDescriptor();
        if (geomDescriptor == null) {
            return null;
        }

        String geomField = geomDescriptor.getLocalName();

        try {
            // Build WHERE clause from filter
            TranslationResult filterResult = translateFilter(query.getFilter(), dataStore, tableName);
            String whereClause = filterResult.isEmpty() ? "" : " WHERE " + filterResult.getWhereClause();

            String surrealQL = "SELECT " + geomField + " FROM " + tableName + whereClause;

            String jsonResult;
            Map<String, Object> params = filterResult.getParams();
            if (params.isEmpty()) {
                jsonResult = client.queryAsJson(surrealQL);
            } else {
                jsonResult = client.queryBindAsJson(surrealQL, params);
            }

            JsonArray records = parseJsonArray(jsonResult);
            if (records.isEmpty()) {
                return null;
            }

            Envelope envelope = new Envelope();
            for (JsonElement element : records) {
                JsonObject record = element.getAsJsonObject();
                if (record.has(geomField) && !record.get(geomField).isJsonNull()) {
                    try {
                        Geometry geom = GeoJsonToJtsConverter.convert(
                                record.get(geomField).getAsJsonObject());
                        envelope.expandToInclude(geom.getEnvelopeInternal());
                    } catch (Exception e) {
                        LOG.debug("Skipping invalid geometry in bounds calculation: {}", e.getMessage());
                    }
                }
            }

            if (envelope.isNull()) {
                return null;
            }

            return new ReferencedEnvelope(envelope,
                    CRS.decode("EPSG:" + dataStore.getConnectionConfig().getSrid()));
        } catch (Exception e) {
            LOG.warn("Failed to calculate bounds for '{}': {}", tableName, e.getMessage());
            return null;
        }
    }

    @Override
    protected int getCountInternal(Query query) throws IOException {
        SurrealDBDataStore dataStore = (SurrealDBDataStore) getDataStore();
        SurrealDBClient client = dataStore.getSurrealDBClient();
        String tableName = entry.getName().getLocalPart();

        try {
            // Build WHERE clause from filter
            TranslationResult filterResult = translateFilter(query.getFilter(), dataStore, tableName);
            String whereClause = filterResult.isEmpty() ? "" : " WHERE " + filterResult.getWhereClause();

            String surrealQL = "SELECT count() AS total FROM " + tableName + whereClause + " GROUP ALL";

            String jsonResult;
            Map<String, Object> params = filterResult.getParams();
            if (params.isEmpty()) {
                jsonResult = client.queryAsJson(surrealQL);
            } else {
                jsonResult = client.queryBindAsJson(surrealQL, params);
            }

            JsonArray records = parseJsonArray(jsonResult);
            if (records.isEmpty()) {
                return 0;
            }

            JsonObject countResult = records.get(0).getAsJsonObject();
            if (countResult.has("total")) {
                return countResult.get("total").getAsInt();
            }

            return -1;
        } catch (Exception e) {
            LOG.warn("Failed to count features for '{}': {}", tableName, e.getMessage());
            return -1;
        }
    }

    /**
     * Builds the SELECT clause, using property selection or wildcard with meta::id.
     */
    private String buildSelectClause(Query query, SimpleFeatureType featureType) {
        String[] propertyNames = query.getPropertyNames();

        if (propertyNames == null || propertyNames.length == 0) {
            // Select all: use * first, then meta::id(id) AS id to override raw record IDs
            return "*, meta::id(id) AS id";
        }

        StringJoiner joiner = new StringJoiner(", ");
        // Always include meta::id(id) AS id for clean record IDs
        joiner.add("meta::id(id) AS id");
        for (String prop : propertyNames) {
            if (!"id".equals(prop)) {
                joiner.add(prop);
            }
        }
        return joiner.toString();
    }

    /**
     * Builds LIMIT and START clauses from the query.
     */
    private String buildLimitClause(Query query) {
        StringBuilder sb = new StringBuilder();

        if (query.getMaxFeatures() != Integer.MAX_VALUE && query.getMaxFeatures() > 0) {
            sb.append(" LIMIT ").append(query.getMaxFeatures());
        }

        if (query.getStartIndex() != null && query.getStartIndex() > 0) {
            sb.append(" START ").append(query.getStartIndex());
        }

        return sb.toString();
    }

    /**
     * Translates an OGC Filter to SurrealQL WHERE clause.
     */
    private TranslationResult translateFilter(Filter filter, SurrealDBDataStore dataStore,
                                              String tableName) {
        if (filter == null || filter == Filter.INCLUDE) {
            return TranslationResult.INCLUDE;
        }

        TableSchema schema = dataStore.getTableSchema(tableName);
        SimpleFeatureType featureType = getSchema();

        String defaultGeom = featureType.getGeometryDescriptor() != null
                ? featureType.getGeometryDescriptor().getLocalName()
                : null;

        SurrealQLFilterTranslator translator = new SurrealQLFilterTranslator(schema, defaultGeom);
        return translator.translate(filter);
    }

    /**
     * Parses a JSON/SurrealQL string result into a JsonArray of records.
     * Uses lenient mode because Value.toString() returns SurrealQL format
     * (unquoted keys, single-quoted strings) rather than strict JSON.
     * Pre-processes the string to handle SurrealDB-specific syntax that
     * even lenient Gson cannot parse (bare record IDs, NONE values).
     */
    static JsonArray parseJsonArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new JsonArray();
        }

        String sanitized = sanitizeSurrealQL(json);

        try {
            com.google.gson.stream.JsonReader reader =
                    new com.google.gson.stream.JsonReader(new java.io.StringReader(sanitized));
            reader.setLenient(true);
            JsonElement element = JsonParser.parseReader(reader);

            if (element.isJsonArray()) {
                return element.getAsJsonArray();
            } else if (element.isJsonObject()) {
                JsonArray array = new JsonArray();
                array.add(element.getAsJsonObject());
                return array;
            }
        } catch (Exception e) {
            LOG.error("Failed to parse query result as JSON: {}", e.getMessage());
            LOG.debug("Raw result (sanitized): {}", sanitized);
        }

        return new JsonArray();
    }

    /**
     * Sanitizes SurrealQL-format output to make it parseable by lenient Gson.
     *
     * <p>SurrealQL-specific syntax that needs handling:</p>
     * <ul>
     *   <li>Bare record IDs: {@code poi:abc123} → {@code 'poi:abc123'}</li>
     *   <li>NONE values: {@code NONE} → {@code null}</li>
     * </ul>
     *
     * <p>Unquoted keys and single-quoted strings are handled by Gson's lenient mode.</p>
     */
    static String sanitizeSurrealQL(String input) {
        // Replace NONE with null (SurrealDB's null representation)
        String result = input.replace("NONE", "null");

        // Quote bare record IDs: patterns like "tablename:alphanumeric" that appear
        // as values (after ": " or ", " or "[ ").
        // These are NOT inside quotes (single or double) and follow a colon+space (key-value separator).
        // Pattern: after value-position delimiters, match word:word
        result = java.util.regex.Pattern
                .compile("(?<=[:,\\[\\s])\\s*([a-zA-Z_]\\w*):([a-zA-Z0-9_]+)(?=[,\\]\\s}])")
                .matcher(result)
                .replaceAll("'$1:$2'");

        return result;
    }
}
