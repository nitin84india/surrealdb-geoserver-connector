# GeoServer-SurrealDB Connector: Architecture & Implementation Plan

## 1. Executive Summary

This document outlines the architecture for a custom GeoServer DataStore plugin that connects to SurrealDB 3.0, enabling geometry data stored in SurrealDB to be served as OGC-compliant WMS/WFS layers. The connector translates GeoServer's OGC filter queries into SurrealQL, converts SurrealDB's GeoJSON geometry responses into JTS Geometry objects, and exposes SurrealDB tables as GeoServer feature types.

**Target Compatibility:**
- GeoServer 2.28.x (stable) / GeoTools 34.x / Java 17+
- SurrealDB 3.0 (Java SDK `3.0.0-beta.1`)
- OGC WMS 1.3.0, WFS 2.0

---

## 2. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          GeoServer                                   │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐                    │
│  │  WMS/WFS   │  │   REST     │  │  Web Admin  │                   │
│  │  Services   │  │   API      │  │    UI       │                   │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘                    │
│        │               │               │                             │
│        └───────────────┼───────────────┘                             │
│                        │                                              │
│  ┌─────────────────────▼──────────────────────┐                      │
│  │         GeoTools DataStore API              │                      │
│  │  (ContentDataStore / ContentFeatureSource)  │                      │
│  └─────────────────────┬──────────────────────┘                      │
└────────────────────────┼─────────────────────────────────────────────┘
                         │
┌────────────────────────┼─────────────────────────────────────────────┐
│                        │   gs-surrealdb-connector                    │
│                        ▼                                              │
│  ┌─────────────────────────────────────────────┐                     │
│  │     SurrealDBDataStoreFactory (SPI)         │ ◄── Entry point     │
│  │     - Connection params (host, port, ns,    │                     │
│  │       db, auth credentials)                 │                     │
│  │     - Creates SurrealDBDataStore            │                     │
│  └─────────────────────┬───────────────────────┘                     │
│                        │                                              │
│  ┌─────────────────────▼───────────────────────┐                     │
│  │     SurrealDBDataStore                       │                     │
│  │     - Manages connection lifecycle           │                     │
│  │     - Discovers tables with geometry fields  │                     │
│  │     - Creates FeatureSource per table        │                     │
│  └─────────────────────┬───────────────────────┘                     │
│                        │                                              │
│  ┌─────────────────────▼───────────────────────┐                     │
│  │     SurrealDBFeatureSource                   │                     │
│  │     - Maps SurrealDB table → SimpleFeature   │                     │
│  │     - Translates OGC Filters → SurrealQL     │                     │
│  │     - Returns FeatureReader / FeatureIterator │                     │
│  └─────────────────────┬───────────────────────┘                     │
│                        │                                              │
│  ┌─────────────────────▼───────────────────────┐                     │
│  │     SurrealDBFilterTranslator                │                     │
│  │     - BBOX → SurrealQL INTERSECTS            │                     │
│  │     - Contains → CONTAINS operator           │                     │
│  │     - Property filters → WHERE clauses       │                     │
│  └─────────────────────┬───────────────────────┘                     │
│                        │                                              │
│  ┌─────────────────────▼───────────────────────┐                     │
│  │     SurrealDBClient (Connection Layer)       │                     │
│  │     - HTTP/WebSocket connection pooling      │                     │
│  │     - JWT token management & refresh         │                     │
│  │     - Query execution & result parsing       │                     │
│  │     - GeoJSON → JTS Geometry conversion      │                     │
│  └─────────────────────┬───────────────────────┘                     │
│                        │                                              │
└────────────────────────┼─────────────────────────────────────────────┘
                         │  HTTP/WebSocket (TLS)
                         ▼
              ┌─────────────────────┐
              │    SurrealDB 3.0    │
              │  ┌───────────────┐  │
              │  │  Namespace    │  │
              │  │  └─Database   │  │
              │  │    └─Tables   │  │
              │  │      (with    │  │
              │  │   geometry    │  │
              │  │    fields)    │  │
              │  └───────────────┘  │
              └─────────────────────┘
