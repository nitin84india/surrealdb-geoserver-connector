package org.geotools.data.surrealdb.client;

/**
 * Thrown when a SurrealQL query fails to execute.
 */
public class SurrealDBQueryException extends RuntimeException {

    private final String surrealQL;

    public SurrealDBQueryException(String message, String surrealQL) {
        super(message);
        this.surrealQL = surrealQL;
    }

    public SurrealDBQueryException(String message, String surrealQL, Throwable cause) {
        super(message, cause);
        this.surrealQL = surrealQL;
    }

    public String getSurrealQL() {
        return surrealQL;
    }
}
