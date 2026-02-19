package org.geotools.data.surrealdb.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.surrealdb.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Adapter implementing {@link SurrealDBClient} using the SurrealDB HTTP REST API
 * for all operations: connection validation, authentication, and query execution.
 *
 * <p>This client uses HTTP Basic authentication on every request, making it
 * compatible with both SurrealDB v2.x and v3.x without depending on the
 * SDK's {@code /rpc} WebSocket endpoint.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Pure HTTP — no dependency on the SDK's /rpc WebSocket protocol</li>
 *   <li>Compatible with SurrealDB v2.x and v3.x</li>
 *   <li>HTTP REST API preserves GeoJSON geometry in responses</li>
 *   <li>Basic auth on every request — no JWT token management needed</li>
 * </ul>
 */
public class SurrealDBSdkClient implements SurrealDBClient {

    private static final Logger LOG = LoggerFactory.getLogger(SurrealDBSdkClient.class);
    private static final Gson GSON = new Gson();

    private ConnectionConfig config;
    private volatile boolean connected;
    private HttpClient httpClient;

    public SurrealDBSdkClient() {
    }

    /**
     * @deprecated AuthManager is no longer used; kept for backward compatibility.
     */
    @Deprecated
    public SurrealDBSdkClient(AuthManager authManager) {
        // AuthManager is not needed with HTTP Basic auth
    }

