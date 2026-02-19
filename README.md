# GeoServer-SurrealDB Connector

A custom GeoServer DataStore plugin (Java) that connects GeoServer to SurrealDB, enabling geometry data stored in SurrealDB to be served as OGC-compliant WMS/WFS layers. The connector introspects SurrealDB schemas, maps SurrealDB types to JTS geometry classes, and converts GeoJSON responses into JTS Geometry objects.

## Architecture

```mermaid
graph TB
    subgraph GeoServer["GeoServer 2.28.x"]
        WMS["WMS 1.3.0"]
        WFS["WFS 2.0"]
        WebAdmin["Web Admin UI"]
        REST["REST API"]
    end

    subgraph Plugin["gs-surrealdb-connector"]
        Factory["SurrealDBDataStoreFactory\n(SPI Entry Point)"]
        DataStore["SurrealDBDataStore\n(ContentDataStore)"]

        subgraph Schema["schema/"]
            Discovery["SchemaDiscovery\n(INFO FOR DB / TABLE)"]
            Detector["GeometryFieldDetector\n(Type Mapping)"]
            Mapper["FeatureTypeMapper\n(SimpleFeatureType Builder)"]
            FieldVO["FieldSchema / TableSchema\n(Value Objects)"]
        end

        subgraph Core["Core Feature Pipeline"]
            FeatureSource["SurrealDBFeatureSource\n(ContentFeatureSource)"]
            FeatureReader["SurrealDBFeatureReader\n(Stub - Phase 3)"]
        end

        subgraph Geometry["geometry/"]
            Converter["GeoJsonToJtsConverter\n(7 Geometry Types)"]
        end

        subgraph Client["client/"]
            Interface["SurrealDBClient\n(Interface / Port)"]
            SdkClient["SurrealDBSdkClient\n(JNI Adapter)"]
            AuthMgr["AuthManager\n(JWT Lifecycle)"]
            Config["ConnectionConfig\n(Value Object)"]
        end
    end

    subgraph Database["SurrealDB v2.x"]
        NS["Namespace > Database\n> SCHEMAFULL Tables\n(geometry fields)"]
    end

    WMS & WFS & WebAdmin & REST --> Factory
    Factory --> DataStore
    DataStore --> Discovery
    Discovery --> Interface
    Discovery --> Detector
    DataStore --> Mapper
    Mapper --> Detector
    DataStore --> FeatureSource
    FeatureSource --> FeatureReader
    FeatureReader --> Interface
    FeatureReader --> Converter
    Interface --> SdkClient
    SdkClient --> AuthMgr
    SdkClient -- "HTTP + JNI\n(Root or DB signin)" --> NS
```

## Schema Discovery Flow

```mermaid
sequenceDiagram
    participant GS as GeoServer
    participant DS as SurrealDBDataStore
    participant SD as SchemaDiscovery
    participant GFD as GeometryFieldDetector
    participant FTM as FeatureTypeMapper
    participant Client as SurrealDBSdkClient
    participant DB as SurrealDB

    GS->>DS: getTypeNames()
    DS->>SD: discoverGeometryTables()
    SD->>Client: queryAsJson("INFO FOR DB")
    Client->>DB: INFO FOR DB
    DB-->>Client: {tables: {poi: "DEFINE TABLE poi...SCHEMAFULL", event: "...SCHEMALESS"}}
    Client-->>SD: JSON response (via Value.toString())
    SD->>SD: Filter SCHEMAFULL tables only

    loop For each SCHEMAFULL table
        SD->>Client: queryAsJson("INFO FOR TABLE poi")
        Client->>DB: INFO FOR TABLE poi
        DB-->>Client: {fields: {geometry: "...TYPE geometry<point>", name: "...TYPE string"}}
        Client-->>SD: JSON response
        SD->>GFD: isGeometryKind("geometry<point>")
        GFD-->>SD: true
        SD->>SD: Build TableSchema + FieldSchema
    end

    SD-->>DS: List<TableSchema> (geometry tables only)
    DS->>DS: Cache schemas in ConcurrentHashMap

    Note over GS, DB: Later, when GeoServer needs the schema...

    GS->>DS: getSchema("poi")
    DS->>FTM: buildFeatureType(tableSchema)
    FTM->>GFD: mapGeometryBinding("geometry<point>") -> Point.class
    FTM->>GFD: mapAttributeBinding("string") -> String.class
    FTM->>FTM: SimpleFeatureTypeBuilder + CRS.decode("EPSG:4326")
    FTM-->>DS: SimpleFeatureType
    DS-->>GS: SimpleFeatureType{id:String, name:String, geometry:Point, category:String}
```

