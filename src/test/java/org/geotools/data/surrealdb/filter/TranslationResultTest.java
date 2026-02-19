package org.geotools.data.surrealdb.filter;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TranslationResult}.
 */
class TranslationResultTest {

    @Test
    void includeSentinelHasEmptyClause() {
        assertTrue(TranslationResult.INCLUDE.isEmpty());
        assertEquals("", TranslationResult.INCLUDE.getWhereClause());
        assertTrue(TranslationResult.INCLUDE.getParams().isEmpty());
    }

    @Test
    void excludeSentinelHasAlwaysFalseClause() {
        assertFalse(TranslationResult.EXCLUDE.isEmpty());
        assertEquals("1 = 0", TranslationResult.EXCLUDE.getWhereClause());
        assertTrue(TranslationResult.EXCLUDE.getParams().isEmpty());
    }

    @Test
    void andWithIncludeReturnsOtherSide() {
        Map<String, Object> params = new HashMap<>();
        params.put("$p0", "test");
        TranslationResult result = new TranslationResult("name = $p0", params);

        TranslationResult combined = TranslationResult.INCLUDE.and(result);
        assertEquals("name = $p0", combined.getWhereClause());

        TranslationResult combined2 = result.and(TranslationResult.INCLUDE);
        assertEquals("name = $p0", combined2.getWhereClause());
    }

    @Test
    void andWithExcludeReturnsExclude() {
        Map<String, Object> params = new HashMap<>();
        params.put("$p0", "test");
        TranslationResult result = new TranslationResult("name = $p0", params);

        TranslationResult combined = result.and(TranslationResult.EXCLUDE);
        assertSame(TranslationResult.EXCLUDE, combined);
    }

    @Test
    void andCombinesTwoResults() {
        Map<String, Object> params1 = new HashMap<>();
        params1.put("$p0", "park");
        TranslationResult r1 = new TranslationResult("category = $p0", params1);

        Map<String, Object> params2 = new HashMap<>();
        params2.put("$p1", 4.0);
        TranslationResult r2 = new TranslationResult("rating > $p1", params2);

        TranslationResult combined = r1.and(r2);

        assertEquals("(category = $p0) AND (rating > $p1)", combined.getWhereClause());
        assertEquals("park", combined.getParams().get("$p0"));
        assertEquals(4.0, combined.getParams().get("$p1"));
    }

    @Test
    void orWithIncludeReturnsInclude() {
        Map<String, Object> params = new HashMap<>();
        params.put("$p0", "test");
        TranslationResult result = new TranslationResult("name = $p0", params);

        TranslationResult combined = result.or(TranslationResult.INCLUDE);
        assertTrue(combined.isEmpty());
    }

    @Test
    void orCombinesTwoResults() {
        Map<String, Object> params1 = new HashMap<>();
        params1.put("$p0", "park");
        TranslationResult r1 = new TranslationResult("category = $p0", params1);

        Map<String, Object> params2 = new HashMap<>();
        params2.put("$p1", "bridge");
        TranslationResult r2 = new TranslationResult("category = $p1", params2);

        TranslationResult combined = r1.or(r2);

        assertEquals("(category = $p0) OR (category = $p1)", combined.getWhereClause());
        assertEquals(2, combined.getParams().size());
    }

    @Test
    void notNegatesResult() {
        Map<String, Object> params = new HashMap<>();
        params.put("$p0", "park");
        TranslationResult result = new TranslationResult("category = $p0", params);

        TranslationResult negated = result.not();

        assertEquals("NOT (category = $p0)", negated.getWhereClause());
        assertEquals("park", negated.getParams().get("$p0"));
    }

    @Test
    void notIncludeReturnsExclude() {
        assertSame(TranslationResult.EXCLUDE, TranslationResult.INCLUDE.not());
    }

    @Test
    void notExcludeReturnsInclude() {
        assertSame(TranslationResult.INCLUDE, TranslationResult.EXCLUDE.not());
    }

    @Test
    void paramsAreImmutable() {
        Map<String, Object> params = new HashMap<>();
        params.put("$p0", "test");
        TranslationResult result = new TranslationResult("name = $p0", params);

        assertThrows(UnsupportedOperationException.class,
                () -> result.getParams().put("$p1", "illegal"));
    }

    @Test
    void orWithExcludeReturnsOtherSide() {
        Map<String, Object> params = new HashMap<>();
        params.put("$p0", "test");
        TranslationResult result = new TranslationResult("name = $p0", params);

        TranslationResult combined = TranslationResult.EXCLUDE.or(result);
        assertEquals("name = $p0", combined.getWhereClause());

        TranslationResult combined2 = result.or(TranslationResult.EXCLUDE);
        assertEquals("name = $p0", combined2.getWhereClause());
    }
}