```

---

## 3. Component Design (Clean Architecture)

### 3.1 Package Structure

```
org.geotools.data.surrealdb/
├── SurrealDBDataStoreFactory.java        # SPI entry point
├── SurrealDBDataStore.java               # ContentDataStore impl
├── SurrealDBFeatureSource.java           # ContentFeatureSource impl
├── SurrealDBFeatureReader.java           # FeatureReader impl
│
├── client/                               # Connection layer
│   ├── SurrealDBClient.java              # Main client interface
│   ├── SurrealDBHttpClient.java          # HTTP-based implementation
│   ├── SurrealDBWebSocketClient.java     # WebSocket-based impl (optional)
│   ├── ConnectionConfig.java             # Connection parameters VO
│   ├── ConnectionPool.java              # Connection pooling
│   └── AuthManager.java                 # JWT token lifecycle
│
├── filter/                               # Query translation
│   ├── SurrealDBFilterTranslator.java   # OGC Filter → SurrealQL
│   ├── SpatialFilterHandler.java        # Spatial filter strategies
│   └── PropertyFilterHandler.java       # Attribute filter strategies
│
├── schema/                               # Schema discovery
│   ├── SchemaDiscovery.java             # Table/field introspection
│   ├── GeometryFieldDetector.java       # Identifies geometry columns
│   └── FeatureTypeMapper.java           # SurrealDB → SimpleFeatureType
│
├── geometry/                             # Geometry conversion
│   ├── GeoJsonToJtsConverter.java       # GeoJSON → JTS Geometry
│   ├── JtsToGeoJsonConverter.java       # JTS → GeoJSON (for writes)
│   └── CoordinateTransformer.java       # CRS handling
│
└── config/                               # Configuration
    ├── SurrealDBDataStoreParams.java    # Parameter definitions
    └── SecurityConfig.java              # TLS, credential encryption
```

### 3.2 Core Classes Detail

#### SurrealDBDataStoreFactory (SPI Registration)

The factory is discovered by GeoServer via `META-INF/services/org.geotools.api.data.DataStoreFactorySpi`. It defines connection parameters and creates the DataStore.

**Connection Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `surrealdb_host` | String | Yes | `localhost` | SurrealDB server host |
| `surrealdb_port` | Integer | Yes | `8000` | SurrealDB server port |
| `surrealdb_namespace` | String | Yes | - | SurrealDB namespace |
| `surrealdb_database` | String | Yes | - | SurrealDB database name |
| `surrealdb_user` | String | Yes | - | Authentication username |
| `surrealdb_password` | String | Yes | - | Authentication password (encrypted) |
| `surrealdb_use_tls` | Boolean | No | `true` | Enable TLS encryption |
| `surrealdb_protocol` | String | No | `http` | Protocol: `http` or `ws` |
| `surrealdb_pool_size` | Integer | No | `10` | Connection pool size |
| `surrealdb_timeout` | Integer | No | `30000` | Query timeout (ms) |
| `surrealdb_geometry_field` | String | No | auto-detect | Override geometry field name |
| `surrealdb_srid` | Integer | No | `4326` | Default SRID for geometries |

#### SurrealDBDataStore

Extends `ContentDataStore`. Responsible for:
1. Managing the `SurrealDBClient` lifecycle (connect/disconnect)
2. Discovering tables with geometry fields via `INFO FOR DB` + `INFO FOR TABLE`
3. Creating `SurrealDBFeatureSource` per geometry-enabled table
4. Caching schema metadata for performance

#### SurrealDBFeatureSource

Extends `ContentFeatureSource`. Responsible for:
1. Building `SimpleFeatureType` from SurrealDB table schema
2. Handling `getReaderInternal(Query)` by translating filters and executing SurrealQL
3. Implementing `getBoundsInternal(Query)` for spatial extent calculation
4. Implementing `getCountInternal(Query)` for record counts

#### SurrealDBFilterTranslator

Translates OGC Filter objects into SurrealQL WHERE clauses:

| OGC Filter | SurrealQL Translation |
|------------|----------------------|
| `BBOX(geom, minx, miny, maxx, maxy)` | `WHERE geom INTERSECTS { type: "Polygon", coordinates: [[...bbox...]] }` |
| `Contains(geom, point)` | `WHERE geom CONTAINS (lon, lat)` |
| `Intersects(geom, other)` | `WHERE geom INTERSECTS { type: "...", coordinates: [...] }` |
| `Within(geom, polygon)` | `WHERE geom INSIDE { type: "Polygon", coordinates: [...] }` |
| `PropertyIsEqualTo(name, val)` | `WHERE name = val` |
| `PropertyIsLike(name, pattern)` | `WHERE name ~ /pattern/` |
| `And(f1, f2)` | `WHERE (f1_translation) AND (f2_translation)` |
| `Or(f1, f2)` | `WHERE (f1_translation) OR (f2_translation)` |

---

## 4. Data Flow

### 4.1 WMS GetMap Request Flow

```
Client (Browser/QGIS)
    │
    │  WMS GetMap (BBOX, SRS, layers, styles)
    ▼