## Connection & Authentication Flow

```mermaid
sequenceDiagram
    participant GS as GeoServer
    participant DSF as DataStoreFactory
    participant Client as SurrealDBSdkClient
    participant Auth as AuthManager
    participant DB as SurrealDB

    GS->>DSF: createDataStore(params)
    DSF->>DSF: toConnectionConfig(params)
    DSF->>Client: connect(config)
    Client->>DB: driver.connect("http://host:port")
    Client->>DB: driver.useNs(ns).useDb(db)

    Client->>Client: performSignin()

    alt Root User
        Client->>DB: signin(Root(user, pass))
        DB-->>Client: JWT Token
    else Database User
        Client->>DB: signin(Database(user, pass, ns, db))
        DB-->>Client: JWT Token
    end

    Client->>Auth: updateToken(token)
    Auth-->>Auth: Cache token + track expiry
    Client-->>DSF: Connected
    DSF-->>GS: SurrealDBDataStore

    Note over GS, DB: On subsequent queries...

    GS->>Client: queryAsJson("SELECT * FROM poi")
    Client->>Auth: needsRefresh()?
    Auth-->>Client: false (token valid)
    Client->>DB: driver.query(surrealQL)
    DB-->>Client: Response (JNI Value)
    Client->>Client: value.toString()
    Client-->>GS: JSON string
```

## Type Mapping

### SurrealDB Geometry Types to JTS

```mermaid
graph LR
    subgraph SurrealDB["SurrealDB Geometry Kinds"]
        GP["geometry&lt;point&gt;"]
        GL["geometry&lt;line&gt;"]
        GPoly["geometry&lt;polygon&gt;"]
        GMP["geometry&lt;multipoint&gt;"]
        GML["geometry&lt;multiline&gt;"]
        GMPoly["geometry&lt;multipolygon&gt;"]
        GC["geometry&lt;collection&gt;"]
        GF["geometry&lt;feature&gt;"]
        G["geometry (bare)"]
    end

    subgraph JTS["JTS Geometry Classes"]
        Point["Point"]
        LineString["LineString"]
        Polygon["Polygon"]
        MultiPoint["MultiPoint"]
        MultiLineString["MultiLineString"]
        MultiPolygon["MultiPolygon"]
        GeometryCollection["GeometryCollection"]
        Geometry["Geometry (any)"]
    end

    GP --> Point
    GL --> LineString
    GPoly --> Polygon
    GMP --> MultiPoint
    GML --> MultiLineString
    GMPoly --> MultiPolygon
    GC --> GeometryCollection
    GF --> Geometry
    G --> Geometry
```

### SurrealDB Attribute Types to Java

| SurrealDB Kind | Java Class |
|----------------|------------|
| `string` | `String` |
| `int` | `Long` |
| `float` | `Double` |
| `number` | `Double` |
| `bool` | `Boolean` |
| `datetime` | `java.util.Date` |
| `decimal` | `BigDecimal` |
| `duration` | `String` |
| `object` / `record` / `array` | `String` (JSON) |

## Quick Start

### Prerequisites

- Java 17+ (build/test with Java 21 recommended)
- Maven 3.9+
- Docker & Docker Compose

### Build

```bash
# Use Java 21 for building (required for Mockito compatibility)
JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn clean package
```

### Run Tests

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn test
# 122 tests across 12 test classes
```

### Deploy to GeoServer

```bash
# Start SurrealDB + GeoServer (plugin JAR auto-mounted)
docker compose up -d

# Initialize sample spatial data
bash docker/init-surreal.sh

