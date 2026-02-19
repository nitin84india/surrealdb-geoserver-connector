package org.geotools.data.surrealdb.filter;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable wrapper signaling that a string value should be emitted as a raw
 * SurrealDB record ID (unquoted) in LET statements.
 *
 * <p>SurrealDB {@code record<X>} fields store record references as IDs like
 * {@code species:neem}. When filtering on such fields, the value must be emitted
 * unquoted so SurrealDB interprets it as a record reference rather than a string literal.</p>
 *
 * <p>Includes format validation to prevent injection: the value must match
 * {@code ^[a-zA-Z_][a-zA-Z0-9_]*:[^\s;]+$}.</p>
 */
public final class RecordId {

    /**
     * Pattern for valid SurrealDB record IDs: {@code table:id} where table starts
     * with a letter or underscore, and the id portion contains no whitespace or semicolons.
     */
    private static final Pattern VALID_RECORD_ID =
            Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*:[^\\s;]+$");

    private final String value;

    /**
     * Creates a new RecordId wrapper.
     *
     * @param value the raw record ID string (e.g. "species:neem"), must not be null
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if value does not match the expected record ID format
     */
    public RecordId(String value) {
        Objects.requireNonNull(value, "RecordId value must not be null");
        if (!VALID_RECORD_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid record ID format: '" + value + "'. "
                            + "Expected format: table:id (e.g. 'species:neem')");
        }
        this.value = value;
    }

    /**
     * Returns the raw record ID value, suitable for unquoted emission in SurrealQL.
     *
     * @return the record ID string
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordId recordId = (RecordId) o;
        return Objects.equals(value, recordId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "RecordId{" + value + "}";
    }
}
