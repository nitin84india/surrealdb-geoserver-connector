package org.geotools.data.surrealdb.geometry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Stateless utility class that converts GeoJSON strings or JsonObjects
 * to JTS Geometry objects.
 *
 * <p>Supports all seven GeoJSON geometry types: Point, LineString, Polygon,
 * MultiPoint, MultiLineString, MultiPolygon, and GeometryCollection.
 * All geometries are created with SRID 4326 (WGS84).</p>
 *
 * <p>This converter is used as the bridge between SurrealDB's native GeoJSON
 * geometry representation and the JTS geometry model required by GeoTools.</p>
 */
public final class GeoJsonToJtsConverter {

    /** Default SRID for all geometries (WGS84). */
    private static final int DEFAULT_SRID = 4326;

    /** Shared geometry factory with floating precision and SRID 4326. */
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), DEFAULT_SRID);

    /** Private constructor to prevent instantiation of this utility class. */
    private GeoJsonToJtsConverter() {
        // Utility class - no instantiation
    }

    /**
     * Converts a GeoJSON string to a JTS Geometry.
     *
     * @param geoJson the GeoJSON string representing a geometry
     * @return the corresponding JTS Geometry
     * @throws IllegalArgumentException if geoJson is null, empty, not valid JSON,
     *                                  or contains an unsupported geometry type
     */
    public static Geometry convert(String geoJson) {
        if (geoJson == null || geoJson.trim().isEmpty()) {
            throw new IllegalArgumentException("GeoJSON string must not be null or empty");
        }

        try {
            JsonObject jsonObject = JsonParser.parseString(geoJson).getAsJsonObject();
            return convert(jsonObject);
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid GeoJSON: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a GeoJSON JsonObject to a JTS Geometry.
     *
     * @param geoJson the GeoJSON JsonObject representing a geometry
     * @return the corresponding JTS Geometry
     * @throws IllegalArgumentException if geoJson is null, missing the "type" field,
     *                                  or contains an unsupported geometry type
     */
    public static Geometry convert(JsonObject geoJson) {
        if (geoJson == null) {
            throw new IllegalArgumentException("GeoJSON object must not be null");
        }

        if (!geoJson.has("type")) {
            throw new IllegalArgumentException("GeoJSON object must have a 'type' field");
        }

        String type = geoJson.get("type").getAsString();

        if ("GeometryCollection".equals(type)) {
            JsonArray geometries = geoJson.getAsJsonArray("geometries");
            if (geometries == null) {
                throw new IllegalArgumentException(
                        "GeometryCollection must have a 'geometries' field");
            }
            return createGeometryCollection(geometries);
        }

        JsonArray coordinates = geoJson.getAsJsonArray("coordinates");
        if (coordinates == null) {
            throw new IllegalArgumentException(
                    "GeoJSON geometry must have a 'coordinates' field");
        }

        switch (type) {
            case "Point":
                return createPoint(coordinates);
            case "LineString":
                return createLineString(coordinates);
            case "Polygon":
                return createPolygon(coordinates);
            case "MultiPoint":
                return createMultiPoint(coordinates);
            case "MultiLineString":
                return createMultiLineString(coordinates);
            case "MultiPolygon":
                return createMultiPolygon(coordinates);
            default:
                throw new IllegalArgumentException("Unsupported GeoJSON type: " + type);
        }
    }

    /**
     * Creates a JTS Point from a GeoJSON coordinate array.
     *
     * @param coords coordinate array [x, y] or [x, y, z]
     * @return JTS Point
     */
    private static Point createPoint(JsonArray coords) {
        return GEOMETRY_FACTORY.createPoint(parseCoordinate(coords));
    }

    /**
     * Creates a JTS LineString from a GeoJSON coordinate array.
     *
     * @param coords array of coordinate arrays [[x1,y1],[x2,y2],...]
     * @return JTS LineString
     */
    private static LineString createLineString(JsonArray coords) {
        Coordinate[] coordinates = parseCoordinateArray(coords);
        return GEOMETRY_FACTORY.createLineString(coordinates);
    }

    /**
     * Creates a JTS Polygon from a GeoJSON coordinate array.
     *
     * <p>The first ring is the exterior (shell), and any subsequent rings
     * are interior holes.</p>
     *
     * @param coords array of ring coordinate arrays [shell, hole1, hole2, ...]
     * @return JTS Polygon
     */
    private static Polygon createPolygon(JsonArray coords) {
        // First ring is the exterior shell
        JsonArray shellCoords = coords.get(0).getAsJsonArray();
        LinearRing shell = GEOMETRY_FACTORY.createLinearRing(parseCoordinateArray(shellCoords));

        // Remaining rings are holes
        LinearRing[] holes = new LinearRing[coords.size() - 1];
        for (int i = 1; i < coords.size(); i++) {
            JsonArray holeCoords = coords.get(i).getAsJsonArray();
            holes[i - 1] = GEOMETRY_FACTORY.createLinearRing(parseCoordinateArray(holeCoords));
        }

        return GEOMETRY_FACTORY.createPolygon(shell, holes);
    }

    /**
     * Creates a JTS MultiPoint from a GeoJSON coordinate array.
     *
     * @param coords array of coordinate arrays [[x1,y1],[x2,y2],...]
     * @return JTS MultiPoint
     */
    private static MultiPoint createMultiPoint(JsonArray coords) {
        Point[] points = new Point[coords.size()];
        for (int i = 0; i < coords.size(); i++) {
            points[i] = GEOMETRY_FACTORY.createPoint(
                    parseCoordinate(coords.get(i).getAsJsonArray()));
        }
        return GEOMETRY_FACTORY.createMultiPoint(points);
    }

    /**
     * Creates a JTS MultiLineString from a GeoJSON coordinate array.
     *
     * @param coords array of line coordinate arrays
     * @return JTS MultiLineString
     */
    private static MultiLineString createMultiLineString(JsonArray coords) {
        LineString[] lineStrings = new LineString[coords.size()];
        for (int i = 0; i < coords.size(); i++) {
            lineStrings[i] = createLineString(coords.get(i).getAsJsonArray());
        }
        return GEOMETRY_FACTORY.createMultiLineString(lineStrings);
    }

    /**
     * Creates a JTS MultiPolygon from a GeoJSON coordinate array.
     *
     * @param coords array of polygon coordinate arrays
     * @return JTS MultiPolygon
     */
    private static MultiPolygon createMultiPolygon(JsonArray coords) {
        Polygon[] polygons = new Polygon[coords.size()];
        for (int i = 0; i < coords.size(); i++) {
            polygons[i] = createPolygon(coords.get(i).getAsJsonArray());
        }
        return GEOMETRY_FACTORY.createMultiPolygon(polygons);
    }

    /**
     * Creates a JTS GeometryCollection from a GeoJSON geometries array.
     *
     * <p>Each element in the array is a complete GeoJSON geometry object
     * with its own "type" and "coordinates" fields. Each is recursively
     * converted via {@link #convert(JsonObject)}.</p>
     *
     * @param geometries array of GeoJSON geometry objects
     * @return JTS GeometryCollection
     */
    private static GeometryCollection createGeometryCollection(JsonArray geometries) {
        Geometry[] geomArray = new Geometry[geometries.size()];
        for (int i = 0; i < geometries.size(); i++) {
            geomArray[i] = convert(geometries.get(i).getAsJsonObject());
        }
        return GEOMETRY_FACTORY.createGeometryCollection(geomArray);
    }

    /**
     * Parses a single GeoJSON coordinate array into a JTS Coordinate.
     *
     * @param coord JSON array of 2 or 3 numbers [x, y] or [x, y, z]
     * @return JTS Coordinate
     * @throws IllegalArgumentException if the array has fewer than 2 elements
     */
    private static Coordinate parseCoordinate(JsonArray coord) {
        if (coord.size() < 2) {
            throw new IllegalArgumentException(
                    "Coordinate array must have at least 2 elements, got " + coord.size());
        }

        double x = coord.get(0).getAsDouble();
        double y = coord.get(1).getAsDouble();

        if (coord.size() >= 3) {
            double z = coord.get(2).getAsDouble();
            return new Coordinate(x, y, z);
        }

        return new Coordinate(x, y);
    }

    /**
     * Parses a GeoJSON coordinate array (array of coordinate arrays) into
     * a JTS Coordinate array.
     *
     * @param coords JSON array of coordinate arrays
     * @return array of JTS Coordinates
     */
    private static Coordinate[] parseCoordinateArray(JsonArray coords) {
        Coordinate[] coordinates = new Coordinate[coords.size()];
        for (int i = 0; i < coords.size(); i++) {
            coordinates[i] = parseCoordinate(coords.get(i).getAsJsonArray());
        }
        return coordinates;
    }
}