# Access GeoServer at http://localhost:8080/geoserver
# Login: admin / geoserver
```

### Configure SurrealDB Data Store

1. Navigate to **Data > Stores > Add new Store**
2. Select **SurrealDB** under Vector Data Sources
3. Fill in connection parameters:

| Parameter | Description | Default |
|-----------|-------------|---------|
| `host` | SurrealDB hostname | `localhost` |
| `port` | SurrealDB port | `8000` |
| `surreal_ns` | SurrealDB namespace | *(required)* |
| `database` | SurrealDB database | *(required)* |
| `user` | Username (root or DB user) | *(required)* |
| `password` | Password | *(required)* |
| `use_tls` | Enable TLS encryption | `false` |
| `protocol` | Connection protocol | `http` |
| `pool_size` | Connection pool size | `5` |
| `timeout` | Connection timeout (ms) | `30000` |
| `geometry_field` | Default geometry field | `geometry` |
| `srid` | Default SRID | `4326` |

4. Click **Save** -- GeoServer auto-discovers SCHEMAFULL tables with geometry fields
5. Navigate to **Data > Layers > Add new Layer**, select the SurrealDB store, and publish layers

### Sample Data (Docker)

The `docker/init-surreal.sh` script creates sample tables in namespace `geoserver`, database `spatial`:

| Table | Type | Geometry | Records |
|-------|------|----------|---------|
| `poi` | SCHEMAFULL | `geometry<point>` | 3 (Central Park, Times Square, Brooklyn Bridge) |
| `park` | SCHEMAFULL | `geometry<polygon>` | 2 (Central Park, Bryant Park) |
| `trail` | SCHEMAFULL | `geometry<line>` | 1 (Hudson River Greenway) |
| `event` | SCHEMALESS | *(excluded)* | 1 (not discovered by connector) |

## Phase Status

| Phase | Description | Status | Tests |
|-------|-------------|--------|-------|
| 1 | Foundation -- Maven scaffolding, DataStoreFactory, SurrealDBClient, JWT auth | Complete | 43 |
| 2 | Schema & Type Mapping -- SchemaDiscovery, GeometryFieldDetector, GeoJSON-to-JTS, GeoServer deployment | Complete | 122 |
| 3 | Feature Reading & Filters -- FeatureReader, FilterTranslator (BBOX, spatial, property), bounds/count | Planned | - |
| 4 | GeoServer Integration -- WMS GetMap, WFS GetFeature, performance profiling | Planned | - |
| 5 | Hardening -- Connection pooling, caching, TLS, error handling, logging, load testing | Planned | - |
| 6 | Write Support (Optional) -- WFS-T INSERT/UPDATE/DELETE, transaction management | Planned | - |

```mermaid
gantt
    title Implementation Phases
    dateFormat YYYY-MM-DD
    axisFormat %b %d

    section Phase 1
    Foundation (SPI, Client, Auth)       :done, p1, 2026-02-17, 1d

    section Phase 2
    Schema & Type Mapping                :done, p2, 2026-02-18, 2d

    section Phase 3
    Feature Reading & Filters            :p3, after p2, 3d

    section Phase 4
    GeoServer Integration                :p4, after p3, 2d

    section Phase 5
    Hardening                            :p5, after p4, 2d

    section Phase 6
    Write Support                        :p6, after p5, 3d