GeoServer WMS Service
    │
    │  Resolves layer → DataStore → FeatureSource
    ▼
SurrealDBFeatureSource.getReaderInternal(query)
    │
    │  1. Extract BBOX filter from query
    │  2. Translate to SurrealQL via FilterTranslator
    │  3. Build query: SELECT * FROM <table> WHERE <geometry_field>
    │     INTERSECTS { type: "Polygon", coordinates: [[bbox]] }
    ▼
SurrealDBClient.executeQuery(surrealQL)
    │
    │  HTTP POST to /sql endpoint
    │  Headers: Authorization: Bearer <jwt_token>
    │           NS: <namespace>
    │           DB: <database>
    │  Body: SurrealQL query string
    ▼
SurrealDB 3.0
    │
    │  Returns JSON array of records with GeoJSON geometry
    ▼
GeoJsonToJtsConverter
    │
    │  Parse each record:
    │  - Convert GeoJSON geometry → JTS Geometry (Point, Polygon, etc.)
    │  - Map attributes → SimpleFeature attributes
    │  - Set CRS (EPSG:4326 default)
    ▼
SurrealDBFeatureReader
    │
    │  Wraps results as FeatureReader<SimpleFeatureType, SimpleFeature>
    │  Returns features one-by-one via next()
    ▼
GeoServer Renderer
    │
    │  Applies SLD styling → renders map tile → returns PNG/JPEG
    ▼
Client receives map image
```

### 4.2 Schema Discovery Flow

```
GeoServer Admin → "Add DataStore" → selects "SurrealDB"
    │
    │  Fills connection params → clicks Save
    ▼
SurrealDBDataStoreFactory.createDataStore(params)
    │
    │  1. Validate params
    │  2. Create SurrealDBClient
    │  3. Authenticate → obtain JWT
    ▼
SurrealDBDataStore.createTypeNames()
    │
    │  Execute: INFO FOR DB
    │  → Returns list of tables in the database
    │
    │  For each table:
    │    Execute: INFO FOR TABLE <table_name>
    │    → Returns field definitions
    │    → Identify fields with geometry types
    │    → Only include tables with at least one geometry field
    ▼
GeoServer Admin → "Add Layer" → sees list of geometry-enabled tables
    │
    │  Selects table → auto-detects bounds, CRS, attributes
    ▼
