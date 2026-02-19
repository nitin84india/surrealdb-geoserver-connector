package org.geotools.data.surrealdb.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldSchemaTest {

    @Test
    void constructionStoresValuesCorrectly() {
        FieldSchema field = new FieldSchema("location", "geometry<point>");

        assertEquals("location", field.getFieldName());
        assertEquals("geometry<point>", field.getSurrealKind());
    }

    @Test
    void equalsReturnsTrueForSameValues() {
        FieldSchema a = new FieldSchema("name", "string");
        FieldSchema b = new FieldSchema("name", "string");

        assertEquals(a, b);
        assertEquals(a, a); // reflexive
    }

    @Test
    void equalsReturnsFalseForDifferentValues() {
        FieldSchema a = new FieldSchema("name", "string");
        FieldSchema differentName = new FieldSchema("title", "string");
        FieldSchema differentKind = new FieldSchema("name", "int");

        assertNotEquals(a, differentName);
        assertNotEquals(a, differentKind);
        assertNotEquals(a, null);
        assertNotEquals(a, "not a FieldSchema");
    }

    @Test
    void hashCodeConsistentWithEquals() {
        FieldSchema a = new FieldSchema("location", "geometry<polygon>");
        FieldSchema b = new FieldSchema("location", "geometry<polygon>");
        FieldSchema c = new FieldSchema("other", "geometry<polygon>");

        assertEquals(a.hashCode(), b.hashCode());
        // Not strictly required by contract, but different objects should ideally differ
        assertNotEquals(a.hashCode(), c.hashCode());
    }

    @Test
    void nullFieldNameThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new FieldSchema(null, "string"));
    }

    @Test
    void nullSurrealKindThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new FieldSchema("name", null));
    }

    @Test
    void toStringContainsFieldValues() {
        FieldSchema field = new FieldSchema("name", "string");
        String str = field.toString();

        assertTrue(str.contains("name"));
        assertTrue(str.contains("string"));
        assertTrue(str.contains("FieldSchema"));
    }
}
