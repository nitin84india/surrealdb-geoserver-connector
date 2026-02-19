package org.geotools.data.surrealdb.filter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable value object representing a translated SurrealQL WHERE clause fragment
 * and its associated bind parameter map.
 *
 * <p>Used as the output of {@link SurrealQLFilterTranslator} to compose parameterized
 * query fragments that can be safely combined with AND/OR/NOT operators.</p>
 */
public final class TranslationResult {

    /** Sentinel: include all records (empty WHERE clause). */
    public static final TranslationResult INCLUDE = new TranslationResult("", Collections.emptyMap());

    /** Sentinel: exclude all records (always-false WHERE clause). */
    public static final TranslationResult EXCLUDE = new TranslationResult("1 = 0", Collections.emptyMap());

    private final String whereClause;
    private final Map<String, Object> params;

    /**
     * Creates a new TranslationResult.
     *
     * @param whereClause the SurrealQL WHERE clause fragment (without "WHERE" keyword)
     * @param params      the bind parameters referenced by the clause
     */
    public TranslationResult(String whereClause, Map<String, Object> params) {
        this.whereClause = whereClause;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /**
     * @return the SurrealQL WHERE clause fragment (without "WHERE" keyword)
     */
    public String getWhereClause() {
        return whereClause;
    }

    /**
     * @return unmodifiable map of bind parameters
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * @return true if this result represents no filter condition (include all)
     */
    public boolean isEmpty() {
        return whereClause.isEmpty();
    }

    /**
     * Combines this result with another using AND.
     * If either side is INCLUDE, the other side is returned.
     * If either side is EXCLUDE, EXCLUDE is returned.
     *
     * @param other the other result to AND with
     * @return the combined result
     */
    public TranslationResult and(TranslationResult other) {
        if (this.isEmpty()) return other;
        if (other.isEmpty()) return this;
        if (this == EXCLUDE || other == EXCLUDE) return EXCLUDE;

        Map<String, Object> mergedParams = new LinkedHashMap<>(this.params);
        mergedParams.putAll(other.params);
        return new TranslationResult(
                "(" + this.whereClause + ") AND (" + other.whereClause + ")",
                mergedParams);
    }

    /**
     * Combines this result with another using OR.
     * If either side is INCLUDE, INCLUDE is returned.
     * If either side is EXCLUDE, the other side is returned.
     *
     * @param other the other result to OR with
     * @return the combined result
     */
    public TranslationResult or(TranslationResult other) {
        if (this.isEmpty() || other.isEmpty()) return INCLUDE;
        if (this == EXCLUDE) return other;
        if (other == EXCLUDE) return this;

        Map<String, Object> mergedParams = new LinkedHashMap<>(this.params);
        mergedParams.putAll(other.params);
        return new TranslationResult(
                "(" + this.whereClause + ") OR (" + other.whereClause + ")",
                mergedParams);
    }

    /**
     * Negates this result using NOT.
     * INCLUDE becomes EXCLUDE and vice versa.
     *
     * @return the negated result
     */
    public TranslationResult not() {
        if (this.isEmpty()) return EXCLUDE;
        if (this == EXCLUDE) return INCLUDE;

        return new TranslationResult("NOT (" + this.whereClause + ")", this.params);
    }

    @Override
    public String toString() {
        return "TranslationResult{whereClause='" + whereClause + "', params=" + params + "}";
    }
}