```

## Project Structure

```
geoserver-surrealdb-connector/
├── pom.xml                          # Maven build (GeoTools 34.1, SurrealDB SDK)
├── docker-compose.yml               # SurrealDB v2.2.1 + GeoServer 2.28.0
├── docker/
│   └── init-surreal.sh              # Sample spatial data (poi, park, trail, event)
├── src/
│   ├── main/
│   │   ├── java/org/geotools/data/surrealdb/
│   │   │   ├── SurrealDBDataStoreFactory.java   # SPI entry point
│   │   │   ├── SurrealDBDataStore.java          # ContentDataStore (table discovery)
│   │   │   ├── SurrealDBFeatureSource.java      # ContentFeatureSource (schema)
│   │   │   ├── SurrealDBFeatureReader.java      # FeatureReader (stub)
│   │   │   ├── client/
│   │   │   │   ├── SurrealDBClient.java         # Interface (Port)
│   │   │   │   ├── SurrealDBSdkClient.java      # JNI adapter (Root/DB signin)
│   │   │   │   ├── ConnectionConfig.java        # Immutable config value object
│   │   │   │   └── AuthManager.java             # JWT token lifecycle
│   │   │   ├── config/
│   │   │   │   └── SurrealDBDataStoreParams.java  # GeoServer param definitions
│   │   │   ├── schema/
│   │   │   │   ├── SchemaDiscovery.java         # INFO FOR DB/TABLE parsing
│   │   │   │   ├── GeometryFieldDetector.java   # SurrealDB kind -> JTS class
│   │   │   │   ├── FeatureTypeMapper.java       # TableSchema -> SimpleFeatureType
│   │   │   │   ├── FieldSchema.java             # Field value object
│   │   │   │   └── TableSchema.java             # Table value object
│   │   │   └── geometry/
│   │   │       └── GeoJsonToJtsConverter.java   # GeoJSON -> JTS (7 types)
│   │   └── resources/META-INF/services/
│   │       └── org.geotools.api.data.DataStoreFactorySpi
│   └── test/java/org/geotools/data/surrealdb/
│       ├── SurrealDBDataStoreFactoryTest.java   # 8 tests
│       ├── SurrealDBDataStoreTest.java          # 5 tests
│       ├── client/
│       │   ├── AuthManagerTest.java             # 12 tests
│       │   ├── ConnectionConfigTest.java        # 12 tests
│       │   └── SurrealDBSdkClientTest.java      # 8 tests
│       ├── config/
│       │   └── SurrealDBDataStoreParamsTest.java  # 3 tests
│       ├── schema/
│       │   ├── FieldSchemaTest.java             # 7 tests
│       │   ├── TableSchemaTest.java             # 11 tests
│       │   ├── GeometryFieldDetectorTest.java   # 31 tests
│       │   ├── SchemaDiscoveryTest.java         # 8 tests
│       │   └── FeatureTypeMapperTest.java       # 5 tests
│       └── geometry/
│           └── GeoJsonToJtsConverterTest.java   # 12 tests
├── ARCHITECTURE.md
├── CLAUDE.md
└── README.md
```

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 17+ (build with 21) |
| Build System | Maven | 3.9+ |
| GeoTools | gt-main, gt-api | 34.1 |
| Database | SurrealDB | v2.2.1 (Java SDK 1.0.0-beta.1) |
| Geometry | JTS Core (LocationTech) | via GeoTools |
| JSON | Gson | 2.11.0 |
| HTTP Client | OkHttp (fallback) | 4.12.0 |
| Logging | SLF4J | via GeoTools |
| Testing | JUnit 5 + Mockito | 5.10+ / 5.15+ |
| Containerization | Docker Compose | - |
| OGC Standards | WMS 1.3.0, WFS 2.0 | - |

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **`Value.toString()` over Gson serialization** | SDK's `Value` extends `Native` (JNI). `Gson.toJson(value)` serializes the pointer field `{"ptr":123}`, not the data. `Value.toString()` calls the native method returning the actual content. |
| **Root-then-Database signin** | `performSignin()` tries `Root` credential first, falls back to `Database`. Server root users can't authenticate with `Database`-scoped signin. |
| **`surreal_ns` parameter key** | GeoServer reserves `namespace` for workspace URI. Renamed to `surreal_ns` to avoid collision. |
| **Gson 2.11.0 override in Docker** | GeoServer ships `gson-2.3.1` which lacks `JsonParser.parseString()`. Docker mounts override both the old and new JAR paths. |
| **SCHEMAFULL-only discovery** | Only SCHEMAFULL tables have typed field definitions. SCHEMALESS tables return empty field lists and are excluded from discovery. |
| **ContentDataStore base class** | Provides built-in entry caching, transaction management, and the standard GeoTools feature pipeline contract. |

## License

This project is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
