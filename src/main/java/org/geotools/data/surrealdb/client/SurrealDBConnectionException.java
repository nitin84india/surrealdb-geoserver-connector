package org.geotools.data.surrealdb.client;

/**
 * Thrown when a connection to SurrealDB cannot be established or is lost.
 */
public class SurrealDBConnectionException extends RuntimeException {

    public SurrealDBConnectionException(String message) {
        super(message);
    }

    public SurrealDBConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
