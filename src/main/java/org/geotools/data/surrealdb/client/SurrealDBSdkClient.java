package org.geotools.data.surrealdb.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.surrealdb.Response;
import com.surrealdb.Surreal;
import com.surrealdb.signin.Database;
import com.surrealdb.signin.Root;
import com.surrealdb.signin.Token;
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
 * Adapter implementing {@link SurrealDBClient} using the SurrealDB Java SDK
 * for connection lifecycle and authentication, and HTTP REST API for JSON queries.
 *
 * <p>The SDK's {@code Value.toString()} returns SurrealQL format which loses
 * GeoJSON geometry structure. The HTTP REST API returns proper JSON, so
 * {@link #queryAsJson} and {@link #queryBindAsJson} use HTTP instead.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Delegates token state management to {@link AuthManager}</li>
 *   <li>Automatic retry on auth-related failures (once)</li>
 *   <li>Protected {@link #createDriver()} factory method for test injection</li>
 *   <li>HTTP REST API for JSON queries (preserves GeoJSON geometry)</li>
 * </ul>
 */
public class SurrealDBSdkClient implements SurrealDBClient {

    private static final Logger LOG = LoggerFactory.getLogger(SurrealDBSdkClient.class);
    private static final Gson GSON = new Gson();

    private Surreal driver;
    private ConnectionConfig config;
    private final AuthManager authManager;
    private volatile boolean connected;
    private HttpClient httpClient;

    public SurrealDBSdkClient() {
        this(new AuthManager());
    }

    public SurrealDBSdkClient(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public void connect(ConnectionConfig config) {
        this.config = config;
        String url = config.buildConnectionUrl();
        LOG.info("Connecting to SurrealDB at {}", url);

        try {
            this.driver = createDriver();
            driver.connect(url);
            driver.useNs(config.getNamespace()).useDb(config.getDatabase());
            performSignin();
            this.httpClient = createHttpClient();
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
        ensureAuthenticated();

        try {
            LOG.debug("Executing query: {}", surrealQL);
            return driver.query(surrealQL);
        } catch (Exception e) {
            if (isAuthError(e)) {
                return retryAfterReauth(surrealQL, null);
            }
            throw new SurrealDBQueryException(
                    "Query execution failed: " + e.getMessage(), surrealQL, e);
        }
    }

    @Override
    public Response queryBind(String surrealQL, Map<String, Object> params) {
        ensureConnected();
        ensureAuthenticated();

        try {
            LOG.debug("Executing parameterized query: {}", surrealQL);
            return driver.queryBind(surrealQL, params);
        } catch (Exception e) {
            if (isAuthError(e)) {
                return retryAfterReauth(surrealQL, params);
            }
            throw new SurrealDBQueryException(
                    "Parameterized query execution failed: " + e.getMessage(), surrealQL, e);
        }
    }

    @Override
    public String queryBindAsJson(String surrealQL, Map<String, Object> params) {
        ensureConnected();
        ensureAuthenticated();

        try {
            LOG.debug("Executing parameterized query (HTTP JSON): {}", surrealQL);
            return executeHttpQuery(buildQueryWithParams(surrealQL, params));
        } catch (Exception e) {
            if (isAuthError(e)) {
                return retryBindAsJsonAfterReauth(surrealQL, params);
            }
            throw new SurrealDBQueryException(
                    "Parameterized JSON query execution failed: " + e.getMessage(), surrealQL, e);
        }
    }

    @Override
    public String queryAsJson(String surrealQL) {
        ensureConnected();
        ensureAuthenticated();

        try {
            LOG.debug("Executing query (HTTP JSON): {}", surrealQL);
            return executeHttpQuery(surrealQL);
        } catch (Exception e) {
            if (isAuthError(e)) {
                return retryAsJsonAfterReauth(surrealQL);
            }
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
        if (!connected || driver == null) {
            return false;
        }
        try {
            driver.query("RETURN true");
            return true;
        } catch (Exception e) {
            LOG.warn("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        LOG.info("Closing SurrealDB connection");
        authManager.invalidate();
        this.connected = false;
        if (driver != null) {
            try {
                driver.close();
            } catch (Exception e) {
                LOG.warn("Error closing SurrealDB driver: {}", e.getMessage());
            }
            driver = null;
        }
    }

    /**
     * Factory method for creating the SurrealDB driver instance.
     * Protected to allow test subclasses to inject mocks.
     */
    protected Surreal createDriver() {
        return new Surreal();
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
     * Executes a SurrealQL query via the HTTP REST API and returns the
     * result array from the first statement as a JSON string.
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
                throw new RuntimeException("auth error: HTTP " + response.statusCode());
            }

            if (response.statusCode() != 200) {
                throw new SurrealDBQueryException(
                        "HTTP query failed with status " + response.statusCode()
                                + ": " + response.body(), surrealQL, null);
            }

            return extractResultFromHttpResponse(response.body());
        } catch (SurrealDBQueryException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SurrealDBQueryException(
                    "HTTP query execution failed: " + e.getMessage(), surrealQL, e);
        }
    }

    /**
     * Extracts the "result" array from the HTTP API response.
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

    private void performSignin() {
        LOG.debug("Signing in to SurrealDB ns={} db={} user={}",
                config.getNamespace(), config.getDatabase(), config.getUsername());

        // Try Root signin first (for server root users), then Database signin (for DB-scoped users)
        try {
            Token token = driver.signin(new Root(config.getUsername(), config.getPassword()));
            authManager.updateToken(token);
            LOG.info("Successfully authenticated to SurrealDB as root user");
            return;
        } catch (Exception rootEx) {
            LOG.debug("Root signin failed, trying database-scoped signin: {}", rootEx.getMessage());
        }

        try {
            Token token = driver.signin(new Database(
                    config.getUsername(), config.getPassword(),
                    config.getNamespace(), config.getDatabase()));
            authManager.updateToken(token);
            LOG.info("Successfully authenticated to SurrealDB as database user");
        } catch (Exception dbEx) {
            throw new SurrealDBConnectionException(
                    "Failed to authenticate to SurrealDB: " + dbEx.getMessage(), dbEx);
        }
    }

    private void ensureAuthenticated() {
        if (authManager.needsRefresh()) {
            LOG.debug("Token is stale, re-authenticating");
            performSignin();
        }
    }

    private void ensureConnected() {
        if (!connected || driver == null) {
            throw new SurrealDBConnectionException("Not connected to SurrealDB. Call connect() first.");
        }
    }

    private boolean isAuthError(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("auth") || lower.contains("token") || lower.contains("401");
    }

    private String retryAsJsonAfterReauth(String surrealQL) {
        LOG.warn("Auth error on JSON query, retrying after re-authentication");
        try {
            authManager.invalidate();
            performSignin();
            return executeHttpQuery(surrealQL);
        } catch (Exception retryEx) {
            throw new SurrealDBQueryException(
                    "JSON query failed after re-authentication: " + retryEx.getMessage(), surrealQL, retryEx);
        }
    }

    private String retryBindAsJsonAfterReauth(String surrealQL, Map<String, Object> params) {
        LOG.warn("Auth error on parameterized JSON query, retrying after re-authentication");
        try {
            authManager.invalidate();
            performSignin();
            return executeHttpQuery(buildQueryWithParams(surrealQL, params));
        } catch (Exception retryEx) {
            throw new SurrealDBQueryException(
                    "Parameterized JSON query failed after re-authentication: " + retryEx.getMessage(),
                    surrealQL, retryEx);
        }
    }

    private Response retryAfterReauth(String surrealQL, Map<String, Object> params) {
        LOG.warn("Auth error detected, retrying after re-authentication");
        try {
            authManager.invalidate();
            performSignin();
            if (params != null) {
                return driver.queryBind(surrealQL, params);
            } else {
                return driver.query(surrealQL);
            }
        } catch (Exception retryEx) {
            throw new SurrealDBQueryException(
                    "Query failed after re-authentication: " + retryEx.getMessage(), surrealQL, retryEx);
        }
    }

    // Visible for testing
    AuthManager getAuthManager() {
        return authManager;
    }
}
