#!/usr/bin/env bash
set -euo pipefail

SURREAL_URL="${SURREAL_URL:-http://localhost:8000}"

echo "Initializing SurrealDB with sample spatial data..."

curl -s -X POST "${SURREAL_URL}/sql" \
  -u root:root \
  -H "Accept: application/json" \
  -H "NS: geoserver" \
  -H "DB: spatial" \
  -d '
DEFINE USER geouser ON DATABASE PASSWORD "geopass" ROLES EDITOR;

DEFINE TABLE poi SCHEMAFULL;

DEFINE FIELD name ON poi TYPE string;
DEFINE FIELD geometry ON poi TYPE geometry<point>;
DEFINE FIELD category ON poi TYPE string;

CREATE poi SET
  name = "Central Park",
  geometry = {"type":"Point","coordinates":[-73.9654,40.7829]},
  category = "park";

CREATE poi SET
  name = "Times Square",
  geometry = {"type":"Point","coordinates":[-73.9855,40.7580]},
  category = "landmark";

CREATE poi SET
  name = "Brooklyn Bridge",
  geometry = {"type":"Point","coordinates":[-73.9969,40.7061]},
  category = "bridge";
'

echo "SurrealDB initialization complete."
