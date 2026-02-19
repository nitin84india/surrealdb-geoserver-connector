package org.geotools.data.surrealdb.schema;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.geotools.data.surrealdb.client.SurrealDBClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Domain service for discovering SurrealDB table schemas.
 * Executes INFO FOR DB and INFO FOR TABLE queries to introspect
 * database structure and map it to {@link TableSchema} value objects.
 *
 * <p>Tables with at least one geometry field are exposed to GeoServer,
 * regardless of whether they are SCHEMAFULL or SCHEMALESS.</p>
 */
public class SchemaDiscovery {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaDiscovery.class);

    private final SurrealDBClient client;
    private final Map<String, Boolean> tableSchemaModes = new HashMap<>();

    public SchemaDiscovery(SurrealDBClient client) {
        this.client = client;
    }

    /**
     * Discovers all table names in the current database.
     * Both SCHEMAFULL and SCHEMALESS tables are included; geometry filtering
     * happens later in {@link #discoverGeometryTables()}.
     *
     * @return list of all table names
     */
    public List<String> discoverTables() {
        LOG.debug("Discovering tables via INFO FOR DB");
        String json = client.queryAsJson("INFO FOR DB");
        LOG.debug("INFO FOR DB response: {}", json);

        JsonObject dbInfo = JsonParser.parseString(json).getAsJsonObject();
        JsonObject tables = dbInfo.getAsJsonObject("tables");

        if (tables == null || tables.isEmpty()) {
            LOG.info("No tables found in database");
            return Collections.emptyList();
        }

        List<String> allTables = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : tables.entrySet()) {
            String tableName = entry.getKey();
            String definition = entry.getValue().getAsString();
            boolean isSchemafull = definition.contains("SCHEMAFULL");

            tableSchemaModes.put(tableName, isSchemafull);
            allTables.add(tableName);
            LOG.debug("Found table: {} ({})", tableName,
                    isSchemafull ? "SCHEMAFULL" : "SCHEMALESS");
        }

        return allTables;
    }

    /**
     * Discovers the schema for a specific table by parsing field definitions.
     *
     * @param tableName the table to introspect
     * @return the table schema with field types
     */
    public TableSchema discoverTableSchema(String tableName) {
        LOG.debug("Discovering schema for table: {}", tableName);
        String json = client.queryAsJson("INFO FOR TABLE " + tableName);
        LOG.debug("INFO FOR TABLE {} response: {}", tableName, json);

        JsonObject tableInfo = JsonParser.parseString(json).getAsJsonObject();
        JsonObject fields = tableInfo.getAsJsonObject("fields");

        List<FieldSchema> fieldSchemas = new ArrayList<>();
        if (fields != null) {
            for (Map.Entry<String, JsonElement> entry : fields.entrySet()) {
                String fieldName = entry.getKey();

                // Skip sub-field definitions (e.g., "address.city", "photos.*")
                if (fieldName.contains(".")) {
                    LOG.debug("  Skipping sub-field '{}'", fieldName);
                    continue;
                }

                String fieldDefinition = entry.getValue().getAsString();
                String kind = extractFieldKind(fieldDefinition);

                if (kind != null) {
                    fieldSchemas.add(new FieldSchema(fieldName, kind));
                    LOG.debug("  Field '{}' kind: {}", fieldName, kind);
                } else {
                    LOG.debug("  Skipping field '{}' with no TYPE: {}", fieldName, fieldDefinition);
                }
            }
        }

        boolean schemafull = tableSchemaModes.getOrDefault(tableName, false);
        return new TableSchema(tableName, fieldSchemas, schemafull);
    }

    /**
     * Discovers all tables that contain at least one geometry field.
     * Both SCHEMAFULL and SCHEMALESS tables are considered.
     *
     * @return list of table schemas with geometry fields
     */
    public List<TableSchema> discoverGeometryTables() {
        List<String> tableNames = discoverTables();
        return tableNames.stream()
                .map(this::discoverTableSchema)
                .filter(TableSchema::hasGeometryField)
                .collect(Collectors.toList());
    }

    /**
     * Extracts the field kind (type) from a DEFINE FIELD definition string.
     * Example: "DEFINE FIELD geometry ON poi TYPE geometry&lt;point&gt;" → "geometry&lt;point&gt;"
     */
    String extractFieldKind(String fieldDefinition) {
        // Pattern: "DEFINE FIELD name ON table TYPE <kind> ..."
        String upper = fieldDefinition.toUpperCase();
        int typeIndex = upper.indexOf(" TYPE ");
        if (typeIndex < 0) {
            return null;
        }

        String afterType = fieldDefinition.substring(typeIndex + 6).trim();
        // Kind ends at the next space, or end of string
        // But geometry<point> contains no spaces, and types like "option<string>" also have angle brackets
        int endIndex = findKindEnd(afterType);
        return afterType.substring(0, endIndex).toLowerCase();
    }

    private int findKindEnd(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            } else if (c == ' ' && depth == 0) {
                return i;
            }
        }
        return s.length();
    }
}
