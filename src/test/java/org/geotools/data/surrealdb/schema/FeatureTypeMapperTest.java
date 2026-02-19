package org.geotools.data.surrealdb.schema;

import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.feature.type.GeometryDescriptor;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeatureTypeMapperTest {

    private FeatureTypeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FeatureTypeMapper(4326);
    }

    @Test
    void buildFeatureTypeWithGeometryAndAttributes() {
        List<FieldSchema> fields = Arrays.asList(
                new FieldSchema("name", "string"),
                new FieldSchema("geometry", "geometry<point>"),
                new FieldSchema("category", "string")
        );
        TableSchema schema = new TableSchema("poi", fields, true);

        SimpleFeatureType featureType = mapper.buildFeatureType(schema);

        assertEquals("poi", featureType.getTypeName());
        // id + name + geometry + category = 4 attributes
        assertEquals(4, featureType.getAttributeCount());
        assertNotNull(featureType.getDescriptor("id"));
        assertNotNull(featureType.getDescriptor("name"));
        assertNotNull(featureType.getDescriptor("geometry"));
        assertNotNull(featureType.getDescriptor("category"));
    }

    @Test
    void setsCorrectCrs() {
        List<FieldSchema> fields = List.of(
                new FieldSchema("geometry", "geometry<point>")
        );
        TableSchema schema = new TableSchema("test", fields, true);

        SimpleFeatureType featureType = mapper.buildFeatureType(schema);

        assertNotNull(featureType.getCoordinateReferenceSystem());
        assertTrue(CRS.equalsIgnoreMetadata(
                featureType.getCoordinateReferenceSystem(),
                featureType.getGeometryDescriptor().getCoordinateReferenceSystem()));
    }

    @Test
    void setsDefaultGeometryToFirstGeometryField() {
        List<FieldSchema> fields = Arrays.asList(
                new FieldSchema("location", "geometry<point>"),
                new FieldSchema("boundary", "geometry<polygon>"),
                new FieldSchema("name", "string")
        );
        TableSchema schema = new TableSchema("multi_geom", fields, true);

        SimpleFeatureType featureType = mapper.buildFeatureType(schema);

        GeometryDescriptor defaultGeom = featureType.getGeometryDescriptor();
        assertNotNull(defaultGeom);
        assertEquals("location", defaultGeom.getLocalName());
        assertEquals(Point.class, defaultGeom.getType().getBinding());
    }

    @Test
    void mapsAttributeTypesCorrectly() {
        List<FieldSchema> fields = Arrays.asList(
                new FieldSchema("geometry", "geometry<multipolygon>"),
                new FieldSchema("name", "string"),
                new FieldSchema("count", "int"),
                new FieldSchema("area", "float"),
                new FieldSchema("active", "bool"),
                new FieldSchema("created", "datetime")
        );
        TableSchema schema = new TableSchema("typed", fields, true);

        SimpleFeatureType featureType = mapper.buildFeatureType(schema);

        assertEquals(MultiPolygon.class, featureType.getDescriptor("geometry").getType().getBinding());
        assertEquals(String.class, featureType.getDescriptor("name").getType().getBinding());
        assertEquals(Long.class, featureType.getDescriptor("count").getType().getBinding());
        assertEquals(Double.class, featureType.getDescriptor("area").getType().getBinding());
        assertEquals(Boolean.class, featureType.getDescriptor("active").getType().getBinding());
        assertEquals(java.util.Date.class, featureType.getDescriptor("created").getType().getBinding());
    }

    @Test
    void alwaysIncludesIdAttribute() {
        List<FieldSchema> fields = List.of(
                new FieldSchema("geometry", "geometry<point>")
        );
        TableSchema schema = new TableSchema("simple", fields, true);

        SimpleFeatureType featureType = mapper.buildFeatureType(schema);

        AttributeDescriptor idAttr = featureType.getDescriptor("id");
        assertNotNull(idAttr);
        assertEquals(String.class, idAttr.getType().getBinding());
    }
}
