package org.geotools.data.surrealdb.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RecordId}.
 */
class RecordIdTest {

    @Test
    void validRecordIdCreatesSuccessfully() {
        RecordId id = new RecordId("species:neem");
        assertEquals("species:neem", id.getValue());
    }

    @Test
    void validRecordIdWithUnderscore() {
        RecordId id = new RecordId("tree_species:oak_123");
        assertEquals("tree_species:oak_123", id.getValue());
    }

    @Test
    void validRecordIdWithNumericId() {
        RecordId id = new RecordId("user:42");
        assertEquals("user:42", id.getValue());
    }

    @Test
    void validRecordIdWithComplexId() {
        RecordId id = new RecordId("table:some-complex-id-123");
        assertEquals("table:some-complex-id-123", id.getValue());
    }

    @Test
    void nullValueThrowsNullPointer() {
        assertThrows(NullPointerException.class, () -> new RecordId(null));
    }

    @Test
    void emptyStringThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RecordId(""));
    }

    @Test
    void missingColonThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RecordId("nocolon"));
    }

    @Test
    void semicolonInValueThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RecordId("table:id;DROP"));
    }

    @Test
    void whitespaceInValueThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RecordId("table:id with space"));
    }

    @Test
    void startsWithNumberThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RecordId("123table:id"));
    }

    @Test
    void equalsAndHashCode() {
        RecordId a = new RecordId("species:neem");
        RecordId b = new RecordId("species:neem");
        RecordId c = new RecordId("species:oak");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void toStringContainsValue() {
        RecordId id = new RecordId("species:neem");
        assertTrue(id.toString().contains("species:neem"));
    }
}
