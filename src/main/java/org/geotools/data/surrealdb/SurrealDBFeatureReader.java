package org.geotools.data.surrealdb;

import org.geotools.api.data.FeatureReader;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;

import java.util.NoSuchElementException;

/**
 * Stub FeatureReader for Phase 2. Returns no features.
 * Phase 3 will replace this with a streaming implementation
 * that lazily iterates over SurrealDB query results.
 */
public class SurrealDBFeatureReader implements FeatureReader<SimpleFeatureType, SimpleFeature> {

    private final SimpleFeatureType featureType;

    public SurrealDBFeatureReader(SimpleFeatureType featureType) {
        this.featureType = featureType;
    }

    @Override
    public SimpleFeatureType getFeatureType() {
        return featureType;
    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public SimpleFeature next() throws NoSuchElementException {
        throw new NoSuchElementException("No features available - stub reader (Phase 3 will implement)");
    }

    @Override
    public void close() {
        // No-op for stub reader
    }
}
