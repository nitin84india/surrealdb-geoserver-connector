# GeoServer-SurrealDB Connector

## Project Overview
A custom GeoServer DataStore plugin (Java) that connects GeoServer to SurrealDB 3.0, enabling geometry data stored in SurrealDB to be served as OGC-compliant WMS/WFS layers. The connector translates OGC filter queries into SurrealQL and converts SurrealDB's GeoJSON responses into JTS Geometry objects.

## Tech Stack (Project-Specific — Overrides Global Defaults)
- **Language**: Java 17+ (NOT dotnet)
- **Build System**: Maven with GeoTools 34.x parent POM
- **Target Platform**: GeoServer 2.28.x / GeoTools 34.x
- **Database**: SurrealDB 3.0 (Java SDK `3.0.0-beta.1`)
- **HTTP Client**: OkHttp 4.12.0 (fallback for SDK instability)
- **JSON**: Gson 2.11.0
- **Geometry**: JTS Core (LocationTech) with GeoJSON interchange format
- **Testing**: JUnit + Testcontainers (SurrealDB Docker image)
- **OGC Standards**: WMS 1.3.0, WFS 2.0

## Package Structure
```
org.geotools.data.surrealdb/
  SurrealDBDataStoreFactory.java   # SPI entry point (registered via META-INF/services)
  SurrealDBDataStore.java          # ContentDataStore implementation
  SurrealDBFeatureSource.java      # ContentFeatureSource implementation
  SurrealDBFeatureReader.java      # FeatureReader implementation
  client/                          # Connection layer (HTTP, WebSocket, JWT auth, pooling)
  filter/                          # OGC Filter to SurrealQL translation
  schema/                          # Table/field introspection and FeatureType mapping
  geometry/                        # GeoJSON <-> JTS Geometry conversion
  config/                          # Parameter definitions, TLS, security
```

## SPI Registration
The plugin is discovered by GeoServer via:
`src/main/resources/META-INF/services/org.geotools.api.data.DataStoreFactorySpi`
containing: `org.geotools.data.surrealdb.SurrealDBDataStoreFactory`

## Implementation Phases
1. **Foundation** — Maven scaffolding, DataStoreFactory, SurrealDBClient (HTTP), JWT auth, unit tests
2. **Schema & Type Mapping** — SchemaDiscovery (`INFO FOR DB`/`INFO FOR TABLE`), GeometryFieldDetector, GeoJSON-to-JTS converter, integration tests
3. **Feature Reading & Filters** — FeatureSource, FeatureReader, FilterTranslator (BBOX, spatial, property filters), bounds/count
4. **GeoServer Integration** — Deploy JAR to `WEB-INF/lib`, test via Web Admin, WMS GetMap, WFS GetFeature, performance profiling
5. **Hardening** — Connection pooling, caching (schema 5min, bounds 10min, counts 5min), TLS, error handling, logging (SLF4J), load testing
6. **Write Support (Optional)** — WFS-T INSERT/UPDATE/DELETE, transaction management, JTS-to-GeoJSON converter

## Key Architecture Decisions
- **HTTP over WebSocket** for initial implementation (simpler, stateless, poolable)
- **ContentDataStore base class** for built-in transaction management and entry caching
- **GeoJSON as interchange format** (SurrealDB native format, maps directly to JTS)
- **Read-only by default** (write support is Phase 6 extension)
- **Default SRID 4326 (WGS84)** — SurrealDB geometry functions assume geographic coordinates
- **Dual client strategy** — SurrealDB Java SDK primary, OkHttp raw HTTP fallback for SDK beta instability

## Key Patterns
- OGC Filter translation: BBOX -> `INTERSECTS`, Contains -> `CONTAINS`, Within -> `INSIDE`
- Query parameterization: All user-supplied values as SurrealQL parameters (`$param`) to prevent injection
- Proactive JWT token refresh (refresh before expiry - 60s buffer)
- Streaming FeatureReader for lazy iteration (avoid loading all features into memory)
- Property selection: Only SELECT fields GeoServer needs per Query.getPropertyNames()

## Testing Strategy
- **Unit Tests**: GeoJsonToJtsConverter (7 geometry types), FilterTranslator, FeatureTypeMapper, AuthManager
- **Integration Tests**: Testcontainers with SurrealDB Docker — full DataStore lifecycle, query execution, spatial queries
- **GeoServer Tests**: Deploy, configure via REST API, WMS/WFS requests, concurrent load (50+ simultaneous GetMap)

## Risk Mitigations
- SurrealDB Java SDK is beta -> dual client with OkHttp fallback
- No native spatial indexing in SurrealDB -> document performance limits, recommend data partitioning
- JWT expiry during long tile renders -> proactive refresh + retry on 401

## Reference
See `ARCHITECTURE.md` for the full architecture document with diagrams, data flows, type mappings, and security details.