    @Override
    public void connect(ConnectionConfig config) {
        this.config = config;
        String url = config.buildConnectionUrl();
        LOG.info("Connecting to SurrealDB at {}", url);

        try {
            this.httpClient = createHttpClient();
            validateConnection();
            this.connected = true;
            LOG.info("Successfully connected to SurrealDB at {}", url);
        } catch (SurrealDBConnectionException e) {
            this.connected = false;
            throw e;
        } catch (Exception e) {
            this.connected = false;
            throw new SurrealDBConnectionException(
                    "Failed to connect to SurrealDB at " + url + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public Response query(String surrealQL) {
        ensureConnected();
        throw new UnsupportedOperationException(
                "SDK Response-based query() is not supported in HTTP mode. Use queryAsJson() instead.");
    }

    @Override
    public Response queryBind(String surrealQL, Map<String, Object> params) {
        ensureConnected();
        throw new UnsupportedOperationException(
                "SDK Response-based queryBind() is not supported in HTTP mode. Use queryBindAsJson() instead.");
    }

    @Override
    public String queryBindAsJson(String surrealQL, Map<String, Object> params) {
        ensureConnected();

        try {
            LOG.debug("Executing parameterized query (HTTP JSON): {}", surrealQL);
            return executeHttpQuery(buildQueryWithParams(surrealQL, params));
        } catch (Exception e) {
            throw new SurrealDBQueryException(
                    "Parameterized JSON query execution failed: " + e.getMessage(), surrealQL, e);
        }
    }

    @Override
    public String queryAsJson(String surrealQL) {
        ensureConnected();

        try {
            LOG.debug("Executing query (HTTP JSON): {}", surrealQL);
            return executeHttpQuery(surrealQL);
        } catch (Exception e) {
            throw new SurrealDBQueryException(
                    "JSON query execution failed: " + e.getMessage(), surrealQL, e);
        }
    }

    @Override
    public ConnectionConfig getConfig() {
        return config;
    }

    @Override
    public boolean isHealthy() {
        if (!connected || httpClient == null) {
            return false;
        }
        try {
            String healthUrl = config.buildConnectionUrl() + "/health";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200;
        } catch (Exception e) {
            LOG.warn("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        LOG.info("Closing SurrealDB connection");
        this.connected = false;
        this.httpClient = null;
    }

    /**
     * Factory method for creating the HTTP client.
     * Protected for test override.
     */
    protected HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .build();
    }

    /**
     * Validates the connection by executing a test query via HTTP.
     * This verifies both network connectivity and credential validity.
     */
    private void validateConnection() {
        String httpUrl = config.buildConnectionUrl() + "/sql";
        String basicAuth = Base64.getEncoder().encodeToString(
                (config.getUsername() + ":" + config.getPassword())
                        .getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(httpUrl))
                .header("Accept", "application/json")
                .header("Authorization", "Basic " + basicAuth)
                .header("surreal-ns", config.getNamespace())
                .header("surreal-db", config.getDatabase())
                .POST(HttpRequest.BodyPublishers.ofString("RETURN true", StandardCharsets.UTF_8))
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new SurrealDBConnectionException(
                        "Authentication failed (HTTP " + response.statusCode() + ")");
            }

            if (response.statusCode() != 200) {
                throw new SurrealDBConnectionException(
                        "Connection validation failed (HTTP " + response.statusCode()
                                + "): " + response.body());
            }

            LOG.info("Successfully authenticated to SurrealDB via HTTP");
        } catch (SurrealDBConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new SurrealDBConnectionException(
                    "Failed to validate SurrealDB connection: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a SurrealQL query via the HTTP REST API and returns the
     * result array from the last statement as a JSON string.
     *
     * <p>The HTTP API returns proper JSON with GeoJSON geometry intact,
     * unlike Value.toString() which returns SurrealQL tuple format.</p>
     */
    private String executeHttpQuery(String surrealQL) {
        String httpUrl = config.buildConnectionUrl() + "/sql";
        String basicAuth = Base64.getEncoder().encodeToString(
                (config.getUsername() + ":" + config.getPassword())
                        .getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(httpUrl))
                .header("Accept", "application/json")
                .header("Authorization", "Basic " + basicAuth)
                .header("surreal-ns", config.getNamespace())
                .header("surreal-db", config.getDatabase())
                .POST(HttpRequest.BodyPublishers.ofString(surrealQL, StandardCharsets.UTF_8))
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new SurrealDBConnectionException(
                        "Authentication failed (HTTP " + response.statusCode() + ")");
            }

            if (response.statusCode() != 200) {
                throw new SurrealDBQueryException(
                        "HTTP query failed with status " + response.statusCode()
                                + ": " + response.body(), surrealQL, null);
            }

            return extractResultFromHttpResponse(response.body());
        } catch (SurrealDBConnectionException | SurrealDBQueryException e) {
            throw e;
        } catch (Exception e) {
            throw new SurrealDBQueryException(
                    "HTTP query execution failed: " + e.getMessage(), surrealQL, e);
        }
    }

    /**
     * Extracts the "result" from the HTTP API response.
     * The response format is: [{result: [...], status: "OK", time: "..."}]
     * When LET statements prepend the query, the response contains multiple entries;
     * the actual data is in the last statement's result.
     */
    private String extractResultFromHttpResponse(String responseBody) {
        JsonArray statements = JsonParser.parseString(responseBody).getAsJsonArray();
        if (statements.isEmpty()) {
            return "[]";
        }

        // Use the last statement — LET statements precede the SELECT and return null
        JsonObject lastStatement = statements.get(statements.size() - 1).getAsJsonObject();
        String status = lastStatement.has("status")
                ? lastStatement.get("status").getAsString() : "UNKNOWN";

        if (!"OK".equals(status)) {
            String errorDetail = lastStatement.has("result")
                    ? lastStatement.get("result").getAsString() : "Unknown error";
            throw new SurrealDBQueryException(
                    "SurrealDB query error: " + errorDetail, "", null);
        }

        JsonElement result = lastStatement.get("result");
        if (result == null || result.isJsonNull()) {
            return "[]";
        }

        return GSON.toJson(result);
    }

    /**
     * Builds a SurrealQL query string with LET statements for bind parameters.
     * This allows using the HTTP /sql endpoint which doesn't natively support bind params.
     */
    private String buildQueryWithParams(String surrealQL, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return surrealQL;
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            sb.append("LET $").append(entry.getKey())
                    .append(" = ").append(toSurrealQLLiteral(entry.getValue()))
                    .append(";\n");
        }
        sb.append(surrealQL);
        return sb.toString();
    }

    /**
     * Converts a Java object to a SurrealQL literal value.
     */
    @SuppressWarnings("unchecked")
    private String toSurrealQLLiteral(Object value) {
        if (value == null) {
            return "NONE";
        }
        if (value instanceof String) {
            return "'" + ((String) value).replace("'", "\\'") + "'";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map) {
            // GeoJSON geometry objects are Maps
            return GSON.toJson(value);
        }
        return "'" + value.toString().replace("'", "\\'") + "'";
    }

    private void ensureConnected() {
        if (!connected || httpClient == null) {
            throw new SurrealDBConnectionException("Not connected to SurrealDB. Call connect() first.");
        }
    }

    // Visible for testing (deprecated — AuthManager no longer used)
    AuthManager getAuthManager() {
        return new AuthManager();
    }
}
