package org.geotools.data.surrealdb;

import org.geotools.api.feature.type.Name;
import org.geotools.data.surrealdb.client.ConnectionConfig;
import org.geotools.data.surrealdb.client.SurrealDBClient;
import org.geotools.data.surrealdb.schema.FeatureTypeMapper;
import org.geotools.data.surrealdb.schema.SchemaDiscovery;
import org.geotools.data.surrealdb.schema.TableSchema;
import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.feature.NameImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ContentDataStore implementation for SurrealDB.
 * Discovers SCHEMAFULL tables with geometry fields and exposes them
 * as GeoServer feature types.
 */
public class SurrealDBDataStore extends ContentDataStore {

    private static final Logger LOG = LoggerFactory.getLogger(SurrealDBDataStore.class);

    private final SurrealDBClient client;
    private final ConnectionConfig config;
    private final SchemaDiscovery schemaDiscovery;
    private final FeatureTypeMapper featureTypeMapper;
    private final Map<String, TableSchema> schemaCache = new ConcurrentHashMap<>();

    public SurrealDBDataStore(SurrealDBClient client, ConnectionConfig config) {
        this.client = client;
        this.config = config;
        this.schemaDiscovery = new SchemaDiscovery(client);
        this.featureTypeMapper = new FeatureTypeMapper(config.getSrid());
    }

    @Override
    protected List<Name> createTypeNames() throws IOException {
        LOG.info("Discovering geometry tables in SurrealDB");
        try {
            List<TableSchema> geometryTables = schemaDiscovery.discoverGeometryTables();

            // Cache discovered schemas for later use by FeatureSource
            for (TableSchema schema : geometryTables) {
                schemaCache.put(schema.getTableName(), schema);
            }

            List<Name> names = geometryTables.stream()
                    .map(schema -> (Name) new NameImpl(schema.getTableName()))
                    .collect(Collectors.toList());

            LOG.info("Discovered {} geometry tables: {}", names.size(),
                    names.stream().map(Name::getLocalPart).collect(Collectors.joining(", ")));

            return names;
        } catch (Exception e) {
            LOG.error("Failed to discover SurrealDB tables", e);
            throw new IOException("Failed to discover SurrealDB tables", e);
        }
    }

    @Override
    protected ContentFeatureSource createFeatureSource(ContentEntry entry) throws IOException {
        String tableName = entry.getName().getLocalPart();
        LOG.debug("Creating FeatureSource for table: {}", tableName);

        // Ensure schema is cached (may not be if entry was created externally)
        if (!schemaCache.containsKey(tableName)) {
            TableSchema schema = schemaDiscovery.discoverTableSchema(tableName);
            schemaCache.put(tableName, schema);
        }

        return new SurrealDBFeatureSource(entry, null, this);
    }

    @Override
    public void dispose() {
        LOG.info("Disposing SurrealDB DataStore");
        try {
            client.close();
        } catch (Exception e) {
            LOG.warn("Error closing SurrealDB client: {}", e.getMessage());
        }
        schemaCache.clear();
        super.dispose();
    }

    // Package-private accessors for FeatureSource and other components

    SurrealDBClient getSurrealDBClient() {
        return client;
    }

    ConnectionConfig getConnectionConfig() {
        return config;
    }

    TableSchema getTableSchema(String tableName) {
        return schemaCache.get(tableName);
    }

    FeatureTypeMapper getFeatureTypeMapper() {
        return featureTypeMapper;
    }

    SchemaDiscovery getSchemaDiscovery() {
        return schemaDiscovery;
    }
}
