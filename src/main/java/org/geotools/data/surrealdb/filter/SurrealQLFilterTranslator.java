package org.geotools.data.surrealdb.filter;

import org.geotools.api.filter.And;
import org.geotools.api.filter.BinaryComparisonOperator;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.Not;
import org.geotools.api.filter.Or;
import org.geotools.api.filter.PropertyIsBetween;
import org.geotools.api.filter.PropertyIsEqualTo;
import org.geotools.api.filter.PropertyIsGreaterThan;
import org.geotools.api.filter.PropertyIsGreaterThanOrEqualTo;
import org.geotools.api.filter.PropertyIsLessThan;
import org.geotools.api.filter.PropertyIsLessThanOrEqualTo;
import org.geotools.api.filter.PropertyIsLike;
import org.geotools.api.filter.PropertyIsNotEqualTo;
import org.geotools.api.filter.PropertyIsNull;
import org.geotools.api.filter.expression.Expression;
import org.geotools.api.filter.expression.Literal;
import org.geotools.api.filter.expression.PropertyName;
import org.geotools.api.filter.spatial.BBOX;
import org.geotools.api.filter.spatial.Contains;
import org.geotools.api.filter.spatial.DWithin;
import org.geotools.api.filter.spatial.Disjoint;
import org.geotools.api.filter.spatial.Intersects;
import org.geotools.api.filter.spatial.Within;
import org.geotools.data.surrealdb.geometry.JtsToGeoJsonConverter;
import org.geotools.data.surrealdb.schema.GeometryFieldDetector;
import org.geotools.data.surrealdb.schema.TableSchema;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Translates OGC Filter objects to SurrealQL WHERE clauses with parameterized bindings.
 *
 * <p>Uses instanceof dispatch (simpler than full FilterVisitor for our supported filter scope).
 * Creates parameterized bindings ($p0, $p1, ...) for all literal values to prevent injection.</p>
 *
 * <p>Unsupported filter types (Crosses, Touches, Overlaps, Beyond) degrade to INCLUDE,
 * allowing GeoTools to apply them as in-memory post-filters.</p>
 */
public class SurrealQLFilterTranslator {

    private static final Logger LOG = LoggerFactory.getLogger(SurrealQLFilterTranslator.class);
    private static final GeometryFactory GF =
            new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

    private final TableSchema schema;
    private final String defaultGeometryField;
    private final AtomicInteger paramCounter = new AtomicInteger(0);

    /**
     * Creates a new filter translator for the given table schema.
     *
     * @param schema               the table schema for field validation
     * @param defaultGeometryField the default geometry field name
     */
    public SurrealQLFilterTranslator(TableSchema schema, String defaultGeometryField) {
        this.schema = schema;
        this.defaultGeometryField = defaultGeometryField;
    }

    /**
     * Translates an OGC Filter to a SurrealQL WHERE clause with bind parameters.
     *
     * @param filter the OGC filter (may be Filter.INCLUDE or Filter.EXCLUDE)
     * @return the translation result
     */
    public TranslationResult translate(Filter filter) {
        if (filter == null || filter == Filter.INCLUDE) {
            return TranslationResult.INCLUDE;
        }
        if (filter == Filter.EXCLUDE) {
            return TranslationResult.EXCLUDE;
        }

        // Logical operators
        if (filter instanceof And) {
            return translateAnd((And) filter);
        }
        if (filter instanceof Or) {
            return translateOr((Or) filter);
        }
        if (filter instanceof Not) {
            return translateNot((Not) filter);
        }

        // Spatial filters
        if (filter instanceof BBOX) {
            return translateBBox((BBOX) filter);
        }
        if (filter instanceof Intersects) {
            return translateSpatialBinary((Intersects) filter, "INTERSECTS");
        }
        if (filter instanceof Contains) {
            return translateSpatialBinary((Contains) filter, "CONTAINS");
        }
        if (filter instanceof Within) {
            return translateSpatialBinary((Within) filter, "INSIDE");
        }
        if (filter instanceof Disjoint) {
            return translateSpatialBinary((Disjoint) filter, "OUTSIDE");
        }
        if (filter instanceof DWithin) {
            return translateDWithin((DWithin) filter);
        }

        // Comparison filters
        if (filter instanceof PropertyIsEqualTo) {
            return translateComparison((PropertyIsEqualTo) filter, "=");
        }
        if (filter instanceof PropertyIsNotEqualTo) {
            return translateComparison((PropertyIsNotEqualTo) filter, "!=");
        }
        if (filter instanceof PropertyIsLessThan) {
            return translateComparison((PropertyIsLessThan) filter, "<");
        }
        if (filter instanceof PropertyIsGreaterThan) {
            return translateComparison((PropertyIsGreaterThan) filter, ">");
        }
        if (filter instanceof PropertyIsLessThanOrEqualTo) {
            return translateComparison((PropertyIsLessThanOrEqualTo) filter, "<=");
        }
        if (filter instanceof PropertyIsGreaterThanOrEqualTo) {
            return translateComparison((PropertyIsGreaterThanOrEqualTo) filter, ">=");
        }
        if (filter instanceof PropertyIsBetween) {
            return translateBetween((PropertyIsBetween) filter);
        }
        if (filter instanceof PropertyIsLike) {
            return translateLike((PropertyIsLike) filter);
        }
        if (filter instanceof PropertyIsNull) {
            return translateIsNull((PropertyIsNull) filter);
        }

        // Unsupported filters degrade to INCLUDE (GeoTools applies in-memory)
        LOG.debug("Unsupported filter type: {}, degrading to INCLUDE", filter.getClass().getSimpleName());
        return TranslationResult.INCLUDE;
    }

