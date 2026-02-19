#!/usr/bin/env bash
set -euo pipefail

# GeoServer REST API automated setup for SurrealDB DataStore
# Creates workspace, datastore, and publishes feature type layers.
# Idempotent: handles 409 Conflict on re-runs.

GEOSERVER_URL="${GEOSERVER_URL:-http://localhost:8080/geoserver}"
GEOSERVER_USER="${GEOSERVER_USER:-admin}"
GEOSERVER_PASS="${GEOSERVER_PASS:-geoserver}"
WORKSPACE="${WORKSPACE:-surrealdb}"
DATASTORE="${DATASTORE:-spatial}"
SURREAL_HOST="${SURREAL_HOST:-surrealdb}"
SURREAL_PORT="${SURREAL_PORT:-8000}"
SURREAL_NS="${SURREAL_NS:-geoserver}"
SURREAL_DB="${SURREAL_DB:-spatial}"
SURREAL_USER="${SURREAL_USER:-root}"
SURREAL_PASS="${SURREAL_PASS:-root}"
MAX_WAIT="${MAX_WAIT:-120}"

AUTH="-u ${GEOSERVER_USER}:${GEOSERVER_PASS}"

echo "=== GeoServer SurrealDB DataStore Setup ==="
echo "GeoServer URL: ${GEOSERVER_URL}"
echo "SurrealDB:     ${SURREAL_HOST}:${SURREAL_PORT} (${SURREAL_NS}/${SURREAL_DB})"

# Step 1: Wait for GeoServer to be ready
echo ""
echo "[1/5] Waiting for GeoServer to be ready..."
elapsed=0
until curl -sf ${AUTH} "${GEOSERVER_URL}/rest/about/version.json" > /dev/null 2>&1; do
    if [ "$elapsed" -ge "$MAX_WAIT" ]; then
        echo "ERROR: GeoServer did not become ready within ${MAX_WAIT}s"
        exit 1
    fi
    sleep 3
    elapsed=$((elapsed + 3))
    echo "  Waiting... (${elapsed}s)"
done
echo "  GeoServer is ready."

# Step 2: Create workspace
echo ""
echo "[2/5] Creating workspace '${WORKSPACE}'..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" ${AUTH} \
    -X POST "${GEOSERVER_URL}/rest/workspaces" \
    -H "Content-Type: application/json" \
    -d "{\"workspace\":{\"name\":\"${WORKSPACE}\"}}")

if [ "$HTTP_CODE" = "201" ]; then
    echo "  Workspace '${WORKSPACE}' created."
elif [ "$HTTP_CODE" = "409" ] || [ "$HTTP_CODE" = "401" ]; then
    echo "  Workspace '${WORKSPACE}' already exists (HTTP ${HTTP_CODE}), skipping."
else
    echo "  WARNING: Unexpected response creating workspace: HTTP ${HTTP_CODE}"
fi

# Step 3: Create SurrealDB DataStore
echo ""
echo "[3/5] Creating SurrealDB DataStore '${DATASTORE}'..."
DATASTORE_JSON=$(cat <<DSJSON
{
  "dataStore": {
    "name": "${DATASTORE}",
    "type": "SurrealDB",
    "connectionParameters": {
      "entry": [
        {"@key": "dbtype",    "\$": "surrealdb"},
        {"@key": "host",      "\$": "${SURREAL_HOST}"},
        {"@key": "port",      "\$": "${SURREAL_PORT}"},
        {"@key": "surreal_ns","\$": "${SURREAL_NS}"},
        {"@key": "database",  "\$": "${SURREAL_DB}"},
        {"@key": "user",      "\$": "${SURREAL_USER}"},
        {"@key": "password",  "\$": "${SURREAL_PASS}"},
        {"@key": "protocol",  "\$": "http"}
      ]
    }
  }
}
DSJSON
)

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" ${AUTH} \
    -X POST "${GEOSERVER_URL}/rest/workspaces/${WORKSPACE}/datastores" \
    -H "Content-Type: application/json" \
    -d "${DATASTORE_JSON}")

