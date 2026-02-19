package org.geotools.data.surrealdb.schema;

import java.util.Objects;

/**
 * Immutable value object representing a single field in a SurrealDB table schema.
 * Holds the field name and its SurrealDB type kind (e.g. "geometry<point>", "string", "int").
 */
public final class FieldSchema {

    private final String fieldName;
    private final String surrealKind;

    /**
     * Creates a new FieldSchema.
     *
     * @param fieldName   the name of the field, must not be null
     * @param surrealKind the SurrealDB type kind string (e.g. "geometry<point>", "string"), must not be null
     * @throws NullPointerException if fieldName or surrealKind is null
     */
    public FieldSchema(String fieldName, String surrealKind) {
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName must not be null");
        this.surrealKind = Objects.requireNonNull(surrealKind, "surrealKind must not be null");
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getSurrealKind() {
        return surrealKind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldSchema that = (FieldSchema) o;
        return Objects.equals(fieldName, that.fieldName)
                && Objects.equals(surrealKind, that.surrealKind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, surrealKind);
    }

    @Override
    public String toString() {
        return "FieldSchema{fieldName='" + fieldName + "', surrealKind='" + surrealKind + "'}";
    }
}