    private TranslationResult translateAnd(And and) {
        TranslationResult result = TranslationResult.INCLUDE;
        for (Filter child : and.getChildren()) {
            result = result.and(translate(child));
        }
        return result;
    }

    private TranslationResult translateOr(Or or) {
        TranslationResult result = TranslationResult.EXCLUDE;
        for (Filter child : or.getChildren()) {
            result = result.or(translate(child));
        }
        return result;
    }

    private TranslationResult translateNot(Not not) {
        return translate(not.getFilter()).not();
    }

    private TranslationResult translateBBox(BBOX bbox) {
        String geomField = resolveGeometryField(bbox.getExpression1());

        // Build the BBOX polygon from the bounds.
        // GeoServer's internal WMS rendering pipeline provides bounds in EAST_NORTH
        // (X=lon, Y=lat) order. Use bounds directly — no axis swap.
        // SurrealDB expects GeoJSON [lon, lat] which matches JTS X=lon, Y=lat.
        org.geotools.api.geometry.BoundingBox bounds = bbox.getBounds();
        Envelope env = new Envelope(
                bounds.getMinX(), bounds.getMaxX(),
                bounds.getMinY(), bounds.getMaxY());
        Geometry bboxPoly = GF.toGeometry(env);

        String paramName = nextParam();
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, JtsToGeoJsonConverter.convert(bboxPoly));

