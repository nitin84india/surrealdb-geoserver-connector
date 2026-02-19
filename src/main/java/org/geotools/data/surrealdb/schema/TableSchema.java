package org.geotools.data.surrealdb.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable value object representing the schema of a SurrealDB table.
 * Contains the table name, its field definitions, and whether the table
 * uses SCHEMAFULL mode (strict schema enforcement).
 */
public final class TableSchema {

    private final String tableName;
    private final List<FieldSchema> fields;
    private final boolean schemafull;

    /**
     * Creates a new TableSchema.
     *
     * @param tableName  the name of the SurrealDB table, must not be null
     * @param fields     the list of field definitions, must not be null (defensive copy is stored)
     * @param schemafull true if the table is SCHEMAFULL, false if SCHEMALESS
     * @throws NullPointerException if tableName or fields is null
     */
    public TableSchema(String tableName, List<FieldSchema> fields, boolean schemafull) {
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
        this.schemafull = schemafull;
    }

    public String getTableName() {
        return tableName;
    }

    /**
     * Returns an unmodifiable list of field schemas for this table.
     *
     * @return unmodifiable list of fields
     */
    public List<FieldSchema> getFields() {
        return fields;
    }

    public boolean isSchemafull() {
        return schemafull;
    }

    /**
     * Returns the subset of fields whose SurrealDB kind represents a geometry type.
     * A field is considered a geometry field if its surrealKind starts with "geometry".
     *
     * @return list of geometry field schemas (may be empty, never null)
     */
    public List<FieldSchema> getGeometryFields() {
        return fields.stream()
                .filter(f -> GeometryFieldDetector.isGeometryKind(f.getSurrealKind()))
                .collect(Collectors.toList());
    }

    /**
     * Returns true if this table has at least one geometry field.
     *
     * @return true if geometry fields exist
     */
    public boolean hasGeometryField() {
        return !getGeometryFields().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TableSchema that = (TableSchema) o;
        return schemafull == that.schemafull
                && Objects.equals(tableName, that.tableName)
                && Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName, fields, schemafull);
    }

    @Override
    public String toString() {
        return "TableSchema{tableName='" + tableName + "'"
                + ", fields=" + fields
                + ", schemafull=" + schemafull + "}";
    }
}