if [ "$HTTP_CODE" = "201" ]; then
    echo "  DataStore '${DATASTORE}' created."
elif [ "$HTTP_CODE" = "409" ]; then
    echo "  DataStore '${DATASTORE}' already exists, skipping."
else
    echo "  WARNING: Unexpected response creating datastore: HTTP ${HTTP_CODE}"
fi

# Step 4: Publish layers
echo ""
echo "[4/5] Publishing feature type layers..."

publish_layer() {
    local LAYER_NAME=$1
    local LAYER_TITLE=$2
    local SRS="${3:-EPSG:4326}"

    LAYER_JSON=$(cat <<LJSON
{
  "featureType": {
    "name": "${LAYER_NAME}",
    "nativeName": "${LAYER_NAME}",
    "title": "${LAYER_TITLE}",
    "srs": "${SRS}",
    "nativeBoundingBox": {
      "minx": -180, "maxx": 180,
      "miny": -90, "maxy": 90,
      "crs": "${SRS}"
    },
    "latLonBoundingBox": {
      "minx": -180, "maxx": 180,
      "miny": -90, "maxy": 90,
      "crs": "EPSG:4326"
    },
    "enabled": true
  }
}
LJSON
)

    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" ${AUTH} \
        -X POST "${GEOSERVER_URL}/rest/workspaces/${WORKSPACE}/datastores/${DATASTORE}/featuretypes" \
        -H "Content-Type: application/json" \
        -d "${LAYER_JSON}")

    if [ "$HTTP_CODE" = "201" ]; then
        echo "  Layer '${LAYER_NAME}' published."
    elif [ "$HTTP_CODE" = "409" ]; then
        echo "  Layer '${LAYER_NAME}' already exists, skipping."
    else
        echo "  WARNING: Unexpected response publishing '${LAYER_NAME}': HTTP ${HTTP_CODE}"
    fi
}

publish_layer "poi"   "Points of Interest"
publish_layer "park"  "Parks"
publish_layer "trail" "Trails"

# Step 5: Print test URLs
echo ""
echo "[5/5] Setup complete. Test URLs:"
echo ""
echo "  WFS GetCapabilities:"
echo "    ${GEOSERVER_URL}/wfs?service=WFS&version=2.0.0&request=GetCapabilities"
echo ""
echo "  WFS GetFeature (poi, JSON):"
echo "    ${GEOSERVER_URL}/wfs?service=WFS&version=2.0.0&request=GetFeature&typeNames=${WORKSPACE}:poi&outputFormat=application/json"
echo ""
echo "  WFS GetFeature (park, JSON):"
echo "    ${GEOSERVER_URL}/wfs?service=WFS&version=2.0.0&request=GetFeature&typeNames=${WORKSPACE}:park&outputFormat=application/json"
echo ""
echo "  WFS GetFeature (trail, JSON):"
echo "    ${GEOSERVER_URL}/wfs?service=WFS&version=2.0.0&request=GetFeature&typeNames=${WORKSPACE}:trail&outputFormat=application/json"
echo ""
echo "  WMS GetMap (poi, PNG):"
echo "    ${GEOSERVER_URL}/wms?service=WMS&version=1.3.0&request=GetMap&layers=${WORKSPACE}:poi&bbox=-180,-90,180,90&width=800&height=400&srs=EPSG:4326&format=image/png"
echo ""
echo "  WMS GetMap (park, PNG):"
echo "    ${GEOSERVER_URL}/wms?service=WMS&version=1.3.0&request=GetMap&layers=${WORKSPACE}:park&bbox=-74.0,-40.5,-73.5,41.0&width=800&height=400&srs=EPSG:4326&format=image/png"
echo ""
echo "=== Done ==="
