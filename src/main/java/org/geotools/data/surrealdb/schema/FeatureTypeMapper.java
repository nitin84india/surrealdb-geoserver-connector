package org.geotools.data.surrealdb.schema;

import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Domain service that builds GeoTools {@link SimpleFeatureType} from
 * {@link TableSchema} value objects. Maps SurrealDB field types to
 * Java/JTS attribute bindings using {@link GeometryFieldDetector}.
 */
public class FeatureTypeMapper {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureTypeMapper.class);

    private final int defaultSrid;

    public FeatureTypeMapper(int defaultSrid) {
        this.defaultSrid = defaultSrid;
    }

    /**
     * Builds a {@link SimpleFeatureType} from a {@link TableSchema}.
     *
     * @param schema the table schema to map
     * @return the GeoTools feature type
     */
    public SimpleFeatureType buildFeatureType(TableSchema schema) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(schema.getTableName());

        CoordinateReferenceSystem crs = decodeCrs();
        builder.setCRS(crs);

        // Always add an 'id' attribute for SurrealDB record IDs
        builder.add("id", String.class);

        boolean defaultGeometrySet = false;

        for (FieldSchema field : schema.getFields()) {
            String fieldName = field.getFieldName();
            String kind = field.getSurrealKind();

            if (GeometryFieldDetector.isGeometryKind(kind)) {
                Class<? extends Geometry> geomBinding = GeometryFieldDetector.mapGeometryBinding(kind);
                builder.add(fieldName, geomBinding);

                if (!defaultGeometrySet) {
                    builder.setDefaultGeometry(fieldName);
                    defaultGeometrySet = true;
                    LOG.debug("Set default geometry to '{}' ({})", fieldName, geomBinding.getSimpleName());
                }
            } else {
                Class<?> attrBinding = GeometryFieldDetector.mapAttributeBinding(kind);
                builder.add(fieldName, attrBinding);
                LOG.debug("Mapped field '{}' ({}) -> {}", fieldName, kind, attrBinding.getSimpleName());
            }
        }

        SimpleFeatureType featureType = builder.buildFeatureType();
        LOG.info("Built SimpleFeatureType '{}' with {} attributes (default geometry: {})",
                featureType.getTypeName(),
                featureType.getAttributeCount(),
                featureType.getGeometryDescriptor() != null
                        ? featureType.getGeometryDescriptor().getLocalName()
                        : "none");

        return featureType;
    }

    private CoordinateReferenceSystem decodeCrs() {
        try {
            return CRS.decode("EPSG:" + defaultSrid);
        } catch (FactoryException e) {
            throw new IllegalStateException("Failed to decode CRS for EPSG:" + defaultSrid, e);
        }
    }
}
