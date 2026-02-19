package org.geotools.data.surrealdb.filter;

import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.data.surrealdb.schema.FieldSchema;
import org.geotools.data.surrealdb.schema.TableSchema;
import org.geotools.factory.CommonFactoryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SurrealQLFilterTranslator}.
 */
class SurrealQLFilterTranslatorTest {

    private static final FilterFactory FF = CommonFactoryFinder.getFilterFactory(null);
    private static final GeometryFactory GF =
            new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

    private SurrealQLFilterTranslator translator;

    @BeforeEach
    void setUp() {
        List<FieldSchema> fields = Arrays.asList(
                new FieldSchema("name", "string"),
                new FieldSchema("geometry", "geometry<point>"),
                new FieldSchema("category", "string"),
                new FieldSchema("rating", "float")
        );
        TableSchema schema = new TableSchema("poi", fields, true);
        translator = new SurrealQLFilterTranslator(schema, "geometry");
    }

    // --- INCLUDE / EXCLUDE ---

    @Test
    void translateIncludeReturnsInclude() {
        TranslationResult result = translator.translate(Filter.INCLUDE);
        assertTrue(result.isEmpty());
    }

    @Test
    void translateExcludeReturnsExclude() {
        TranslationResult result = translator.translate(Filter.EXCLUDE);
        assertEquals("1 = 0", result.getWhereClause());
    }

    @Test
    void translateNullReturnsInclude() {
        TranslationResult result = translator.translate(null);
        assertTrue(result.isEmpty());
    }

    // --- BBOX ---

    @Test
    void translateBBoxProducesIntersects() {
        Filter bbox = FF.bbox("geometry", -74.0, 40.7, -73.9, 40.8, "EPSG:4326");

        TranslationResult result = translator.translate(bbox);

        assertTrue(result.getWhereClause().contains("geometry INTERSECTS $p"));
        assertEquals(1, result.getParams().size());

        // Verify the parameter is a GeoJSON-like map (polygon)
        Map.Entry<String, Object> entry = result.getParams().entrySet().iterator().next();
        @SuppressWarnings("unchecked")
        Map<String, Object> geojson = (Map<String, Object>) entry.getValue();
        assertEquals("Polygon", geojson.get("type"));
    }

    // --- Spatial operators ---