Published as WMS/WFS layer
```

---

## 5. Type Mapping

### 5.1 SurrealDB → Java/GeoTools Type Map

| SurrealDB Type | Java Type | GeoTools Binding |
|----------------|-----------|-----------------|
| `string` | `String` | `String.class` |
| `int` | `Long` | `Long.class` |
| `float` | `Double` | `Double.class` |
| `bool` | `Boolean` | `Boolean.class` |
| `datetime` | `Date` | `Date.class` |
| `duration` | `String` | `String.class` |
| `geometry<point>` | `Point` | `Point.class` (JTS) |
| `geometry<linestring>` | `LineString` | `LineString.class` (JTS) |
| `geometry<polygon>` | `Polygon` | `Polygon.class` (JTS) |
| `geometry<multipoint>` | `MultiPoint` | `MultiPoint.class` (JTS) |
| `geometry<multilinestring>` | `MultiLineString` | `MultiLineString.class` (JTS) |
| `geometry<multipolygon>` | `MultiPolygon` | `MultiPolygon.class` (JTS) |
| `geometry<collection>` | `GeometryCollection` | `GeometryCollection.class` (JTS) |
| `object` / `record` | `String` (JSON) | `String.class` |
| `array` | `String` (JSON) | `String.class` |

### 5.2 GeoJSON → JTS Conversion

SurrealDB stores and returns geometry as GeoJSON. The converter parses the JSON structure and builds JTS objects using `GeometryFactory`:

```
GeoJSON { type: "Point", coordinates: [lon, lat] }
    → new Coordinate(lon, lat)
    → geometryFactory.createPoint(coordinate)

GeoJSON { type: "Polygon", coordinates: [[[x1,y1],[x2,y2],...,[x1,y1]]] }
    → Coordinate[] for each ring
    → geometryFactory.createLinearRing(coords)
    → geometryFactory.createPolygon(shell, holes[])
```

---

## 6. Security Architecture

### 6.1 Authentication Flow

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│  GeoServer   │         │  AuthManager  │         │  SurrealDB   │
│  DataStore   │         │              │         │              │
└──────┬──────┘         └──────┬───────┘         └──────┬──────┘
       │  1. Connect            │                        │
       │───────────────────────>│                        │
       │                        │  2. POST /signin       │
       │                        │  {user, pass, ns, db}  │
       │                        │───────────────────────>│
       │                        │                        │
       │                        │  3. JWT token           │
       │                        │<───────────────────────│
       │  4. Token cached       │                        │
       │<───────────────────────│                        │
       │                        │                        │
       │  5. Query with Bearer  │                        │
       │───────────────────────────────────────────────>│
       │                        │                        │
       │          ...time passes, token expires...       │
       │                        │                        │
       │  6. 401 Unauthorized   │                        │
       │<───────────────────────────────────────────────│
       │                        │                        │
       │  7. Re-authenticate    │                        │
       │───────────────────────>│  8. POST /signin       │
       │                        │───────────────────────>│
       │                        │  9. New JWT             │
       │  10. Retry query       │<───────────────────────│
       │───────────────────────────────────────────────>│
```

### 6.2 Security Measures

1. **Credential Storage**: Passwords stored in GeoServer's encrypted data directory, never in plaintext config files.
2. **TLS Enforcement**: Default `surrealdb_use_tls=true`; all connections over HTTPS/WSS.
3. **Token Lifecycle**: JWT tokens cached with proactive refresh before expiry (default 1-hour lifespan).
4. **Connection Pooling**: Pooled connections prevent credential leakage through connection reuse.
5. **Query Parameterization**: All user-supplied values passed as SurrealQL parameters (`$param`) to prevent injection.
6. **Namespace Isolation**: Each DataStore connects to exactly one namespace:database pair, enforcing multi-tenant boundaries.
7. **Read-Only Mode**: The connector operates in read-only mode by default (SELECT queries only). Write support is an optional future extension.

---

## 7. Scalability Design

### 7.1 Connection Pooling

