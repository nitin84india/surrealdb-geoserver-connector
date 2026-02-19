package org.geotools.data.surrealdb.geometry;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link GeoJsonToJtsConverter}.
 *
 * <p>Covers all seven GeoJSON geometry types, 3D coordinate support,
 * and error handling for invalid inputs.</p>
 */
class GeoJsonToJtsConverterTest {

    private static final double DELTA = 0.0001;

    @Test
    void convertPointReturnsCorrectCoordinatesAndSrid() {
        String geoJson = "{\"type\":\"Point\",\"coordinates\":[-73.9654,40.7829]}";

        Geometry result = GeoJsonToJtsConverter.convert(geoJson);

        assertInstanceOf(Point.class, result);
        Point point = (Point) result;
        assertEquals(-73.9654, point.getX(), DELTA);
        assertEquals(40.7829, point.getY(), DELTA);
        assertEquals(4326, point.getSRID());
    }

    @Test
    void convertLineStringReturnsTwoPoints() {
        String geoJson = "{\"type\":\"LineString\",\"coordinates\":"
                + "[[-73.9654,40.7829],[-73.9855,40.7580]]}";

        Geometry result = GeoJsonToJtsConverter.convert(geoJson);

        assertInstanceOf(LineString.class, result);
        LineString lineString = (LineString) result;
        assertEquals(2, lineString.getNumPoints());

        Coordinate start = lineString.getCoordinateN(0);
        assertEquals(-73.9654, start.getX(), DELTA);
        assertEquals(40.7829, start.getY(), DELTA);

        Coordinate end = lineString.getCoordinateN(1);
        assertEquals(-73.9855, end.getX(), DELTA);
        assertEquals(40.7580, end.getY(), DELTA);
    }

    @Test
    void convertPolygonWithNoHolesReturnsClosedRing() {
        String geoJson = "{\"type\":\"Polygon\",\"coordinates\":["
                + "[[-73.98,40.76],[-73.96,40.76],[-73.96,40.78],"
                + "[-73.98,40.78],[-73.98,40.76]]]}";

        Geometry result = GeoJsonToJtsConverter.convert(geoJson);

        assertInstanceOf(Polygon.class, result);
        Polygon polygon = (Polygon) result;
        assertEquals(5, polygon.getExteriorRing().getNumPoints());
        assertEquals(0, polygon.getNumInteriorRing());
    }

    @Test
    void convertPolygonWithHoleReturnsOneInteriorRing() {
        String geoJson = "{\"type\":\"Polygon\",\"coordinates\":["
                + "[[-73.98,40.76],[-73.96,40.76],[-73.96,40.78],"
                + "[-73.98,40.78],[-73.98,40.76]],"
                + "[[-73.975,40.765],[-73.965,40.765],[-73.965,40.775],"
                + "[-73.975,40.775],[-73.975,40.765]]]}";

        Geometry result = GeoJsonToJtsConverter.convert(geoJson);

        assertInstanceOf(Polygon.class, result);
        Polygon polygon = (Polygon) result;
        assertEquals(5, polygon.getExteriorRing().getNumPoints());
        assertEquals(1, polygon.getNumInteriorRing());
        assertEquals(5, polygon.getInteriorRingN(0).getNumPoints());
    }

    @Test
    void convertMultiPointReturnsTwoGeometries() {
        String geoJson = "{\"type\":\"MultiPoint\",\"coordinates\":"
                + "[[-73.9654,40.7829],[-73.9855,40.7580]]}";

        Geometry result = GeoJsonToJtsConverter.convert(geoJson);

        assertInstanceOf(MultiPoint.class, result);
        MultiPoint multiPoint = (MultiPoint) result;
        assertEquals(2, multiPoint.getNumGeometries());

        Point first = (Point) multiPoint.getGeometryN(0);
        assertEquals(-73.9654, first.getX(), DELTA);
        assertEquals(40.7829, first.getY(), DELTA);

        Point second = (Point) multiPoint.getGeometryN(1);
        assertEquals(-73.9855, second.getX(), DELTA);
        assertEquals(40.7580, second.getY(), DELTA);
    }

