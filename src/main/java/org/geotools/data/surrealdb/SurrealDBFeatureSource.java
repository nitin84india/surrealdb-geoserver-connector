package org.geotools.data.surrealdb;

import org.geotools.api.data.FeatureReader;
import org.geotools.api.data.Query;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.surrealdb.schema.TableSchema;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * ContentFeatureSource implementation for a single SurrealDB table.
 * Phase 2 implements schema building (buildFeatureType).
 * Phase 3 will add query execution (getReaderInternal, getBoundsInternal, getCountInternal).
 */
public class SurrealDBFeatureSource extends ContentFeatureSource {

    private static final Logger LOG = LoggerFactory.getLogger(SurrealDBFeatureSource.class);

    public SurrealDBFeatureSource(ContentEntry entry, Query query, SurrealDBDataStore dataStore) {
        super(entry, query);
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
        // Phase 3 will implement full streaming reader
        SimpleFeatureType featureType = getSchema();
        return new SurrealDBFeatureReader(featureType);
    }

    @Override
    protected ReferencedEnvelope getBoundsInternal(Query query) throws IOException {
        // Returning null is allowed by the GeoTools contract — means "unknown bounds"
        // Phase 3 will implement real bounds calculation
        return null;
    }

    @Override
    protected int getCountInternal(Query query) throws IOException {
        // Returning -1 is allowed by the GeoTools contract — means "unknown count"
        // Phase 3 will implement real count query
        return -1;
    }
}