        return new TranslationResult(
                geomField + " INTERSECTS $" + paramName, params);
    }

    private TranslationResult translateSpatialBinary(
            org.geotools.api.filter.spatial.BinarySpatialOperator filter, String operator) {
        String geomField = resolveGeometryField(filter.getExpression1());
        Geometry geometry = extractGeometry(filter.getExpression2());

        if (geometry == null) {
            LOG.warn("Could not extract geometry from spatial filter, degrading to INCLUDE");
            return TranslationResult.INCLUDE;
        }

        String paramName = nextParam();
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, JtsToGeoJsonConverter.convert(geometry));

        return new TranslationResult(
                geomField + " " + operator + " $" + paramName, params);
    }

    private TranslationResult translateDWithin(DWithin dWithin) {
        String geomField = resolveGeometryField(dWithin.getExpression1());
        Geometry geometry = extractGeometry(dWithin.getExpression2());

        if (geometry == null) {
            LOG.warn("Could not extract geometry from DWithin filter, degrading to INCLUDE");
            return TranslationResult.INCLUDE;
        }

        String geomParam = nextParam();
        String distParam = nextParam();
        Map<String, Object> params = new HashMap<>();
        params.put(geomParam, JtsToGeoJsonConverter.convert(geometry));
        params.put(distParam, dWithin.getDistance());

        return new TranslationResult(
                "geo::distance(" + geomField + ", $" + geomParam + ") < $" + distParam, params);
    }

    private TranslationResult translateComparison(BinaryComparisonOperator filter, String operator) {
        String propertyName = extractPropertyName(filter.getExpression1());
        Object value = extractLiteralValue(filter.getExpression2());

        if (propertyName == null || value == null) {
            LOG.warn("Could not extract property/value from comparison filter, degrading to INCLUDE");
            return TranslationResult.INCLUDE;
        }

        String paramName = nextParam();
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, value);

        return new TranslationResult(
                propertyName + " " + operator + " $" + paramName, params);
    }

    private TranslationResult translateBetween(PropertyIsBetween between) {
        String propertyName = extractPropertyName(between.getExpression());
        Object lower = extractLiteralValue(between.getLowerBoundary());
        Object upper = extractLiteralValue(between.getUpperBoundary());

        if (propertyName == null || lower == null || upper == null) {
            LOG.warn("Could not extract between bounds, degrading to INCLUDE");
            return TranslationResult.INCLUDE;
        }

        String lowerParam = nextParam();
        String upperParam = nextParam();
        Map<String, Object> params = new HashMap<>();
        params.put(lowerParam, lower);
        params.put(upperParam, upper);

        return new TranslationResult(
                propertyName + " >= $" + lowerParam + " AND " + propertyName + " <= $" + upperParam,
                params);
    }

    private TranslationResult translateLike(PropertyIsLike like) {
        String propertyName = extractPropertyName(like.getExpression());
        if (propertyName == null) {
            return TranslationResult.INCLUDE;
        }

        // Convert LIKE pattern to regex
        String pattern = like.getLiteral();
        String wildCard = like.getWildCard();
        String singleChar = like.getSingleChar();
        String escape = like.getEscape();

        String regex = likeToRegex(pattern, wildCard, singleChar, escape);

        // SurrealDB v2.x: the ~ operator is a fuzzy/contains match, NOT a regex operator.
        // Use string::matches(field, pattern) for proper regex matching.
        String paramName = nextParam();
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, regex);

        return new TranslationResult(
                "string::matches(" + propertyName + ", $" + paramName + ")", params);
    }

    private TranslationResult translateIsNull(PropertyIsNull isNull) {
        String propertyName = extractPropertyName(isNull.getExpression());
        if (propertyName == null) {
            return TranslationResult.INCLUDE;
        }

        return new TranslationResult(propertyName + " IS NONE", new HashMap<>());
    }

    private String resolveGeometryField(Expression expression) {
        if (expression instanceof PropertyName) {
            String name = ((PropertyName) expression).getPropertyName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return defaultGeometryField;
    }

    private Geometry extractGeometry(Expression expression) {
        if (expression instanceof Literal) {
            Object value = ((Literal) expression).getValue();
            if (value instanceof Geometry) {
                return (Geometry) value;
            }
        }
        return null;
    }

    private String extractPropertyName(Expression expression) {
        if (expression instanceof PropertyName) {
            return ((PropertyName) expression).getPropertyName();
        }
        return null;
    }

    private Object extractLiteralValue(Expression expression) {
        if (expression instanceof Literal) {
            return ((Literal) expression).getValue();
        }
        return null;
    }

    private String nextParam() {
        return "p" + paramCounter.getAndIncrement();
    }

    /**
     * Converts an OGC LIKE pattern to a regex pattern.
     *
     * @param pattern    the LIKE pattern
     * @param wildCard   the wildcard character (e.g., "%")
     * @param singleChar the single character match (e.g., "_")
     * @param escape     the escape character (e.g., "\\")
     * @return the equivalent regex pattern
     */
    static String likeToRegex(String pattern, String wildCard, String singleChar, String escape) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            String ch = String.valueOf(pattern.charAt(i));

            if (escape != null && ch.equals(escape) && i + 1 < pattern.length()) {
                // Escaped character - take the next character literally
                i++;
                regex.append(escapeRegex(String.valueOf(pattern.charAt(i))));
            } else if (ch.equals(wildCard)) {
                regex.append(".*");
            } else if (ch.equals(singleChar)) {
                regex.append(".");
            } else {
                regex.append(escapeRegex(ch));
            }
        }
        regex.append("$");
        return regex.toString();
    }

    private static String escapeRegex(String s) {
        return s.replaceAll("([.\\\\+*?\\[^\\]$(){}=!<>|:\\-])", "\\\\$1");
    }
}
