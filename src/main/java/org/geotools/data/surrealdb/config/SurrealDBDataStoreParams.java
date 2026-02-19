package org.geotools.data.surrealdb.config;

import org.geotools.api.data.DataAccessFactory.Param;
import org.geotools.data.surrealdb.client.ConnectionConfig;

import java.io.IOException;
import java.util.Map;

/**
 * Defines all GeoServer DataStore connection parameters for SurrealDB.
 * These parameters appear in the GeoServer Web Admin "New DataStore" form.
 */
public final class SurrealDBDataStoreParams {

    public static final String DBTYPE_VALUE = "surrealdb";

    public static final Param DBTYPE = new Param(
            "dbtype", String.class, "Database type identifier", true, DBTYPE_VALUE);

    public static final Param HOST = new Param(
            "host", String.class, "SurrealDB server hostname", false, "localhost");

    public static final Param PORT = new Param(
            "port", Integer.class, "SurrealDB server port", false, 8000);

    public static final Param NAMESPACE = new Param(
            "namespace", String.class, "SurrealDB namespace", true);

    public static final Param DATABASE = new Param(
            "database", String.class, "SurrealDB database", true);

    public static final Param USER = new Param(
            "user", String.class, "SurrealDB username", true);

    public static final Param PASSWORD = new Param(
            "password", String.class, "SurrealDB password", true, null, null,
            Map.of(Param.IS_PASSWORD, true));

    public static final Param USE_TLS = new Param(
            "use_tls", Boolean.class, "Enable TLS encryption", false, false);

    public static final Param PROTOCOL = new Param(
            "protocol", String.class, "Connection protocol (http or ws)", false, "http");

    public static final Param POOL_SIZE = new Param(
            "pool_size", Integer.class, "Connection pool size", false, 5);

    public static final Param TIMEOUT = new Param(
            "timeout", Integer.class, "Connection timeout in milliseconds", false, 30000);

    public static final Param GEOMETRY_FIELD = new Param(
            "geometry_field", String.class, "Default geometry field name", false, "geometry");

    public static final Param SRID = new Param(
            "srid", Integer.class, "Default spatial reference ID", false, 4326);

    public static final Param[] ALL_PARAMS = {
            DBTYPE, HOST, PORT, NAMESPACE, DATABASE, USER, PASSWORD,
            USE_TLS, PROTOCOL, POOL_SIZE, TIMEOUT, GEOMETRY_FIELD, SRID
    };

    private SurrealDBDataStoreParams() {
    }

    /**
     * Converts a GeoServer parameter map into a {@link ConnectionConfig}.
     *
     * @param params the GeoServer parameter map
     * @return a fully populated ConnectionConfig
     * @throws IOException if required parameters are missing
     */
    public static ConnectionConfig toConnectionConfig(Map<String, ?> params) throws IOException {
        String host = (String) HOST.lookUp(params);
        Integer port = (Integer) PORT.lookUp(params);
        String namespace = (String) NAMESPACE.lookUp(params);
        String database = (String) DATABASE.lookUp(params);
        String user = (String) USER.lookUp(params);
        String password = (String) PASSWORD.lookUp(params);
        Boolean useTls = (Boolean) USE_TLS.lookUp(params);
        String protocol = (String) PROTOCOL.lookUp(params);
        Integer poolSize = (Integer) POOL_SIZE.lookUp(params);
        Integer timeout = (Integer) TIMEOUT.lookUp(params);
        Integer srid = (Integer) SRID.lookUp(params);

        ConnectionConfig.Builder builder = ConnectionConfig.builder()
                .host(host != null ? host : "localhost")
                .namespace(namespace)
                .database(database)
                .username(user)
                .password(password);

        if (port != null) builder.port(port);
        if (useTls != null) builder.useTls(useTls);
        if (protocol != null) builder.protocol(protocol);
        if (poolSize != null) builder.poolSize(poolSize);
        if (timeout != null) builder.timeoutMs(timeout);
        if (srid != null) builder.srid(srid);

        return builder.build();
    }
}
