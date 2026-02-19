package org.geotools.data.surrealdb.geometry;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless utility class that converts JTS Geometry objects to GeoJSON-compatible
 * Map structures (type + coordinates/geometries).
 *
 * <p>This is the reverse of {@link GeoJsonToJtsConverter} and is used to serialize
 * JTS geometries as SurrealQL bind parameters for spatial filter queries.</p>
 *
 * <p>Supports all seven GeoJSON geometry types: Point, LineString, Polygon,
 * MultiPoint, MultiLineString, MultiPolygon, and GeometryCollection.
 * 3D coordinates (Z values) are included only when the Z value is not NaN.</p>
 */
public final class JtsToGeoJsonConverter {

    private JtsToGeoJsonConverter() {
        // Utility class - no instantiation
    }

    /**
     * Converts a JTS Geometry to a GeoJSON-compatible Map structure.
     *
     * @param geometry the JTS geometry to convert
     * @return a Map with "type" and "coordinates" (or "geometries") keys
     * @throws IllegalArgumentException if geometry is null or an unsupported type
     */
    public static Map<String, Object> convert(Geometry geometry) {
        if (geometry == null) {
            throw new IllegalArgumentException("Geometry must not be null");
        }

        if (geometry instanceof Point) {
            return convertPoint((Point) geometry);
        } else if (geometry instanceof LineString) {
            return convertLineString((LineString) geometry);
        } else if (geometry instanceof Polygon) {
            return convertPolygon((Polygon) geometry);
        } else if (geometry instanceof MultiPoint) {
            return convertMultiPoint((MultiPoint) geometry);
        } else if (geometry instanceof MultiLineString) {
            return convertMultiLineString((MultiLineString) geometry);
        } else if (geometry instanceof MultiPolygon) {
            return convertMultiPolygon((MultiPolygon) geometry);
        } else if (geometry instanceof GeometryCollection) {
            return convertGeometryCollection((GeometryCollection) geometry);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported geometry type: " + geometry.getClass().getSimpleName());
        }
    }

    private static Map<String, Object> convertPoint(Point point) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "Point");
        result.put("coordinates", coordinateToList(point.getCoordinate()));
        return result;
    }

    private static Map<String, Object> convertLineString(LineString lineString) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "LineString");
        result.put("coordinates", coordinateArrayToList(lineString.getCoordinates()));
        return result;
    }

    private static Map<String, Object> convertPolygon(Polygon polygon) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "Polygon");

        List<List<List<Number>>> rings = new ArrayList<>();
        // Exterior ring
        rings.add(coordinateArrayToList(polygon.getExteriorRing().getCoordinates()));
        // Interior rings (holes)
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            rings.add(coordinateArrayToList(polygon.getInteriorRingN(i).getCoordinates()));
        }
        result.put("coordinates", rings);
        return result;
    }

    private static Map<String, Object> convertMultiPoint(MultiPoint multiPoint) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "MultiPoint");
        result.put("coordinates", coordinateArrayToList(multiPoint.getCoordinates()));
        return result;
    }

    private static Map<String, Object> convertMultiLineString(MultiLineString multiLineString) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "MultiLineString");

        List<List<List<Number>>> lines = new ArrayList<>();
        for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
            LineString line = (LineString) multiLineString.getGeometryN(i);
            lines.add(coordinateArrayToList(line.getCoordinates()));
        }
        result.put("coordinates", lines);
        return result;
    }

    private static Map<String, Object> convertMultiPolygon(MultiPolygon multiPolygon) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "MultiPolygon");

        List<List<List<List<Number>>>> polygons = new ArrayList<>();
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
            Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
            List<List<List<Number>>> rings = new ArrayList<>();
            rings.add(coordinateArrayToList(polygon.getExteriorRing().getCoordinates()));
            for (int j = 0; j < polygon.getNumInteriorRing(); j++) {
                rings.add(coordinateArrayToList(polygon.getInteriorRingN(j).getCoordinates()));
            }
            polygons.add(rings);
        }
        result.put("coordinates", polygons);
        return result;
    }

    private static Map<String, Object> convertGeometryCollection(GeometryCollection collection) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "GeometryCollection");

        List<Map<String, Object>> geometries = new ArrayList<>();
        for (int i = 0; i < collection.getNumGeometries(); i++) {
            geometries.add(convert(collection.getGeometryN(i)));
        }
        result.put("geometries", geometries);
        return result;
    }

    private static List<Number> coordinateToList(Coordinate coord) {
        List<Number> list = new ArrayList<>();
        list.add(coord.getX());
        list.add(coord.getY());
        if (!Double.isNaN(coord.getZ())) {
            list.add(coord.getZ());
        }
        return list;
    }

    private static List<List<Number>> coordinateArrayToList(Coordinate[] coords) {
        List<List<Number>> list = new ArrayList<>();
        for (Coordinate coord : coords) {
            list.add(coordinateToList(coord));
        }
        return list;
    }
}
