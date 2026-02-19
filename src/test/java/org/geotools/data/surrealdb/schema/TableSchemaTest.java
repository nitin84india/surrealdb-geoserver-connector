package org.geotools.data.surrealdb.schema;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableSchemaTest {

    @Test
    void constructionStoresValuesCorrectly() {
        List<FieldSchema> fields = Arrays.asList(
                new FieldSchema("name", "string"),
                new FieldSchema("location", "geometry<point>")
        );

        TableSchema schema = new TableSchema("cities", fields, true);

        assertEquals("cities", schema.getTableName());
        assertEquals(2, schema.getFields().size());
        assertTrue(schema.isSchemafull());
    }

    @Test
    void hasGeometryFieldReturnsTrueWhenGeometryFieldPresent() {
        List<FieldSchema> fields = Arrays.asList(
                new FieldSchema("name", "string"),
                new FieldSchema("location", "geometry<point>")
        );

        TableSchema schema = new TableSchema("cities", fields, true);

        assertTrue(schema.hasGeometryField());
    }

    @Test
    void hasGeometryFieldReturnsFalseWhenNoGeometryFields() {
        List<FieldSchema> fields = Arrays.asList(
                new FieldSchema("name", "string"),
                new FieldSchema("age", "int")
        );

        TableSchema schema = new TableSchema("users", fields, false);

        assertFalse(schema.hasGeometryField());
    }

    @Test
    void getGeometryFieldsReturnsOnlyGeometryFields() {
        FieldSchema nameField = new FieldSchema("name", "string");
        FieldSchema locationField = new FieldSchema("location", "geometry<point>");
        FieldSchema ageField = new FieldSchema("age", "int");
        FieldSchema boundaryField = new FieldSchema("boundary", "geometry<polygon>");

        List<FieldSchema> fields = Arrays.asList(nameField, locationField, ageField, boundaryField);
        TableSchema schema = new TableSchema("places", fields, true);

        List<FieldSchema> geometryFields = schema.getGeometryFields();

        assertEquals(2, geometryFields.size());
        assertTrue(geometryFields.contains(locationField));
        assertTrue(geometryFields.contains(boundaryField));
        assertFalse(geometryFields.contains(nameField));
        assertFalse(geometryFields.contains(ageField));
    }

    @Test
    void fieldsListIsUnmodifiable() {
        List<FieldSchema> fields = Arrays.asList(
                new FieldSchema("name", "string")
        );

        TableSchema schema = new TableSchema("test", fields, false);

        assertThrows(UnsupportedOperationException.class, () ->
                schema.getFields().add(new FieldSchema("extra", "int"))
        );
    }

    @Test
    void constructorMakesDefensiveCopyOfFieldsList() {
        List<FieldSchema> mutableFields = new java.util.ArrayList<>();
        mutableFields.add(new FieldSchema("name", "string"));

        TableSchema schema = new TableSchema("test", mutableFields, false);

        // Mutating the original list should not affect the schema
        mutableFields.add(new FieldSchema("extra", "int"));

        assertEquals(1, schema.getFields().size());
    }

    @Test
    void equalityAndInequality() {
        List<FieldSchema> fields = Arrays.asList(
                new FieldSchema("name", "string"),
                new FieldSchema("location", "geometry<point>")
        );

        TableSchema a = new TableSchema("cities", fields, true);
        TableSchema b = new TableSchema("cities", fields, true);
        TableSchema differentName = new TableSchema("towns", fields, true);
        TableSchema differentSchemafull = new TableSchema("cities", fields, false);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, differentName);
        assertNotEquals(a, differentSchemafull);
        assertNotEquals(a, null);
    }

    @Test
    void emptyFieldsList() {
        TableSchema schema = new TableSchema("empty_table", Collections.emptyList(), false);

        assertEquals(0, schema.getFields().size());
        assertFalse(schema.hasGeometryField());
        assertTrue(schema.getGeometryFields().isEmpty());
    }

    @Test
    void toStringContainsTableName() {
        TableSchema schema = new TableSchema("cities", Collections.emptyList(), true);
        String str = schema.toString();

        assertTrue(str.contains("cities"));
        assertTrue(str.contains("TableSchema"));
        assertTrue(str.contains("schemafull=true"));
    }

    @Test
    void nullTableNameThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new TableSchema(null, Collections.emptyList(), false)
        );
    }

    @Test
    void nullFieldsListThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new TableSchema("test", null, false)
        );
    }
}