```
SurrealDBClient
    │
    ├── ConnectionPool (configurable size, default 10)
    │   ├── Connection 1 (HTTP session with JWT)
    │   ├── Connection 2
    │   ├── ...
    │   └── Connection N
    │
    ├── Health Check (periodic keep-alive)
    └── Auto-reconnect on failure
```

### 7.2 Caching Strategy

| Cache | TTL | Purpose |
|-------|-----|---------|
| Schema metadata | 5 min (configurable) | Avoid repeated `INFO FOR TABLE` calls |
| Bounds cache | 10 min (configurable) | Cache spatial extent per layer |
| Feature count cache | 5 min | Cache COUNT results |
| JWT token | Until expiry - 60s | Proactive token refresh |

### 7.3 Query Optimization

1. **BBOX Pre-filtering**: Always include BBOX from WMS request as the primary spatial filter, reducing result sets.
2. **Property Selection**: Only SELECT fields that GeoServer needs (based on Query.getPropertyNames), avoiding full-record retrieval.
3. **LIMIT/OFFSET**: Support pagination via SurrealQL `LIMIT` and `START` clauses for large datasets.
4. **Streaming Results**: Use `SurrealDBFeatureReader` to iterate results lazily rather than loading all features into memory.
5. **Concurrent Tile Requests**: Connection pool allows GeoServer's tile rendering to issue parallel queries.

---

## 8. Maven Project Structure

