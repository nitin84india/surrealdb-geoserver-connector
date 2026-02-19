package org.geotools.data.surrealdb.geometry;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JtsToGeoJsonConverter}.
 *
 * <p>Covers all seven GeoJSON geometry types, 3D coordinate support,
 * round-trip conversion, and error handling.</p>
 */
class JtsToGeoJsonConverterTest {

    private static final GeometryFactory GF =
            new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

    @Test
    void convertPointReturnsCorrectStructure() {
        Point point = GF.createPoint(new Coordinate(-73.9654, 40.7829));

        Map<String, Object> result = JtsToGeoJsonConverter.convert(point);

        assertEquals("Point", result.get("type"));
        @SuppressWarnings("unchecked")
        List<Number> coords = (List<Number>) result.get("coordinates");
        assertEquals(-73.9654, coords.get(0).doubleValue(), 0.0001);
        assertEquals(40.7829, coords.get(1).doubleValue(), 0.0001);
        assertEquals(2, coords.size());
    }

    @Test
    void convertPointWith3DCoordinatesIncludesZ() {
        Point point = GF.createPoint(new Coordinate(-73.9654, 40.7829, 100.0));

        Map<String, Object> result = JtsToGeoJsonConverter.convert(point);

        @SuppressWarnings("unchecked")
        List<Number> coords = (List<Number>) result.get("coordinates");
        assertEquals(3, coords.size());
        assertEquals(100.0, coords.get(2).doubleValue(), 0.0001);
    }

    @Test
    void convertLineStringReturnsCorrectStructure() {
        Coordinate[] coords = {
                new Coordinate(-73.9654, 40.7829),
                new Coordinate(-73.9855, 40.7580)
        };
        var lineString = GF.createLineString(coords);

        Map<String, Object> result = JtsToGeoJsonConverter.convert(lineString);

        assertEquals("LineString", result.get("type"));
        @SuppressWarnings("unchecked")
        List<List<Number>> coordsList = (List<List<Number>>) result.get("coordinates");
        assertEquals(2, coordsList.size());
        assertEquals(-73.9654, coordsList.get(0).get(0).doubleValue(), 0.0001);
    }

    @Test
    void convertPolygonWithNoHolesReturnsOneRing() {
        Coordinate[] shellCoords = {
                new Coordinate(-73.98, 40.76), new Coordinate(-73.96, 40.76),
                new Coordinate(-73.96, 40.78), new Coordinate(-73.98, 40.78),
                new Coordinate(-73.98, 40.76)
        };
        var polygon = GF.createPolygon(shellCoords);

        Map<String, Object> result = JtsToGeoJsonConverter.convert(polygon);

        assertEquals("Polygon", result.get("type"));
        @SuppressWarnings("unchecked")
        List<List<List<Number>>> rings = (List<List<List<Number>>>) result.get("coordinates");
        assertEquals(1, rings.size());
        assertEquals(5, rings.get(0).size());
    }

    @Test
    void convertPolygonWithHoleReturnsTwoRings() {
        Coordinate[] shellCoords = {
                new Coordinate(0, 0), new Coordinate(10, 0),
                new Coordinate(10, 10), new Coordinate(0, 10),
                new Coordinate(0, 0)
        };
        Coordinate[] holeCoords = {
                new Coordinate(2, 2), new Coordinate(8, 2),
                new Coordinate(8, 8), new Coordinate(2, 8),
                new Coordinate(2, 2)
        };
        LinearRing shell = GF.createLinearRing(shellCoords);
        LinearRing hole = GF.createLinearRing(holeCoords);
        var polygon = GF.createPolygon(shell, new LinearRing[]{hole});

        Map<String, Object> result = JtsToGeoJsonConverter.convert(polygon);

        @SuppressWarnings("unchecked")
        List<List<List<Number>>> rings = (List<List<List<Number>>>) result.get("coordinates");
        assertEquals(2, rings.size());
        assertEquals(5, rings.get(0).size());
        assertEquals(5, rings.get(1).size());
    }

    @Test
    void convertMultiPointReturnsCorrectStructure() {
        var multiPoint = GF.createMultiPoint(new Point[]{
                GF.createPoint(new Coordinate(1, 2)),
                GF.createPoint(new Coordinate(3, 4))
        });

        Map<String, Object> result = JtsToGeoJsonConverter.convert(multiPoint);

        assertEquals("MultiPoint", result.get("type"));
        @SuppressWarnings("unchecked")
        List<List<Number>> coords = (List<List<Number>>) result.get("coordinates");
        assertEquals(2, coords.size());
    }

