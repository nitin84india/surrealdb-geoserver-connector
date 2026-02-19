package org.geotools.data.surrealdb.schema;

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

/**
 * Static utility class for detecting and mapping SurrealDB geometry field types
 * to JTS Geometry classes, and mapping non-geometry SurrealDB types to Java classes.
 *
 * <p>SurrealDB stores geometry data using parameterized "geometry" kind strings such as
 * "geometry&lt;point&gt;", "geometry&lt;polygon&gt;", etc. This detector maps those strings
 * to their corresponding JTS geometry bindings for GeoTools FeatureType construction.</p>
 */
public final class GeometryFieldDetector {

    private GeometryFieldDetector() {
        // Utility class - prevent instantiation
    }

    /**
     * Strips the "option&lt;&gt;" wrapper from a SurrealDB kind string.
     * For example, "option&lt;string&gt;" becomes "string" and
     * "option&lt;geometry&lt;point&gt;&gt;" becomes "geometry&lt;point&gt;".
     *
     * @param kind the SurrealDB field kind string, may be null
     * @return the unwrapped kind, or the original if not wrapped
     */
    static String unwrapOption(String kind) {
        if (kind != null && kind.startsWith("option<") && kind.endsWith(">")) {
            return kind.substring(7, kind.length() - 1);
        }
        return kind;
    }

    /**
     * Returns true if the given SurrealDB kind string represents a geometry type.
     * A kind is considered geometry if it is non-null and starts with "geometry" (case-sensitive).
     * Also recognizes option-wrapped geometry types like "option&lt;geometry&lt;point&gt;&gt;".
     *
     * @param kind the SurrealDB field kind string, may be null
     * @return true if the kind represents a geometry type
     */
    public static boolean isGeometryKind(String kind) {
        String unwrapped = unwrapOption(kind);
        return unwrapped != null && unwrapped.startsWith("geometry");
    }

    /**
     * Maps a SurrealDB geometry kind string to the corresponding JTS Geometry class.
     *
     * <table>
     *   <tr><th>SurrealDB Kind</th><th>JTS Class</th></tr>
     *   <tr><td>geometry&lt;point&gt;</td><td>Point</td></tr>
     *   <tr><td>geometry&lt;line&gt;</td><td>LineString</td></tr>
     *   <tr><td>geometry&lt;polygon&gt;</td><td>Polygon</td></tr>
     *   <tr><td>geometry&lt;multipoint&gt;</td><td>MultiPoint</td></tr>
     *   <tr><td>geometry&lt;multiline&gt;</td><td>MultiLineString</td></tr>
     *   <tr><td>geometry&lt;multipolygon&gt;</td><td>MultiPolygon</td></tr>
     *   <tr><td>geometry&lt;collection&gt;</td><td>GeometryCollection</td></tr>
     *   <tr><td>geometry&lt;feature&gt;</td><td>Geometry</td></tr>
     *   <tr><td>geometry (bare)</td><td>Geometry</td></tr>
     * </table>
     *
     * @param kind the SurrealDB geometry kind string (e.g. "geometry&lt;point&gt;")
     * @return the corresponding JTS Geometry class
     * @throws IllegalArgumentException if kind is null or does not start with "geometry"
     */
    public static Class<? extends Geometry> mapGeometryBinding(String kind) {
        String unwrapped = unwrapOption(kind);
        if (unwrapped == null || !unwrapped.startsWith("geometry")) {
            throw new IllegalArgumentException(
                    "Not a geometry kind: " + kind);
        }

        switch (unwrapped) {
            case "geometry<point>":
                return Point.class;
            case "geometry<line>":
                return LineString.class;
            case "geometry<polygon>":
                return Polygon.class;
            case "geometry<multipoint>":
                return MultiPoint.class;
            case "geometry<multiline>":
                return MultiLineString.class;
            case "geometry<multipolygon>":
                return MultiPolygon.class;
            case "geometry<collection>":
                return GeometryCollection.class;
            case "geometry<feature>":
                return Geometry.class;
            case "geometry":
                return Geometry.class;
            default:
                // Unknown parameterization, fall back to generic Geometry
                return Geometry.class;
        }
    }

    /**
     * Maps a non-geometry SurrealDB field type to its corresponding Java class.
     * Unknown types fall back to String.class.
     *
     * <table>
     *   <tr><th>SurrealDB Kind</th><th>Java Class</th></tr>
     *   <tr><td>string</td><td>String</td></tr>
     *   <tr><td>int</td><td>Long</td></tr>
     *   <tr><td>float</td><td>Double</td></tr>
     *   <tr><td>bool</td><td>Boolean</td></tr>
     *   <tr><td>datetime</td><td>Date</td></tr>
     *   <tr><td>number</td><td>Double</td></tr>
     *   <tr><td>decimal</td><td>BigDecimal</td></tr>
     *   <tr><td>duration</td><td>String</td></tr>
     *   <tr><td>object</td><td>String</td></tr>
     *   <tr><td>record</td><td>String</td></tr>
     *   <tr><td>array</td><td>String</td></tr>
     * </table>
     *
     * @param kind the SurrealDB field kind string
     * @return the corresponding Java class binding
     */
    public static Class<?> mapAttributeBinding(String kind) {
        if (kind == null) {
            return String.class;
        }

        String unwrapped = unwrapOption(kind);

        // Handle parameterized types (e.g., record<species>, array<string>)
        if (unwrapped.startsWith("record")) {
            return String.class;
        }
        if (unwrapped.startsWith("array")) {
            return String.class;
        }

        switch (unwrapped) {
            case "string":
                return String.class;
            case "int":
                return Long.class;
            case "float":
                return Double.class;
            case "bool":
                return Boolean.class;
            case "datetime":
                return Date.class;
            case "number":
                return Double.class;
            case "decimal":
                return BigDecimal.class;
            case "duration":
                return String.class;
            case "object":
                return String.class;
            default:
                return String.class;
        }
    }
}
