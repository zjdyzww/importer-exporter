/*
 * 3D City Database - The Open Source CityGML Database
 * http://www.3dcitydb.org/
 *
 * Copyright 2013 - 2019
 * Chair of Geoinformatics
 * Technical University of Munich, Germany
 * https://www.gis.bgu.tum.de/
 *
 * The 3D City Database is jointly developed with the following
 * cooperation partners:
 *
 * virtualcitySYSTEMS GmbH, Berlin <http://www.virtualcitysystems.de/>
 * M.O.S.S. Computer Grafik Systeme GmbH, Taufkirchen <http://www.moss.de/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.citydb.citygml.exporter.database.content;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.citydb.citygml.exporter.CityGMLExportException;
import org.citydb.citygml.exporter.util.AttributeValueSplitter;
import org.citydb.citygml.exporter.util.AttributeValueSplitter.SplitValue;
import org.citydb.config.geometry.GeometryObject;
import org.citydb.database.schema.TableEnum;
import org.citydb.database.schema.mapping.FeatureType;
import org.citydb.query.filter.lod.LodFilter;
import org.citydb.query.filter.lod.LodIterator;
import org.citydb.query.filter.projection.CombinedProjectionFilter;
import org.citydb.query.filter.projection.ProjectionFilter;
import org.citygml4j.model.citygml.core.ImplicitGeometry;
import org.citygml4j.model.citygml.core.ImplicitRepresentationProperty;
import org.citygml4j.model.citygml.vegetation.SolitaryVegetationObject;
import org.citygml4j.model.gml.basicTypes.Code;
import org.citygml4j.model.gml.geometry.AbstractGeometry;
import org.citygml4j.model.gml.geometry.GeometryProperty;
import org.citygml4j.model.gml.measures.Length;
import org.citygml4j.model.module.citygml.CityGMLModuleType;

import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.select.Select;

public class DBSolitaryVegetatObject extends AbstractFeatureExporter<SolitaryVegetationObject> {
	private DBSurfaceGeometry geometryExporter;
	private DBCityObject cityObjectExporter;
	private DBImplicitGeometry implicitGeometryExporter;
	private GMLConverter gmlConverter;

	private String vegetationModule;
	private LodFilter lodFilter;
	private AttributeValueSplitter valueSplitter;
	private boolean hasObjectClassIdColumn;
	private Set<String> adeHookTables;

	public DBSolitaryVegetatObject(Connection connection, CityGMLExportManager exporter) throws CityGMLExportException, SQLException {
		super(SolitaryVegetationObject.class, connection, exporter);

		CombinedProjectionFilter projectionFilter = exporter.getCombinedProjectionFilter(TableEnum.SOLITARY_VEGETAT_OBJECT.getName());
		vegetationModule = exporter.getTargetCityGMLVersion().getCityGMLModule(CityGMLModuleType.VEGETATION).getNamespaceURI();
		lodFilter = exporter.getLodFilter();
		String schema = exporter.getDatabaseAdapter().getConnectionDetails().getSchema();
		hasObjectClassIdColumn = exporter.getDatabaseAdapter().getConnectionMetaData().getCityDBVersion().compareTo(4, 0, 0) >= 0;

		table = new Table(TableEnum.SOLITARY_VEGETAT_OBJECT.getName(), schema);
		select = new Select().addProjection(table.getColumn("id"));
		if (hasObjectClassIdColumn) select.addProjection(table.getColumn("objectclass_id"));
		if (projectionFilter.containsProperty("class", vegetationModule)) select.addProjection(table.getColumn("class"), table.getColumn("class_codespace"));
		if (projectionFilter.containsProperty("function", vegetationModule)) select.addProjection(table.getColumn("function"), table.getColumn("function_codespace"));
		if (projectionFilter.containsProperty("usage", vegetationModule)) select.addProjection(table.getColumn("usage"), table.getColumn("usage_codespace"));
		if (projectionFilter.containsProperty("species", vegetationModule)) select.addProjection(table.getColumn("species"), table.getColumn("species_codespace"));
		if (projectionFilter.containsProperty("height", vegetationModule)) select.addProjection(table.getColumn("height"), table.getColumn("height_unit"));
		if (projectionFilter.containsProperty("trunkDiameter", vegetationModule)) select.addProjection(table.getColumn("trunk_diameter"), table.getColumn("trunk_diameter_unit"));
		if (projectionFilter.containsProperty("crownDiameter", vegetationModule)) select.addProjection(table.getColumn("crown_diameter"), table.getColumn("crown_diameter_unit"));
		if (projectionFilter.containsProperty("lod1Geometry", vegetationModule)) select.addProjection(table.getColumn("lod1_brep_id"), exporter.getGeometryColumn(table.getColumn("lod1_other_geom")));
		if (projectionFilter.containsProperty("lod2Geometry", vegetationModule)) select.addProjection(table.getColumn("lod2_brep_id"), exporter.getGeometryColumn(table.getColumn("lod2_other_geom")));
		if (projectionFilter.containsProperty("lod3Geometry", vegetationModule)) select.addProjection(table.getColumn("lod3_brep_id"), exporter.getGeometryColumn(table.getColumn("lod3_other_geom")));
		if (projectionFilter.containsProperty("lod4Geometry", vegetationModule)) select.addProjection(table.getColumn("lod4_brep_id"), exporter.getGeometryColumn(table.getColumn("lod4_other_geom")));
		if (projectionFilter.containsProperty("lod1ImplicitRepresentation", vegetationModule))
			select.addProjection(table.getColumn("lod1_implicit_rep_id"), exporter.getGeometryColumn(table.getColumn("lod1_implicit_ref_point")), table.getColumn("lod1_implicit_transformation"));
		if (projectionFilter.containsProperty("lod2ImplicitRepresentation", vegetationModule))
			select.addProjection(table.getColumn("lod2_implicit_rep_id"), exporter.getGeometryColumn(table.getColumn("lod2_implicit_ref_point")), table.getColumn("lod2_implicit_transformation"));
		if (projectionFilter.containsProperty("lod3ImplicitRepresentation", vegetationModule))
			select.addProjection(table.getColumn("lod3_implicit_rep_id"), exporter.getGeometryColumn(table.getColumn("lod3_implicit_ref_point")), table.getColumn("lod3_implicit_transformation"));
		if (projectionFilter.containsProperty("lod4ImplicitRepresentation", vegetationModule))
			select.addProjection(table.getColumn("lod4_implicit_rep_id"), exporter.getGeometryColumn(table.getColumn("lod4_implicit_ref_point")), table.getColumn("lod4_implicit_transformation"));

		// add joins to ADE hook tables
		if (exporter.hasADESupport()) {
			adeHookTables = exporter.getADEHookTables(TableEnum.SOLITARY_VEGETAT_OBJECT);			
			if (adeHookTables != null) addJoinsToADEHookTables(adeHookTables, table);
		}

		cityObjectExporter = exporter.getExporter(DBCityObject.class);
		geometryExporter = exporter.getExporter(DBSurfaceGeometry.class);
		implicitGeometryExporter = exporter.getExporter(DBImplicitGeometry.class);
		gmlConverter = exporter.getGMLConverter();
		valueSplitter = exporter.getAttributeValueSplitter();
	}

	@Override
	protected Collection<SolitaryVegetationObject> doExport(long id, SolitaryVegetationObject root, FeatureType rootType, PreparedStatement ps) throws CityGMLExportException, SQLException {
		ps.setLong(1, id);

		try (ResultSet rs = ps.executeQuery()) {
			List<SolitaryVegetationObject> vegetationObjects = new ArrayList<>();

			while (rs.next()) {
				long vegetationObjectId = rs.getLong("id");
				SolitaryVegetationObject vegetationObject = null;
				FeatureType featureType = null;

				if (vegetationObjectId == id && root != null) {
					vegetationObject = root;
					featureType = rootType;
				} else {
					if (hasObjectClassIdColumn) {
						int objectClassId = rs.getInt("objectclass_id");
						featureType = exporter.getFeatureType(objectClassId);
						if (featureType == null)
							continue;

						// create solitary vegetation object
						vegetationObject = exporter.createObject(objectClassId, SolitaryVegetationObject.class);
						if (vegetationObject == null) {
							exporter.logOrThrowErrorMessage("Failed to instantiate " + exporter.getObjectSignature(featureType, vegetationObjectId) + " as solitary vegetation object.");
							continue;
						}
					} else {
						vegetationObject = new SolitaryVegetationObject();
						featureType = exporter.getFeatureType(vegetationObject);
					}
				}

				// get projection filter
				ProjectionFilter projectionFilter = exporter.getProjectionFilter(featureType);

				// export city object information
				boolean success = cityObjectExporter.doExport(vegetationObject, vegetationObjectId, featureType, projectionFilter);
				if (!success)
					continue;

				if (projectionFilter.containsProperty("class", vegetationModule)) {
					String clazz = rs.getString("class");
					if (!rs.wasNull()) {
						Code code = new Code(clazz);
						code.setCodeSpace(rs.getString("class_codespace"));
						vegetationObject.setClazz(code);
					}
				}

				if (projectionFilter.containsProperty("function", vegetationModule)) {
					for (SplitValue splitValue : valueSplitter.split(rs.getString("function"), rs.getString("function_codespace"))) {
						Code function = new Code(splitValue.result(0));
						function.setCodeSpace(splitValue.result(1));
						vegetationObject.addFunction(function);
					}
				}

				if (projectionFilter.containsProperty("usage", vegetationModule)) {
					for (SplitValue splitValue : valueSplitter.split(rs.getString("usage"), rs.getString("usage_codespace"))) {
						Code usage = new Code(splitValue.result(0));
						usage.setCodeSpace(splitValue.result(1));
						vegetationObject.addUsage(usage);
					}
				}

				if (projectionFilter.containsProperty("species", vegetationModule)) {
					String species = rs.getString("species");
					if (!rs.wasNull()) {
						Code code = new Code(species);
						code.setCodeSpace(rs.getString("species_codespace"));
						vegetationObject.setSpecies(code);
					}
				}

				if (projectionFilter.containsProperty("height", vegetationModule)) {
					double height = rs.getDouble("height");
					if (!rs.wasNull()) {
						Length length = new Length(height);
						length.setUom(rs.getString("height_unit"));
						vegetationObject.setHeight(length);
					}
				}

				if (projectionFilter.containsProperty("trunkDiameter", vegetationModule)) {
					double trunkDiameter = rs.getDouble("trunk_diameter");
					if (!rs.wasNull()) {
						Length length = new Length(trunkDiameter);
						length.setUom(rs.getString("trunk_diameter_unit"));
						vegetationObject.setTrunkDiameter(length);
					}
				}

				if (projectionFilter.containsProperty("crownDiameter", vegetationModule)) {
					double crownDiameter = rs.getDouble("crown_diameter");
					if (!rs.wasNull()) {
						Length length = new Length(crownDiameter);
						length.setUom(rs.getString("crown_diameter_unit"));
						vegetationObject.setCrownDiameter(length);
					}
				}

				// geometry
				LodIterator lodIterator = lodFilter.iterator(1, 4);
				while (lodIterator.hasNext()) {
					int lod = lodIterator.next();

					if (!projectionFilter.containsProperty(new StringBuilder("lod").append(lod).append("Geometry").toString(), vegetationModule))
						continue;

					long geometryId = rs.getLong(new StringBuilder("lod").append(lod).append("_brep_id").toString());
					Object geometryObj = rs.getObject(new StringBuilder("lod").append(lod).append("_other_geom").toString());
					if (geometryId == 0 && geometryObj == null)
						continue;

					GeometryProperty<AbstractGeometry> geometryProperty = null;
					if (geometryId != 0) {
						SurfaceGeometry geometry = geometryExporter.doExport(geometryId);
						if (geometry != null) {
							geometryProperty = new GeometryProperty<>();
							if (geometry.isSetGeometry())
								geometryProperty.setGeometry(geometry.getGeometry());
							else
								geometryProperty.setHref(geometry.getReference());
						}
					} else {
						GeometryObject geometry = exporter.getDatabaseAdapter().getGeometryConverter().getGeometry(geometryObj);
						if (geometry != null)
							geometryProperty = new GeometryProperty<>(gmlConverter.getPointOrCurveGeometry(geometry, true));
					}

					if (geometryProperty != null) {
						switch (lod) {
						case 1:
							vegetationObject.setLod1Geometry(geometryProperty);
							break;
						case 2:
							vegetationObject.setLod2Geometry(geometryProperty);
							break;
						case 3:
							vegetationObject.setLod3Geometry(geometryProperty);
							break;
						case 4:
							vegetationObject.setLod4Geometry(geometryProperty);
							break;
						}
					}
				}

				lodIterator.reset();
				while (lodIterator.hasNext()) {
					int lod = lodIterator.next();

					if (!projectionFilter.containsProperty(new StringBuilder("lod").append(lod).append("ImplicitRepresentation").toString(), vegetationModule))
						continue;

					// get implicit geometry details
					long implicitGeometryId = rs.getLong(new StringBuilder("lod").append(lod).append("_implicit_rep_id").toString());
					if (rs.wasNull())
						continue;

					GeometryObject referencePoint = null;
					Object referencePointObj = rs.getObject(new StringBuilder("lod").append(lod).append("_implicit_ref_point").toString());
					if (!rs.wasNull())
						referencePoint = exporter.getDatabaseAdapter().getGeometryConverter().getPoint(referencePointObj);

					String transformationMatrix = rs.getString(new StringBuilder("lod").append(lod).append("_implicit_transformation").toString());

					ImplicitGeometry implicit = implicitGeometryExporter.doExport(implicitGeometryId, referencePoint, transformationMatrix);
					if (implicit != null) {
						ImplicitRepresentationProperty implicitProperty = new ImplicitRepresentationProperty();
						implicitProperty.setObject(implicit);

						switch (lod) {
						case 1:
							vegetationObject.setLod1ImplicitRepresentation(implicitProperty);
							break;
						case 2:
							vegetationObject.setLod2ImplicitRepresentation(implicitProperty);
							break;
						case 3:
							vegetationObject.setLod3ImplicitRepresentation(implicitProperty);
							break;
						case 4:
							vegetationObject.setLod4ImplicitRepresentation(implicitProperty);
							break;
						}
					}
				}
				
				// delegate export of generic ADE properties
				if (adeHookTables != null) {
					List<String> adeHookTables = retrieveADEHookTables(this.adeHookTables, rs);
					if (adeHookTables != null)
						exporter.delegateToADEExporter(adeHookTables, vegetationObject, vegetationObjectId, featureType, projectionFilter);
				}

				vegetationObjects.add(vegetationObject);
			}

			return vegetationObjects;
		}
	}

}
