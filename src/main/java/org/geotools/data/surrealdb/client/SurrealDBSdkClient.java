package org.geotools.data.surrealdb.client;

import com.surrealdb.Response;
import com.surrealdb.Surreal;
import com.surrealdb.signin.Database;
import com.surrealdb.signin.Root;
import com.surrealdb.signin.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Adapter implementing {@link SurrealDBClient} using the SurrealDB Java SDK.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Delegates token state management to {@link AuthManager}</li>
 *   <li>Automatic retry on auth-related failures (once)</li>
 *   <li>Protected {@link #createDriver()} factory method for test injection</li>
 * </ul>
 */
public class SurrealDBSdkClient implements SurrealDBClient {

    private static final Logger LOG = LoggerFactory.getLogger(SurrealDBSdkClient.class);

    private Surreal driver;
    private ConnectionConfig config;
    private final AuthManager authManager;
    private volatile boolean connected;

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
    public String queryAsJson(String surrealQL) {
        ensureConnected();
        ensureAuthenticated();

        try {
            LOG.debug("Executing query (JSON): {}", surrealQL);
            Response response = driver.query(surrealQL);
            com.surrealdb.Value value = response.take(0);
            return value.toString();
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
            Response response = driver.query(surrealQL);
            com.surrealdb.Value value = response.take(0);
            return value.toString();
        } catch (Exception retryEx) {
            throw new SurrealDBQueryException(
                    "JSON query failed after re-authentication: " + retryEx.getMessage(), surrealQL, retryEx);
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
