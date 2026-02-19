package org.geotools.data.surrealdb.client;

import java.util.Objects;

/**
 * Immutable value object holding SurrealDB connection parameters.
 * Uses the Builder pattern for construction with validation.
 */
public final class ConnectionConfig {

    private final String host;
    private final int port;
    private final String namespace;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useTls;
    private final String protocol;
    private final int poolSize;
    private final int timeoutMs;
    private final int srid;

    private ConnectionConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.namespace = builder.namespace;
        this.database = builder.database;
        this.username = builder.username;
        this.password = builder.password;
        this.useTls = builder.useTls;
        this.protocol = builder.protocol;
        this.poolSize = builder.poolSize;
        this.timeoutMs = builder.timeoutMs;
        this.srid = builder.srid;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isUseTls() {
        return useTls;
    }

    public String getProtocol() {
        return protocol;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public int getSrid() {
        return srid;
    }

    /**
     * Builds the connection URL based on protocol and TLS settings.
     * <ul>
     *   <li>http + no TLS  -> http://host:port</li>
     *   <li>http + TLS     -> https://host:port</li>
     *   <li>ws + no TLS    -> ws://host:port</li>
     *   <li>ws + TLS       -> wss://host:port</li>
     * </ul>
     */
    public String buildConnectionUrl() {
        String scheme;
        if ("ws".equals(protocol)) {
            scheme = useTls ? "wss" : "ws";
        } else {
            scheme = useTls ? "https" : "http";
        }
        return scheme + "://" + host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionConfig that = (ConnectionConfig) o;
        return port == that.port
                && useTls == that.useTls
                && poolSize == that.poolSize
                && timeoutMs == that.timeoutMs
                && srid == that.srid
                && Objects.equals(host, that.host)
                && Objects.equals(namespace, that.namespace)
                && Objects.equals(database, that.database)
                && Objects.equals(username, that.username)
                && Objects.equals(password, that.password)
                && Objects.equals(protocol, that.protocol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, namespace, database, username, password,
                useTls, protocol, poolSize, timeoutMs, srid);
    }

    @Override
    public String toString() {
        return "ConnectionConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", namespace='" + namespace + '\'' +
                ", database='" + database + '\'' +
                ", username='" + username + '\'' +
                ", password='****'" +
                ", useTls=" + useTls +
                ", protocol='" + protocol + '\'' +
                ", poolSize=" + poolSize +
                ", timeoutMs=" + timeoutMs +
                ", srid=" + srid +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String host;
        private int port = 8000;
        private String namespace;
        private String database;
        private String username;
        private String password;
        private boolean useTls = false;
        private String protocol = "http";
        private int poolSize = 5;
        private int timeoutMs = 30000;
        private int srid = 4326;

        private Builder() {
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder useTls(boolean useTls) {
            this.useTls = useTls;
            return this;
        }

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder poolSize(int poolSize) {
            this.poolSize = poolSize;
            return this;
        }

        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder srid(int srid) {
            this.srid = srid;
            return this;
        }

        public ConnectionConfig build() {
            Objects.requireNonNull(host, "host is required");
            Objects.requireNonNull(namespace, "namespace is required");
            Objects.requireNonNull(database, "database is required");
            Objects.requireNonNull(username, "username is required");
            Objects.requireNonNull(password, "password is required");

            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port must be between 1 and 65535, got: " + port);
            }
            if (!"http".equals(protocol) && !"ws".equals(protocol)) {
                throw new IllegalArgumentException("protocol must be 'http' or 'ws', got: " + protocol);
            }

            return new ConnectionConfig(this);
        }
    }
}
