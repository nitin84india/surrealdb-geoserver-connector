package org.geotools.data.surrealdb.client;

import com.surrealdb.Response;

import java.io.Closeable;
import java.util.Map;

/**
 * Port interface defining the contract for SurrealDB communication.
 * Implementations handle connection lifecycle, authentication, and query execution.
 */
public interface SurrealDBClient extends Closeable {

    /**
     * Establishes a connection to SurrealDB using the provided configuration.
     *
     * @param config connection parameters
     * @throws SurrealDBConnectionException if connection fails
     */
    void connect(ConnectionConfig config);

    /**
     * @return true if the client is currently connected
     */
    boolean isConnected();

    /**
     * Executes a SurrealQL query.
     *
     * @param surrealQL the query string
     * @return the query response
     * @throws SurrealDBQueryException if the query fails
     * @throws SurrealDBConnectionException if not connected
     */
    Response query(String surrealQL);

    /**
     * Executes a SurrealQL query and returns the result as a JSON string.
     * This decouples schema parsing from the SDK's JNI-backed Response/Value types,
     * enabling testability via mock clients.
     *
     * @param surrealQL the query string
     * @return the query result as a JSON string
     * @throws SurrealDBQueryException if the query fails
     * @throws SurrealDBConnectionException if not connected
     */
    String queryAsJson(String surrealQL);

    /**
     * Executes a parameterized SurrealQL query.
     *
     * @param surrealQL the query string with $param placeholders
     * @param params    parameter bindings
     * @return the query response
     * @throws SurrealDBQueryException if the query fails
     * @throws SurrealDBConnectionException if not connected
     */
    Response queryBind(String surrealQL, Map<String, Object> params);

    /**
     * @return the current connection configuration, or null if not connected
     */
    ConnectionConfig getConfig();

    /**
     * @return true if the connection is alive and responsive
     */
    boolean isHealthy();

    /**
     * Closes the connection and releases resources.
     */
    @Override
    void close();
}