    @Test
    void translateIntersectsProducesSurrealQLIntersects() {
        Polygon polygon = createTestPolygon();
        Filter filter = FF.intersects(FF.property("geometry"), FF.literal(polygon));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("geometry INTERSECTS $p"));
        assertEquals(1, result.getParams().size());
    }

    @Test
    void translateContainsProducesSurrealQLContains() {
        Polygon polygon = createTestPolygon();
        Filter filter = FF.contains(FF.property("geometry"), FF.literal(polygon));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("geometry CONTAINS $p"));
    }

    @Test
    void translateWithinProducesSurrealQLInside() {
        Polygon polygon = createTestPolygon();
        Filter filter = FF.within(FF.property("geometry"), FF.literal(polygon));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("geometry INSIDE $p"));
    }

    @Test
    void translateDisjointProducesSurrealQLOutside() {
        Polygon polygon = createTestPolygon();
        Filter filter = FF.disjoint(FF.property("geometry"), FF.literal(polygon));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("geometry OUTSIDE $p"));
    }

    @Test
    void translateDWithinProducesGeoDistance() {
        Point point = GF.createPoint(new Coordinate(-73.9654, 40.7829));
        Filter filter = FF.dwithin(FF.property("geometry"), FF.literal(point), 1000, "m");

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("geo::distance(geometry, $p"));
        assertTrue(result.getWhereClause().contains(") < $p"));
        assertEquals(2, result.getParams().size());
    }

    // --- Comparison operators ---

    @Test
    void translatePropertyIsEqualTo() {
        Filter filter = FF.equals(FF.property("category"), FF.literal("park"));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("category = $p"));
        assertTrue(result.getParams().containsValue("park"));
    }

    @Test
    void translatePropertyIsNotEqualTo() {
        Filter filter = FF.notEqual(FF.property("category"), FF.literal("park"));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("category != $p"));
    }

    @Test
    void translatePropertyIsLessThan() {
        Filter filter = FF.less(FF.property("rating"), FF.literal(4.0));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("rating < $p"));
        assertTrue(result.getParams().containsValue(4.0));
    }

    @Test
    void translatePropertyIsGreaterThan() {
        Filter filter = FF.greater(FF.property("rating"), FF.literal(4.0));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("rating > $p"));
    }

    @Test
    void translatePropertyIsLessThanOrEqual() {
        Filter filter = FF.lessOrEqual(FF.property("rating"), FF.literal(4.0));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("rating <= $p"));
    }

    @Test
    void translatePropertyIsGreaterThanOrEqual() {
        Filter filter = FF.greaterOrEqual(FF.property("rating"), FF.literal(4.0));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("rating >= $p"));
    }

    // --- Between ---

    @Test
    void translateBetweenProducesRangeClause() {
        Filter filter = FF.between(FF.property("rating"), FF.literal(3.0), FF.literal(5.0));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("rating >= $p"));
        assertTrue(result.getWhereClause().contains("AND rating <= $p"));
        assertEquals(2, result.getParams().size());
        assertTrue(result.getParams().containsValue(3.0));
        assertTrue(result.getParams().containsValue(5.0));
    }

    // --- Like ---

    @Test
    void translateLikeProducesStringMatchesRegex() {
        Filter filter = FF.like(FF.property("name"), "Central%", "%", "_", "\\");

        TranslationResult result = translator.translate(filter);

        assertTrue(result.getWhereClause().contains("string::matches(name, $p"),
                "LIKE should produce string::matches() function call, got: " + result.getWhereClause());
        String regex = (String) result.getParams().values().iterator().next();
        assertEquals("^Central.*$", regex);
    }

    // --- IsNull ---

    @Test
    void translateIsNullProducesIsNone() {
        Filter filter = FF.isNull(FF.property("category"));

        TranslationResult result = translator.translate(filter);

        assertEquals("category IS NONE", result.getWhereClause());
        assertTrue(result.getParams().isEmpty());
    }

    // --- Logical operators ---

    @Test
    void translateAndCombinesChildren() {
        Filter eq = FF.equals(FF.property("category"), FF.literal("park"));
        Filter gt = FF.greater(FF.property("rating"), FF.literal(4.0));
        Filter and = FF.and(eq, gt);

        TranslationResult result = translator.translate(and);

        assertTrue(result.getWhereClause().contains("AND"));
        assertTrue(result.getWhereClause().contains("category = $p"));
        assertTrue(result.getWhereClause().contains("rating > $p"));
        assertEquals(2, result.getParams().size());
    }

    @Test
    void translateOrCombinesChildren() {
        Filter eq1 = FF.equals(FF.property("category"), FF.literal("park"));
        Filter eq2 = FF.equals(FF.property("category"), FF.literal("bridge"));
        Filter or = FF.or(eq1, eq2);

        TranslationResult result = translator.translate(or);

        assertTrue(result.getWhereClause().contains("OR"));
        assertEquals(2, result.getParams().size());
    }

    @Test
    void translateNotNegatesChild() {
        Filter eq = FF.equals(FF.property("category"), FF.literal("park"));
        Filter not = FF.not(eq);

        TranslationResult result = translator.translate(not);

        assertTrue(result.getWhereClause().startsWith("NOT ("));
        assertTrue(result.getWhereClause().contains("category = $p"));
    }

    // --- Unsupported filters ---

    @Test
    void unsupportedFilterDegradesToInclude() {
        // Crosses is not supported
        Polygon polygon = createTestPolygon();
        Filter filter = FF.crosses(FF.property("geometry"), FF.literal(polygon));

        TranslationResult result = translator.translate(filter);

        assertTrue(result.isEmpty());
    }

    // --- Param uniqueness ---

    @Test
    void eachTranslationUsesUniqueParamNames() {
        Filter eq1 = FF.equals(FF.property("name"), FF.literal("A"));
        Filter eq2 = FF.equals(FF.property("category"), FF.literal("B"));

        TranslationResult r1 = translator.translate(eq1);
        TranslationResult r2 = translator.translate(eq2);

        // Params should have different names
        String param1 = r1.getParams().keySet().iterator().next();
        String param2 = r2.getParams().keySet().iterator().next();
        assertNotEquals(param1, param2);
    }

    // --- Like pattern conversion ---

    @Test
    void likeToRegexConvertsWildcards() {
        assertEquals("^Central.*$",
                SurrealQLFilterTranslator.likeToRegex("Central%", "%", "_", "\\"));
        assertEquals("^Te.t$",
                SurrealQLFilterTranslator.likeToRegex("Te_t", "%", "_", "\\"));
        assertEquals("^100%$",
                SurrealQLFilterTranslator.likeToRegex("100\\%", "%", "_", "\\"));
    }

    // --- Helpers ---

    private Polygon createTestPolygon() {
        Coordinate[] coords = {
                new Coordinate(-74.0, 40.7), new Coordinate(-73.9, 40.7),
                new Coordinate(-73.9, 40.8), new Coordinate(-74.0, 40.8),
                new Coordinate(-74.0, 40.7)
        };
        return GF.createPolygon(coords);
    }
}