    @Test
    void convertMultiLineStringReturnsCorrectStructure() {
        var line1 = GF.createLineString(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(1, 1)
        });
        var line2 = GF.createLineString(new Coordinate[]{
                new Coordinate(2, 2), new Coordinate(3, 3)
        });
        var multiLine = GF.createMultiLineString(new org.locationtech.jts.geom.LineString[]{line1, line2});

        Map<String, Object> result = JtsToGeoJsonConverter.convert(multiLine);

        assertEquals("MultiLineString", result.get("type"));
        @SuppressWarnings("unchecked")
        List<List<List<Number>>> lines = (List<List<List<Number>>>) result.get("coordinates");
        assertEquals(2, lines.size());
    }

    @Test
    void convertMultiPolygonReturnsCorrectStructure() {
        var poly1 = GF.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(1, 0),
                new Coordinate(1, 1), new Coordinate(0, 1),
                new Coordinate(0, 0)
        });
        var poly2 = GF.createPolygon(new Coordinate[]{
                new Coordinate(2, 2), new Coordinate(3, 2),
                new Coordinate(3, 3), new Coordinate(2, 3),
                new Coordinate(2, 2)
        });
        var multiPoly = GF.createMultiPolygon(
                new org.locationtech.jts.geom.Polygon[]{poly1, poly2});

        Map<String, Object> result = JtsToGeoJsonConverter.convert(multiPoly);

        assertEquals("MultiPolygon", result.get("type"));
        @SuppressWarnings("unchecked")
        List<List<List<List<Number>>>> polygons =
                (List<List<List<List<Number>>>>) result.get("coordinates");
        assertEquals(2, polygons.size());
    }

    @Test
    void convertGeometryCollectionReturnsCorrectStructure() {
        var point = GF.createPoint(new Coordinate(1, 2));
        var line = GF.createLineString(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(1, 1)
        });
        var collection = GF.createGeometryCollection(new Geometry[]{point, line});

        Map<String, Object> result = JtsToGeoJsonConverter.convert(collection);

        assertEquals("GeometryCollection", result.get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> geometries = (List<Map<String, Object>>) result.get("geometries");
        assertEquals(2, geometries.size());
        assertEquals("Point", geometries.get(0).get("type"));
        assertEquals("LineString", geometries.get(1).get("type"));
    }

    @Test
    void convertNullThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> JtsToGeoJsonConverter.convert(null));
    }

    @Test
    void roundTripPointPreservesCoordinates() {
        Point original = GF.createPoint(new Coordinate(-73.9654, 40.7829));

        Map<String, Object> geoJson = JtsToGeoJsonConverter.convert(original);

        // Convert Map to JSON string for the reverse converter
        String json = mapToJson(geoJson);
        Geometry roundTripped = GeoJsonToJtsConverter.convert(json);

        assertInstanceOf(Point.class, roundTripped);
        Point result = (Point) roundTripped;
        assertEquals(original.getX(), result.getX(), 0.0001);
        assertEquals(original.getY(), result.getY(), 0.0001);
    }

    @Test
    void roundTripPolygonPreservesStructure() {
        Coordinate[] shellCoords = {
                new Coordinate(-73.98, 40.76), new Coordinate(-73.96, 40.76),
                new Coordinate(-73.96, 40.78), new Coordinate(-73.98, 40.78),
                new Coordinate(-73.98, 40.76)
        };
        var original = GF.createPolygon(shellCoords);

        Map<String, Object> geoJson = JtsToGeoJsonConverter.convert(original);
        String json = mapToJson(geoJson);
        Geometry roundTripped = GeoJsonToJtsConverter.convert(json);

        assertInstanceOf(org.locationtech.jts.geom.Polygon.class, roundTripped);
        var result = (org.locationtech.jts.geom.Polygon) roundTripped;
        assertEquals(original.getExteriorRing().getNumPoints(),
                result.getExteriorRing().getNumPoints());
    }

    /**
     * Simple Map-to-JSON serializer for round-trip tests.
     * Uses Gson for proper JSON formatting.
     */
    private static String mapToJson(Map<String, Object> map) {
        return new com.google.gson.Gson().toJson(map);
    }
}
