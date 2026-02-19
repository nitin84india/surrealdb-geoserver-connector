package org.geotools.data.surrealdb;

import org.geotools.data.surrealdb.client.ConnectionConfig;
import org.geotools.data.surrealdb.client.SurrealDBSdkClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for integration tests against a live SurrealDB instance.
 * Uses the Testcontainers singleton container pattern: the container starts once
 * when this class is loaded and is shared by all subclasses for the entire test run.
 *
 * <p>Provides helper methods for building configs, creating clients,
 * and executing raw SQL against the running container.</p>
 *
 * <p>Test data mirrors {@code docker/init-surreal.sh} plus edge-case tables
 * ({@code empty_geo} and {@code config}).</p>
 */
public abstract class SurrealDBContainerIT {

    protected static final String ROOT_USER = "root";
    protected static final String ROOT_PASS = "root";
    protected static final String NAMESPACE = "geoserver";
    protected static final String DATABASE = "spatial";

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> SURREAL_DB =
            new GenericContainer<>("surrealdb/surrealdb:v2.2.1")
                    .withExposedPorts(8000)
                    .withCommand("start", "--user", ROOT_USER, "--pass", ROOT_PASS,
                            "--bind", "0.0.0.0:8000", "memory")
                    .waitingFor(new HttpWaitStrategy()
                            .forPort(8000)
                            .forPath("/health")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(30)));

    static {
        SURREAL_DB.start();
        try {
            executeSql(buildInitSql());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SurrealDB schema", e);
        }
    }

    /**
     * Builds the GeoServer DataStore parameter map for the running container.
     */
    protected static Map<String, Object> buildDataStoreParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("dbtype", "surrealdb");
        params.put("host", SURREAL_DB.getHost());
        params.put("port", SURREAL_DB.getMappedPort(8000));
        params.put("surreal_ns", NAMESPACE);
        params.put("database", DATABASE);
        params.put("user", ROOT_USER);
        params.put("password", ROOT_PASS);
        params.put("protocol", "http");
        params.put("timeout", 30000);
        return params;
    }

    /**
     * Builds a {@link ConnectionConfig} for the running container.
     */
    protected static ConnectionConfig buildConfig() {
        return ConnectionConfig.builder()
                .host(SURREAL_DB.getHost())
                .port(SURREAL_DB.getMappedPort(8000))
                .namespace(NAMESPACE)
                .database(DATABASE)
                .username(ROOT_USER)
                .password(ROOT_PASS)
                .protocol("http")
                .timeoutMs(30000)
                .build();
    }

    /**
     * Creates and connects a new {@link SurrealDBSdkClient} to the running container.
     */
    protected static SurrealDBSdkClient createConnectedClient() {
        SurrealDBSdkClient client = new SurrealDBSdkClient();
        client.connect(buildConfig());
        return client;
    }

    /**
     * Executes raw SQL against the SurrealDB HTTP API.
     *
     * @param sql the SurrealQL to execute
     * @return the raw JSON response body
     */
    protected static String executeSql(String sql) throws Exception {
        String url = "http://" + SURREAL_DB.getHost() + ":" + SURREAL_DB.getMappedPort(8000) + "/sql";
        String basicAuth = Base64.getEncoder().encodeToString(
                (ROOT_USER + ":" + ROOT_PASS).getBytes(StandardCharsets.UTF_8));

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("Authorization", "Basic " + basicAuth)
                .header("surreal-ns", NAMESPACE)
                .header("surreal-db", DATABASE)
                .POST(HttpRequest.BodyPublishers.ofString(sql, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new RuntimeException("SQL execution failed (HTTP " + response.statusCode()
                    + "): " + response.body());
        }

        return response.body();
    }

    /**
     * Returns the HTTP base URL for the running SurrealDB container.
     */
    protected static String getSurrealUrl() {
        return "http://" + SURREAL_DB.getHost() + ":" + SURREAL_DB.getMappedPort(8000);
    }

    /**
     * Builds the initialization SQL that mirrors docker/init-surreal.sh
     * plus edge-case tables (empty_geo, config).
     */
    private static String buildInitSql() {
        return String.join("\n",
                // POI table
                "DEFINE TABLE poi SCHEMAFULL;",
                "DEFINE FIELD name ON poi TYPE string;",
                "DEFINE FIELD geometry ON poi TYPE geometry<point>;",
                "DEFINE FIELD category ON poi TYPE option<string>;",
                "DEFINE FIELD rating ON poi TYPE option<float>;",
                "",
                "CREATE poi SET name = 'Central Park', geometry = {\"type\":\"Point\",\"coordinates\":[-73.9654,40.7829]}, category = 'park', rating = 4.8;",
                "CREATE poi SET name = 'Times Square', geometry = {\"type\":\"Point\",\"coordinates\":[-73.9855,40.7580]}, category = 'landmark', rating = 4.2;",
                "CREATE poi SET name = 'Brooklyn Bridge', geometry = {\"type\":\"Point\",\"coordinates\":[-73.9969,40.7061]}, category = 'bridge', rating = 4.6;",
                "CREATE poi SET name = 'Statue of Liberty', geometry = {\"type\":\"Point\",\"coordinates\":[-74.0445,40.6892]}, category = 'landmark', rating = 4.9;",
                "CREATE poi SET name = 'Los Angeles Convention Center', geometry = {\"type\":\"Point\",\"coordinates\":[-118.2695,34.0407]}, category = 'venue', rating = 3.5;",
                "CREATE poi SET name = 'Golden Gate Bridge', geometry = {\"type\":\"Point\",\"coordinates\":[-122.4783,37.8199]}, category = 'bridge', rating = 4.7;",
                "CREATE poi SET name = 'Unnamed Spot', geometry = {\"type\":\"Point\",\"coordinates\":[-73.9500,40.7500]}, category = NONE, rating = NONE;",
                "",
                // Park table
                "DEFINE TABLE park SCHEMAFULL;",
                "DEFINE FIELD name ON park TYPE string;",
                "DEFINE FIELD geometry ON park TYPE geometry<polygon>;",
                "DEFINE FIELD area_sqm ON park TYPE float;",
                "",
                "CREATE park SET name = 'Central Park', geometry = {\"type\":\"Polygon\",\"coordinates\":[[[-73.981,40.768],[-73.958,40.768],[-73.958,40.800],[-73.981,40.800],[-73.981,40.768]]]}, area_sqm = 3410000.0;",
                "CREATE park SET name = 'Bryant Park', geometry = {\"type\":\"Polygon\",\"coordinates\":[[[-73.9847,40.7536],[-73.9822,40.7536],[-73.9822,40.7554],[-73.9847,40.7554],[-73.9847,40.7536]]]}, area_sqm = 39000.0;",
                "",
                // Event table (SCHEMALESS - should be excluded)
                "DEFINE TABLE event SCHEMALESS;",
                "CREATE event SET name = 'Concert in the Park', date = '2024-07-04', location = {\"type\":\"Point\",\"coordinates\":[-73.9654,40.7829]};",
                "",
                // Trail table
                "DEFINE TABLE trail SCHEMAFULL;",
                "DEFINE FIELD name ON trail TYPE string;",
                "DEFINE FIELD geometry ON trail TYPE geometry<line>;",
                "DEFINE FIELD difficulty ON trail TYPE string;",
                "",
                "CREATE trail SET name = 'Hudson River Greenway', geometry = {\"type\":\"LineString\",\"coordinates\":[[-74.0060,40.7128],[-74.0100,40.7300],[-74.0080,40.7500]]}, difficulty = 'easy';",
                "",
                // Empty geo table (SCHEMAFULL with geometry but no records)
                "DEFINE TABLE empty_geo SCHEMAFULL;",
                "DEFINE FIELD name ON empty_geo TYPE string;",
                "DEFINE FIELD geometry ON empty_geo TYPE geometry<point>;",
                "",
                // Config table (SCHEMAFULL but NO geometry fields - should be excluded)
                "DEFINE TABLE config SCHEMAFULL;",
                "DEFINE FIELD key ON config TYPE string;",
                "DEFINE FIELD value ON config TYPE string;",
                "",
                "CREATE config SET key = 'version', value = '1.0';"
        );
    }
}
