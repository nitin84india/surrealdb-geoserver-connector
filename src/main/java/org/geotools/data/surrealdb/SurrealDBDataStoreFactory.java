package org.geotools.data.surrealdb;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFactorySpi;
import org.geotools.data.surrealdb.client.ConnectionConfig;
import org.geotools.data.surrealdb.client.SurrealDBClient;
import org.geotools.data.surrealdb.client.SurrealDBSdkClient;
import org.geotools.data.surrealdb.config.SurrealDBDataStoreParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * SPI entry point for the SurrealDB DataStore plugin.
 * GeoServer discovers this class via META-INF/services/org.geotools.api.data.DataStoreFactorySpi.
 *
 * <p>Phase 1: Validates connection parameters and verifies connectivity.
 * The actual DataStore implementation will be added in Phase 2.</p>
 */
public class SurrealDBDataStoreFactory implements DataStoreFactorySpi {

    private static final Logger LOG = LoggerFactory.getLogger(SurrealDBDataStoreFactory.class);

    @Override
    public String getDisplayName() {
        return "SurrealDB";
    }

    @Override
    public String getDescription() {
        return "DataStore for SurrealDB 3.0 - serves geometry data as OGC-compliant WMS/WFS layers";
    }

    @Override
    public Param[] getParametersInfo() {
        return SurrealDBDataStoreParams.ALL_PARAMS;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("com.surrealdb.Surreal");
            return true;
        } catch (ClassNotFoundException e) {
            LOG.warn("SurrealDB SDK not found on classpath");
            return false;
        }
    }

    @Override
    public boolean canProcess(Map<String, ?> params) {
        if (params == null) {
            return false;
        }
        try {
            Object dbtype = SurrealDBDataStoreParams.DBTYPE.lookUp(params);
            if (!SurrealDBDataStoreParams.DBTYPE_VALUE.equals(dbtype)) {
                return false;
            }
            // Verify required params are present
            return SurrealDBDataStoreParams.NAMESPACE.lookUp(params) != null
                    && SurrealDBDataStoreParams.DATABASE.lookUp(params) != null
                    && SurrealDBDataStoreParams.USER.lookUp(params) != null;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public DataStore createDataStore(Map<String, ?> params) throws IOException {
        LOG.info("Creating SurrealDB DataStore");
        ConnectionConfig config = SurrealDBDataStoreParams.toConnectionConfig(params);

        SurrealDBClient client = createClient();
        client.connect(config);
        LOG.info("SurrealDB connection verified successfully");

        // Phase 1 placeholder - DataStore class will be implemented in Phase 2
        client.close();
        throw new UnsupportedOperationException(
                "SurrealDBDataStore not yet implemented - Phase 2. Connection was verified successfully.");
    }

    @Override
    public DataStore createNewDataStore(Map<String, ?> params) throws IOException {
        throw new UnsupportedOperationException(
                "SurrealDB connector is read-only. Use createDataStore() for existing databases.");
    }

    /**
     * Factory method for creating the SurrealDB client.
     * Protected to allow test subclasses to inject mocks.
     */
    protected SurrealDBClient createClient() {
        return new SurrealDBSdkClient();
    }
}
