#!/usr/bin/env bash
set -euo pipefail

SURREAL_URL="${SURREAL_URL:-http://localhost:8000}"

echo "Initializing SurrealDB with sample spatial data..."

curl -s -X POST "${SURREAL_URL}/sql" \
  -u root:root \
  -H "Accept: application/json" \
  -H "surreal-ns: geoserver" \
  -H "surreal-db: spatial" \
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

DEFINE TABLE park SCHEMAFULL;
DEFINE FIELD name ON park TYPE string;
DEFINE FIELD geometry ON park TYPE geometry<polygon>;
DEFINE FIELD area_sqm ON park TYPE float;

CREATE park SET
  name = "Central Park",
  geometry = {"type":"Polygon","coordinates":[[[-73.981,40.768],[-73.958,40.768],[-73.958,40.800],[-73.981,40.800],[-73.981,40.768]]]},
  area_sqm = 3410000.0;

CREATE park SET
  name = "Bryant Park",
  geometry = {"type":"Polygon","coordinates":[[[-73.9847,40.7536],[-73.9822,40.7536],[-73.9822,40.7554],[-73.9847,40.7554],[-73.9847,40.7536]]]},
  area_sqm = 39000.0;

DEFINE TABLE event SCHEMALESS;

CREATE event SET
  name = "Concert in the Park",
  date = "2024-07-04",
  location = {"type":"Point","coordinates":[-73.9654,40.7829]};

DEFINE TABLE trail SCHEMAFULL;
DEFINE FIELD name ON trail TYPE string;
DEFINE FIELD geometry ON trail TYPE geometry<line>;
DEFINE FIELD difficulty ON trail TYPE string;

CREATE trail SET
  name = "Hudson River Greenway",
  geometry = {"type":"LineString","coordinates":[[-74.0060,40.7128],[-74.0100,40.7300],[-74.0080,40.7500]]},
  difficulty = "easy";
'

echo "SurrealDB initialization complete."