    @Test
    void convertMultiLineStringReturnsTwoGeometries() {
        String geoJson = "{\"type\":\"MultiLineString\",\"coordinates\":["
                + "[[-73.96,40.78],[-73.97,40.77]],"
                + "[[-73.98,40.76],[-73.99,40.75]]]}";

        Geometry result = GeoJsonToJtsConverter.convert(geoJson);

        assertInstanceOf(MultiLineString.class, result);
        MultiLineString multiLineString = (MultiLineString) result;
        assertEquals(2, multiLineString.getNumGeometries());

        LineString first = (LineString) multiLineString.getGeometryN(0);
        assertEquals(2, first.getNumPoints());

        LineString second = (LineString) multiLineString.getGeometryN(1);
        assertEquals(2, second.getNumPoints());
    }

    @Test
    void convertMultiPolygonReturnsTwoGeometries() {
        String geoJson = "{\"type\":\"MultiPolygon\",\"coordinates\":["
                + "[[[-73.98,40.76],[-73.96,40.76],[-73.96,40.78],"
                + "[-73.98,40.78],[-73.98,40.76]]],"
                + "[[[-73.95,40.73],[-73.93,40.73],[-73.93,40.75],"
                + "[-73.95,40.75],[-73.95,40.73]]]]}";

        Geometry result = GeoJsonToJtsConverter.convert(geoJson);

        assertInstanceOf(MultiPolygon.class, result);
        MultiPolygon multiPolygon = (MultiPolygon) result;
        assertEquals(2, multiPolygon.getNumGeometries());

        Polygon first = (Polygon) multiPolygon.getGeometryN(0);
        assertEquals(5, first.getExteriorRing().getNumPoints());

        Polygon second = (Polygon) multiPolygon.getGeometryN(1);
        assertEquals(5, second.getExteriorRing().getNumPoints());
    }

    @Test
    void convertGeometryCollectionReturnsCorrectSubGeometryTypes() {
        String geoJson = "{\"type\":\"GeometryCollection\",\"geometries\":["
                + "{\"type\":\"Point\",\"coordinates\":[-73.9654,40.7829]},"
                + "{\"type\":\"LineString\",\"coordinates\":"
                + "[[-73.96,40.78],[-73.97,40.77]]}]}";

        Geometry result = GeoJsonToJtsConverter.convert(geoJson);

        assertInstanceOf(GeometryCollection.class, result);
        GeometryCollection collection = (GeometryCollection) result;
        assertEquals(2, collection.getNumGeometries());

        assertInstanceOf(Point.class, collection.getGeometryN(0));
        assertInstanceOf(LineString.class, collection.getGeometryN(1));

        Point point = (Point) collection.getGeometryN(0);
        assertEquals(-73.9654, point.getX(), DELTA);
        assertEquals(40.7829, point.getY(), DELTA);
    }

    @Test
    void convertPointWith3DCoordinatesPreservesZValue() {
        String geoJson = "{\"type\":\"Point\",\"coordinates\":[-73.9654,40.7829,100.0]}";

        Geometry result = GeoJsonToJtsConverter.convert(geoJson);

        assertInstanceOf(Point.class, result);
        Point point = (Point) result;
        assertEquals(-73.9654, point.getX(), DELTA);
        assertEquals(40.7829, point.getY(), DELTA);
        assertEquals(100.0, point.getCoordinate().getZ(), DELTA);
    }

    @Test
    void convertNullStringThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                GeoJsonToJtsConverter.convert((String) null));
    }

    @Test
    void convertEmptyStringThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                GeoJsonToJtsConverter.convert(""));
    }

    @Test
    void convertUnknownTypeThrowsIllegalArgumentException() {
        String geoJson = "{\"type\":\"InvalidType\",\"coordinates\":[0,0]}";

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GeoJsonToJtsConverter.convert(geoJson));

        assertEquals("Unsupported GeoJSON type: InvalidType", exception.getMessage());
    }
}
