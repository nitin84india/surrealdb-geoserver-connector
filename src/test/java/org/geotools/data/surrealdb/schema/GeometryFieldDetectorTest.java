package org.geotools.data.surrealdb.schema;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class GeometryFieldDetectorTest {

    // --- isGeometryKind tests ---

    @Test
    void isGeometryKindReturnsTrueForGeometryPoint() {
        assertTrue(GeometryFieldDetector.isGeometryKind("geometry<point>"));
    }

    @Test
    void isGeometryKindReturnsTrueForGeometryLine() {
        assertTrue(GeometryFieldDetector.isGeometryKind("geometry<line>"));
    }

    @Test
    void isGeometryKindReturnsTrueForBareGeometry() {
        assertTrue(GeometryFieldDetector.isGeometryKind("geometry"));
    }

    @Test
    void isGeometryKindReturnsTrueForGeometryFeature() {
        assertTrue(GeometryFieldDetector.isGeometryKind("geometry<feature>"));
    }

    @Test
    void isGeometryKindReturnsFalseForString() {
        assertFalse(GeometryFieldDetector.isGeometryKind("string"));
    }

    @Test
    void isGeometryKindReturnsFalseForInt() {
        assertFalse(GeometryFieldDetector.isGeometryKind("int"));
    }

    @Test
    void isGeometryKindReturnsFalseForNull() {
        assertFalse(GeometryFieldDetector.isGeometryKind(null));
    }

    // --- mapGeometryBinding tests ---

    @Test
    void mapGeometryBindingPointReturnsPointClass() {
        assertEquals(Point.class, GeometryFieldDetector.mapGeometryBinding("geometry<point>"));
    }

    @Test
    void mapGeometryBindingLineReturnsLineStringClass() {
        assertEquals(LineString.class, GeometryFieldDetector.mapGeometryBinding("geometry<line>"));
    }

    @Test
    void mapGeometryBindingPolygonReturnsPolygonClass() {
        assertEquals(Polygon.class, GeometryFieldDetector.mapGeometryBinding("geometry<polygon>"));
    }

    @Test
    void mapGeometryBindingMultipointReturnsMultiPointClass() {
        assertEquals(MultiPoint.class, GeometryFieldDetector.mapGeometryBinding("geometry<multipoint>"));
    }

    @Test
    void mapGeometryBindingMultilineReturnsMultiLineStringClass() {
        assertEquals(MultiLineString.class, GeometryFieldDetector.mapGeometryBinding("geometry<multiline>"));
    }

    @Test
    void mapGeometryBindingMultipolygonReturnsMultiPolygonClass() {
        assertEquals(MultiPolygon.class, GeometryFieldDetector.mapGeometryBinding("geometry<multipolygon>"));
    }

    @Test
    void mapGeometryBindingCollectionReturnsGeometryCollectionClass() {
        assertEquals(GeometryCollection.class, GeometryFieldDetector.mapGeometryBinding("geometry<collection>"));
    }

    @Test
    void mapGeometryBindingFeatureReturnsGeometryClass() {
        assertEquals(Geometry.class, GeometryFieldDetector.mapGeometryBinding("geometry<feature>"));
    }

    @Test
    void mapGeometryBindingBareGeometryReturnsGeometryClass() {
        assertEquals(Geometry.class, GeometryFieldDetector.mapGeometryBinding("geometry"));
    }

    @Test
    void mapGeometryBindingThrowsForNonGeometryKind() {
        assertThrows(IllegalArgumentException.class, () ->
                GeometryFieldDetector.mapGeometryBinding("string")
        );
    }

    @Test
    void mapGeometryBindingThrowsForNull() {
        assertThrows(IllegalArgumentException.class, () ->
                GeometryFieldDetector.mapGeometryBinding(null)
        );
    }

    // --- mapAttributeBinding tests ---

    @Test
    void mapAttributeBindingStringReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding("string"));
    }

    @Test
    void mapAttributeBindingIntReturnsLongClass() {
        assertEquals(Long.class, GeometryFieldDetector.mapAttributeBinding("int"));
    }

    @Test
    void mapAttributeBindingFloatReturnsDoubleClass() {
        assertEquals(Double.class, GeometryFieldDetector.mapAttributeBinding("float"));
    }

    @Test
    void mapAttributeBindingBoolReturnsBooleanClass() {
        assertEquals(Boolean.class, GeometryFieldDetector.mapAttributeBinding("bool"));
    }

    @Test
    void mapAttributeBindingDatetimeReturnsDateClass() {
        assertEquals(Date.class, GeometryFieldDetector.mapAttributeBinding("datetime"));
    }

    @Test
    void mapAttributeBindingNumberReturnsDoubleClass() {
        assertEquals(Double.class, GeometryFieldDetector.mapAttributeBinding("number"));
    }

    @Test
    void mapAttributeBindingDecimalReturnsBigDecimalClass() {
        assertEquals(BigDecimal.class, GeometryFieldDetector.mapAttributeBinding("decimal"));
    }

    @Test
    void mapAttributeBindingDurationReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding("duration"));
    }

    @Test
    void mapAttributeBindingObjectReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding("object"));
    }

    @Test
    void mapAttributeBindingRecordReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding("record"));
    }

    @Test
    void mapAttributeBindingArrayReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding("array"));
    }

    @Test
    void mapAttributeBindingUnknownKindReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding("something_unknown"));
    }

    @Test
    void mapAttributeBindingNullReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding(null));
    }

    // --- option<> unwrapping tests ---

    @Test
    void isGeometryKindReturnsTrueForOptionGeometryPoint() {
        assertTrue(GeometryFieldDetector.isGeometryKind("option<geometry<point>>"));
    }

    @Test
    void isGeometryKindReturnsFalseForOptionString() {
        assertFalse(GeometryFieldDetector.isGeometryKind("option<string>"));
    }

    @Test
    void isGeometryKindReturnsFalseForOptionFloat() {
        assertFalse(GeometryFieldDetector.isGeometryKind("option<float>"));
    }

    @Test
    void mapGeometryBindingOptionGeometryPointReturnsPointClass() {
        assertEquals(Point.class, GeometryFieldDetector.mapGeometryBinding("option<geometry<point>>"));
    }

    @Test
    void mapGeometryBindingOptionGeometryPolygonReturnsPolygonClass() {
        assertEquals(Polygon.class, GeometryFieldDetector.mapGeometryBinding("option<geometry<polygon>>"));
    }

    @Test
    void mapGeometryBindingOptionBareGeometryReturnsGeometryClass() {
        assertEquals(Geometry.class, GeometryFieldDetector.mapGeometryBinding("option<geometry>"));
    }

    @Test
    void mapAttributeBindingOptionStringReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding("option<string>"));
    }

    @Test
    void mapAttributeBindingOptionFloatReturnsDoubleClass() {
        assertEquals(Double.class, GeometryFieldDetector.mapAttributeBinding("option<float>"));
    }

    @Test
    void mapAttributeBindingOptionIntReturnsLongClass() {
        assertEquals(Long.class, GeometryFieldDetector.mapAttributeBinding("option<int>"));
    }

    @Test
    void mapAttributeBindingOptionBoolReturnsBooleanClass() {
        assertEquals(Boolean.class, GeometryFieldDetector.mapAttributeBinding("option<bool>"));
    }

    @Test
    void mapAttributeBindingOptionDatetimeReturnsDateClass() {
        assertEquals(Date.class, GeometryFieldDetector.mapAttributeBinding("option<datetime>"));
    }

    // --- parameterized record<X> and array<X> tests ---

    @Test
    void mapAttributeBindingRecordWithTableReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding("record<species>"));
    }

    @Test
    void mapAttributeBindingRecordWithProjectReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding("record<project>"));
    }

    @Test
    void mapAttributeBindingArrayWithStringReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding("array<string>"));
    }

    @Test
    void mapAttributeBindingArrayWithRecordReturnsStringClass() {
        assertEquals(String.class, GeometryFieldDetector.mapAttributeBinding("array<record<ward>>"));
    }
}