```xml
<!-- pom.xml -->
<project>
    <groupId>org.geotools</groupId>
    <artifactId>gt-surrealdb</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <parent>
        <groupId>org.geotools</groupId>
        <artifactId>gt-plugin</artifactId>
        <version>34.2</version> <!-- Match GeoServer 2.28.x -->
    </parent>

    <dependencies>
        <!-- GeoTools Core -->
        <dependency>
            <groupId>org.geotools</groupId>
            <artifactId>gt-main</artifactId>
        </dependency>
        <dependency>
            <groupId>org.geotools</groupId>
            <artifactId>gt-api</artifactId>
        </dependency>

        <!-- JTS Geometry -->
        <dependency>
            <groupId>org.locationtech.jts</groupId>
            <artifactId>jts-core</artifactId>
        </dependency>

        <!-- SurrealDB Java SDK -->
        <dependency>
            <groupId>com.surrealdb</groupId>
            <artifactId>surrealdb</artifactId>
            <version>3.0.0-beta.1</version>
        </dependency>

        <!-- HTTP Client (fallback if SDK insufficient) -->
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
            <version>4.12.0</version>
        </dependency>

        <!-- JSON Processing -->
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.11.0</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>1.19.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**SPI Registration File:**
```
# src/main/resources/META-INF/services/org.geotools.api.data.DataStoreFactorySpi
org.geotools.data.surrealdb.SurrealDBDataStoreFactory
```

---

## 9. Implementation Phases

### Phase 1: Foundation (Week 1-2)
- [ ] Maven project scaffolding with GeoTools 34.x parent
- [ ] `SurrealDBDataStoreFactory` with full parameter definitions
- [ ] `SurrealDBClient` with HTTP connection, JWT auth, and basic query execution
- [ ] `ConnectionConfig` value object and `AuthManager`
- [ ] Unit tests with mocked HTTP responses

### Phase 2: Schema & Type Mapping (Week 2-3)
- [ ] `SchemaDiscovery` using `INFO FOR DB` and `INFO FOR TABLE`
- [ ] `GeometryFieldDetector` to identify geometry-typed columns
- [ ] `FeatureTypeMapper` to build `SimpleFeatureType` from SurrealDB schema
- [ ] `GeoJsonToJtsConverter` for all supported geometry types
- [ ] `SurrealDBDataStore` extending `ContentDataStore`
- [ ] Integration tests with Testcontainers (SurrealDB Docker)

### Phase 3: Feature Reading & Filters (Week 3-4)
- [ ] `SurrealDBFeatureSource` extending `ContentFeatureSource`
- [ ] `SurrealDBFeatureReader` implementing `FeatureReader`
- [ ] `SurrealDBFilterTranslator` for BBOX, spatial, and property filters
- [ ] `getBoundsInternal` and `getCountInternal` implementations
- [ ] End-to-end integration test: load features from SurrealDB

### Phase 4: GeoServer Integration (Week 4-5)
- [ ] Deploy JAR to GeoServer `WEB-INF/lib`
- [ ] Test DataStore creation via Web Admin UI
- [ ] Test layer publishing and WMS GetMap requests
- [ ] Test WFS GetFeature requests
- [ ] Performance profiling with sample datasets (10K, 100K, 1M records)

### Phase 5: Hardening & Production Readiness (Week 5-6)
- [ ] Connection pooling implementation
- [ ] Schema and bounds caching
- [ ] TLS certificate validation
- [ ] Error handling, retry logic, and graceful degradation
- [ ] Logging (SLF4J) at appropriate levels
- [ ] Documentation: README, configuration guide, troubleshooting
- [ ] Load testing under concurrent WMS tile requests

### Phase 6 (Optional): Write Support
- [ ] `SurrealDBFeatureWriter` for WFS-T INSERT/UPDATE/DELETE
- [ ] Transaction management
- [ ] `JtsToGeoJsonConverter` for geometry serialization

---

## 10. Testing Strategy

### Unit Tests
- `GeoJsonToJtsConverterTest` — all 7 geometry types
- `SurrealDBFilterTranslatorTest` — OGC filter → SurrealQL correctness
- `FeatureTypeMapperTest` — schema mapping accuracy
- `AuthManagerTest` — token lifecycle, refresh, error handling

### Integration Tests (Testcontainers)
- `SurrealDBDataStoreIntegrationTest` — full DataStore lifecycle
- `SurrealDBFeatureSourceIntegrationTest` — query execution, result parsing
- `SpatialQueryIntegrationTest` — BBOX, contains, intersects with real geometry data

### GeoServer Tests
- Deploy and configure via REST API
- WMS GetMap with various BBOX extents
- WFS GetFeature with CQL filters
- Concurrent request load test (50+ simultaneous GetMap)

---

## 11. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| SurrealDB Java SDK instability (beta) | High | Medium | Dual strategy: SDK primary, raw HTTP fallback via OkHttp |
| SurrealQL spatial operator limitations | Medium | High | Test all OGC filter types early; degrade gracefully for unsupported filters |
| No native spatial indexing in SurrealDB | Medium | High | Document performance expectations; recommend data partitioning for large datasets |
| GeoTools API changes in 34.x | Low | Medium | Pin versions; follow GeoTools migration guide |
| JWT token expiry during long tile renders | Medium | Low | Proactive refresh; retry on 401 |
| Memory pressure with large result sets | Medium | Medium | Streaming FeatureReader; enforce LIMIT on queries |

---

## 12. Key Design Decisions

1. **HTTP over WebSocket for initial implementation**: HTTP is simpler, stateless, and easier to pool. WebSocket can be added later for real-time use cases.

2. **ContentDataStore base class over raw DataStore**: GeoTools' ContentDataStore provides transaction management, locking, and entry caching out of the box, reducing boilerplate.

3. **GeoJSON as the interchange format**: SurrealDB natively stores and returns GeoJSON. This avoids any custom serialization and maps directly to JTS via well-known conversion libraries.

4. **Read-only by default**: Keeps the initial scope focused. WFS-T write support is a clean extension point without architectural changes.

5. **Default SRID 4326 (WGS84)**: SurrealDB's geometry functions (distance, area) assume geographic coordinates. EPSG:4326 is the natural CRS.

6. **Dual client strategy**: Use the official SurrealDB Java SDK as the primary client, with OkHttp-based raw HTTP as a fallback to insulate against SDK instability.
